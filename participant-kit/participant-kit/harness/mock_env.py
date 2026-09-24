"""Mocked external tools with deterministic latency and failure injection.

Every tool is read_only or state_modifying; delays are seeded per
(scenario_id, tool_name, call_index); scenarios may override individual calls
via `tool_overrides`; args are validated against the schema. Hidden tools follow
the same conventions (docs/TOOLS.md).
"""

from __future__ import annotations
import asyncio
import hashlib
import re
from typing import Any, Dict, List, Optional

TOOL_REGISTRY: Dict[str, Dict[str, Any]] = {
    "flight_search": {
        "kind": "read_only",
        "delay_range_ms": [1500, 3000],
        "description": "Search flights to a destination city on a given date.",
        "args": {
            "destination": {"type": "string", "required": True,
                            "description": "Destination city name or airport code."},
            "date": {"type": "string", "required": False,
                     "description": "Departure date, free-form (e.g. 'tomorrow', '2026-09-12')."},
        },
    },
    "book_flight": {
        "kind": "state_modifying",
        "delay_range_ms": [800, 1600],
        "description": "Book a specific flight returned by flight_search.",
        "args": {
            "flight_id": {"type": "string", "required": True,
                          "description": "A flight id returned by flight_search."},
            "passenger_name": {"type": "string", "required": True,
                               "description": "Full name of the passenger."},
        },
    },
    "cancel_booking": {
        "kind": "state_modifying",
        "delay_range_ms": [600, 1200],
        "description": "Cancel an existing booking.",
        "args": {
            "booking_id": {"type": "string", "required": True,
                           "description": "The booking id to cancel."},
        },
    },
    "lookup_manual": {
        "kind": "read_only",
        "delay_range_ms": [1200, 2500],
        "description": "Retrieve pages from indexed device manuals. Supports "
                       "hybrid text + visual-embedding search.",
        "args": {
            "query": {"type": "string", "required": True,
                      "description": "Natural-language search query."},
            "image_embedding": {"type": "array", "items": "number", "required": False,
                                "description": "Optional dense visual embedding of the "
                                               "relevant video frame (hybrid search)."},
            "device_model": {"type": "string", "required": False,
                             "enum": ["QN90", "S24", "WF45", "GENERIC"],
                             "description": "Restrict search to one device family."},
        },
    },
    "create_support_ticket": {
        "kind": "state_modifying",
        "delay_range_ms": [700, 1400],
        "description": "Open a support ticket for an unresolved device issue.",
        "args": {
            "device": {"type": "object", "required": True,
                       "properties": {
                           "model": {"type": "string", "required": True},
                           "serial": {"type": "string", "required": False}},
                       "description": "The affected device."},
            "issue": {"type": "object", "required": True,
                      "properties": {
                          "summary": {"type": "string", "required": True},
                          "severity": {"type": "string", "required": True,
                                       "enum": ["low", "medium", "high"]}},
                      "description": "Description of the problem."},
        },
    },
}

_MANUAL_CORPUS = [
    {"doc": "GENERIC-laptop-manual", "page": 21, "title": "Headphone / Microphone Jack",
     "keywords": ["headphone", "audio jack", "microphone", "port", "side panel", "3.5mm"]},
    {"doc": "GENERIC-laptop-manual", "page": 23, "title": "USB Ports",
     "keywords": ["usb", "port", "side panel", "flash drive", "peripheral"]},
    {"doc": "GENERIC-laptop-manual", "page": 25, "title": "Ethernet (LAN) Port",
     "keywords": ["lan", "ethernet", "port", "side panel", "network", "wired"]},
    {"doc": "GENERIC-laptop-manual", "page": 27, "title": "HDMI Output",
     "keywords": ["hdmi", "port", "side panel", "external display", "monitor", "projector", "cable"]},
    {"doc": "QN90-manual", "page": 42, "title": "HDMI Connections",
     "keywords": ["hdmi", "port", "cable", "input", "rear panel", "connect"]},
    {"doc": "QN90-manual", "page": 57, "title": "LED Status Indicators",
     "keywords": ["led", "blinking", "light", "red", "status"]},
    {"doc": "WF45-manual", "page": 12, "title": "Drum Error Codes",
     "keywords": ["drum", "error", "washer", "de", "code"]},
    {"doc": "S24-manual", "page": 8, "title": "Charging Port Troubleshooting",
     "keywords": ["charging", "usb", "port", "phone", "battery"]},
]


def deterministic_delay_ms(scenario_id: str, tool_name: str, call_index: int,
                           lo: float, hi: float) -> float:
    key = f"{scenario_id}:{tool_name}:{call_index}".encode()
    h = hashlib.sha256(key).digest()
    frac = int.from_bytes(h[:4], "big") / 2**32
    return lo + frac * (hi - lo)


