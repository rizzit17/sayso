# design.md
## Product / UI / UX Specification — Teachable Voice Automation (Samsung PRISM Theme 3)

This document defines the **user-facing experience**. It must feel like a polished, Samsung-quality intelligent automation tool — not a generic chatbot, and not a bare-bones student CRUD app. See `context.md` for requirements, `architecture.md`/`systemdesign.md` for the underlying system this UI drives.

---

## 1. Information Architecture

```
Home
 ├─ Teach Flow
 │   └─ Listening / Teaching (live capture)
 │       └─ Learning Review
 ├─ Learned Flows
 │   └─ Workflow Details (inspection)
 ├─ Replay / Execution (entered via voice command from anywhere)
 │   ├─ User Clarification (modal/sheet)
 │   ├─ Stuck / Recovery (inline banner)
 │   └─ Credential Handoff (full-screen takeover)
 ├─ Run Report / History
 └─ Settings / Permissions
```

Navigation is a bottom nav or nav-rail with three primary destinations: **Home**, **Learned Flows**, **History** — plus a persistent floating voice-trigger (mic FAB) reachable from every screen, since voice commands are the primary interaction surface for Replay.

---

## 2. Screen List (12 screens, per requirement)

1. Home
2. Teach Flow (entry/setup)
3. Listening / Teaching (live capture)
4. Learning Review
5. Learned Flows (list)
6. Workflow Details (inspection)
7. Replay / Execution
8. User Clarification
9. Stuck / Recovery
10. Credential Handoff
11. Run Report / History
12. Settings / Permissions

---

## 3. Screen-by-Screen Specifications

### 3.1 Home
- **Purpose:** Orientation + fastest path into Teach or Replay.
- **Content:** Large mic FAB with system-status label (`READY` / `LISTENING` / `RUNNING`); a "Teach a new flow" primary button; a horizontally scrollable strip of the 2–3 most recently used Learned Flows as tappable chips (voice is primary, tap-to-replay is a secondary convenience); a compact status card showing the outcome of the last run ("Last run: Completed to payment · Domino's pizza · 2m ago").
- **Empty state:** No flows yet → hero illustration + "Teach your first flow" CTA, one-line explainer: "Show me something once, then just ask for it."
- **Loading state:** N/A (static).

### 3.2 Teach Flow (entry)
- **Purpose:** Start a teaching session with a clear contract.
- **Content:** "What are you about to show me?" prompt with mic button to speak the utterance (e.g., "Order a Margherita pizza from Domino's on Zomato"); live transcript preview; "Start Teaching" confirms and transitions to Listening/Teaching; secondary text: "I'll watch your taps until you reach payment or you tell me you're done."
- **Empty/invalid state:** Empty transcript → disable Start Teaching, inline hint "Try describing what you're about to do."

### 3.3 Listening / Teaching (live capture)
- **Purpose:** Transparent, real-time feedback that the system is observing — critical for trust and for the demo.
- **Content:** Persistent top banner: **"TEACHING — I'm watching your taps"** with a subtle pulsing indicator; a live, auto-scrolling step list building in real time as actions are recorded ("Opened Zomato", "Searched 'Domino's'", "Selected Margherita Pizza..."); a discreet "×" marker automatically appears next to any action the system filters as irrelevant (e.g., "Answered call — ignored") giving the user visible proof the Irrelevant Action Filter works (satisfies Bonus B1 demoability); a floating "Done Teaching" button for manual end, in addition to automatic stop at a credential boundary.
- **On boundary hit:** Banner changes to **"Learning stopped before payment."** and auto-transitions to Learning Review.
- **Loading state:** Each captured step shows a brief shimmer before resolving to its semantic label (steps are summarized slightly after capture, not instantly, since summarization may involve the AI layer).

