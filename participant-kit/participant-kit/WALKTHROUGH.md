# Walkthrough — from zero to a scored submission

Read this first. It tells you what the problem is, which files matter, what you must implement, how to score yourself, and what to submit.

---

## 1. Problem statement

You are building the a **real-time voice assistant**. A user talks to it continuously; while they talk, the assistant must:

- **respond immediately** (a human expects a reply within about a second — silence feels broken),
- **do real work** that takes seconds (search flights, book, look up a manual) by calling **tools**, without going silent while waiting,
- **cope with the user changing their mind mid-sentence** ("book Boston… wait, actually New York"), which means abandoning work that is now useless and never acting on stale results,
- **never do a risky thing twice** (booking the same flight twice, opening two tickets),
- handle **raw multimodal input**: audio clips with indistinct words and self-corrections ("uh… to Boston — actually New York"), and **video frames** the user refers to ("what is _this_ port?").

Text turns arrive already transcribed; audio turns and video frames arrive as **raw media** (MP3 / PNG) — transcribing and looking is your job, and scenarios that need it carry 1.5× weight. The kit gives you fake ("mock") tools that behave like real APIs: they take 0.6–3 s, sometimes fail, and reject bad arguments.

## 2. Goal

Write one Python class that reads a stream of **events** and writes a stream of **actions**, and score as high as possible on scenarios you have never seen. Each scenario is scored 0–100 on:

| category | weight | what it means                                                                                                         |
| -------- | ------ | --------------------------------------------------------------------------------------------------------------------- |
| task     | 40     | you called the right tools with the right arguments and gave a grounded final answer                                  |
| recovery | 35     | after an interruption you cancelled stale work and your state reflects the new intent                                 |
| latency  | 15     | you said _something real_ within ~800 ms of the user finishing a turn or interrupting                                 |
| safety   | 10     | no duplicate state-changing calls, no filler spam, no malformed messages, no claiming things happened before they did |

The full rubric is in [docs/SCORING.md](docs/SCORING.md). The scorer that grades you is [harness/scorer.py](harness/scorer.py) — the file in this kit is the file used on the hidden sets.

## 3. Steps

### Step 1 — Look around

Run it before reading anything:

```bash
python run_local.py --scenario scenarios/pub_02_text_interrupt.json
```

You will see a live trace: events going in, actions coming out, tools completing, then a score report. That trace is the whole game.

Now read, in this order:

| read                                                                   | why                                                                                                                                                                   |
| ---------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [docs/PROTOCOL.md](docs/PROTOCOL.md)                                   | every event you receive and every action you may send, field by field. **§5 (runtime contract) is mandatory** before you touch an LLM client.                         |
| [agent/agent.py](agent/agent.py)                                       | `ParticipantAgent` is your empty template; `BaselineAgent` below it is a tiny working agent that shows each action type in use.                                       |
| [scenarios/pub_01_text_simple.json](scenarios/pub_01_text_simple.json) | a scenario file: `events` (what you will receive) and `ground_truth` (what the scorer checks). Read two or three; the checkpoints tell you exactly what "good" means. |
| [docs/TOOLS.md](docs/TOOLS.md)                                         | the 5 public tools and the schema conventions every hidden tool also follows.                                                                                         |
| [docs/SCORING.md](docs/SCORING.md)                                     | the rubric, the anti-gaming rules, and how response quality is graded.                                                                                                |
| [harness/mock_env.py](harness/mock_env.py)                             | what the tools actually return. Short; read it.                                                                                                                       |

### Step 2 — Understand the contract you implement

Your agent is a Python class. The harness constructs it, optionally awaits `setup()`, then runs `run()` as a task **on the same asyncio event loop** that delivers events and executes tools.

```python
class ParticipantAgent:
    def __init__(self, in_queue: asyncio.Queue, out_queue: asyncio.Queue):
        self.in_q, self.out_q = in_queue, out_queue     # keep this trivial

    async def setup(self):
        # optional; runs BEFORE the clock starts (own 300 s cap): load models, warm clients

    async def run(self):
        while True:
            event = await self.in_q.get()               # a dict: {"timestamp_ms", "event_type", "payload"}
            ...                                         # decide, then: await self.out_q.put({"action": ..., "payload": ...})
```

**Events you receive** (`event_type`):

| event               | when                | what to do                                                                                                                                                        |
| ------------------- | ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `tool_manifest`     | always first        | store `payload["tools"]` — the schemas of every tool you may call _in this scenario_. Hidden scenarios add tools you have never seen; this is how you learn them. |
| `user_speech_chunk` | as the user talks   | buffer `text`; act when `end_of_turn` is true                                                                                                                     |
| `user_audio_chunk`  | audio scenarios     | an MP3 at `audio_ref`, no transcript — transcribe it yourself (acknowledge first); if the transcription is shaky, **ask, don't guess**                             |
| `video_frame`       | visual scenarios    | a PNG at `image_ref`, no caption — keep the latest frame; the next question refers to what is *in* it                                                          |
| `interruption`      | user barged in      | acknowledge fast, `cancel_tool` anything now stale, update state, re-plan                                                                                         |
| `tool_result`       | a tool finished     | match `call_id`; ignore results of calls you cancelled                                                                                                            |
| `scenario_end`      | no more user events | finish pending work within the 6 s tail                                                                                                                           |

