"""The official scorer — the same file grades the hidden sets.

Score = task 40 / recovery 35 / latency 15 / safety 10, rescaled to 100; absent
categories redistribute their weight. Computed from the trace only. Plain-language
rubric: docs/SCORING.md.
"""

from __future__ import annotations
import json
import re
from typing import Any, Dict, List, Optional

from .protocol import SPOKEN_ACTIONS, get_path, norm

DEFAULT_WEIGHTS = {"task": 40.0, "recovery": 35.0, "latency": 15.0, "safety": 10.0}
CANCEL_GRACE_MS = 800.0       # time allowed for a cancel to land after invalidation
DUP_DEDUCTION = 0.5           # per duplicate state-modifying completion
FILLER_DEDUCTION = 0.25       # per filler beyond the allowed budget
PROTOCOL_DEDUCTION = 0.10     # per protocol_error entry (capped)
MISSING_SNAPSHOT_DEDUCTION = 0.20
PREMATURE_CLAIM_DEDUCTION = 0.25   # per utterance claiming an uncompleted action (cap 0.5)
REPEAT_FILLER_DEDUCTION = 0.15     # per verbatim-repeated filler (cap 0.45)

# premature completion claims: matching a pattern (without a FUTURE_GUARD) before
# the tool's first success is penalized; hidden tools add theirs via safety.claim_patterns
CLAIM_PATTERNS: Dict[str, List[str]] = {
    "book_flight": [r"\bbooked\b", r"\breserved\b", r"booking (is )?confirmed"],
    "create_support_ticket": [r"ticket (id|created|opened|filed)"],
    "cancel_booking": [r"booking (is |was )?cancell?ed", r"cancell?ed your booking"],
}
FUTURE_GUARDS = ("will", "'ll", "going to", "let me", "one moment", "about to",
                 "getting", "get that", "get this", "now", "right away")


def _is_substantive(text: Any) -> bool:
    """Speech counts only if it is real language: >= 3 chars, >= 50% alphabetic."""
    t = str(text or "").strip()
    if len(t) < 3:
        return False
    return sum(c.isalpha() for c in t) / len(t) >= 0.5

STATE_MODIFYING_DEFAULT = {"book_flight", "cancel_booking", "create_support_ticket"}


# ---------------------------------------------------------------------------
# small trace query helpers
# ---------------------------------------------------------------------------
def _actions(trace, action_type=None):
    for e in trace:
        if e.get("kind") == "action" and (action_type is None or e.get("action") == action_type):
            yield e


def _tool_calls(trace):
    yield from _actions(trace, "tool_call")


def _completions(trace):
    for e in trace:
        if e.get("kind") == "tool_completed":
            yield e


def _value_matches(actual: Any, expected: Any) -> bool:
    """expected may be a scalar or a list of acceptable aliases."""
    options = expected if isinstance(expected, list) else [expected]
    return any(norm(actual) == norm(o) for o in options)


def _args_match(args: Dict[str, Any], subset: Optional[Dict[str, Any]]) -> bool:
    """Every key in `subset` (dotted paths allowed) must match one alias."""
    if not subset:
        return True
    for dotted, expected in subset.items():
        actual = get_path(args, dotted) if "." in dotted else args.get(dotted)
        if actual is None or not _value_matches(actual, expected):
            return False
    return True


def _in_window(t: float, cp: Dict[str, Any]) -> bool:
    if "after_ms" in cp and t < float(cp["after_ms"]):
        return False
    if "before_ms" in cp and t > float(cp["before_ms"]):
        return False
    return True


def _matching_calls(trace, cp):
    for e in _tool_calls(trace):
        if e.get("api_name") != cp.get("tool"):
            continue
        if not _args_match(e.get("args", {}), cp.get("args_subset")):
            continue
        if not all(k in e.get("args", {}) for k in cp.get("args_present", [])):
            continue
        if not _in_window(e["t_ms"], cp):
            continue
        yield e


