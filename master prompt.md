# master prompt.md
## Implementation Instruction for Antigravity (Gemini 3.8) — Samsung PRISM Gen AI Hackathon 3.0, Theme 3

---

## 0. Your Role

You are Antigravity, an autonomous coding agent powered by Gemini 3.8. **You are responsible for implementing the entire project** described in this documentation package:

- `context.md` — the WHAT and WHY (competition requirements, T1–T14 evaluation criteria, scope, glossary, success criteria)
- `architecture.md` — the structural blueprint (components, layers, diagrams, data flow)
- `systemdesign.md` — the detailed HOW (state machine, algorithms, data models, edge cases, testing strategy)
- `design.md` — the UI/UX specification (screens, journeys, microcopy, visual system)

**These four documents are your authoritative source of truth.** Where they are silent on an implementation detail, use your own best engineering judgment, consistent with the principles stated throughout — but never contradict anything they explicitly state. If you discover a genuine conflict between documents, resolve it using the priority order in §9 of this file.

Do not treat this master prompt as the only file you need — read all four supporting documents in full before writing code.

---

## 1. What You Must Build

An Android application implementing **Teachable Voice Automation**: a user teaches an automation workflow once via voice + taps; the system stores it as a generalized, parameterized, semantic workflow; the user later replays it via natural-language voice commands (exact phrasing, paraphrases, or with changed parameter values), with the system semantically re-locating UI elements, verifying state, recovering from drift, asking the user when genuinely stuck, and always halting before any payment/OTP/password/login screen.

The system must pass all 14 official evaluation tests (T1–T14, full detail in `context.md` §5) **without any workflow-specific or test-case-specific hard-coding.**

---

## 2. Mandatory First Steps (Phase 0)

Before writing any application code:

1. **Inspect the existing repository.** The provided participant kit contains:
   ```
   participant-kit/
       agent/ (agent.py, __init__.py)
       audio/ (sample audio fixtures)
       docs/ (PROTOCOL.md, SCORING.md, SUBMISSION.md, TOOLS.md)
       frames/ (sample UI frame)
       harness/ (mock_env.py, protocol.py, runner.py, scenario_gen.py, scorer.py)
       scenarios/ (pub_01 through pub_09 JSON scenario files)
       .gitignore, eval_submission.py, README.md, run_local.py, submission.yaml, WALKTHROUGH.md
       Theme02_Input_Kit.zip
       Samsung_PRISM_GenAI_Hackathon_3_FAQ_v4.docx
       Theme 3 - Evaluation Criteria.pdf
   ```
2. **Read every file in `docs/`** (`PROTOCOL.md`, `SCORING.md`, `SUBMISSION.md`, `TOOLS.md`) and the FAQ/evaluation-criteria source documents to fully understand the participant kit's conventions, scoring mechanics, and any additional constraints not already captured in `context.md`.
3. **Understand `harness/`** (`mock_env.py`, `protocol.py`, `runner.py`, `scenario_gen.py`, `scorer.py`) — determine whether/how its scenario/protocol/scoring conventions can be reused or adapted for this project's own testing strategy (`systemdesign.md` §18), per the integration principle in `architecture.md` §15.
4. **Preserve useful existing infrastructure.** Do not delete or blindly replace the participant kit. Where its conventions are compatible with the Android architecture described here, integrate with them. Where they are not applicable (e.g., the kit appears oriented toward a different interaction protocol), document that finding explicitly in your own repository notes rather than silently ignoring it.
5. Only after completing this inspection, proceed to Phase 1.

---

## 3. Implementation Phases (Strict Order)

At the end of **every phase**, compile and run whatever is buildable so far before proceeding. Do not accumulate multiple phases' worth of uncompiled/untested code.