### 3.4 Learning Review
- **Purpose:** Confirm what was learned; show it's a generalized workflow, not a rigid script.
- **Content:** Headline: **"Learned: {generated summary}"** (e.g., "Learned: order Margherita pizza from Domino's on Zomato"); a clean, numbered step list (semantic labels, not coordinates); a highlighted "Detected parameters" section listing identified slots as chips (Item: Margherita pizza · Restaurant: Domino's · Platform: Zomato · Quantity: 1 · Address: default) with a small "these can change next time" caption; primary CTA "Save Flow"; secondary "Discard".
- **Empty/error state:** Degenerate workflow (0 usable steps) → "I couldn't learn a usable flow from that — want to try again?" with retry CTA, no dead end.

### 3.5 Learned Flows (list)
- **Purpose:** Browse everything taught so far.
- **Content:** Card list, each card: flow name/summary, app icon(s), slot count, last-run status pill (Completed / Stopped at payment / Failed / Never run), last-used timestamp. Tap → Workflow Details. Long-press → delete/rename.
- **Empty state:** Same as Home empty state, contextualized ("No flows learned yet").

### 3.6 Workflow Details (inspection)
- **Purpose:** Full developer/user-facing inspectability requirement (T1 "recorded steps are inspectable").
- **Content:** Workflow name/intent; supported app(s); full ordered step list, each step expandable to show: semantic target (text/role/resource-id summary — human-readable, e.g., "Button: 'Add to Cart'"), action type, expected outcome, confidence, and whether it's parameterized (slot badge); slot list with types and defaults; safety boundary indicator (where in the flow it will hand off); "Last run" summary; "Run this flow" CTA (manual trigger alternative to voice, useful for demo reliability); "Delete flow".
- **Loading state:** Skeleton list while loading from Room.

### 3.7 Replay / Execution
- **Purpose:** Live, legible view of autonomous execution in progress — the emotional core of the demo.
- **Content:** Full-width status header with current macro-state as a clear label — **LISTENING → MATCHING → RUNNING → WAITING FOR USER → STUCK → COMPLETED → FAILED** (see §5 State Labels); below it, a live step tracker (checklist style: past steps ✓, current step animated, future steps greyed); a small "confidence" indicator per step (subtle, not alarming — a thin colored dot: green/amber); bound parameter chips shown at the top once extracted (e.g., "Farmhouse · Domino's · Zomato · x2 · Work") so the judge visibly sees generalization happening; a persistent "Stop" control for the user to abort at any time.
- **Success/Completed state:** Green check header "Completed" or, more commonly for this domain, "Reached payment — stopped safely" for boundary completions (this IS success, framed positively, not as an error).
- **Failure state:** Clear red-toned header with the specific `failure_reason`, never a bare generic error.

### 3.8 User Clarification (modal/sheet)
- **Purpose:** Ambiguity (T13) and missing-slot (mid-flow parameter, Bonus B3) resolution.
- **Content:** Bottom sheet, non-blocking of context (execution view dimmed behind it), a single specific question (e.g., "Which pizza would you like?" or "I know two saved addresses — Home or Work?"), quick-tap option chips when the domain is enumerable (e.g., Home/Work), a mic button + text field for open answers, and a visible "closest match" affordance when applicable ("Did you mean your 'Order Margherita from Domino's' flow?" [Yes] [No, teach a new one]).
- **Timeout state:** If unanswered past a short grace period, sheet remains open (no forced auto-dismiss during an active judge demo) but execution view shows "Waiting for you..." so it never appears frozen/ambiguous itself.

### 3.9 Stuck / Recovery
- **Purpose:** T7/T10 — visible, specific, honest recovery communication.
- **Content:** Inline banner within Replay/Execution (not a separate full screen unless escalated) showing amber "Working around a change..." during autonomous recovery attempts (T7 autonomous path), or escalating to the Clarification sheet with the *specific* generated question when recovery is exhausted; for T10 "genuinely stuck," a distinct red-toned banner: **"I can't safely continue — {specific reason, e.g., 'the app appears to be in Hindi and I can't find matching text'}."** with actions "Try again" / "Cancel" / "Take over manually".
- Never shows a generic "Something went wrong" string anywhere in the app (explicit requirement).

### 3.10 Credential Handoff
- **Purpose:** T11 — the single highest-stakes UX moment (−10 point failure risk if mishandled).
- **Content:** Full-screen, unmistakable, high-contrast takeover: large icon (shield/hand), headline **"Your turn"**, subline "I've reached a payment/login screen and stopped here for your safety. Please continue manually." A visible "Resume watching" toggle for the user to explicitly hand control back to the app for run-reporting purposes once they're done (optional; does not affect the boundary itself — automation still requires explicit re-invocation, per systemdesign.md §1.2). This screen must be impossible to mistake for a normal loading state — deliberately distinct visual treatment (color-shift, iconography) from every other screen in the app.

### 3.11 Run Report / History
- **Purpose:** T14 — accurate reporting on demand ("Did the last run succeed?").
- **Content:** Reverse-chronological run list, each entry: workflow name, timestamp, outcome pill (Completed / Stopped at Payment / Failed / Cancelled / Asked User), and the specific stopping step if not fully completed; tapping expands full step-by-step trace (reuses the Workflow Details step-list component, annotated with actual vs. expected outcome per step for that run); a top-of-list "Ask me" affordance hinting the same info is available by voice ("Did the last run succeed?").
- **Empty state:** "No runs yet — teach a flow to get started."

### 3.12 Settings / Permissions
- **Purpose:** Onboarding and ongoing permission health.
- **Content:** Accessibility Service status with a direct deep-link to system settings if not granted (this is the one legitimate, necessary use of navigating to system settings — not app automation deep-linking); microphone permission status; target-app declarations (which packages the system is aware of / has learned flows for); a clear, plain-language privacy note: "Your passwords, OTPs, and payment details are never seen, recorded, or stored."

---

## 4. Navigation

- Bottom navigation: **Home | Learned Flows | History** (+ always-visible mic FAB, present as an overlay across all three).
- Teaching and Replay/Execution are modal-style full-screen flows entered from Home/Learned Flows or via voice trigger, with a clear back/cancel affordance at all times (except during the Credential Handoff screen, which intentionally has no "back" — the user must consciously acknowledge and act).
- Deep internal navigation (Workflow Details from a History run) preserves back-stack correctly (standard Android back behavior).

---

## 5. State Labels (Global Vocabulary)

Exactly these labels are used consistently across Home, Replay/Execution, and notifications, matching the UX states named in the original brief:

`LISTENING` · `TEACHING` · `LEARNING` · `READY` · `RUNNING` · `WAITING FOR USER` · `STUCK` · `COMPLETED` · `FAILED`

Each maps 1:1 to a subset of the internal state machine (`systemdesign.md` §1) but is deliberately coarser/friendlier for the user-facing label (e.g., internal `MATCHING_INTENT`/`EXTRACTING_SLOTS`/`VALIDATING_PARAMETERS` all surface simply as `RUNNING` with the live step tracker providing the detail).

---

## 6. User Journeys

### 6.1 Teaching Journey
Home → tap "Teach a new flow" → speak utterance → Listening/Teaching (perform taps, system filters noise live) → boundary hit → Learning Review (confirm slots/steps) → Save → Learned Flows now shows the new entry.

### 6.2 Exact/Paraphrase Replay Journey
Anywhere → tap mic FAB → speak command → brief `RUNNING` (matching) → step tracker fills in → either `COMPLETED`/stopped-at-boundary (Credential Handoff) or `WAITING FOR USER` (Clarification) → Run Report auto-updated.

### 6.3 Changed-Slot Replay Journey
Same as 6.2, but bound-parameter chips visibly differ from the originally taught values at the top of Replay/Execution — this visual diff is the single most important UX proof-point for judges evaluating T4–T6, T9.

### 6.4 Stuck/Recovery Journey
Replay/Execution → amber "Working around a change..." banner (autonomous attempt) → either resolves silently (banner disappears, tracker continues) or escalates to Clarification sheet with a specific question → user answers → execution resumes from the correct step.

### 6.5 Credential Boundary Journey
Replay/Execution progressing normally → abrupt, deliberate full-screen transition to Credential Handoff → user completes payment manually in the target app → user returns to our app (still showing Handoff or Home) → Run Report reflects "Stopped at Payment" as a successful outcome.

### 6.6 Unknown Intent Journey
Voice command → `RUNNING` briefly → "I haven't learned this yet." card with a direct "Teach me now" CTA that pre-fills the Teach Flow utterance field with the spoken command, minimizing friction to convert an unknown-intent moment into a new taught flow live.

### 6.7 Ambiguity Journey
Voice command ("Order pizza") → Clarification sheet immediately (no wasted RUNNING step-tracker time) → user picks/confirms → proceeds as 6.2/6.3.

---

## 7. Visual Hierarchy, Typography, Color

- **Design language:** Clean, confident, "intelligent systems" aesthetic — closer to a professional automation/monitoring tool than a playful chatbot. Generous whitespace, clear state-driven color coding, minimal decorative chrome.
- **Typography:** A single geometric sans (system default, e.g., Roboto/Inter equivalent via Material 3 type scale) — Display/Headline for state labels and "Learned:" confirmations, Body for step lists, Label/Caption for metadata (timestamps, confidence).
- **Color system (semantic, not merely decorative):**
  - Neutral/Ready — brand primary (single accent color, e.g., a deep indigo or Samsung-adjacent blue) for default UI chrome and primary actions.
  - Listening/Teaching — a distinct "active capture" hue (e.g., violet) with a pulsing treatment.
  - Running/Matching — the primary accent, animated progress indication.
  - Waiting for User / Stuck — amber, never red (amber = "needs you," not "broken").
  - Failed — a restrained red/terracotta, used sparingly and only for genuine failures, not for boundary stops (which are amber/neutral "success" framed, not red).
  - Credential Handoff — a unique, unmistakable treatment (e.g., a warm neutral or distinct brand-safe color) reserved exclusively for this one screen, reinforcing its special status.
  - Completed — success green, used sparingly.
- **Motion:** Subtle, purposeful only — step-tracker check animations, pulsing capture indicator, banner slide-ins. No motion for its own sake; nothing that could read as "toy-like" to judges.

---

## 8. Component System

- `StateHeaderBar` — the global state label + icon, reused across Home/Teaching/Replay.
- `StepTrackerList` — ordered step list with per-item status (pending/active/done/failed) and optional confidence dot; reused in Listening/Teaching, Replay/Execution, Workflow Details, Run Report.
- `SlotChipRow` — horizontally scrollable parameter chips; reused in Learning Review and Replay/Execution.
- `ClarificationSheet` — bottom sheet with question + quick-options + mic/text fallback.
- `RecoveryBanner` — inline amber/red banner variant.
- `RunOutcomePill` — small status pill used in Learned Flows list and Run History.
- `FullScreenHandoff` — the singular Credential Handoff treatment, not reused elsewhere.

---

## 9. Accessibility (App's Own, Not the Automation Subsystem)

- All interactive elements have content descriptions (dogfooding good Accessibility practice, fittingly).
- Color is never the sole signal — state labels always paired with text/iconography.
- Minimum touch target sizes per Material guidelines.
- Voice-first design inherently supports users who prefer not to read dense UI, but visual fallbacks (text field, tap options) are always present for users who cannot or prefer not to use voice.
- Sufficient contrast ratios for all state-color treatments (especially amber-on-background and the Credential Handoff screen, which must remain legible in bright outdoor demo conditions).

---

## 10. Microcopy Bank

| Context | Copy |
|---|---|
| Teaching start | "Okay. I'll watch your actions." |
| Teaching live | "TEACHING — I'm watching your taps" |
| Boundary hit during teaching | "Learning stopped before payment." |
| Learned confirmation | "Learned: {summary}" |
| Irrelevant action filtered | "{action} — ignored" |
| Replay matching | "Got it — finding your flow..." |
| Replay running | "On it." |
| Boundary hit during replay | "Your turn — I've reached the payment screen." |
| Recovery (autonomous) | "Working around a change..." |
| Recovery (asking) | Specific, generated per-context — never generic |
| Unknown intent | "I haven't learned this yet. Want to teach me?" |
| Ambiguous intent | "I know a few things like that — which one did you mean?" |
| Run success report | "Yes — completed successfully up to {step/boundary}." |
| Run failure report | "No — it stopped at {step} because {reason}." |
| Permission missing | "I need Accessibility access to watch and repeat actions for you." |

---

## 11. Loading / Empty / Error States (Consolidated)

- **Loading:** shimmer skeletons for lists (Learned Flows, History), inline spinners only for short (<1s expected) operations; longer AI-bound operations (intent matching) use the animated `StateHeaderBar` itself as the loading indicator rather than a separate spinner, to keep attention on state, not mechanics.
- **Empty:** every list screen has a purposeful empty state with a single clear CTA (never a bare "No data").
- **Error:** every error surfaces the specific `failure_reason` from `systemdesign.md` §19's edge-case table in plain language — the UI layer maintains a lookup table mapping internal reason codes to the exact user-facing sentence, ensuring consistency and preventing ad hoc "something went wrong" strings from creeping in anywhere.

---

## 12. Demo-First UX Notes

Per `context.md` §"Success Criteria" and the official demo requirements, the UI must make each of the following visually unambiguous within seconds, without narration, for a judge watching a 5-minute unedited video:

1. Teaching is happening (Listening/Teaching banner + live step list).
2. A flow was learned (Learning Review "Learned:" headline + slot chips).
3. Exact replay executes unattended (Replay/Execution step tracker advancing with no manual input).
4. Paraphrase maps to the same flow (bound-parameter chips visibly matching the original taught values despite different spoken phrasing — optionally a small "matched via paraphrase" tag).
5. Changed slot value takes effect (bound-parameter chips visibly differing from the original taught values).
6. Credential boundary handoff is unmistakable (the dedicated full-screen treatment).
7. Stuck/clarification is specific, not generic (Clarification sheet or Stuck banner text is always concrete).

This mapping directly supports the Demo-First Design journey defined in `architecture.md`/the master prompt, ensuring the UI and the underlying execution architecture stay in lockstep for the recorded demo video.