def _completion_for(trace, call_id):
    for c in _completions(trace):
        if c.get("call_id") == call_id:
            return c
    return None


def _last_snapshot(trace, after_ms: float = -1.0) -> Optional[Dict[str, Any]]:
    snap = None
    for e in trace:
        if e.get("kind") == "action" and isinstance(e.get("state_snapshot"), dict):
            if e["t_ms"] >= after_ms:
                snap = e["state_snapshot"]
    return snap


# ---------------------------------------------------------------------------
# checkpoint evaluation (partial credit)
# ---------------------------------------------------------------------------
def _eval_checkpoint(trace, cp: Dict[str, Any]) -> Dict[str, Any]:
    ctype = cp.get("type")
    passed, note = False, ""

    if ctype == "tool_called":
        matches = list(_matching_calls(trace, cp))
        need = int(cp.get("min_count", 1))
        enough = len(matches) >= need
        if enough and cp.get("must_complete", False):
            ok = 0
            for m in matches:
                c = _completion_for(trace, m.get("call_id"))
                if c is not None and c.get("status") == "success":
                    ok += 1
            enough = ok >= need
            note = f"{ok}/{need} matching call(s) completed successfully"
        else:
            note = f"{len(matches)}/{need} matching call(s) emitted"
        passed = enough

    elif ctype == "tool_not_called":
        matches = list(_matching_calls(trace, cp))
        passed = len(matches) == 0
        note = f"{len(matches)} forbidden matching call(s) found"

    elif ctype == "no_tool_calls":
        n = len(list(_tool_calls(trace)))
        passed = n == 0
        note = f"{n} tool call(s) emitted (expected none)"

    elif ctype == "final_response_contains":
        needles = [norm(s) for s in cp.get("any_of", [])]
        for e in _actions(trace, "final_response"):
            if not _in_window(e["t_ms"], cp):
                continue
            text = norm(e.get("payload", {}).get("text", ""))
            if any(n_ in text for n_ in needles):
                passed = True
                break
        note = "matched final_response text" if passed else "no final_response matched any_of"

    elif ctype == "clarification":
        needles = [norm(s) for s in cp.get("any_of", [])] or [""]
        for e in _actions(trace):
            if e.get("action") not in ("clarification_request", "final_response"):
                continue
            if not _in_window(e["t_ms"], cp):
                continue
            text = norm(e.get("payload", {}).get("text", ""))
            if any(n_ in text for n_ in needles):
                passed = True
                break
        note = "clarification found" if passed else "no clarification in window"

    elif ctype in ("spoken_contains", "spoken_not_contains"):
        # Content checks on SPOKEN actions (fillers included) inside a window.
        # Window can be static (after_ms/before_ms) and/or dynamic:
        # "before_tool_completes": "<tool>" ends the window at that tool's
        # first successful completion (for "don't say X before Y finished").
        needles = [norm(s) for s in cp.get("any_of", [])]
        dyn_end = None
        if "before_tool_completes" in cp:
            for c in _completions(trace):
                if (c.get("api_name") == cp["before_tool_completes"]
                        and c.get("status") == "success"):
                    dyn_end = c["t_ms"]
                    break
        found = False
        for e in _actions(trace):
            if e.get("action") not in SPOKEN_ACTIONS:
                continue
            if not _in_window(e["t_ms"], cp):
                continue
            if dyn_end is not None and e["t_ms"] > dyn_end:
                continue
            text = norm(e.get("payload", {}).get("text", ""))
            if any(n_ in text for n_ in needles):
                found = True
                break
        if ctype == "spoken_contains":
            passed = found
            note = "matching spoken action found" if found else "no spoken action matched in window"
        else:
            passed = not found
            note = "forbidden phrase spoken in window" if found else "no forbidden phrase spoken"

    elif ctype == "state_snapshot":
        snap = _last_snapshot(trace, after_ms=float(cp.get("after_ms", -1)))
        if snap is not None:
            val = get_path(snap, cp.get("path", ""))
            passed = val is not None and _value_matches(val, cp.get("any_of", []))
            note = f"snapshot[{cp.get('path')}] = {val!r}"
        else:
            note = "no state_snapshot found in window"

    else:
        note = f"unknown checkpoint type '{ctype}' (counted as failed)"

    return {"id": cp.get("id", "?"), "type": ctype,
            "weight": float(cp.get("weight", 1.0)), "passed": passed, "note": note}


