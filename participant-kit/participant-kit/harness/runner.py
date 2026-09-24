"""Time-stepped streaming harness.

Replays scenario events into the agent's in_queue at their virtual timestamps,
runs tool_call actions asynchronously against MockEnvironment, feeds results back
as tool_result events, and records everything into a trace — the only thing the
scorer reads. All times are virtual ms: time_scale speeds up the replay, not your
own compute. Optional agent.setup() runs before the clock starts.
"""

from __future__ import annotations
import asyncio
import inspect
import time
from typing import Any, Callable, Dict, List, Optional

from .mock_env import MockEnvironment
from .protocol import validate_action

AgentFactory = Callable[[asyncio.Queue, asyncio.Queue], Any]


class EvaluationHarness:
    def __init__(self, scenario: Dict[str, Any], agent_factory: AgentFactory,
                 time_scale: float = 1.0, tail_ms: float = 6000.0,
                 verbose: bool = True,
                 extra_tools: Optional[Dict[str, Any]] = None):
        self.scenario = scenario
        self.agent_factory = agent_factory
        self.time_scale = max(float(time_scale), 0.001)
        self.tail_ms = tail_ms
        self.verbose = verbose
        self.extra_tools = extra_tools

        self.in_q: asyncio.Queue = asyncio.Queue()
        self.out_q: asyncio.Queue = asyncio.Queue()
        self.trace: List[Dict[str, Any]] = []
        self.pending: Dict[str, asyncio.Task] = {}
        self._auto_call_counter = 0
        self._t0 = 0.0
        self._agent: Any = None
        self._setup_entry: Optional[Dict[str, Any]] = None

    def _virt_ms(self) -> float:
        return (time.monotonic() - self._t0) * 1000.0 * self.time_scale

    async def _sleep_virt(self, ms: float):
        if ms > 0:
            await asyncio.sleep(ms / 1000.0 / self.time_scale)

    def _log(self, entry: Dict[str, Any]):
        entry.setdefault("t_ms", round(self._virt_ms(), 1))
        self.trace.append(entry)
        if self.verbose:
            t = entry["t_ms"]
            kind = entry["kind"]
            detail = {k: v for k, v in entry.items() if k not in ("t_ms", "kind")}
            print(f"[+{int(t):>6}ms] {kind:<16} {detail}")

    async def prepare(self):
        """Construct the agent and await its optional setup(), off the clock. Idempotent."""
        if self._agent is not None:
            return
        self._agent = self.agent_factory(self.in_q, self.out_q)
        setup = getattr(self._agent, "setup", None)
        if not callable(setup):
            return
        t = time.monotonic()
        entry: Dict[str, Any] = {"kind": "agent_setup"}
        try:
            res = setup()
            if inspect.isawaitable(res):
                await res
        except Exception as e:
            entry["error"] = f"{type(e).__name__}: {e}"
        entry["wall_ms"] = round((time.monotonic() - t) * 1000.0, 1)
        self._setup_entry = entry

    async def run(self) -> List[Dict[str, Any]]:
        manifest: Dict[str, Any] = dict(self.scenario.get("tool_manifest") or {})
        if self.extra_tools:
            manifest.update(self.extra_tools)
        env = MockEnvironment(
            scenario_id=self.scenario.get("scenario_id", "unknown"),
            tool_overrides=self.scenario.get("tool_overrides"),
            time_scale=self.time_scale,
            extra_tools=manifest or None,
        )
        await self.prepare()
        agent_task = asyncio.create_task(self._run_agent(self._agent))
        monitor_task = asyncio.create_task(self._monitor(env))

        self._t0 = time.monotonic()
        if self._setup_entry:
            self._log({**self._setup_entry, "t_ms": 0.0})

        # first event of every scenario: the tool schemas (trace keeps names only)
        self._log({"kind": "event", "event_type": "tool_manifest",
                   "payload": {"tools": sorted(env.registry)}})
        await self.in_q.put({"timestamp_ms": 0, "event_type": "tool_manifest",
                             "payload": {"schema_version": "1.0",
                                         "tools": dict(env.registry)}})

        events = list(self.scenario.get("events", []))
        for ev in events:
            target = float(ev.get("timestamp_ms", 0))
            await self._sleep_virt(target - self._virt_ms())
            self._log({"kind": "event", "event_type": ev.get("event_type"),
                       "payload": ev.get("payload", {})})
            # keys starting with "_" are organizer annotations, never delivered
            await self.in_q.put({k: v for k, v in ev.items() if not str(k).startswith("_")})

        end_ev = {"timestamp_ms": round(self._virt_ms(), 1),
                  "event_type": "scenario_end", "payload": {}}
        self._log({"kind": "event", "event_type": "scenario_end", "payload": {}})
        await self.in_q.put(end_ev)

        await self._sleep_virt(self.tail_ms)

        for call_id, task in list(self.pending.items()):
            task.cancel()
            self._log({"kind": "tool_abandoned", "call_id": call_id})
        self.pending.clear()
        agent_task.cancel()
        monitor_task.cancel()
        for t in (agent_task, monitor_task):
            try:
                await t
            except (asyncio.CancelledError, Exception):
                pass
        return self.trace

    async def _run_agent(self, agent):
        try:
            await agent.run()
        except asyncio.CancelledError:
            raise
        except Exception as e:
            self._log({"kind": "agent_crash", "error": f"{type(e).__name__}: {e}"})

    async def _monitor(self, env: MockEnvironment):
        while True:
            action = await self.out_q.get()
            problems = validate_action(action)
            if problems:
                self._log({"kind": "protocol_error", "problems": problems,
                           "raw": repr(action)[:300]})
                if not isinstance(action, dict):
                    continue

            kind = action.get("action")
            payload = action.get("payload", {}) if isinstance(action.get("payload"), dict) else {}

            if kind == "tool_call":
                call_id = payload.get("call_id")
                if not isinstance(call_id, str) or not call_id:
                    self._auto_call_counter += 1
                    call_id = f"auto_{self._auto_call_counter}"
                api = payload.get("api_name", "?")
                args = payload.get("args", {}) if isinstance(payload.get("args"), dict) else {}
                self._log({"kind": "action", "action": "tool_call", "call_id": call_id,
                           "api_name": api, "args": args})
                task = asyncio.create_task(self._exec_tool(env, call_id, api, args))
                self.pending[call_id] = task

            elif kind == "cancel_tool":
                call_id = payload.get("call_id", "")
                task = self.pending.pop(call_id, None)
                if task is not None and not task.done():
                    task.cancel()
                    self._log({"kind": "tool_cancelled", "call_id": call_id})
                else:
                    self._log({"kind": "cancel_noop", "call_id": call_id,
                               "note": "no such pending call (already finished?)"})

            else:
                entry = {"kind": "action", "action": kind, "payload": payload}
                if "state_snapshot" in action:
                    entry["state_snapshot"] = action["state_snapshot"]
                self._log(entry)

    async def _exec_tool(self, env: MockEnvironment, call_id: str,
                         api_name: str, args: Dict[str, Any]):
        try:
            result = await env.execute(api_name, args)
        except asyncio.CancelledError:
            return
        self.pending.pop(call_id, None)
        status = result.get("status", "success")
        self._log({"kind": "tool_completed", "call_id": call_id,
                   "api_name": api_name, "args": args,
                   "status": status, "result": result})
        await self.in_q.put({
            "timestamp_ms": round(self._virt_ms(), 1),
            "event_type": "tool_result",
            "payload": {"call_id": call_id, "api_name": api_name,
                        "status": status, "result": result},
        })


def run_scenario(scenario: Dict[str, Any], agent_factory: AgentFactory,
                 time_scale: float = 1.0, verbose: bool = True,
                 tail_ms: float = 6000.0) -> List[Dict[str, Any]]:
    harness = EvaluationHarness(scenario, agent_factory,
                                time_scale=time_scale, verbose=verbose,
                                tail_ms=tail_ms)
    return asyncio.run(harness.run())
