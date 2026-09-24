#!/usr/bin/env python3
"""The official submission evaluator. Dry-run your package before you submit.

Stages: 1) submission.yaml + entry point validation, 2) contract smoke test
(setup() completes, run() stays alive), 3) every scenario x N reps, per-scenario
median, wall cap 120 s (setup() under its own 300 s cap), weighted average
(audio/visual x1.5, L3/L4 x1.25), 4) optional --expect comparison.

    python eval_submission.py path/to/your-submission                      # official: real time, 3 reps
    python eval_submission.py path/to/your-submission --time-scale 8 --reps 1
    python eval_submission.py path/to/your-submission --out results.json
"""

from __future__ import annotations
import argparse
import asyncio
import glob
import importlib
import inspect
import json
import os
import statistics
import sys
from typing import Any, Dict, List, Optional, Tuple

KIT_ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, KIT_ROOT)

from harness.runner import EvaluationHarness           # noqa: E402
from harness.scorer import score_scenario              # noqa: E402

TOLERANCE = 2.0  # points, for --expect comparison


def _strip_inline_comment(line: str) -> str:
    """Drop a trailing ' # comment' that is not inside quotes."""
    quote = None
    for i, ch in enumerate(line):
        if quote:
            if ch == quote:
                quote = None
        elif ch in ("'", '"'):
            quote = ch
        elif ch == "#" and (i == 0 or line[i - 1].isspace()):
            return line[:i].rstrip()
    return line


def parse_submission_yaml(path: str) -> Dict[str, Any]:
    """Minimal YAML subset: top-level key: value and '- item' lists; keeps the kit dependency-free."""
    config: Dict[str, Any] = {}
    current_list: Optional[str] = None
    with open(path) as f:
        for raw in f:
            line = raw.rstrip("\n")
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            stripped = _strip_inline_comment(stripped)
            if stripped.startswith("- "):
                if current_list is None:
                    raise ValueError(f"list item outside a list: {line!r}")
                config[current_list].append(stripped[2:].strip().strip('"').strip("'"))
                continue
            if ":" not in stripped:
                raise ValueError(f"cannot parse line: {line!r}")
            key, _, value = stripped.partition(":")
            key, value = key.strip(), value.strip()
            if value in ("", "[]"):
                config[key] = []
                current_list = key if value == "" else None
            else:
                config[key] = value.strip('"').strip("'")
                current_list = None
    return config


# ---------------------------------------------------------------------------
# stage 1: package validation
# ---------------------------------------------------------------------------
def validate_submission(sub_dir: str) -> Tuple[Optional[Dict], Optional[Any], List[str]]:
    errors: List[str] = []
    yaml_path = os.path.join(sub_dir, "submission.yaml")
    if not os.path.isfile(yaml_path):
        return None, None, [f"missing {yaml_path}"]
    try:
        config = parse_submission_yaml(yaml_path)
    except ValueError as e:
        return None, None, [f"submission.yaml parse error: {e}"]

    for field in ("team", "entry_point"):
        if not config.get(field):
            errors.append(f"submission.yaml missing required field '{field}'")
    if errors:
        return config, None, errors

    entry = config["entry_point"]
    module_name, _, class_name = entry.partition(":")
    if not module_name or not class_name:
        return config, None, [f"entry_point must be 'module:Class' (got {entry!r})"]

    sys.path.insert(0, os.path.abspath(sub_dir))
    try:
        module = importlib.import_module(module_name)
    except Exception as e:
        return config, None, [f"cannot import module '{module_name}': {type(e).__name__}: {e}"]
    cls = getattr(module, class_name, None)
    if cls is None:
        return config, None, [f"module '{module_name}' has no class '{class_name}'"]

    try:
        agent = cls(asyncio.Queue(), asyncio.Queue())
    except Exception as e:
        return config, None, [f"cannot construct {class_name}(in_queue, out_queue): "
                              f"{type(e).__name__}: {e}"]
    if not hasattr(agent, "run") or not inspect.iscoroutinefunction(agent.run):
        return config, None, [f"{class_name}.run must be an 'async def' method"]
    return config, cls, []


