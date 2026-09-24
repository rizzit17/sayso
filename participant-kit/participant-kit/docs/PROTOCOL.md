# The I/O Streaming Protocol

Your agent is a loop: it reads **events** from an input queue and writes **actions** to an output queue. Both are plain dicts (JSON objects in the official Docker run — identical shape). That's the entire integration surface. This document defines every event and action, field by field.

**One clock rules everything: virtual milliseconds.** Scenario timestamps, tool delays, trace entries, and every latency threshold in scoring are all in virtual ms. Locally you can run with `--time-scale 8` and the trace timestamps still read as if it were real time. Official runs use scale 1.0.

---

## 1. Incoming events (Harness → Agent)

Every event has this envelope:

```json
{"timestamp_ms": 800, "event_type": "...", "payload": { ... }}
```

### 1.0 `tool_manifest` — the tools available in this scenario (always the first event)

```json
{"timestamp_ms": 0, "event_type": "tool_manifest",
 "payload": {
   "schema_version": "1.0",
   "tools": {
     "flight_search": {"kind": "read_only", "delay_range_ms": [1500, 3000],
                       "description": "...", "args": {"destination": {"type": "string", "required": true}, ...}},
     "weather_lookup": {"kind": "read_only", "delay_range_ms": [900, 1800],
                        "description": "...", "args": {"city": {"type": "string", "required": true}, ...},
                        "default_result": {"condition": "sunny", "temp_f": 74}}
   }}}
```

| field | meaning |
|---|---|
| `tools` | map of tool name → schema, in exactly the shape of `TOOL_REGISTRY` in `harness/mock_env.py` (see [TOOLS.md](TOOLS.md) for every schema field) |
| `schema_version` | `"1.0"` for the whole event |

Every scenario — public and hidden — begins with this event, before any user speech. In most public scenarios it lists just the 5 public tools. `pub_09` adds `weather_lookup`, which exists nowhere else; hidden scenarios add ~10 tools you have never seen. **Build your tool-calling from this payload, not from a hardcoded list** — reading a schema and producing valid arguments for a tool you first met 800 ms ago is a scored skill. Calling a tool that is not in the manifest returns `unknown_tool`.

### 1.1 `user_speech_chunk` — clean text

A fragment of what the user said, already transcribed perfectly. Chunks arrive at speaking speed; a turn may span several chunks.

```json
{"timestamp_ms": 100, "event_type": "user_speech_chunk",
 "payload": {"text": "Please book a flight to ", "end_of_turn": false}}
{"timestamp_ms": 800, "event_type": "user_speech_chunk",
 "payload": {"text": "Boston for tomorrow.", "end_of_turn": true}}
```

| field | type | meaning |
|---|---|---|
| `text` | string | the transcribed fragment |
| `end_of_turn` | bool | `true` = the user stopped talking; this is your cue to act |

> **Design note:** waiting for `end_of_turn` before acting is the safe baseline. Ambitious agents can *speculate* on partial turns to cut latency — just be ready to revise if the rest of the turn changes things.

### 1.2 `user_audio_chunk` — raw audio

This is how audio scenarios work: you get the **recording, not a transcript**. Transcribing it — and knowing when your transcription is unsure — is part of the task.

```json
{"timestamp_ms": 100, "event_type": "user_audio_chunk",
 "payload": {"audio_ref": "audio/pub_05_turn1.mp3", "duration_ms": 1400, "end_of_turn": true}}
```

| field | type | meaning |
|---|---|---|
| `audio_ref` | string | path (relative to the kit root) of a mono audio clip (MP3 in this kit) of what the user just said |
| `duration_ms` | number | length of the clip |
| `end_of_turn` | bool | `true` = the user stopped talking; a turn may span several clips |

Everything that makes audio hard is *in the audio*: an indistinct city name (`pub_05`), hesitations and mid-sentence self-repairs (`pub_06`). A good agent clarifies when its transcription is shaky rather than firing a tool on a guess, and treats the *repaired* value as the user's intent. Transcription takes time — acknowledge first, transcribe behind the filler. Hosted multimodal APIs (e.g. Gemini) accept the audio file directly; local models (Whisper, Gemma audio variants) are equally welcome — load them in `setup()`.

### 1.3 `video_frame` — raw frame

```json
{"timestamp_ms": 100, "event_type": "video_frame",
 "payload": {"frame_id": "f_017", "image_ref": "frames/pub_07_f017.png", "device_hint": "GENERIC"}}
```

| field | meaning |
|---|---|
| `image_ref` | path (relative to the kit root) of a PNG frame from the user's camera |
| `frame_id` | identifier of the frame |
| `device_hint` | device family if the system knows it; **may be absent** in harder scenarios |

There is **no caption and no precomputed embedding**: what is in the picture is the only source of the answer. The frame is **context, not a question** — the user's next utterance ("what is *this* port?") refers to it, so keep the latest frame in your state. To earn the hybrid-search bonus in `lookup_manual`, compute your own embedding of the frame and pass it as `image_embedding`.

