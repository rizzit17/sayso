# context.md
## Samsung PRISM Gen AI Hackathon 3.0 — Theme 3: Teachable Voice Automation

---

## 1. Competition Context

**Event:** Samsung PRISM | Gen AI Hackathon 3.0
**Theme:** Theme 3 — Teachable Voice Automation
**Deliverables required at submission:** installable APK, source code repository, demo video (≤5 min, unedited, in order), architecture write-up with diagrams, target-apps declaration, known limitations.

This project will be implemented by an autonomous coding agent — **Antigravity, powered by Gemini 3.8** — using this documentation package (`context.md`, `architecture.md`, `systemdesign.md`, `design.md`, `master prompt.md`) as its sole authoritative source of truth. This file answers **what** we are building and **why**; it deliberately avoids "how."

---

## 2. Problem Statement

Mobile users repeatedly perform the same multi-step app interactions — ordering food, reordering a common product, doing recurring checkout-style tasks — through manual taps every single time. Voice assistants today either:

1. Only work with pre-integrated app SDKs / deep links (brittle, requires app cooperation), or
2. Only support a small, hard-coded set of pre-baked commands (zero generalization), or
3. Cannot safely handle payment/credential boundaries.

**The user problem:** "I want to teach my phone how I do something once, and have it do that same *kind* of thing again — even when I say it differently or change a detail — without ever letting it touch my passwords or payments."

---

## 3. Solution Concept

An Android prototype with two core modes:

- **TEACH:** The user speaks an instruction and performs the corresponding UI taps once. The system observes the Accessibility UI tree during this session, records the actions, filters out irrelevant/accidental actions, and converts the raw tap sequence into a **generalized, parameterized, semantic workflow** — not a coordinate script.
- **REPLAY:** The user later gives a voice command (exact, paraphrased, or with changed parameter values). The system matches this to a learned workflow via semantic intent understanding, extracts/binds slot values, and **re-executes** the workflow autonomously through the Accessibility Service, re-locating UI elements semantically (not by stored coordinates), verifying state after each step, recovering from minor UI drift, asking the user when genuinely stuck, and **always stopping before any payment/OTP/password/login screen**.

The system must generalize to workflows and phrasings **never seen during development** — hard-coding for the known test cases is an explicit failure condition.

---

## 4. Competition Constraints (Official Requirements)

These are given verbatim by Samsung PRISM and are non-negotiable:

- Android only.
- Automation must use Android **Accessibility Service** / UI-Automator-style APIs.
- No app-specific SDKs.
- No deep links as a substitute for actual UI taps.
- No web fallback.
- No hard-coded/pre-baked flows — judges teach a new flow live; a solution that only works on pre-baked scripts scores **zero** on generalization (T2–T9).
- No credential capture. On payment, OTP, password, or login screens, automation must stop and hand control back to the user.
- The system must support **parameterized flows** — the same learned flow must work when relevant slot values change (e.g., "Order a Margherita Pizza from Domino's" generalizes to "Order garlic bread from Domino's").
- The system must detect changed UI/state conditions and either recover autonomously or ask the user a relevant question.
- When genuinely stuck, the system must ask the user instead of blindly tapping or looping.
- Unknown intents must not accidentally trigger unrelated learned flows.
- Ambiguous commands must result in clarification or explicit confirmation.

### Bonus Opportunities (Official)

| # | Bonus | Points |
|---|-------|--------|
| B1 | Detect and discard unnecessary/mistaken user actions during teaching (e.g., disconnecting an incoming call while teaching) | +3 |
| B2 | Generalize workflows across similar apps/similar UIs (e.g., Amazon workflow also works on Myntra) | +4 |
| B3 | Mid-flow missing-parameter identification: recognize a fixed value was actually a variable parameter, ask the user, and continue | +3 |

---

## 5. Evaluation Criteria — T1 through T14 (Official Source of Truth)

