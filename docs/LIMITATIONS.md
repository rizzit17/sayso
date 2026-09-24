# Known Limitations & Fallback Behavior

**Samsung PRISM GenAI Hackathon 3.0 — Theme 3: Teachable Voice Automation**

Per §8 and §4 of `master prompt.md`, this document provides an honest assessment of current technical boundaries, offline fallback mechanisms, and environment constraints.

---

## 1. Intent Matching & Offline Hybrid Retrieval
* **Primary Path**: Dual hybrid matching combining cosine similarity over intent embeddings (60%) with symbolic token Jaccard & Levenshtein edit distance (40%).
* **Offline Fallback**: When cloud embedding APIs or network access are unavailable, the engine automatically falls back to deterministic local symbolic matching (`IntentMatcher`). This evaluates normalized token Jaccard similarity, slot keyword coverage, and Levenshtein similarity with strict confidence thresholds (`high=0.80`, `low=0.45`, `ambiguityDelta=0.08`).

## 2. Speech-to-Text & Text-to-Speech Engine
* **Native Android STT/TTS**: Relies on Android's system `SpeechRecognizer` (`android.speech.SpeechRecognizer`) and `TextToSpeech` (`android.speech.tts.TextToSpeech`).
* **Environment Fallback**: In environments without a microphone or Google Speech Services installed (such as headless emulators), the application provides a direct text command bar on the Home screen to dispatch exact or paraphrased natural language utterances.

## 3. UI Node Accessibility Limitations
* **Custom Canvases & SurfaceViews**: Certain non-standard UI elements (e.g. game surfaces, custom Flutter/Unity canvases without accessibility labels) may not expose child `AccessibilityNodeInfo` hierarchies.
* **Deterministic Fallback**: In such instances, the 5-stage `RecoveryManager` attempts resnapshotting, dismissing transient overlays, relaxing matching thresholds, or escalating to the `StuckDetector` to ask the user via a multi-modal Clarification Sheet rather than making erroneous blind taps.

## 4. Strict Safety Boundary Handoff (Design Guarantee)
* **No Automated Bypass**: By design, the engine **never** attempts to guess or input payment pins, OTPs, or passwords. Reaching any payment, UPI, or credential screen causes an immediate, non-negotiable halt (`RunStatus.COMPLETED_TO_BOUNDARY`), protecting users and adhering strictly to the T11 criteria (-10 penalty prevention).
