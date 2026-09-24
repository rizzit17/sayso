# systemdesign.md
## Detailed System Design — Teachable Voice Automation (Samsung PRISM Theme 3)

This is the deepest technical layer: exact behavior, algorithms, data models, and edge-case handling. Read alongside `architecture.md` (structure) and `context.md` (requirements).

---

## 1. State Machine

### 1.1 States

```
IDLE
TEACHING
LEARNING_CONFIRMATION
READY
MATCHING_INTENT
EXTRACTING_SLOTS
VALIDATING_PARAMETERS
LAUNCHING_APP
OBSERVING_UI
MATCHING_STEP
EXECUTING_ACTION
VERIFYING_STATE
RECOVERING
ASKING_USER
CREDENTIAL_BOUNDARY
PAYMENT_BOUNDARY
COMPLETED
FAILED
UNKNOWN_INTENT
AMBIGUOUS_INTENT
```

### 1.2 Transition Table

| From | Event / Guard | To |
|---|---|---|
| IDLE | user starts teach mode | TEACHING |
| IDLE | user issues voice command | MATCHING_INTENT |
| TEACHING | credential/payment screen detected | LEARNING_CONFIRMATION (workflow truncated before boundary) |
| TEACHING | user manually ends teaching | LEARNING_CONFIRMATION |
| LEARNING_CONFIRMATION | workflow validated & persisted | READY |
| LEARNING_CONFIRMATION | validation fails (empty/degenerate workflow) | FAILED → IDLE (with error microcopy) |
| READY | voice command received | MATCHING_INTENT |
| MATCHING_INTENT | confidence ≥ HIGH_THRESHOLD | EXTRACTING_SLOTS |
| MATCHING_INTENT | LOW_THRESHOLD ≤ confidence < HIGH_THRESHOLD | AMBIGUOUS_INTENT |
| MATCHING_INTENT | confidence < LOW_THRESHOLD | UNKNOWN_INTENT |
| AMBIGUOUS_INTENT | user disambiguates | EXTRACTING_SLOTS |
| AMBIGUOUS_INTENT | user declines / timeout | IDLE |
| UNKNOWN_INTENT | user accepts teach offer | TEACHING |
| UNKNOWN_INTENT | user declines | IDLE |
| EXTRACTING_SLOTS | slots resolved (all required present) | VALIDATING_PARAMETERS |
| EXTRACTING_SLOTS | required slot missing | ASKING_USER (guard: `slotMissing=true`) |
| VALIDATING_PARAMETERS | valid | LAUNCHING_APP |
| VALIDATING_PARAMETERS | invalid (type mismatch, out of domain) | ASKING_USER |
| LAUNCHING_APP | app launched / already foreground | OBSERVING_UI |
| LAUNCHING_APP | target app not installed | FAILED (specific reason: `APP_NOT_INSTALLED`) |
| OBSERVING_UI | snapshot captured | MATCHING_STEP |
| MATCHING_STEP | target resolved, confidence ≥ threshold | (pre-check) → CREDENTIAL_BOUNDARY check |
| MATCHING_STEP | target resolved, confidence < threshold | RECOVERING |
| (pre-check) | step target is credential/payment/OTP/login | CREDENTIAL_BOUNDARY / PAYMENT_BOUNDARY |
| (pre-check) | step target is safe | EXECUTING_ACTION |
| EXECUTING_ACTION | action dispatched | VERIFYING_STATE |
| VERIFYING_STATE | expected post-state observed | (more steps? → OBSERVING_UI : → COMPLETED) |
| VERIFYING_STATE | unexpected state | RECOVERING |
| RECOVERING | recovery strategy succeeds (retry budget not exceeded) | OBSERVING_UI |
| RECOVERING | retry budget exceeded / recovery exhausted | ASKING_USER |
| ASKING_USER | user supplies needed info | (resume at point of interruption) |
| ASKING_USER | user cancels / timeout (StuckDetector cap, default 30s for T10-class stuck) | FAILED |
| CREDENTIAL_BOUNDARY / PAYMENT_BOUNDARY | user manually completes and resumes automation (explicit user action) | READY (new run may continue post-boundary only if user explicitly re-invokes; automation NEVER auto-resumes past a boundary) |
| CREDENTIAL_BOUNDARY / PAYMENT_BOUNDARY | user dismisses | IDLE |
| COMPLETED | — | IDLE (after run report recorded) |
| FAILED | — | IDLE (after run report recorded) |