> **Media files** live under `audio/` and `frames/` in the kit; hidden scenarios ship theirs the same way. Your agent must not crash if a referenced file is missing.

> **Organizer annotations.** Scenario files may contain keys starting with `_` (e.g. `_reference_text` next to an audio event, `_reference_image` next to a frame) — short authoring notes, not transcripts or captions to build your agent from. The harness strips them before delivery; your agent never receives them, locally or officially. Hidden scenarios are not guaranteed to carry them at all. Reading them from the scenario file is the same as reading `ground_truth` — disqualifying.

### 1.4 `interruption` — the user barged in

```json
{"timestamp_ms": 1900, "event_type": "interruption",
 "payload": {"text": "Wait, actually make it New York."}}
```

The single highest-stakes event. The contract you are graded on:

1. **Acknowledge fast** — a filler within the latency threshold (~800 ms for full credit).
2. **Abort stale work** — `cancel_tool` any in-flight call the interruption invalidates.
3. **Update state** — your next `state_snapshot` must reflect the new intent/slots.
4. **Never act on stale results** — if a stale call slips through and completes, do not ground your answer in it, and never re-issue the old arguments.

Interruptions can also be **retractions** ("actually, never mind") or full **intent changes** ("forget the flight, my TV is broken") — hidden scenarios use all three.

### 1.5 `tool_result` — an earlier tool call finished

```json
{"timestamp_ms": 3100, "event_type": "tool_result",
 "payload": {"call_id": "c1", "api_name": "flight_search",
             "status": "success",
             "result": {"status": "success", "flights": [ ... ]}}}
```

`status` is `"success"` or `"error"`. Error results carry `result.error` (a machine-readable code like `"timeout"`, `"invalid_args"`, `"not_found"`, `"duplicate_booking"`) and `result.detail`. **Errors are normal** — some scenarios inject them deliberately; retrying a *read-only* tool once is usually right, retrying a *state-modifying* tool blindly is how you lose safety points.

### 1.6 `scenario_end`

No more scripted user events will arrive (tool results may still arrive). You have a grace window (`tail_ms`, default 6000 virtual ms) to finish pending work and emit your final response.

---

## 2. Outgoing actions (Agent → Harness)

### 2.1 `filler_speech` — mask latency, keep the floor

```json
{"action": "filler_speech",
 "payload": {"text": "Looking up flights to Boston — one moment."}}
```

Counts as a "spoken response" for latency scoring. **Content-aware fillers** ("switching to New York") are worth exactly the same as generic ones to the automated scorer, but the LLM quality grade sees your transcripts ([SCORING.md](SCORING.md)) — and spamming fillers costs safety points (budget: ~4 per scenario unless stated).

### 2.2 `tool_call` — delegate to the slow path (non-blocking)

```json
{"action": "tool_call",
 "payload": {"call_id": "c1", "api_name": "flight_search",
             "args": {"destination": "Boston"}}}
```

* `call_id` is **yours to choose** (any unique string). If you omit it, the harness assigns one — but then you can't cancel the call, so always set it.
* The call runs in the background; the result comes back later as a `tool_result` event. Your agent keeps listening in the meantime — that's the whole point.
* Arguments are validated against the schema; bad args return an `invalid_args` error result (they never crash anything, but they waste real time).

### 2.3 `cancel_tool` — abort in-flight work

```json
{"action": "cancel_tool", "payload": {"call_id": "c1"}}
```

Cancelling a call that already finished is a harmless no-op (logged, not penalized). Cancelling promptly after an interruption is the core of the recovery score.

### 2.4 `clarification_request` — ask before acting

```json
{"action": "clarification_request",
 "payload": {"text": "Just to confirm — did you say Austin or Boston?"}}
```

Counts as a spoken response for latency. Use it when a slot is ambiguous or a required argument is missing. Guessing on a shaky slot and firing a state-modifying tool is worse than asking.

### 2.5 `final_response` — your grounded answer (snapshot required)

```json
{"action": "final_response",
 "payload": {"text": "I found FL-NYC-8AM departing 08:00 for $129."},
 "state_snapshot": {"intent": "book_flight",
                    "slots": {"destination": "New York",
                              "flight_id": "FL-NYC-8AM"}}}
```

* `state_snapshot` is a **top-level** field (sibling of `payload`), and it is **mandatory** on every `final_response` (missing → safety deduction).
* The snapshot is how the harness verifies your internal state **without caring about your architecture**. Convention used by all ground truth: `{"intent": <string>, "slots": {<name>: <value>, ...}}`. Slot names used in public + hidden scenarios: `destination`, `date`, `flight_id`, `passenger_name`, `booking_id`, `device_model`, `issue_summary`.
* You may attach a `state_snapshot` to any other action too (useful after interruptions — the recovery scorer reads the *latest* snapshot after the interruption timestamp, whatever action it rides on).