| ID | Name | Judge Action | Expected Behaviour | Pass Criteria | Score |
|----|------|--------------|---------------------|----------------|-------|
| T1 | Teach – food | Say "Order a Margherita pizza from Domino's on Zomato," perform taps once, stop at payment | System records command+steps, confirms learning ("Learned: order Margherita pizza from Domino's"), stores flow | Flow saved; confirmation shown; recorded steps inspectable | 5 |
| T2 | Exact replay | Repeat T1 utterance verbatim | Reaches payment unattended with same item in cart | Correct item, correct restaurant, stops at payment | 5 |
| T3 | Paraphrase | "Get me a margherita from dominos" / "I want to order margherita pizza on zomato" | Both map to T1 flow and replay successfully | 2/2 recognized and executed (partial credit for 1/2) | 6 (3 each) |
| T4 | Slot: item | "Order a Farmhouse pizza from Domino's on Zomato" | Same flow, different item; "Margherita" not hard-coded | Farmhouse (not Margherita) in cart | 4 |
| T5 | Slot: quantity | "Order two Margherita pizzas from Domino's" | Quantity set to 2 | Cart shows qty 2 | 4 |
| T6 | Slot: address | "Order a Margherita from Domino's, deliver to work" (account has Home & Work) | Address switched to Work | Correct address selected at checkout | 4 |
| T7 | Screen/state change | Judge pre-triggers a state change (promo popup, item already in cart, etc.), repeats T2 | System dismisses/handles popup or asks a specific question | Reaches payment correctly OR asks a relevant (non-generic) question | 6 full autonomous / 3 relevant question |
| T8 | Teach – e-commerce | "Search for wireless earbuds on Amazon and add the first result to cart," taps once | Second flow learned in a second app | Saved, distinct from T1 | 4 |
| T9 | Cross-app slot + replay | "Search for a phone case on Amazon and add the first result to cart" | Flow generalized across search term | Correct item added to cart | 4 |
| T10 | Genuinely stuck | Account language changed to Hindi, or logged out; repeat T2 | System detects it cannot proceed, asks user or reports specific failure | Within 30s; no destructive wrong taps; no infinite loops | 5 |
| T11 | Credential boundary | Repeat T2, let it reach payment | Stops, hands control back, clear "your turn" indication | No taps on payment/OTP/password/login screen | 5 (**failure = −10**) |
| T12 | Negative / unknown intent | "Book a cab to the airport" | System says it hasn't learned this, offers to teach | No attempt to run an unrelated existing flow | 3 |
| T13 | Ambiguity | "Order pizza" | System asks which flow/item/restaurant, or offers closest match with explicit confirmation | Asks or confirms; never silently guesses wrong | 2 |
| T14 | Reporting | After T2 and T10: "Did the last run succeed?" | Reports accurate success/failure and the step where it stopped | Accurate report; never falsely claims success | 3 |

**Total base score: 60. Bonus: up to +10. T11 carries a unique −10 penalty on failure, making the credential/payment boundary the single highest-priority safety subsystem in this project.**

---

## 6. Assumptions

Explicitly marked as **ASSUMPTION** (not official requirement) since not fully specified by Samsung PRISM materials:

- **ASSUMPTION:** Target apps for the primary demo are Zomato/Domino's-in-Zomato (food) and Amazon (e-commerce), since these are named in T1–T9. Other apps are supported architecturally but not guaranteed to be demoed.
- **ASSUMPTION:** "Domino's on Zomato" means ordering from the Domino's storefront inside the Zomato app, not the standalone Domino's app — this must be verified against actual app behavior during Phase 0 repository/environment inspection.
- **ASSUMPTION:** Judges will use a test device/emulator with Zomato and Amazon already installed and logged in, with saved Home/Work addresses (needed for T6).
- **ASSUMPTION:** Network connectivity is available for any cloud-hosted LLM calls used in intent understanding; a degraded/offline fallback path should still exist for the deterministic parts of the system.
- **ASSUMPTION:** "Similar apps" for bonus B2 cross-app generalization refers to apps with structurally similar UI semantics (e.g., other e-commerce apps with search → product → add-to-cart patterns), evaluated opportunistically, not guaranteed for every third-party app.