class MockEnvironment:
    def __init__(self, scenario_id: str,
                 tool_overrides: Optional[Dict[str, List[dict]]] = None,
                 time_scale: float = 1.0,
                 extra_tools: Optional[Dict[str, Dict[str, Any]]] = None):
        self.scenario_id = scenario_id
        self.overrides = tool_overrides or {}
        self.time_scale = max(time_scale, 0.001)
        self.call_counts: Dict[str, int] = {}
        self.bookings: Dict[str, dict] = {}
        self.tickets: Dict[str, dict] = {}
        self.registry = dict(TOOL_REGISTRY)
        if extra_tools:
            self.registry.update(extra_tools)

    async def execute(self, tool_name: str, args: Dict[str, Any]) -> Dict[str, Any]:
        idx = self.call_counts.get(tool_name, 0)
        self.call_counts[tool_name] = idx + 1

        spec = self.registry.get(tool_name)
        if spec is None:
            await self._sleep(300)
            return {"status": "error", "error": "unknown_tool",
                    "detail": f"No tool named '{tool_name}'."}

        problems = self._validate_args(spec, args)
        if problems:
            await self._sleep(200)
            return {"status": "error", "error": "invalid_args", "detail": problems}

        lo, hi = spec["delay_range_ms"]
        delay = deterministic_delay_ms(self.scenario_id, tool_name, idx, lo, hi)

        override = self._find_override(tool_name, idx)
        if override and "delay_ms" in override:
            delay = float(override["delay_ms"])

        await self._sleep(delay)

        if override:
            if "error" in override:
                return {"status": "error", "error": override["error"],
                        "detail": override.get("detail", "injected failure")}
            if "result" in override:
                return {"status": "success", **override["result"]}

        return self._default_result(tool_name, args)

    async def _sleep(self, ms: float):
        await asyncio.sleep(ms / 1000.0 / self.time_scale)

    def _find_override(self, tool_name: str, call_index: int) -> Optional[dict]:
        for ov in self.overrides.get(tool_name, []):
            if int(ov.get("call_index", -1)) == call_index:
                return ov
        return None

    def _validate_args(self, spec: dict, args: Dict[str, Any]) -> List[str]:
        problems = []
        if not isinstance(args, dict):
            return ["args must be an object"]
        for name, aspec in spec.get("args", {}).items():
            if aspec.get("required") and name not in args:
                problems.append(f"missing required arg '{name}'")
                continue
            if name not in args:
                continue
            val = args[name]
            if "enum" in aspec and val not in aspec["enum"]:
                problems.append(f"arg '{name}' must be one of {aspec['enum']}")
            if aspec.get("type") == "object":
                if not isinstance(val, dict):
                    problems.append(f"arg '{name}' must be an object")
                else:
                    for sub, sspec in aspec.get("properties", {}).items():
                        if sspec.get("required") and sub not in val:
                            problems.append(f"missing required field '{name}.{sub}'")
                        elif sub in val and "enum" in sspec and val[sub] not in sspec["enum"]:
                            problems.append(f"field '{name}.{sub}' must be one of {sspec['enum']}")
            if aspec.get("type") == "array" and not isinstance(val, list):
                problems.append(f"arg '{name}' must be an array")
        return problems

    def _default_result(self, tool_name: str, args: Dict[str, Any]) -> Dict[str, Any]:
        if tool_name == "flight_search":
            dest = str(args.get("destination", "")).strip()
            code = re.sub(r"[^A-Za-z]", "", dest)[:3].upper() or "XXX"
            if dest.lower() in ("new york", "nyc"):
                code = "NYC"
            return {"status": "success",
                    "flights": [
                        {"flight_id": f"FL-{code}-8AM", "depart": "08:00", "price_usd": 129},
                        {"flight_id": f"FL-{code}-2PM", "depart": "14:00", "price_usd": 99},
                    ]}

        if tool_name == "book_flight":
            fid = str(args["flight_id"])
            key = f"{fid}|{str(args['passenger_name']).lower()}"
            if key in self.bookings:
                return {"status": "error", "error": "duplicate_booking",
                        "detail": "This flight is already booked for this passenger.",
                        "booking_id": self.bookings[key]["booking_id"]}
            bid = f"BK-{len(self.bookings) + 1:04d}"
            self.bookings[key] = {"booking_id": bid, "flight_id": fid,
                                  "passenger": args["passenger_name"]}
            return {"status": "success", "booking_id": bid, "flight_id": fid}

        if tool_name == "cancel_booking":
            bid = str(args["booking_id"])
            for k, b in list(self.bookings.items()):
                if b["booking_id"] == bid:
                    del self.bookings[k]
                    return {"status": "success", "cancelled": bid}
            return {"status": "error", "error": "not_found",
                    "detail": f"No booking with id {bid}."}

        if tool_name == "lookup_manual":
            q = str(args.get("query", "")).lower()
            has_embedding = isinstance(args.get("image_embedding"), list) and args["image_embedding"]
            scored = []
            for page in _MANUAL_CORPUS:
                hits = sum(1 for kw in page["keywords"] if kw in q)
                if has_embedding and hits > 0:
                    hits += 1  # hybrid boost
                if hits > 0:
                    scored.append((hits, page))
            scored.sort(key=lambda x: -x[0])
            pages = [{"doc": p["doc"], "page": p["page"], "title": p["title"]}
                     for _, p in scored[:3]]
            return {"status": "success", "pages": pages,
                    "search_mode": "hybrid" if has_embedding else "text_only"}

        if tool_name == "create_support_ticket":
            tid = f"TK-{len(self.tickets) + 1:04d}"
            self.tickets[tid] = dict(args)
            return {"status": "success", "ticket_id": tid}

        # manifest tools: return the schema's default_result
        spec = self.registry.get(tool_name, {})
        return {"status": "success", **spec.get("default_result", {})}
