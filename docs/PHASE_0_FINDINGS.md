# Phase 0 Inspection & Findings Report
## Samsung PRISM Gen AI Hackathon 3.0 — Theme 3: Teachable Voice Automation

### 1. Executive Summary
This document records the mandatory Phase 0 inspection mandated by `master prompt.md` §2. We have inspected the repository, the participant kit (`participant-kit/`), the official hackathon FAQ (`Samsung_PRISM_GenAI_Hackathon_3_FAQ_v4.docx`), and the Theme 3 evaluation criteria specification (`Theme 3 - Evaluation Criteria.pdf`).

---

### 2. Participant Kit Findings & Compatibility Analysis

#### 2.1 Kit Origin & Contents
- Location: `participant-kit/` (and nested `participant-kit/participant-kit` / `Theme02_Input_Kit.zip`).
- Composition:
  - `agent/`: Python template agent (`agent.py`) implementing an async queue loop.
  - `audio/`: MP3 audio turn fixtures for testing speech input.
  - `frames/`: PNG UI frame fixtures for camera/screen context.
  - `docs/`: `PROTOCOL.md`, `SCORING.md`, `SUBMISSION.md`, `TOOLS.md`.
  - `harness/`: Python streaming simulation harness (`runner.py`, `protocol.py`, `mock_env.py`, `scenario_gen.py`, `scorer.py`).
  - `scenarios/`: Scenarios `pub_01` through `pub_09` (flight search, booking, cancel, manual lookup, unseen tools).

#### 2.2 Protocol Mismatch & Adaptation
- **Nature of the Mismatch**: The participant kit in `Theme02_Input_Kit.zip` is designed for **Theme 2 (Smart Guided Troubleshooting Engine)**, which uses an in-process Python queue streaming protocol (`tool_manifest`, `user_speech_chunk`, `interruption`, `tool_call`, `final_response`).
- **Theme 3 Requirement**: As defined in `Theme 3 - Evaluation Criteria.pdf`, `context.md`, `architecture.md`, `systemdesign.md`, and `master prompt.md`, **Theme 3 (Teachable Voice Automation)** is an **Android-native application** running on Android OS using the Android **Accessibility Service** (`AutomationAccessibilityService`), UI tree capture (`AccessibilityNodeInfo`), semantic UI element matching, Room persistence, Jetpack Compose UI, and ASR/intent/slot extraction.
- **Architectural Decision**:
  - The participant kit in `participant-kit/` is **strictly preserved** and not deleted.
  - The architectural and scoring principles of the participant kit are integrated into the Theme 3 test harness:
    1. **Structured Trace Logging**: The Android `RunLogger` and `RunReporter` will record step-by-step traces with timestamps, actions, outcomes, and state snapshots, matching the transparency of the harness trace.
    2. **Scenario-Based Testing (T1–T14)**: The end-to-end test suite (`systemdesign.md` §18) will use structured fixtures (mock UI trees representing Zomato and Amazon screens) to validate teaching, replay, paraphrase matching, slot variation, pop-up recovery, stuck detection, and credential boundary halting.

---

### 3. Evaluation Criteria & FAQ Review

#### 3.1 Evaluation Weighting & Deliverables
- **Core Score**: 60 base points across 14 test cases (T1–T14) + up to 10 bonus points (B1: +3 for irrelevant action filtering; B2: +4 for cross-app generalization; B3: +3 for mid-flow parameter clarification).
- **Safety Criticality**: Test case T11 (Credential Boundary) imposes a **−10 penalty on failure**. Halting before payment/OTP/password/login screens is the single highest-priority requirement in the codebase.
- **Deliverables**:
  1. Installable Android APK.
  2. Complete source code repository with reproducible build configuration.
  3. Demo video (≤ 5 minutes, continuous unedited take demonstrating: Teach, Exact Replay, Paraphrase Replay, Changed Slot Replay, Recovery/Stuck Question, Payment Handoff, Unknown Intent).
  4. Architecture presentation / documentation with Mermaid diagrams.
  5. Target apps declaration (Zomato & Amazon).
  6. Known limitations documentation.
  7. Git release tag: `PRISM_GENAI_HACKATHON_Y2026`.

---

### 4. Build Environment & Toolchain Readiness
- **Operating System**: Windows 11 (amd64)
- **Java Development Kit**: OpenJDK 17.0.19 (Eclipse Adoptium)
- **Android SDK**: `C:\Users\Rishit\AppData\Local\Android\Sdk`
  - Platforms: `android-34`, `android-36`, `android-36.1`
  - Build-Tools: `35.0.0`, `36.1.0`, `37.0.0`
- **Gradle**: Gradle 8.14.3 (Kotlin 2.0.21 compatible)
- **Python**: Python 3.13.7 (available for auxiliary test scripts and fixture generation)

Phase 0 is complete. Proceeding to Phase 1 (Android Project Foundation).