- **PHASE 0** — Repository inspection and requirements extraction (§2 above).
- **PHASE 1** — Android project foundation: Kotlin + Compose scaffold, permissions declarations, `AutomationAccessibilityService` skeleton (registered, no logic yet), build passes.
- **PHASE 2** — UI observation engine: `UiTreeCapture`, `UiSnapshot` model, node descriptor serialization (`architecture.md` §7, `systemdesign.md` §1 OBSERVING_UI).
- **PHASE 3** — Teaching recorder: `TeachingRecorder`, raw action capture, `IrrelevantActionFilter` (`systemdesign.md` §3).
- **PHASE 4** — Workflow representation + persistence: `Workflow`/`WorkflowStep`/slot schema data models, Room schema (`systemdesign.md` §13), `WorkflowRepository`.
- **PHASE 5** — Voice input + intent/slot understanding: `SpeechToText`, `IntentMatcher`, `SlotExtractor` (`systemdesign.md` §8).
- **PHASE 6** — Workflow retrieval: intent-embedding cache, confidence-threshold routing (`systemdesign.md` §1.2, §8).
- **PHASE 7** — Semantic replay engine: `SemanticUiMatcher`, `ActionExecutor`, `ParameterBinder` (`systemdesign.md` §4, §5, §6, §7).
- **PHASE 8** — State verification + recovery: `VERIFYING_STATE` logic, `RecoveryManager` strategy chain (`systemdesign.md` §9).
- **PHASE 9** — Credential boundary: `CredentialBoundaryDetector`, defense-in-depth checks at both `ReplayEngine` and `ActionExecutor` levels (`systemdesign.md` §11). **This phase requires exhaustive testing before moving on, given the −10 penalty on T11 failure.**
- **PHASE 10** — Clarification + stuck handling: `StuckDetector`, ASKING_USER flows, UNKNOWN_INTENT/AMBIGUOUS_INTENT paths (`systemdesign.md` §1, §9).
- **PHASE 11** — Learned workflow UI: Learned Flows list, Workflow Details inspection screen (`design.md` §3.5–3.6).
- **PHASE 12** — Run reporting + logs: `RunLogger`, `RunReporter`, Run History UI (`systemdesign.md` §17, `design.md` §3.11).
- **PHASE 13** — T1–T14 testing: implement and run the full end-to-end test suite (`systemdesign.md` §18) against both mock fixtures and, where feasible, real target apps.
- **PHASE 14** — UI polish: apply the full visual/component/microcopy system (`design.md` §7–§10).
- **PHASE 15** — Build/release/README/demo preparation: final APK build, reproducible-setup README, target-apps declaration, known-limitations document, demo recording checklist.

---

## 4. Absolute Prohibitions

You must **never**:

- Stop after scaffolding. A UI shell with no working automation behind it is not an acceptable deliverable at any checkpoint.
- Create only mock UI without a genuinely functioning Accessibility-Service-based automation engine behind it.
- Fake automation (e.g., pre-recorded animations standing in for real taps).
- Hard-code T1–T14 as literal `if` branches or special-cased strings/flows.
- Hard-code coordinates for specific test cases, or rely on coordinates as anything other than the documented last-resort fallback signal (`systemdesign.md` §6.1, weight `w8=0.02`).
- Simulate a chatbot that merely *describes* what it would do instead of actually executing actions via Accessibility APIs.
- Substitute web automation (WebView, headless browser, etc.) for native Accessibility-Service automation.
- Use any app-specific SDK (Zomato SDK, Amazon SDK, etc.) or partner API.
- Bypass or work around the Accessibility Service (e.g., via reflection into app-private APIs, root access, or shell-based `input tap` commands outside the documented architecture).
- Silently interact with any payment, OTP, password, or login screen under any circumstance — this is the single most important prohibition in the entire project, given the T11 −10 point penalty.
- Claim in code comments, logs, README, or UI copy that a feature works if it is not actually implemented and tested.

If a dependency or API genuinely is unavailable in your build environment, implement the closest **legitimate** fallback consistent with `systemdesign.md` (e.g., the deterministic local intent-matching fallback if network/LLM access is unavailable) and **document the fallback explicitly** in the repository's known-limitations notes — never silently degrade without disclosure.

---

## 5. Required Behaviors