# ---------------------------------------------------------------------------
# stage 2: contract smoke test
# ---------------------------------------------------------------------------
def contract_smoke_test(cls, setup_cap_s: float) -> List[str]:
    async def probe() -> List[str]:
        in_q: asyncio.Queue = asyncio.Queue()
        out_q: asyncio.Queue = asyncio.Queue()
        agent = cls(in_q, out_q)
        setup = getattr(agent, "setup", None)
        if callable(setup):
            try:
                res = setup()
                if inspect.isawaitable(res):
                    await asyncio.wait_for(res, timeout=setup_cap_s)
            except asyncio.TimeoutError:
                return [f"setup() exceeded the setup cap ({setup_cap_s}s)"]
            except Exception as exc:
                return [f"setup() crashed: {type(exc).__name__}: {exc}"]
        task = asyncio.create_task(agent.run())
        await in_q.put({"timestamp_ms": 0, "event_type": "user_speech_chunk",
                        "payload": {"text": "hello", "end_of_turn": False}})
        await asyncio.sleep(0.25)
        problems: List[str] = []
        if task.done():
            exc = task.exception()
            problems.append("agent's run() returned immediately"
                            if exc is None else
                            f"agent crashed on first event: {type(exc).__name__}: {exc}")
        else:
            task.cancel()
            try:
                await task
            except (asyncio.CancelledError, Exception):
                pass
        return problems
    return asyncio.run(probe())


# ---------------------------------------------------------------------------
# stage 3: scored evaluation
# ---------------------------------------------------------------------------
def scenario_weight(meta: Dict[str, Any]) -> float:
    w = 1.0
    if meta.get("modality") in ("audio", "visual"):
        w *= 1.5
    if meta.get("difficulty") in ("L3", "L4"):
        w *= 1.25
    return w


def run_once(scenario: Dict, cls, time_scale: float, wall_cap_s: float,
             setup_cap_s: float) -> Dict[str, Any]:
    async def go():
        h = EvaluationHarness(scenario, lambda a, b: cls(a, b),
                              time_scale=time_scale, verbose=False)
        try:
            await asyncio.wait_for(h.prepare(), timeout=setup_cap_s)
        except asyncio.TimeoutError:
            return None
        return await asyncio.wait_for(h.run(), timeout=wall_cap_s)
    try:
        trace = asyncio.run(go())
    except asyncio.TimeoutError:
        return {"scenario_id": scenario.get("scenario_id"), "total": 0.0,
                "breakdown": {}, "note": f"wall-clock cap ({wall_cap_s}s) exceeded"}
    if trace is None:
        return {"scenario_id": scenario.get("scenario_id"), "total": 0.0,
                "breakdown": {}, "note": f"setup cap ({setup_cap_s}s) exceeded"}
    return score_scenario(scenario, trace)


def evaluate(cls, scenario_paths: List[str], reps: int,
             time_scale: float, wall_cap_s: float,
             setup_cap_s: float = 300.0) -> Dict[str, Any]:
    rows = []
    for path in scenario_paths:
        with open(path) as f:
            scenario = json.load(f)
        meta = scenario.get("metadata", {})
        results = [run_once(scenario, cls, time_scale, wall_cap_s, setup_cap_s)
                   for _ in range(reps)]
        totals = [r["total"] for r in results]
        med = statistics.median(totals)
        median_run = min(results, key=lambda r: abs(r["total"] - med))
        rows.append({
            "scenario_id": scenario.get("scenario_id"),
            "modality": meta.get("modality"),
            "difficulty": meta.get("difficulty"),
            "weight": scenario_weight(meta),
            "rep_totals": totals,
            "median_total": round(med, 1),
            "median_breakdown": {k: v["points"] for k, v in
                                 median_run.get("breakdown", {}).items()},
        })
        print(f"  {rows[-1]['median_total']:>5.1f}  (reps: "
              f"{', '.join(f'{t:.1f}' for t in totals)})"
              f"  [{meta.get('modality')}/{meta.get('difficulty')} "
              f"x{rows[-1]['weight']:.2f}]  {rows[-1]['scenario_id']}")

    wsum = sum(r["weight"] for r in rows) or 1.0
    weighted = sum(r["median_total"] * r["weight"] for r in rows) / wsum
    plain = sum(r["median_total"] for r in rows) / len(rows)
    by_mod: Dict[str, List[float]] = {}
    for r in rows:
        by_mod.setdefault(r["modality"] or "?", []).append(r["median_total"])
    return {
        "scenarios": rows,
        "summary": {
            "plain_average": round(plain, 1),
            "weighted_score": round(weighted, 1),
            "by_modality": {m: round(sum(v) / len(v), 1) for m, v in by_mod.items()},
        },
    }


