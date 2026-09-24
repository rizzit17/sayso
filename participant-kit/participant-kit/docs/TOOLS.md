# Tools: the 5 Public Schemas and the Conventions Hidden Tools Follow

Machine-readable schemas: `TOOL_REGISTRY` in `harness/mock_env.py`, and at runtime the `tool_manifest` event. `mock_env.py` is exactly what runs in evaluation, minus the hidden tools.

---

## Part 1 — The conventions (this is the important part)

Roughly 10 additional tools exist only in hidden scenarios. **You will meet them for the first time inside a scenario:** every scenario starts with a `tool_manifest` event ([PROTOCOL.md §1.0](PROTOCOL.md)) whose `tools` payload lists every tool you may call, in the same JSON shape as `TOOL_REGISTRY`. Scenario files declare their extra tools in a top-level `tool_manifest` field; the harness merges it with the public registry and hands the result to your agent. `pub_09` is the public example (`weather_lookup`), and `python -m harness.scenario_gen --template unseen_tool` produces more. Handling a never-seen tool correctly from its schema alone is a scored skill. Every tool — public and hidden — obeys these rules:

1. **Kind tag.** Every tool is `"kind": "read_only"` or `"kind": "state_modifying"`.
   * `read_only`: safe to retry, safe to call speculatively, safe to abandon.
   * `state_modifying`: changes the world. Duplicate successful calls with identical arguments cost safety points, whether or not the mock environment happens to reject the duplicate. Retry only when you have evidence the first attempt did not commit (e.g. an explicit `invalid_args` or `timeout` *before* commit semantics — when unsure, ask the user or check with a read-only call).
2. **Declared latency.** Every schema publishes `delay_range_ms: [lo, hi]`. Actual delay is deterministic per `(scenario_id, tool_name, call_index)` — same run, same delays, every time. Use the range to decide when a filler is needed.
3. **Argument types** come from this closed set: `string`, `number`, `boolean`, `array` (with `items` type), `object` (with `properties`), plus optional `enum` on strings and `required: true/false`. Nothing exotic. If your arg-builder handles the 5 public tools, it handles all hidden tools.
4. **Validation before execution.** Missing required args, wrong enum values, wrong shapes → the call returns fast with `{"status": "error", "error": "invalid_args", "detail": [...]}`. It never crashes, but it burns real seconds.
5. **Error shape is uniform.** All failures look like `{"status": "error", "error": "<machine_code>", "detail": "<human text>"}`. Codes used anywhere in the event: `timeout`, `invalid_args`, `not_found`, `duplicate_booking`, `unknown_tool`. Hidden tools reuse these codes.
6. **Results are JSON objects** with `"status": "success"` plus tool-specific fields. Field names are always lowercase snake_case.
7. **Manifest tools carry a `default_result`.** A tool declared in a scenario's `tool_manifest` has no bespoke mock; a valid call returns `{"status": "success", **default_result}` after its declared delay, unless the scenario's `tool_overrides` injects a specific result or error for that call. So the schema tells you both *how to call it* and *what shape comes back* — ground your final answer in those fields.

> **Practice drop:** midway through the event we release one practice unseen tool + one scenario using it, so you can verify your zero-shot tool handling before it counts.

---

## Part 2 — The 5 public tools

### `flight_search` — read_only, 1500–3000 ms

Search flights to a destination.

| arg | type | required | notes |
|---|---|---|---|
| `destination` | string | yes | city name or airport code |
| `date` | string | no | free-form |

Success: `{"status": "success", "flights": [{"flight_id": "FL-CHI-8AM", "depart": "08:00", "price_usd": 129}, ...]}`. Flight ids are stable and referenceable: `FL-<CODE>-<TIME>`.

### `book_flight` — state_modifying, 800–1600 ms

| arg | type | required |
|---|---|---|
| `flight_id` | string | yes — must come from a `flight_search` result (chained call) |
| `passenger_name` | string | yes |

Success: `{"status": "success", "booking_id": "BK-0001", "flight_id": "..."}`. Booking the same flight for the same passenger twice returns `duplicate_booking` — and even when an environment *doesn't* protect you like this, the scorer's idempotence penalty still applies. Never fire-and-forget a booking.

### `cancel_booking` — state_modifying, 600–1200 ms

| arg | type | required |
|---|---|---|
| `booking_id` | string | yes |

Errors with `not_found` for unknown ids.

### `lookup_manual` — read_only, 1200–2500 ms

Hybrid text + visual retrieval over device manuals. This is the multimodal workhorse.

| arg | type | required | notes |
|---|---|---|---|
| `query` | string | yes | natural language |
| `image_embedding` | array of numbers | no | your own embedding of the current frame → hybrid search, better ranking, and a scored bonus checkpoint in visual scenarios |
| `device_model` | string enum: `QN90`, `S24`, `WF45`, `GENERIC` | no | wrong enum value → `invalid_args` |

Success: `{"status": "success", "pages": [{"doc": "QN90-manual", "page": 42, "title": "HDMI Connections"}], "search_mode": "hybrid" | "text_only"}`. An empty `pages` list is a *successful* call with no hits — handle it gracefully.

### `create_support_ticket` — state_modifying, 700–1400 ms

The nested-object exercise. If your code assumes flat args, this tool is where it breaks — fix it here, not on a hidden tool.

```json
{"device": {"model": "QN90", "serial": "optional"},
 "issue":  {"summary": "LED blinking red", "severity": "low" | "medium" | "high"}}
```

Both `device` and `issue` are required objects; `device.model`, `issue.summary`, `issue.severity` are required fields; `severity` is an enum. Success: `{"status": "success", "ticket_id": "TK-0001"}`.

---

## Part 3 — Schema-feature coverage map

Every schema feature that any hidden tool uses is exercised by at least one public tool. This table is your test checklist:

| feature | public tool that exercises it |
|---|---|
| required string arg | all |
| optional arg | `flight_search.date`, `lookup_manual.device_model` |
| enum validation | `lookup_manual.device_model`, `create_support_ticket.issue.severity` |
| array arg | `lookup_manual.image_embedding` |
| nested object arg | `create_support_ticket.device`, `.issue` |
| chained call (arg from prior result) | `flight_search` → `book_flight` |
| state_modifying + idempotence | `book_flight`, `cancel_booking`, `create_support_ticket` |
| injected failure / retry decision | `flight_search` in `pub_08` |
| empty-but-successful result | `lookup_manual` with an off-corpus query |
| never-seen tool, schema from `tool_manifest` only | `weather_lookup` in `pub_09`; `scenario_gen --template unseen_tool` |