**Actions you send** (`action`):

| action                  | payload                                                        | notes                                                                                         |
| ----------------------- | -------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `filler_speech`         | `{"text"}`                                                     | fast, short, _true_, ideally content-aware ("switching to New York") — budget ~4 per scenario |
| `tool_call`             | `{"call_id", "api_name", "args"}`                              | non-blocking; **always set your own `call_id`** or you cannot cancel it                       |
| `cancel_tool`           | `{"call_id"}`                                                  | the heart of the recovery score                                                               |
| `clarification_request` | `{"text"}`                                                     | when a slot is ambiguous or a required argument is missing                                    |
| `final_response`        | `{"text"}` + top-level `"state_snapshot": {"intent", "slots"}` | the snapshot is mandatory; ground the text in the tool result                                 |

### Step 3 — Implement

Work through the public scenarios in order: `pub_01`/`pub_04` (turn → tool → grounded answer, or no tool at all), `pub_02` (interruption: filler, `cancel_tool`, new state, re-plan), `pub_03` (chained calls, book once), `pub_08` (retry a read-only failure once), `pub_05`/`pub_06` (transcribe the audio; clarify when unsure; the last stated value wins), `pub_07` (read the port type off the frame; pass your own embedding), `pub_09` (call a tool you only know from `tool_manifest`). Each scenario's `ground_truth` lists exactly what is checked.

Put an LLM where judgment is needed (intent and slots, choosing a manifest tool and its arguments, phrasing); keep acknowledgment and cancellation on a rule-based fast path so they land in <800 ms. Use the async client or `asyncio.to_thread` — a synchronous call freezes the harness (PROTOCOL.md §5).

### Step 4 — Check your local score

```bash
# one scenario, live trace (real time)
python run_local.py --scenario scenarios/pub_02_text_interrupt.json --agent agent.agent:ParticipantAgent

# all public scenarios, fast, score table only
python run_local.py --all --time-scale 8 --quiet --agent agent.agent:ParticipantAgent

# dump trace + score as JSON to inspect or feed to your own LLM quality check
python run_local.py --scenario scenarios/pub_05_audio_asr_ambiguity.json --agent agent.agent:ParticipantAgent --json out.json
```

Reading the report: every checkpoint prints `[PASS]`/`[FAIL]` with a note, the latency lines show your delta per event, and safety prints each deduction. Fix the `[FAIL]` notes; they name exactly what was missing.

Two habits:

- **Run at `--time-scale 1` before you trust a number.** Scale 8 speeds up the scenario clock, not your code, so an LLM call that fits at scale 1 may look late at scale 8 — and vice versa for nothing; scale 1 is the official setting.
- **Generate scenarios you did not write:**
  ```bash
  python -m harness.scenario_gen --template search_interrupt --n 10 --seed 1 --out generated/
  python -m harness.scenario_gen --template unseen_tool --n 5 --seed 3 --out generated/
  python run_local.py --scenario generated/gen_unseen_000.json --agent agent.agent:ParticipantAgent
  ```
  Different cities, timings, and tools. If you pass these without touching your code, you are learning the mechanics rather than the files.

The reference `BaselineAgent` scores about 57/100 on the public set. Beating it on day one is the warm-up; the hidden set punishes its keyword rules.

## 4. What the hidden test set looks like

Roughly 60 scenarios plus about 10 tools you have not seen. Nothing in them is a new _mechanic_ — every event type, payload field, action, and scoring rule appears in this kit — but they are harder _combinations_:

- **New tools, delivered by schema only.** They follow [docs/TOOLS.md](docs/TOOLS.md) exactly: `kind` tag, `delay_range_ms`, typed `args` with `required`/`enum`/nested `object`/`array`, uniform error shapes, and a `default_result` telling you the result fields. Your agent gets them in the `tool_manifest` event at the start of the scenario. Build a generic "schema → arguments" path; test it on `pub_09` and the `unseen_tool` template. Some hidden tools are `state_modifying` — the duplicate penalty applies to them too.
- **Interruptions everywhere.** During chained calls, during a tool that is about to return, twice in one scenario, as retractions ("never mind") and full intent changes ("forget the flight, my TV is broken"). Any in-flight call the interruption invalidates must be cancelled within ~800 ms.
- **More audio and visual weight.** About 30% audio-flavored and 20% visual, each weighted **1.5×**. Expect frames without `device_hint`, longer and noisier recordings, and distractor speech that should _not_ trigger a tool.
- **Re-skinned public scenarios.** Same structure, different cities, names, timings, tools. Hardcoded strings or scenario IDs will be caught and reviewed.
- **Paraphrases.** "I need to get to Denver", "any seats to Denver Friday", "Denver, Friday, book it" — keyword lists will not survive this.
- **Response quality graded by an LLM.** On top of the deterministic scorer, an LLM grades your transcripts on relevance, truthfulness, naturalness, and non-redundancy, giving a capped **×0.90–1.10** multiplier. The prompt and model are not published; the four dimensions are the contract (illustrative prompt in [docs/SCORING.md](docs/SCORING.md)). Content-aware, honest, non-repetitive fillers score well; "one moment" ×4 does not, and text aimed at the grader scores truthfulness 0.
- **Anti-gaming is deterministic too:** gibberish does not stop the latency clock, silence scores 0, claiming "booked!" before `book_flight` completes is penalized.