### 1.3 Guards, Timeouts, Retry Limits

- `HIGH_THRESHOLD = 0.80`, `LOW_THRESHOLD = 0.45` (intent-match cosine/LLM-confidence scale 0–1). **ASSUMPTION** (recommended default; tunable).
- `MAX_STEP_RETRIES = 3` per step before escalating to ASKING_USER.
- `MAX_RECOVERY_WALL_CLOCK = 20s` per step-level recovery attempt.
- `STUCK_TIMEOUT = 30s` — matches T10's explicit 30-second pass criterion; if no resolution (recovered or user-asked) within 30s of first detecting the stuck condition, the system MUST have already transitioned to ASKING_USER or FAILED with a specific reason.
- `MATCHING_STEP` and `RECOVERING` are the only states permitted to loop back on themselves, and only under the above bounded counters — the state machine implementation MUST enforce these as hard guards (not just conventions), e.g. via a per-run `retryCount` and `startTimestamp` checked on every re-entry.
- All ASKING_USER states carry a `pendingContext` (which slot / which mismatch) so that once resolved, execution resumes at the correct point rather than restarting the whole workflow.

---

## 2. Workflow Lifecycle

```
DRAFT (during teaching) → VALIDATED → ACTIVE (available for matching)
  → (each replay) → RUN_RECORDED (linked run history)
  → DEPRECATED (optional, if user deletes/replaces)
```

A workflow becomes `ACTIVE` only after `WorkflowGeneralizer` produces at least 1 non-degenerate step and the Learning Review UI is confirmed (implicit confirm allowed for demo speed, but the object model always supports explicit review).

---

## 3. Teaching Lifecycle (Detailed)

1. **Trigger:** user says "Teach me to..." or taps "Teach a new flow"; utterance captured as `originalUtterance`.
2. **Session start:** `TeachingRecorder.start(utterance)` opens a `TeachingSession { sessionId, startTime, targetPackageHint }`.
3. **Per-event capture:** every relevant `AccessibilityEvent` → `UiSnapshot` (before) + performed action + `UiSnapshot` (after) → `RawAction { timestamp, packageName, actionType, targetNodeDescriptor, screenBefore, screenAfter }`.
4. **Irrelevant Action Filtering** (runs incrementally, not just at the end): each `RawAction` is scored by `IrrelevantActionFilter.scoreRelevance()` using:
   - **Package continuity:** does the action stay within the target app / a launched dependent app (e.g., a permission dialog)? Large deviation (e.g., switching to Phone/Dialer app mid-teach) → strong negative signal.
   - **Temporal locality:** actions with anomalously large idle gaps before/after relative to session average, especially bracketed by an app switch, are flagged.
   - **Goal contribution:** does the action's target UI role/semantic (e.g., "decline call" button in a Phone app) have zero semantic relation to the stated intent (e.g., "order pizza")?
   - **State reversibility:** if a subsequent action appears to undo an immediately preceding one within the *same* app (e.g., accidental tap then immediate back-navigation with no net state change), collapse both into a no-op.
   - Actions scoring below `RELEVANCE_THRESHOLD` (default 0.3, **ASSUMPTION**, tunable) are discarded from the persisted workflow but retained in the raw session log for developer debugging.