### Protocol hygiene

Malformed actions never crash the harness — they are logged as `protocol_error` entries and cost a small safety deduction. Common mistakes: non-dict payload, empty `text`, `final_response` without `state_snapshot`, `tool_call` without `args`.

---

## 3. The trace — what the scorer actually sees

Everything above is recorded into a flat list of trace entries with virtual timestamps: `event`, `action`, `tool_completed`, `tool_cancelled`, `tool_abandoned` (still pending at shutdown), `cancel_noop`, `protocol_error`, `agent_crash`, and `agent_setup` (at `t=0`: real milliseconds your `setup()` took, plus any error it raised — informational, never scored). Run any scenario with `--json out.json` to inspect the exact structure. **If a behavior isn't in the trace, it isn't scored** — there is no hidden channel.

---

## 4. Timing cheat-sheet

| number | value | where it bites |
|---|---|---|
| full-credit response time | ≤ 800 ms after a turn ends / interruption | latency score |
| zero-credit response time | ≥ 2500 ms | latency score (linear in between) |
| cancel grace after interruption | 800 ms | a stale call completing later than this, uncancelled, is a recovery violation |
| tail window after `scenario_end` | 6000 ms | finish your work by then |
| tool delays | 600–3000 ms depending on the tool, deterministic per (scenario, tool, nth-call) | plan fillers accordingly |
| official wall-clock cap | 120 s per scenario (the scenario itself) | infinite loops score 0 |
| official setup cap | 300 s per scenario for `setup()` (model loading, warm-up) | exceeding it scores that scenario 0 |

---

## 5. Runtime contract — read this before wiring an LLM

Your agent is a Python class (3.10–3.12) constructed as `YourAgent(in_queue, out_queue)`; the harness runs `await agent.run()` **as a task on the same asyncio event loop that delivers events and executes tools**. That single fact drives every rule below.

**1. Never block the loop.** A synchronous HTTP call (`anthropic.Anthropic().messages.create`, `openai.OpenAI().chat.completions.create`, `requests.post`), `time.sleep`, or a CPU-heavy loop inside `run()` freezes the *entire simulation*: user events are delivered late and bunched together, in-flight tools stop progressing, and the trace records your subsequent actions at the wrong virtual time. We measured it: a 2-second sync call in `pub_02` turned a 100-point run into 61 — and the scorer blamed the agent for "re-issuing a stale call after the interruption", because the interruption event was still stuck in the queue when the agent emitted the call. Nothing in the trace tells you the real cause. Use the async clients (`AsyncAnthropic`, `AsyncOpenAI`, `httpx.AsyncClient`, `aiohttp`), or wrap sync code:

```python
result = await asyncio.to_thread(client.messages.create, model=..., messages=...)
```

**2. Threads.** If you run work in threads, never call `out_queue.put_nowait(...)` from the worker thread — `asyncio.Queue` is not thread-safe. Either return the value to your coroutine via `asyncio.to_thread`, or schedule the put on the loop: `loop.call_soon_threadsafe(out_queue.put_nowait, action)`.

**3. Cold starts go in `setup()`.** Loading a local model or warming an API client can take seconds to minutes. Define an optional `async def setup(self)`: the harness awaits it **before the virtual clock starts** and **before the per-scenario wall-clock cap begins** — it has its own, more generous cap (official: 300 s). Its cost is recorded in the trace as an `agent_setup` entry at `t=0` so you can see it, but it never affects latency scoring. Keep `__init__` trivial (store the queues). Because a fresh instance is constructed for every scenario, cache heavy resources at module level so the second `setup()` is instant:

```python
_MODEL = None

class ParticipantAgent:
    async def setup(self):
        global _MODEL
        if _MODEL is None:
            _MODEL = await asyncio.to_thread(load_model)   # off the clock, once per process
        self.model = _MODEL
```

Anything inside `run()` — including a lazy load on the first user turn — is on the clock.

**4. Background tasks are fine.** Speculative planning, prefetching, a "slow thinker" coroutine — spawn them with `asyncio.create_task`. At shutdown the harness cancels `run()`; wrap your loop in `try/finally` and cancel your own tasks there so nothing leaks a warning.

**5. Streaming LLM output.** Do not emit each streamed token chunk as a `filler_speech`: the budget is ~4 fillers per scenario (−0.25 safety each beyond it) and verbatim repeats cost extra. Speak once to acknowledge, then emit one `final_response`. Multiple `final_response`s are allowed (e.g. to revise) — each must carry a `state_snapshot`.

**6. One agent instance per scenario.** The harness constructs a fresh instance for every scenario (and every repetition). Keep per-conversation state on `self`; keep expensive shared resources (models, clients) at module level (see §5.3).

**7. Python only.** The evaluator imports your class in-process. There is no subprocess or JSON-over-stdio transport, and no non-Python runtime.