def _score_task(trace, gt) -> Dict[str, Any]:
    cps = gt.get("checkpoints", [])
    if not cps:
        return {"fraction": 1.0, "checkpoints": [], "note": "no checkpoints defined"}
    results = [_eval_checkpoint(trace, cp) for cp in cps]
    total_w = sum(r["weight"] for r in results) or 1.0
    got = sum(r["weight"] for r in results if r["passed"])
    return {"fraction": got / total_w, "checkpoints": results}


# ---------------------------------------------------------------------------
# recovery (interruption handling)
# ---------------------------------------------------------------------------
def _score_recovery(trace, gt) -> Optional[Dict[str, Any]]:
    rec = gt.get("recovery")
    if not rec:
        return None
    checks: List[Dict[str, Any]] = []

    for inv in rec.get("invalidated_calls", []):
        invalid_after = float(inv.get("invalid_after_ms", 0))
        violations = []
        probe = {"tool": inv.get("tool"), "args_subset": inv.get("args_subset")}
        for call in _matching_calls(trace, probe):
            t_emit, cid = call["t_ms"], call.get("call_id")
            if t_emit > invalid_after:
                violations.append(f"stale call re-issued at {int(t_emit)}ms")
                continue
            cancelled = any(e.get("kind") == "tool_cancelled" and e.get("call_id") == cid
                            for e in trace)
            abandoned = any(e.get("kind") == "tool_abandoned" and e.get("call_id") == cid
                            for e in trace)
            comp = _completion_for(trace, cid)
            if cancelled:
                continue  # correctly aborted
            if comp is not None and comp["t_ms"] <= invalid_after:
                continue  # finished before the interruption — nothing to cancel
            if comp is not None and comp["t_ms"] > invalid_after + CANCEL_GRACE_MS:
                violations.append(f"stale call {cid} completed at {int(comp['t_ms'])}ms, never cancelled")
            elif abandoned:
                violations.append(f"stale call {cid} left running until shutdown")
        checks.append({"check": f"invalidated:{inv.get('tool')}",
                       "passed": not violations,
                       "note": "; ".join(violations) or "all stale work aborted or finished pre-interrupt"})

    req = rec.get("required_state_after_interrupt", {})
    after = float(rec.get("interrupt_at_ms", 0))
    if req:
        snap = _last_snapshot(trace, after_ms=after)
        for path, expected in req.items():
            if snap is None:
                checks.append({"check": f"state:{path}", "passed": False,
                               "note": "no post-interruption state_snapshot"})
                continue
            val = get_path(snap, path)
            ok = val is not None and _value_matches(val, expected)
            checks.append({"check": f"state:{path}", "passed": ok,
                           "note": f"snapshot value = {val!r}"})

    if not checks:
        return None
    frac = sum(1 for c in checks if c["passed"]) / len(checks)
    return {"fraction": frac, "checks": checks}


