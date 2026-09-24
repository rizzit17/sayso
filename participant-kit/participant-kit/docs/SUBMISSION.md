# Submitting Your Agent

## What a submission is

A git repo (or zip) containing this kit's layout with your agent inside, plus a `submission.yaml` at the root. **Submissions are Python (3.10–3.12) only** — the evaluator imports your class in-process; there is no subprocess or other-language transport. Read [PROTOCOL.md §5](PROTOCOL.md) (runtime contract) before wiring an LLM client.

```yaml
team: "your-team-name"
entry_point: "agent.agent:ParticipantAgent"   # module:Class, same as --agent locally
python: "3.12"                                 # 3.10–3.12 supported
# optional: extra pip packages (must resolve on public PyPI)
requirements:
  - google-genai
  - numpy
# optional: env vars your agent needs; SECRET_* values are injected from
# the credentials you register on the event portal, never committed to the repo
env:
  - SECRET_GEMINI_API_KEY
```

The evaluation runner does, in effect:

```bash
pip install -r <your requirements>
python run_local.py --all --agent <your entry_point> --time-scale 1 --quiet --json results.json
# ...but with scenarios/ replaced by the hidden set, plus per-scenario tool manifests
```

Your agent class only ever sees the two queues. **Test that exact command locally before every submission** — an import error costs you one of your daily submissions.

## Models and dependencies

Any model is welcome — hosted APIs or open weights. We particularly encourage the **Gemini API** and the open **Gemma** models.

* **Hosted APIs:** list the client library under `requirements` and the key *name* under `env`; values are registered on the portal, never committed.
* **Open models** (Gemma, Llama, Mistral, Whisper, …): point us at a public checkpoint (e.g. a Hugging Face id) or include the hosting script that starts your model server, so we can reproduce your setup. Load weights in `setup()`, which runs off the clock.
* The agent's decision logic must live in the submission — do not route it through your own servers.

Timing caps: 120 s wall clock per scenario and 300 s for `setup()` ([PROTOCOL.md §4–5](PROTOCOL.md)).

**Latency reality check:** LLM API round-trips (300–2000 ms) count against the same clock as everything else. This is not an accident — it *is* the dual-process problem. The winning pattern is a fast local/rule/small-model layer that speaks within 800 ms while a slower reasoning call runs behind it.

## Determinism requirements

* Seed anything you can (`temperature=0` or fixed seeds where the API supports it).
* The sealed test run does 3 repetitions and takes the per-scenario **median**, so occasional API jitter won't sink you — but an agent that only sometimes works will show its true colors.
* Do not depend on wall-clock time, machine specs, or network timing side channels.

## Submission rules

* **One final submission per team**, before the deadline. There is no leaderboard and no feedback during the event: only the public scenarios in this kit are open; everything else is evaluated after the deadline.
* A package that fails to import or crashes scores 0 — run `eval_submission.py` on it before submitting.
* A submission that aces the public set but collapses on re-skinned hidden scenarios is reviewed manually for hardcoding. Clean code with an honest drop is fine; scenario-ID lookup tables are not.

## Dry-run the official procedure yourself

The evaluator is public. Point it at your own package to run the exact official flow — validation, contract smoke test, 3 reps, median, weighting:

```bash
python eval_submission.py path/to/your-submission --reps 3
```

Your package is just this kit's layout with your agent in `agent/agent.py` and the `submission.yaml` shown at the top of this page. The evaluator prints `VERDICT: INVALID SUBMISSION` with the reason if the YAML is malformed, the entry point doesn't import, or the agent crashes on its first event — fix those locally before you submit.

## Checklist before you submit

1. `python run_local.py --all --agent <your entry> --time-scale 1 --quiet` finishes with no crashes.
2. Same run at `--time-scale 1` (not 8!) — your real think time fits inside the latency curve.
3. Your agent handles a tool it has never seen: `python run_local.py --scenario scenarios/pub_09_text_unseen_tool.json --agent <your entry>`, then `python -m harness.scenario_gen --template unseen_tool --n 5 --seed 3 --out generated/` and run those. Your agent must get the schema from the `tool_manifest` event — hardcoding `weather_lookup` proves nothing, the hidden tools are different.
4. No secrets in the repo; `SECRET_*` env vars registered on the portal.
5. `submission.yaml` entry point spelled correctly (`module:Class`).

## Honor code

Public scenarios are for development; hidden scenarios are the exam. Sharing hidden-scenario details, submitting under multiple team names, or attacking the evaluation infrastructure disqualifies the team. When something is ambiguous, ask the organizers — clarifications are announced to all teams and become binding for everyone.
