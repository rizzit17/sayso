# Theme 5: Interruptible Agents

Build a real-time voice-native agent that thinks fast **and** slow: respond to a live conversational stream within duplex latency limits, delegate real work (searches, bookings, manual lookups) to slow asynchronous tools, and survive the user changing their mind mid-execution.

This kit is what grades you — same harness, same scorer, same tool conventions — minus the hidden scenarios.

**New here? Start with [WALKTHROUGH.md](WALKTHROUGH.md)** — problem, goal, what to implement, how to score yourself, what the hidden set looks like, what to submit.

## The challenge

The harness streams user events (text chunks, raw audio clips, video frames, interruptions) into your agent at speaking speed. Your agent streams actions back: quick spoken responses, async tool calls, cancellations, and grounded final answers with state snapshots. You are scored on **task completion**, **interruption recovery**, **latency**, and **safety** (no duplicate state-modifying actions, no filler spam). Rubric: [docs/SCORING.md](docs/SCORING.md).

## Quickstart (Python 3.10+, zero dependencies)

```bash
# watch the minimal reference agent handle an interruption, live
python run_local.py --scenario scenarios/pub_02_text_interrupt.json

# run the public set at 8x; the reference agent scores ~57/100 — closing the gaps is the challenge
python run_local.py --all --time-scale 8 --quiet

# fill in ParticipantAgent in agent/agent.py, then run yours
python run_local.py --all --agent agent.agent:ParticipantAgent

# generate practice scenarios, including tools you have never seen
python -m harness.scenario_gen --template search_interrupt --n 10 --seed 1 --out generated/
python -m harness.scenario_gen --template unseen_tool --n 5 --seed 3 --out generated/
```

Your agent may use anything — hosted LLM APIs, open models, planners; Gemini and Gemma are encouraged (see [docs/SUBMISSION.md](docs/SUBMISSION.md) for how to declare models and dependencies). **Read [docs/PROTOCOL.md §5](docs/PROTOCOL.md) before wiring an LLM client** — a blocking call freezes the harness.

## Files

| path | what |
|---|---|
| `agent/agent.py` | `ParticipantAgent` (yours) + `BaselineAgent`, a minimal reference that handles only the two simplest scenarios |
| `harness/runner.py` | streaming harness: replays events, runs tools async, records the trace |
| `harness/mock_env.py` | the 5 public tools with deterministic delays and failure injection |
| `harness/scorer.py` | **the official scorer** — byte-identical to the one grading the hidden sets |
| `harness/scenario_gen.py` | seeded generator for unlimited practice scenarios |
| `harness/protocol.py` | event/action definitions and validation |
| `scenarios/*.json` | 9 public scenarios (6 text, 2 audio, 1 visual) with ground truth |
| `audio/`, `frames/` | the raw MP3 / PNG media for the audio and visual scenarios — no transcripts or captions are given |
| `docs/` | [PROTOCOL](docs/PROTOCOL.md) · [TOOLS](docs/TOOLS.md) · [SCORING](docs/SCORING.md) · [SUBMISSION](docs/SUBMISSION.md) |
| `run_local.py` | run scenarios, print scores, dump traces |
| `eval_submission.py` | the official submission evaluator — dry-run your package before you submit |

## Public vs. hidden

We hide compositions, not rules.

- **Guarantee 1:** every event type, payload field, action type, and scoring rule used in hidden scenarios appears in this kit. Hidden scenarios are harder *combinations*, never new *mechanics*.
- **Guarantee 2:** hidden tools follow the schema conventions in [docs/TOOLS.md](docs/TOOLS.md), and every scenario hands your agent its tool list up front via the `tool_manifest` event. Handling an unseen tool from its schema is a scored skill; `pub_09` and the `unseen_tool` generator template let you practice it.
- **Guarantee 3:** the scorer here is the scorer that grades you. All hidden scenarios are published after the event.

Hidden set: ~60 scenarios, ~50% text / 30% audio / 20% visual, ~10 hidden tools. Multimodal scenarios carry **1.5× weight**.

## Evaluation

1. **Public scenarios** (this kit): unlimited local runs — this is the only feedback you get during the event.
2. **Final submission**: one package per team by the deadline, evaluated afterwards on the hidden set — 3 repetitions per scenario, median. Submissions that ace the public set but collapse on re-skinned hidden scenarios are reviewed manually.

## Rules

- Any architecture: rule-based, LLM-orchestrated, hybrid.
- Do not hardcode scenario IDs, timestamps, or expected strings; hidden scenarios include re-skinned public ones.
- Your agent sees only the event stream and tool results. `ground_truth` is not served to your agent; reading it is disqualifying.
- Official runs use `time_scale = 1.0`, a 120 s wall-clock cap per scenario, and a separate 300 s cap for your optional `setup()`.
- Response quality is additionally graded by an LLM (capped ×0.9–1.1 multiplier, see [docs/SCORING.md](docs/SCORING.md)); the prompt and model are not published.

Protocol clarifications are announced to all teams at once.