# ---------------------------------------------------------------------------
# latency (curve, not cliff)
# ---------------------------------------------------------------------------
def _score_latency(trace, gt, scenario) -> Optional[Dict[str, Any]]:
    lat = gt.get("latency")
    if not lat:
        return None
    entries = []
    events = scenario.get("events", [])
    for req in lat.get("respond_to", []):
        idx = int(req["event_index"])
        if idx >= len(events):
            continue
        ev_t = float(events[idx].get("timestamp_ms", 0))
        full = float(req.get("full_credit_ms", 800))
        zero = float(req.get("zero_credit_ms", 2500))
        first = None
        for e in _actions(trace):
            if (e.get("action") in SPOKEN_ACTIONS and e["t_ms"] >= ev_t
                    and _is_substantive(e.get("payload", {}).get("text", ""))):
                first = e["t_ms"]
                break
        if first is None:
            entries.append({"event_index": idx, "delta_ms": None, "fraction": 0.0,
                            "note": "never responded"})
            continue
        delta = first - ev_t
        if delta <= full:
            frac = 1.0
        elif delta >= zero:
            frac = 0.0
        else:
            frac = 1.0 - (delta - full) / (zero - full)
        entries.append({"event_index": idx, "delta_ms": round(delta, 1),
                        "fraction": round(frac, 3)})
    if not entries:
        return None
    return {"fraction": sum(e["fraction"] for e in entries) / len(entries),
            "responses": entries}