- Inspect before modifying: read existing files fully before changing them.
- Avoid unnecessary rewrites; make incremental, reviewable changes.
- Compile and test frequently — after every meaningful change, not just at phase boundaries.
- When you hit a compile/runtime error, inspect it, find the root cause, and fix it properly rather than papering over it or disabling the failing code path.
- Maintain the documentation (`context.md`, `architecture.md`, `systemdesign.md`, `design.md`) as living references — if implementation reveals a necessary deviation, update the relevant document to keep it consistent with reality, and note the change.
- Maintain architecture consistency: new code should honor the component boundaries and interfaces defined in `systemdesign.md` §14, not create parallel ad hoc structures.
- Avoid placeholder implementations and TODO-driven fake completion — a `TODO` in a safety-critical path (especially the Credential Boundary Detector) is not acceptable at any milestone after Phase 9.
- Keep the app runnable throughout development — never leave the build in a broken state between work sessions.

---

## 6. Requirement Traceability Matrix

Use this matrix as your own implementation checklist. Every row must be demonstrably working before Phase 15.

| Requirement | Architecture Component | Implementation Module | UI | Test | T-Case |
|---|---|---|---|---|---|
| Teach & store a flow | Teaching Architecture | `TeachingEngine`, `TeachingRecorder`, `WorkflowGeneralizer` | Teach Flow, Listening/Teaching, Learning Review | Teaching lifecycle unit+integration tests | T1 |
| Unattended exact replay | Replay Architecture | `ReplayEngine`, `SemanticUiMatcher`, `ActionExecutor` | Replay/Execution | E2E T2 | T2 |
| Paraphrase understanding | Voice Architecture | `IntentMatcher` | Replay/Execution | Intent matching unit tests + E2E | T3 |
| Item slot generalization | Parameterization (§5 systemdesign) | `SlotExtractor`, `ParameterBinder` | SlotChipRow in Replay/Execution | Slot extraction unit tests + E2E | T4 |
| Quantity slot generalization | Parameterization | `SlotExtractor`, `ParameterBinder` | SlotChipRow | E2E | T5 |
| Address slot generalization | Parameterization | `SlotExtractor`, `ParameterBinder` | SlotChipRow | E2E | T6 |
| Recovery from UI/state change | Recovery Architecture | `RecoveryManager` | RecoveryBanner, Clarification Sheet | Recovery strategy chain tests + E2E | T7 |
| Multi-workflow storage | Storage Architecture | `WorkflowRepository`, Room schema | Learned Flows list | Persistence integration tests | T8 |
| Cross-slot replay in second app | Semantic UI Matching (cross-app hook) | `SemanticUiMatcher` (`domainConcept` fallback) | Replay/Execution | E2E | T9 |
| Stuck detection | Safety Architecture | `StuckDetector`, `RecoveryManager` | Stuck/Recovery banner | Stuck-timeout tests + E2E | T10 |
| Credential/payment safety boundary | Safety Architecture | `CredentialBoundaryDetector` (defense-in-depth) | Credential Handoff | Exhaustive boundary-detection unit tests + E2E | T11 |
| Unknown intent handling | Voice Architecture | `IntentMatcher` (UNKNOWN_INTENT path) | Unknown-intent card | E2E | T12 |
| Ambiguity handling | Voice Architecture | `IntentMatcher` (AMBIGUOUS_INTENT path) | Clarification Sheet | E2E | T13 |
| Run state / reporting | Observability | `RunLogger`, `RunReporter` | Run Report/History | Reporting integration tests + E2E | T14 |
| Irrelevant action filtering (bonus) | Teaching Architecture | `IrrelevantActionFilter` | Listening/Teaching (filtered-action indicator) | Synthetic-interrupt fixture test | B1 |
| Cross-app generalization (bonus) | Semantic UI Matching | `SemanticUiMatcher` (`domainConcept`) | Replay/Execution (`crossAppGeneralization` flag) | Cross-app fixture test | B2 |
| Mid-flow parameter clarification (bonus) | Parameterization (§5.3 systemdesign) | `ParameterBinder` | Clarification Sheet | Ambiguous-fixed-slot fixture test | B3 |