5. **Boundary truncation:** the moment `CredentialBoundaryDetector` flags the current screen (payment/OTP/password/login), `TeachingRecorder` stops capturing further actions immediately (no further nodes are even read beyond what's needed for boundary classification) and marks the session `truncatedAtBoundary = true`.
6. **Generalization:** `WorkflowGeneralizer.generalize(session)`:
   - Converts each retained `RawAction` into a `WorkflowStep` with a multi-signal `StepTarget` (§6).
   - Cross-references `originalUtterance` (via `SlotExtractor`, same component used in replay) against literal values that appeared as typed/selected UI content (e.g., "Margherita" typed into a search box, or a restaurant name tapped in a list) to identify **candidate slots** — see §5 Parameterization.
   - Produces `expectedStateTransition` per step from the diff of `screenBefore`/`screenAfter`.
7. **Validation:** workflow must have ≥1 step, a non-empty intent, and no step referencing a credential-boundary node; else → FAILED with reason `DEGENERATE_WORKFLOW`.
8. **Persistence:** `WorkflowRepository.save(workflow, status=ACTIVE)`.
9. **Confirmation:** UI shows generated "Learned: {summary}" microcopy (LLM-assisted summarization, deterministic fallback = template using intent+slots).

---

## 4. Replay Lifecycle (Detailed)

1. Voice command → transcript (§8).
2. `IntentMatcher.match(transcript)` → ranked candidate workflows with confidence.
3. Threshold routing per state table (§1.2).
4. `SlotExtractor.extract(transcript, candidateWorkflow.slotSchema)` → `Map<slotName, ExtractedValue>`.
5. `ParameterBinder.bind()`: for any required slot not extracted, or extracted with confidence below `SLOT_CONFIDENCE_THRESHOLD` (0.6, **ASSUMPTION**) → ASKING_USER with `pendingContext = missingSlot`. This is also where **bonus B3 (mid-flow parameter clarification)** applies: if a value that was recorded as a *fixed* literal during teaching is later found to be referenced as variable-like (e.g., user says "order a pizza on Zomato" with no restaurant mentioned, and restaurant was only ever a single fixed value in the one taught example), the binder still allows it to be asked rather than blindly reused, when `slotSchema` marks it ambiguous-fixed (see §5.3).
6. Execution loop (`ReplayEngine.run(workflow, boundParams)`):
   - `LAUNCHING_APP`: launch/foreground target package via standard `Intent` (package launch, not a deep link into app-specific screens — deep links are prohibited as an execution substitute per requirements, but launching the app's default launcher activity is legitimate and required).
   - For each `WorkflowStep` in order:
     a. `OBSERVING_UI`: capture current `UiSnapshot`.
     b. `MATCHING_STEP`: `SemanticUiMatcher.resolve(step.target, snapshot)` (§6).
     c. **Mandatory credential pre-check** on the resolved node before any dispatch (§ Safety, architecture.md §12).
     d. `EXECUTING_ACTION`: `ActionExecutor.perform(resolvedNode, step.actionType, boundParams)` — for parameterized steps (e.g., typing a search term, selecting a quantity stepper N times), the bound value replaces the originally recorded literal.
     e. `VERIFYING_STATE`: compare new snapshot against `step.expectedStateTransition` (fuzzy match — see §Verification below).
     f. On mismatch → `RECOVERING` (§9).
7. On reaching a step flagged `isBoundary=true` (payment/login/etc., either pre-recorded from teaching truncation or freshly detected) → `CREDENTIAL_BOUNDARY`/`PAYMENT_BOUNDARY`, run marked `COMPLETED_TO_BOUNDARY` (a success state for T2/T11 purposes — reaching payment unattended and stopping IS the success criterion).
8. On exhausting all steps without hitting a boundary (e.g., a workflow that doesn't have one) → `COMPLETED`.
9. `RunReporter.record(RunResult)` always executes, regardless of outcome.

### Verification ("expected post-action state")

A step's `expectedStateTransition` is a lightweight signature (e.g., "a new node containing {slotValue} becomes visible", "package changes to X", "a node matching role=Cart-Badge increments") captured during teaching. Verification is **fuzzy**: it checks that *a* node satisfying the transition's semantic pattern now exists, not that the tree is pixel-identical — this is what allows survival of minor UI changes (T7).

---

## 5. Parameterization

### 5.1 Slot Schema (per workflow)

```json
{
  "slots": [
    { "name": "item", "type": "string", "required": true, "sourceStepId": "step_2" },
    { "name": "restaurant", "type": "string", "required": true, "sourceStepId": "step_1" },
    { "name": "platform", "type": "enum", "required": true, "sourceStepId": "step_0" },
    { "name": "quantity", "type": "integer", "required": false, "default": 1, "sourceStepId": "step_3" },
    { "name": "address", "type": "enum(Home|Work|...)", "required": false, "default": "default_saved", "sourceStepId": "step_5" }
  ]
}
```

### 5.2 Binding Algorithm

`ParameterBinder.bind(extracted, schema)`:
1. For each schema slot, look up `extracted[slot.name]`.
2. If present and passes type/domain validation → bind.
3. If absent and `required=false` → use `default`.
4. If absent and `required=true` → ASKING_USER.
5. Bound values are substituted at execution time into the specific step's action payload (e.g., text-input value, or used to re-run the semantic search for "find element with text == boundValue" instead of the originally recorded literal text) — **the original literal string recorded during teaching is never replayed verbatim for a slot-tagged step.**

### 5.3 Fixed vs. Variable Detection (Teaching-Time Heuristic)

During generalization, a recorded literal becomes a **slot candidate** if any of:
- It was extracted by `SlotExtractor` from `originalUtterance` and also appears (exactly or fuzzily) as content typed/selected in a `RawAction`.
- It corresponds to a UI role commonly variable across the app's domain (e.g., a search-box input, a quantity stepper, an address-selector list item) — a small **domain role dictionary** seeded per target app category (food-delivery: item/restaurant/quantity/address; e-commerce: search-term/quantity/address) is used, marked clearly in code as an **ASSUMPTION-based heuristic**, not an official requirement.
- Everything else remains a fixed structural step (e.g., "tap 'Add to Cart' button", "tap the cart icon").

---

## 6. Semantic UI Matching Algorithm

### 6.1 StepTarget Signal Hierarchy (highest → lowest priority)

1. `resourceId` (exact match)
2. `contentDescription` (exact, then fuzzy ≥0.85 similarity)
3. `visibleText` (exact, then fuzzy ≥0.85 similarity, then slot-substituted expected text for parameterized steps)
4. `semanticRole` (inferred role: button/list-item/input/checkbox, from className + clickable + text patterns)
5. `className`
6. `parentChildContext` (does a sibling/parent match a recorded neighboring-label signature?)
7. `screenStateSignature` similarity (is this even plausibly the same screen as during teaching?)
8. `boundsRelativePosition` (last-resort fallback only, normalized to screen size, never absolute pixels)

### 6.2 Scoring

```
score(candidateNode, stepTarget) =
    w1*resourceIdMatch + w2*contentDescMatch + w3*textMatch +
    w4*roleMatch + w5*classMatch + w6*contextMatch +
    w7*screenSigMatch + w8*boundsMatch
```
Default weights (ASSUMPTION, tunable): `w1=0.30, w2=0.20, w3=0.20, w4=0.10, w5=0.05, w6=0.08, w7=0.05, w8=0.02` (bounds weight deliberately minimal — last resort only).

- `CONFIDENCE_THRESHOLD_EXECUTE = 0.65`. Below this → do **not** tap; go to RECOVERING.
- When multiple candidates score within `0.05` of each other (ambiguous), prefer the one whose `parentChildContext` best matches, then LLM tie-break is invoked (AI-assisted path per architecture.md §4) with the two candidates' serialized descriptors and the step's semantic intent as the prompt — bounded single call, cached per (workflow,step) for the run.

### 6.3 Cross-App Generalization Hook (Bonus B2)

`StepTarget` additionally stores an app-agnostic `semanticRole` and `domainConcept` (e.g., `domainConcept="add_to_cart_button"`). When resolving on an app not exactly matching `workflow.supportedPackages`, the matcher falls back to `domainConcept`+`semanticRole`+`visibleText` fuzzy matching only, with a stricter confidence threshold (`0.75`), and the run is explicitly flagged `crossAppGeneralization=true` in logs/report for demo transparency. This is a **best-effort recommended design**, not a guaranteed capability for arbitrary apps.

---

## 7. Action Planner & Executor

- **Planner:** produces an ordered `List<PlannedAction>` from `List<WorkflowStep>` + bound parameters, resolving one step at a time (not all up-front) so each resolution uses the freshest UI snapshot — this is essential for T7/T10 resilience.
- **Executor:** wraps `AccessibilityNodeInfo.performAction()` for click/focus/set-text, and `dispatchGesture()` for tap/scroll/swipe where a direct node action isn't available. Text input uses `ACTION_SET_TEXT` where supported, falling back to a synthesized tap+type gesture sequence otherwise.
- Every dispatched action is logged with: node descriptor, action type, bound value (if any), pre/post snapshot hashes, timestamp, and confidence score.

---

## 8. Voice / Intent / Slot Pipeline

1. **ASR:** Android `SpeechRecognizer`, partial + final results; final transcript normalized (lowercase, punctuation-stripped, common ASR-error corrections via a small substitution table — e.g., "dominoes"→"domino's").
2. **Intent embedding:** each `ACTIVE` workflow has a cached embedding of its `generalizedIntentRepresentation` (a short canonical phrase, e.g., "order food item from restaurant via delivery platform"), computed once at save-time and cached in storage (`workflows.intent_embedding` BLOB) — avoids recomputation on every utterance (Performance Strategy §19).
3. Incoming transcript embedded once per utterance; cosine similarity against all cached workflow embeddings → ranked list.
4. Confidence routing per §1.2.
5. **Slot extraction:** hybrid — regex/gazetteer fast path for structured slots (numbers → quantity, "home"/"work" → address enum) plus LLM-based free-text entity extraction for open-domain slots (item name, search term, restaurant name) constrained by a JSON-schema prompt (structured output per `anthropic_api_in_artifacts` JSON convention if the AI layer is Claude/Gemini-schema-based — the concrete model is Gemini 3.8 per the coding agent's own stack, chosen by Antigravity during implementation).
6. Ambiguity detection: if top-2 intent candidates' confidences differ by < `0.1` → AMBIGUOUS_INTENT even if the top one exceeds `HIGH_THRESHOLD`, to satisfy T13's "don't silently guess wrong."

---

## 9. Recovery Engine (Detailed Strategy Chain)

Invoked from `MATCHING_STEP` (low confidence) or `VERIFYING_STATE` (mismatch):

```
1. RESNAPSHOT — wait briefly (e.g., 500ms, bounded), re-capture UI (handles async loading).
2. DISMISS_KNOWN_OVERLAY — if a node matches a known "dismissible" pattern (close/X icon, "Not now", "Skip") 
   AND dismissing it is judged non-destructive (does not match any credential/payment/irreversible-action 
   pattern) AND it did not appear during teaching (i.e., it's a novel interstitial) → dismiss, then RESNAPSHOT.
3. RELAX_MATCH — re-run SemanticUiMatcher with next-lower-priority signal combination / lower threshold 
   (still ≥ a hard floor, e.g., 0.5, below which it will never auto-tap).
4. ALTERNATE_STATE_CHECK — check for named alternate-branch signatures recorded/known for this step 
   (e.g., "item already in cart" branch for T7) — if matched, take the corresponding alternate action 
   (e.g., proceed to checkout instead of re-adding) if that alternate action is itself deterministic and safe; 
   otherwise fall through.
5. ESCALATE — if steps 1-4 exhausted within MAX_STEP_RETRIES / MAX_RECOVERY_WALL_CLOCK → StuckDetector 
   generates a SPECIFIC question using the last known mismatch context, e.g.:
   "I can't find the Domino's restaurant in the current search results. Should I search again?"
   (Never a generic "something went wrong.")
```

`StuckDetector` also independently tracks **global run time** and a **repeated-identical-action counter** (to hard-prevent infinite loops even if individual step-level guards were somehow bypassed) — if the same `(stepId, resolvedNodeDescriptor)` pair is attempted more than 2 times in a run, force ASKING_USER regardless of other guards.

---

## 10. Confidence System

A single normalized `RunConfidence` is tracked and exposed in logs/report, aggregating: intent-match confidence, per-step matching confidence (min across executed steps), and recovery-invocation count (each invocation reduces displayed confidence slightly for transparency) — purely observational, does not itself gate execution (execution gating uses the specific per-decision thresholds above).

---

## 11. Credential Boundary Detection (Deterministic Spec)

A node/screen is classified as a boundary if **any** of:
- `AccessibilityNodeInfo.isPassword() == true` on any visible input.
- `inputType` flags matching `TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`.
- `resourceId` or `text`/`contentDescription` (case-insensitive) contains any of a maintained keyword list: `password, otp, one-time, cvv, card number, expiry, pin, login, sign in, log in, pay now, payment, upi pin, verify` (localizable list; English baseline for hackathon scope).
- `className` matches known secure-entry widget classes.
- Screen/package is a recognized system payment sheet (e.g., Google Pay overlay) — detected via package name.

On any match → immediate halt **before** the pending action is dispatched, regardless of which pipeline stage triggered detection (teaching or replay). This check is duplicated at the lowest possible layer (`ActionExecutor.perform()` itself refuses to execute against any node independently re-classified as a boundary, even if an upstream caller somehow failed to check) — **defense in depth**, given the −10 penalty on failure.

---

## 12. Caching

- Workflow intent embeddings cached at save-time (see §8.2).
- Per-run, resolved `StepTarget` candidate scores cached to avoid recomputation if `VERIFYING_STATE` needs to re-read the same snapshot.
- LLM calls (disambiguation, slot extraction, tie-break) are cached by `(promptHash)` within a run to avoid duplicate calls on retry loops.

---

## 13. Persistence Schema

```
workflows(
  id TEXT PK, intent_tag TEXT, original_utterance TEXT,
  generalized_intent TEXT, intent_embedding BLOB,
  supported_packages TEXT (JSON array), slot_schema TEXT (JSON),
  status TEXT, created_at INTEGER, updated_at INTEGER, version INTEGER
)
workflow_steps(
  id TEXT PK, workflow_id TEXT FK, step_order INTEGER,
  action_type TEXT, step_target TEXT (JSON: resourceId, contentDesc, text,
    role, className, parentContext, screenSig, bounds),
  expected_state_transition TEXT (JSON), is_boundary INTEGER,
  slot_binding TEXT NULLABLE (slot name if parameterized)
)
runs(
  id TEXT PK, workflow_id TEXT FK, started_at INTEGER, ended_at INTEGER,
  status TEXT (COMPLETED|COMPLETED_TO_BOUNDARY|FAILED|ASKED_USER),
  stopped_at_step_id TEXT NULLABLE, failure_reason TEXT NULLABLE,
  bound_params TEXT (JSON), confidence REAL
)
run_steps(
  id TEXT PK, run_id TEXT FK, step_id TEXT FK, outcome TEXT,
  confidence REAL, recovery_invoked INTEGER, timestamp INTEGER
)
app_registry(
  package_name TEXT PK, display_name TEXT, category TEXT, last_seen_installed INTEGER
)
```

Inspectability: every table is browsable via an in-app "Learned Flows" / "Run History" screen (design.md) and via standard `adb shell` Room/SQLite inspection during development.

---

## 14. APIs / Interfaces Between Modules

```kotlin
interface ITeachingEngine {
    suspend fun startTeaching(utterance: String): TeachingSession
    suspend fun finalizeTeaching(session: TeachingSession): Workflow
}
interface IReplayEngine {
    suspend fun execute(workflow: Workflow, boundParams: Map<String, Any>): RunResult
}
interface IIntentUnderstanding {
    suspend fun matchIntent(transcript: String, candidates: List<Workflow>): List<ScoredMatch>
    suspend fun extractSlots(transcript: String, schema: SlotSchema): Map<String, ExtractedValue>
}
interface ISemanticUiMatcher {
    fun resolve(target: StepTarget, snapshot: UiSnapshot): MatchResult
}
interface ICredentialBoundaryDetector {
    fun isBoundary(node: UiNode, snapshot: UiSnapshot): Boolean
}
interface IRecoveryManager {
    suspend fun recover(context: RecoveryContext): RecoveryOutcome
}
interface IWorkflowRepository {
    suspend fun save(workflow: Workflow)
    suspend fun findActive(): List<Workflow>
    suspend fun recordRun(result: RunResult)
    suspend fun lastRun(workflowId: String? = null): RunResult?
}
```

These interfaces are the contract boundary Antigravity must preserve so each component is independently testable (§Testing Strategy).

---

## 15. Concurrency

- All accessibility event handling on a dedicated coroutine dispatcher, never blocking the main/UI thread.
- Voice capture and intent/slot resolution run concurrently with UI observation warm-up (pre-fetch current snapshot while ASR is still finalizing) to reduce perceived latency.
- Only one active `ReplayEngine` execution permitted at a time (mutex-guarded) — concurrent teach+replay is disallowed by the state machine (`TEACHING` and replay states are mutually exclusive).

---

## 16. Timeout / Retry Strategy

Summarized from §1.3: step-level retries ≤3, step recovery wall-clock ≤20s, global stuck timeout 30s (T10 requirement), and a hard global run timeout (e.g., 90s, **ASSUMPTION**) after which the run is force-FAILED with reason `GLOBAL_TIMEOUT` regardless of internal state, guaranteeing the demo never appears frozen.

---

## 17. Logging

Every run emits structured (JSON-lines) log entries per the observability list in `context.md`/master prompt: user command, matched workflow, extracted slots, current app, current screen/state signature, matched UI target descriptor, selected action, confidence, verification result, recovery attempt(s), failure reason, stopping reason, final status. Logs are timestamped and correlated by `runId` for easy live debugging during the hackathon and for populating the Run History UI.

---

## 18. Testing Strategy

### Unit Tests
- Intent matching (embedding similarity thresholds, tie-break paths).
- Slot extraction (item/quantity/address/search-term, including ASR-noise variants).
- Parameter binding (required/optional/default logic).
- Semantic UI matching (signal weighting, threshold behavior, fallback-to-bounds).
- Confidence scoring aggregation.
- Credential boundary detection (exhaustive keyword/inputType/class matrix).
- Irrelevant action filtering (synthetic "phone call interrupt" fixture).
- State machine transitions (every edge in §1.2 table, including guard/timeout edges).

### Integration Tests
- Teaching → persistence round-trip (record synthetic session → assert stored `Workflow` shape).
- Persistence → retrieval (assert intent-embedding cache hit path).
- Retrieval → replay (mocked `UiSnapshot` fixtures simulating Zomato/Amazon-like trees).
- Replay → state verification (assert mismatch correctly triggers RECOVERING).
- Failure → recovery (assert bounded retries, correct escalation to ASKING_USER).
- Recovery → user clarification (assert specific, non-generic question text is generated).

### End-to-End Tests (T1–T14)
Each of T1–T14 implemented as an E2E scenario against a controlled test harness (ideally reusing/extending the participant kit's `harness/mock_env.py` + `scenario_gen.py` + `scorer.py` conventions per architecture.md §15) with a mock or instrumented UI tree fixture standing in for live Zomato/Amazon where full live-app E2E isn't feasible in CI, while still validating against the **real** apps manually before the recorded demo.

### Additional Coverage
- Unseen workflows / unseen phrasings not present in any fixture (generalization spot-checks).
- Unseen UI variations (structurally perturbed fixture trees).
- ASR error injection (character-level noise).
- Missing-parameter scenarios (all slot-omission permutations).
- Multiple learned workflows coexisting (no cross-contamination — T8/T12).
- Ambiguous/unknown intent fixtures.
- Accidental-tap fixtures (phone call, notification tap).
- Popup-appearance fixtures (T7).
- Logout/language-change fixtures (T10).
- Repeated credential-boundary fixtures (T11 — exhaustive, since failure is catastrophic to score).

---

## 19. Edge Cases (Deterministic Behavior Required)

| Case | Behavior |
|---|---|
| App not installed | `LAUNCHING_APP` → FAILED, reason `APP_NOT_INSTALLED`, clear user-facing message |
| Accessibility permission missing | Block all teach/replay entry points; onboarding screen requesting grant (design.md) |
| Speech recognition unavailable | Fallback to a text-input command bar; log `ASR_UNAVAILABLE` |
| Speech unclear (empty/low-confidence transcript) | Prompt "Sorry, I didn't catch that — try again?" — no state change |
| Workflow not found | UNKNOWN_INTENT path (T12) |
| Ambiguous workflow | AMBIGUOUS_INTENT path (T13) |
| Missing slot | ASKING_USER, `pendingContext=slot` |
| UI element missing | RECOVERING chain, then ASKING_USER |
| UI changed (structurally) | RELAX_MATCH strategy, then ASKING_USER if unresolved |
| Popup | DISMISS_KNOWN_OVERLAY strategy |
| Timeout | Global/step timeout → FAILED or ASKING_USER per §16 |
| Network failure (AI call) | Deterministic local fallback matcher/extractor engaged; degraded-confidence flag logged |
| AI unavailable | Same as network failure; core safety paths unaffected (they're deterministic) |
| Login required | CREDENTIAL_BOUNDARY |
| Payment screen | PAYMENT_BOUNDARY |
| OTP screen | CREDENTIAL_BOUNDARY |
| Password screen | CREDENTIAL_BOUNDARY |
| App crash (target app) | Detected via `TYPE_WINDOW_STATE_CHANGED` to launcher/crash-dialog package → FAILED, reason `TARGET_APP_CRASHED` |
| User cancellation | Immediate transition to IDLE, run recorded as `CANCELLED` |
| User manually intervenes mid-replay | Detected via unexpected user-originated accessibility event during automation → pause automation, ask "Did you want to take over?" |
| Workflow partially completed | Always recorded in `runs.stopped_at_step_id`; T14 reporting surfaces this exactly |

---

## 20. Security / Privacy

- No credential/payment/OTP data is ever captured, logged, or persisted — enforced structurally by halting *before* such screens are read beyond boundary-classification fields.
- `UiSnapshot`s store only structural/semantic metadata, never screenshots or raw user-entered free text beyond what's needed for slot values the user themselves spoke (which are already voice-transcribed, not a new exposure).
- All storage is local (Room/SQLite) — no cloud sync of workflow data, minimizing privacy surface for a hackathon prototype.
- LLM calls send only the minimum necessary text (transcript, candidate node descriptors) — never full-screen dumps containing potentially sensitive unrelated on-screen content beyond the resolved step's context.

---

## 21. Performance Strategy

- Intent embeddings precomputed/cached at save-time (§8.2) → replay matching is a cheap cosine-similarity scan, not a fresh LLM call, for the common case.
- LLM calls are bounded: disambiguation and tie-break calls only, each with a hard timeout (e.g., 3s) and single retry, after which the deterministic fallback path is used — ensures a live demo never stalls waiting on a network call.
- UI observation throttled (e.g., debounce `TYPE_WINDOW_CONTENT_CHANGED` bursts to one snapshot per ~150ms) to avoid excessive tree-walking on chatty screens.
- Step-level caching: a resolved node's descriptor is reused for `VERIFYING_STATE` immediately following `EXECUTING_ACTION` rather than re-resolving from scratch.
- Deterministic fast paths (credential detection, state machine, retry counters) never touch the network, keeping the safety-critical loop's latency bounded purely by on-device Accessibility API calls.