# ---------------------------------------------------------------------------
# stage 4: system self-check
# ---------------------------------------------------------------------------
def compare_expected(report: Dict, expected_path: str) -> bool:
    with open(expected_path) as f:
        expected = json.load(f)
    ok = True
    exp_rows = {r["scenario_id"]: r for r in expected.get("scenarios", [])}
    for row in report["scenarios"]:
        exp = exp_rows.get(row["scenario_id"])
        if exp is None:
            print(f"  [WARN] no expectation stored for {row['scenario_id']}")
            continue
        diff = abs(row["median_total"] - exp["median_total"])
        mark = "OK  " if diff <= TOLERANCE else "FAIL"
        if diff > TOLERANCE:
            ok = False
        print(f"  [{mark}] {row['scenario_id']}: got {row['median_total']}, "
              f"expected {exp['median_total']} (diff {diff:.1f})")
    got_w = report["summary"]["weighted_score"]
    exp_w = expected["summary"]["weighted_score"]
    if abs(got_w - exp_w) > TOLERANCE:
        ok = False
        print(f"  [FAIL] weighted score: got {got_w}, expected {exp_w}")
    else:
        print(f"  [OK  ] weighted score: got {got_w}, expected {exp_w}")
    return ok


# ---------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("submission_dir", help="path to the submission package")
    ap.add_argument("--scenarios", default=os.path.join(KIT_ROOT, "scenarios"),
                    help="scenario directory (organizers point this at the hidden set)")
    ap.add_argument("--reps", type=int, default=3)
    ap.add_argument("--time-scale", type=float, default=1.0,
                    help="official = 1.0; use 8 for fast pipeline smoke tests")
    ap.add_argument("--wall-cap", type=float, default=120.0,
                    help="real seconds allowed per scenario run")
    ap.add_argument("--setup-cap", type=float, default=300.0,
                    help="real seconds allowed for the agent's optional setup() "
                         "(model loading) before each scenario; not charged to --wall-cap")
    ap.add_argument("--out", help="write results JSON here")
    ap.add_argument("--expect", help="expected_results.json for the system self-check")
    args = ap.parse_args()

    print("=" * 62)
    print("STAGE 1 — package validation")
    config, cls, errors = validate_submission(args.submission_dir)
    if errors:
        for e in errors:
            print(f"  [FAIL] {e}")
        print("\nVERDICT: INVALID SUBMISSION (would score 0 — fix before you submit)")
        sys.exit(1)
    print(f"  [OK] team={config['team']!r}  entry_point={config['entry_point']!r}")
    if config.get("requirements"):
        print(f"  [OK] requirements declared: {config['requirements']} "
              f"(installed by the portal, not by this script)")

    print("STAGE 2 — contract smoke test")
    problems = contract_smoke_test(cls, args.setup_cap)
    if problems:
        for p in problems:
            print(f"  [FAIL] {p}")
        print("\nVERDICT: INVALID SUBMISSION (would score 0 — fix before you submit)")
        sys.exit(1)
    print("  [OK] agent boots, consumes events, stays alive")

    print(f"STAGE 3 — scored evaluation "
          f"(reps={args.reps}, time_scale={args.time_scale}, "
          f"wall_cap={args.wall_cap}s, setup_cap={args.setup_cap}s, median per scenario)")
    paths = sorted(glob.glob(os.path.join(args.scenarios, "*.json")))
    if not paths:
        sys.exit(f"no scenarios found in {args.scenarios}")
    report = evaluate(cls, paths, args.reps, args.time_scale, args.wall_cap, args.setup_cap)
    report = {"team": config["team"], "entry_point": config["entry_point"],
              "reps": args.reps, "time_scale": args.time_scale, **report}

    s = report["summary"]
    print("-" * 62)
    print(f"  plain average : {s['plain_average']:>5.1f}")
    print(f"  by modality   : " + "  ".join(f"{m}={v}" for m, v in s["by_modality"].items()))
    print(f"  WEIGHTED SCORE: {s['weighted_score']:>5.1f}   "
          f"(audio/visual x1.5, L3/L4 x1.25)")

    if args.out:
        with open(args.out, "w") as f:
            json.dump(report, f, indent=2)
        print(f"  wrote {args.out}")

    if args.expect:
        print("STAGE 4 — system self-check vs expected results")
        ok = compare_expected(report, args.expect)
        print("=" * 62)
        print("SYSTEM CHECK PASSED — evaluation pipeline is healthy" if ok
              else "SYSTEM CHECK FAILED — investigate before the event")
        sys.exit(0 if ok else 2)
    print("=" * 62)


if __name__ == "__main__":
    main()