Official runs: `time_scale = 1.0`, 3 repetitions per scenario with the **median** taken, 120 s wall-clock cap per scenario, 300 s cap for `setup()`.

## 5. What to submit

Your submission is **this kit's layout with your code in it**:

```
your-repo/
├── submission.yaml       team, entry_point, python, requirements, env
├── agent/agent.py        your ParticipantAgent (may import other modules you add)
├── harness/  scenarios/  docs/  run_local.py  eval_submission.py   (unchanged)
└── ...anything else your agent needs (prompts, small models, data files)
```

Fill in [submission.yaml](submission.yaml):

```yaml
team: "your-team-name"
entry_point: "agent.agent:ParticipantAgent" # module:Class
python: "3.12" # 3.10–3.12
requirements: # public PyPI only
  - google-genai
env: # names only; values registered on the portal
  - SECRET_GEMINI_API_KEY
```

Before every upload, run the exact official procedure yourself:

```bash
python eval_submission.py . --time-scale 8 --reps 1     # fast: does it import, boot, and score?
python eval_submission.py . --reps 3                      # real time, official procedure
```

`VERDICT: INVALID SUBMISSION` means your package would score 0 — fix it locally. Then check:

1. Package imports and runs at `--time-scale 1` with no crashes.
2. No secrets in the repo; every key your code reads is listed under `env` and registered on the portal.
3. Models are declared: hosted-API key names under `env`; open models via a public checkpoint or an included hosting script (see [docs/SUBMISSION.md](docs/SUBMISSION.md)). Your agent logic lives in the submission, not on your own server.
4. `setup()` finishes well inside 300 s; a scenario finishes inside 120 s.
5. Nothing reads `ground_truth`, scenario IDs, or timestamps to decide behavior.

**Submission:** one final package per team by the deadline, evaluated after the deadline on the hidden set. There is no leaderboard and no feedback during the event — your local runs and generated scenarios are your only signal. How to declare models and dependencies, and the honor code, are in [docs/SUBMISSION.md](docs/SUBMISSION.md).

## 6. Mistakes that cost the most points

| mistake                                            | effect                                                     | fix                                                         |
| -------------------------------------------------- | ---------------------------------------------------------- | ----------------------------------------------------------- |
| synchronous LLM/HTTP call inside `run()`           | freezes the harness; late events, false recovery penalties | async client, or `await asyncio.to_thread(...)`             |
| waiting for the tool before saying anything        | latency 0                                                  | filler first, then `tool_call`                              |
| no `call_id` on `tool_call`                        | cannot cancel → recovery violation                         | always set your own                                         |
| `final_response` without `state_snapshot`          | safety deduction, state checkpoints fail                   | make `emit()` add it automatically                          |
| re-issuing the old arguments after an interruption | "stale call re-issued" violation                           | clear pending work and slots before re-planning             |
| acting on an unclear audio slot                    | loses the clarification checkpoints                        | `clarification_request` first                               |
| retrying `book_flight` after a timeout             | duplicate booking penalty                                  | check with a read-only call or ask                          |
| fillers like "…" or the same line four times       | no latency credit, safety deductions                       | ≥3 alphabetic characters, vary the text, ≤4 per scenario    |
| hardcoding `weather_lookup` or city lists          | fails re-skinned and hidden scenarios                      | read `tool_manifest`; extract slots generally               |
| lazy model load on the first user turn             | first response several seconds late                        | load in `setup()` (off the clock) and cache at module level |

## 7. Glossary

- **event / action** — a dict the harness sends you / you send back. The only interface.
- **virtual ms** — the scenario clock. `--time-scale` changes how fast it runs, not its numbers.
- **slot** — a value you extracted (`destination`, `date`, `flight_id`, `passenger_name`, `booking_id`, `device_model`, `issue_summary`).
- **state_snapshot** — `{"intent": ..., "slots": {...}}`, your current understanding; how the scorer checks state without caring about your architecture.
- **read_only / state_modifying** — tool kinds. Retry the first freely; never duplicate the second.
- **trace** — the recorded list of events, actions, and tool completions. If it is not in the trace, it is not scored.