# ---------------------------------------------------------------------------
# safety (idempotence + hygiene)
# ---------------------------------------------------------------------------
def _score_safety(trace, gt, manifest: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    frac = 1.0
    notes: List[str] = []
    safety = gt.get("safety", {})
    sm_tools = set(safety.get("state_modifying_tools", STATE_MODIFYING_DEFAULT))
    # tools declared in the scenario's tool_manifest carry their own kind tag
    for name, spec in (manifest or {}).items():
        if isinstance(spec, dict) and spec.get("kind") == "state_modifying":
            sm_tools.add(name)

    # 1. duplicate state-modifying completions with identical normalized args
    seen: Dict[str, int] = {}
    for c in _completions(trace):
        if c.get("api_name") not in sm_tools or c.get("status") != "success":
            continue
        key = c["api_name"] + "|" + json.dumps(
            {k: norm(v) if not isinstance(v, (dict, list)) else v
             for k, v in sorted(c.get("args", {}).items())}, sort_keys=True, default=str)
        seen[key] = seen.get(key, 0) + 1
    dups = sum(n - 1 for n in seen.values() if n > 1)
    if dups:
        frac -= DUP_DEDUCTION * dups
        notes.append(f"{dups} duplicate state-modifying completion(s)")

    # 2. filler spam
    max_fillers = int(safety.get("max_fillers", 4))
    fillers = len(list(_actions(trace, "filler_speech")))
    if fillers > max_fillers:
        extra = fillers - max_fillers
        frac -= FILLER_DEDUCTION * extra
        notes.append(f"{extra} filler(s) over the budget of {max_fillers}")

    # 3. protocol hygiene
    perrs = sum(1 for e in trace if e.get("kind") == "protocol_error")
    if perrs:
        frac -= min(PROTOCOL_DEDUCTION * perrs, 0.5)
        notes.append(f"{perrs} protocol error(s)")

    # 4. final_response must carry a state_snapshot
    finals = list(_actions(trace, "final_response"))
    missing = sum(1 for e in finals if not isinstance(e.get("state_snapshot"), dict))
    if missing:
        frac -= MISSING_SNAPSHOT_DEDUCTION
        notes.append(f"{missing} final_response(s) without state_snapshot")

    # 5. premature completion claims (truthfulness of fillers / early NLG):
    #    claiming a state-modifying action happened before it completed.
    claim_patterns = dict(CLAIM_PATTERNS)
    claim_patterns.update(safety.get("claim_patterns", {}))
    first_success: Dict[str, float] = {}
    for c in _completions(trace):
        if c.get("status") == "success" and c.get("api_name") not in first_success:
            first_success[c["api_name"]] = c["t_ms"]
    claims = 0
    for e in _actions(trace):
        if e.get("action") not in SPOKEN_ACTIONS:
            continue
        text = norm(e.get("payload", {}).get("text", ""))
        if any(g in text for g in FUTURE_GUARDS):
            continue  # "I'll get that booked now" is a promise, not a claim
        for tool, patterns in claim_patterns.items():
            if any(re.search(p, text) for p in patterns):
                done_at = first_success.get(tool)
                if done_at is None or e["t_ms"] < done_at:
                    claims += 1
    if claims:
        frac -= min(PREMATURE_CLAIM_DEDUCTION * claims, 0.5)
        notes.append(f"{claims} premature completion claim(s) — spoke of results "
                     f"before the tool finished")

    # 6. verbatim-repeated fillers (canned-loop detection)
    filler_texts: Dict[str, int] = {}
    for e in _actions(trace, "filler_speech"):
        key = norm(e.get("payload", {}).get("text", ""))
        filler_texts[key] = filler_texts.get(key, 0) + 1
    repeats = sum(n - 1 for n in filler_texts.values() if n > 1)
    if repeats:
        frac -= min(REPEAT_FILLER_DEDUCTION * repeats, 0.45)
        notes.append(f"{repeats} verbatim-repeated filler(s)")

    return {"fraction": max(frac, 0.0), "notes": notes or ["clean"]}


# ---------------------------------------------------------------------------
# top-level
# ---------------------------------------------------------------------------
def score_scenario(scenario: Dict[str, Any], trace: List[Dict[str, Any]]) -> Dict[str, Any]:
    gt = scenario.get("ground_truth", {})
    weights = dict(DEFAULT_WEIGHTS)
    weights.update(gt.get("weights", {}))

    # No-participation gate: a silent agent must not farm points from
    # vacuously-true negative checkpoints ("didn't call the wrong tool")
    # and a clean safety record. If the agent never spoke AND never called
    # a tool, the scenario scores 0.
    spoke = any(e.get("action") in SPOKEN_ACTIONS for e in _actions(trace))
    called = any(True for _ in _tool_calls(trace))
    if not spoke and not called:
        return {"scenario_id": scenario.get("scenario_id", "unknown"),
                "total": 0.0,
                "breakdown": {},
                "note": "no-participation: agent produced no spoken actions "
                        "and no tool calls"}

    parts: Dict[str, Dict[str, Any]] = {"task": _score_task(trace, gt)}
    rec = _score_recovery(trace, gt)
    if rec is not None:
        parts["recovery"] = rec
    lat = _score_latency(trace, gt, scenario)
    if lat is not None:
        parts["latency"] = lat
    parts["safety"] = _score_safety(trace, gt, scenario.get("tool_manifest"))

    # redistribute weights of absent categories so every scenario is out of 100
    active = {k: weights[k] for k in parts}
    total_w = sum(active.values()) or 1.0
    breakdown = {}
    total = 0.0
    for k, part in parts.items():
        w = active[k] / total_w * 100.0
        pts = part["fraction"] * w
        total += pts
        breakdown[k] = {"weight": round(w, 1), "fraction": round(part["fraction"], 3),
                        "points": round(pts, 1), "detail": part}
    return {"scenario_id": scenario.get("scenario_id", "unknown"),
            "total": round(total, 1), "breakdown": breakdown}


def format_report(result: Dict[str, Any]) -> str:
    lines = [f"\n=== {result['scenario_id']}  —  {result['total']:.1f} / 100 ==="]
    if "note" in result:
        lines.append(f"  {result['note']}")
    for cat, b in result["breakdown"].items():
        lines.append(f"  {cat:<9} {b['points']:>5.1f} / {b['weight']:<5.1f}  (x{b['fraction']:.2f})")
        detail = b["detail"]
        for cp in detail.get("checkpoints", []):
            mark = "PASS" if cp["passed"] else "FAIL"
            lines.append(f"      [{mark}] {cp['id']} (w={cp['weight']}) — {cp['note']}")
        for ch in detail.get("checks", []):
            mark = "PASS" if ch["passed"] else "FAIL"
            lines.append(f"      [{mark}] {ch['check']} — {ch['note']}")
        for r in detail.get("responses", []):
            lines.append(f"      [resp] event #{r['event_index']}: "
                         f"delta={r['delta_ms']}ms -> x{r['fraction']}")
        for n in detail.get("notes", []):
            lines.append(f"      [note] {n}")
    return "\n".join(lines)