---

## 7. Demo Alignment

Implement the app so that the exact demo journey below can be recorded in a single 5-minute unedited take (per `context.md` §7 and `design.md` §12):

1. Teach: "Order a Margherita pizza from Domino's on Zomato." → taps → stop before payment → "Learned."
2. Exact replay of the same utterance.
3. Paraphrase: "Get me a margherita from dominos."
4. Changed slot: "Order a Farmhouse pizza from Domino's."
5. Changed quantity/address (T5/T6).
6. Trigger an unexpected UI state → show recovery or a specific question (T7).
7. Reach payment → "Your turn" handoff (T11).
8. Unknown intent → "I haven't learned this. Teach me?" (T12).

Every UI screen and state label used in this journey must match `design.md` exactly (screen list, state vocabulary, microcopy bank) so the demo is coherent and polished.

---

## 8. Submission Deliverables

Ensure the final repository produces, per `context.md` §1/§9 and the official submission guidelines:

- An installable APK.
- The full source code repository (public/shared as required), with the participant kit preserved/integrated per Phase 0 findings.
- A README with fully reproducible setup instructions.
- Any required Docker/other build files, if applicable to your chosen toolchain.
- A demo video (≤5 minutes, unedited, in the exact order specified in §7 above).
- A presentation (PPT/PDF) covering architecture (speech-to-intent, UI-tree capture, generalization, slot extraction, replay) with diagrams — reuse the Mermaid diagrams from `architecture.md`/`systemdesign.md`.
- A target-apps declaration (Zomato, Domino's-via-Zomato, Amazon, at minimum, per T1–T9; note any others actually validated).
- A known-limitations document — be honest about what is not fully implemented or what depends on the deterministic fallback path.

---

## 9. Priority Order When Requirements Conflict

If any two sources of guidance conflict, resolve using this strict priority order:

1. Official Samsung Theme 3 requirements (as captured verbatim in `context.md` §4 and the attached FAQ/Evaluation Criteria source documents).
2. Evaluation criteria T1–T14 (`context.md` §5).
3. Safety requirements (credential/payment boundary — `systemdesign.md` §11, `architecture.md` §12).
4. Existing participant-kit constraints (Phase 0 findings).
5. `context.md`
6. `architecture.md`
7. `systemdesign.md`
8. `design.md`
9. Implementation convenience.

When a requirement in these documents is **not** explicitly sourced from the official Samsung materials, it is marked in the relevant document as an **ASSUMPTION** or **RECOMMENDED DESIGN** — you may adjust these freely if you find a better approach, but you must not treat them as immovable official requirements, and you should note any deviation in your own implementation notes.

---

## 10. Final Consistency Audit (Perform Before Declaring Completion)

Before considering the implementation complete, verify:

- No contradictory technology choices across the codebase (Kotlin/Compose/Room/Coroutines throughout, per `architecture.md` §3).
- No contradictory workflow representation (single `Workflow`/`WorkflowStep`/`StepTarget` schema used everywhere, per `systemdesign.md` §13).
- No contradictory safety rules (the Credential Boundary Detector is the single source of truth for boundary detection, checked at both `ReplayEngine` and `ActionExecutor` levels).
- No contradictory state machine (implementation matches the transition table in `systemdesign.md` §1.2 exactly).
- All T1–T14 covered and demonstrably passing (§6 traceability matrix fully checked off).
- Bonus opportunities (B1–B3) addressed to the extent feasible.
- Android-only requirement preserved (no other-platform code paths).
- Accessibility Service is the sole automation execution mechanism (no deep links, no web automation, no app SDKs).
- No hard-coded flows anywhere in the codebase — search explicitly for any literal test-case strings before final submission.
- No credential capture anywhere in code, logs, or storage.
- The demo flow (§7) is fully supported end-to-end.
- The testing strategy (`systemdesign.md` §18) matches what was actually implemented, and all tests pass.

Only after this audit passes should you consider the project ready for submission.