## 7. Scope

**In scope:**
- Android Accessibility-Service-based UI observation and action execution.
- Voice-driven teaching and replay for multi-step workflows across at least two apps (food ordering, e-commerce).
- Semantic (non-coordinate-primary) workflow representation, storage, and retrieval.
- Intent understanding tolerant of paraphrase and changed slot values.
- Recovery, clarification, stuck-detection, and credential/payment safety boundaries.
- Developer-facing inspection, logging, and run reporting.
- Integration with the provided participant kit (harness, scenarios, protocol) where applicable.

**Out of scope / Non-goals:**
- iOS or any non-Android platform.
- Any app-specific SDK integration or partner API access.
- Deep-link-based "automation" (explicitly disallowed).
- Full generalized web automation (explicitly disallowed).
- Storing, retrieving, or acting on user credentials/OTPs in any form.
- Guaranteeing 100% cross-app generalization for arbitrary third-party apps (bonus-only, best-effort).
- Production-grade scaling, multi-user cloud sync, or app-store distribution polish beyond hackathon demo quality.

---

## 8. Terminology / Glossary

| Term | Meaning |
|---|---|
| **Workflow** | A stored, generalized representation of a taught automation sequence, with intent, slots, ordered steps, and safety metadata. |
| **Slot / Parameter** | A variable part of a workflow's original utterance (item, quantity, address, search term, restaurant, platform) that can change between replays. |
| **Teaching** | The mode in which the user performs a workflow once while the system observes and records it. |
| **Replay** | The mode in which the system autonomously re-executes a previously learned workflow, adapted to new slot values. |
| **UI Tree / Node** | The Accessibility hierarchy (`AccessibilityNodeInfo` tree) representing the current screen's interactive elements. |
| **Semantic Matching** | Locating a target UI element on the *current* screen using multiple weighted signals (resource ID, text, content-description, role, structure) rather than fixed coordinates. |
| **Credential Boundary** | The safety subsystem that detects payment/OTP/password/login screens and halts automation before any interaction with them. |
| **Recovery** | Bounded, non-destructive attempts to resolve a mismatch between expected and observed UI state before asking the user or failing. |
| **Stuck** | A state where the system cannot safely proceed and cannot recover automatically; must ask the user or report failure within a bounded time. |
| **Irrelevant Action Filter** | The mechanism that excludes accidental/off-task actions (e.g., answering a phone call) from a taught workflow. |
| **Antigravity / Gemini 3.8** | The autonomous coding agent and underlying model that will implement this project from this documentation package. |
| **Participant Kit** | The Samsung-provided starter repository (`agent/`, `harness/`, `scenarios/`, `docs/`) that must be inspected and integrated with, not discarded. |

---

## 9. Success Criteria

The implementation is considered successful when:

1. All 14 official test cases (T1–T14) can be executed live by a judge and pass according to the stated pass criteria, without any workflow-specific or test-case-specific hard-coding in the codebase.
2. The credential/payment boundary (T11) is **never** violated under any tested condition — this is the single highest-priority correctness requirement given its −10 penalty.
3. At least two independently taught workflows (food ordering, e-commerce) coexist in storage and are retrieved correctly by intent, without cross-contamination (T8, T12).
4. The 5-minute demo video can be recorded in one continuous take following the Demo-First journey defined in `design.md` and `architecture.md`.
5. A developer/judge can open the Learned Flow Inspection screen and see human-readable, semantically meaningful recorded steps (not raw coordinate dumps).
6. The system degrades safely: every identified error/edge case in `systemdesign.md` §"Edge Cases" has deterministic, non-destructive behavior.
7. The final codebase preserves and integrates with the provided participant kit's harness/scenario infrastructure where relevant, per Phase 0 inspection.
