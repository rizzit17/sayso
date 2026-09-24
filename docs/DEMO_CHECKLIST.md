# Demo Recording Checklist & Script (≤5 Minutes)

**Samsung PRISM GenAI Hackathon 3.0 — Theme 3: Teachable Voice Automation**

Per `master prompt.md §7` and `design.md §12`, record the demonstration video in a **single unedited take** following this exact sequence:

---

## Pre-Flight Checklist
- [ ] Install `app-debug.apk` onto an Android device or emulator (Android 8.0+ / API 26+).
- [ ] Open **Settings → Accessibility → Downloaded apps → PRISM Automation Service → Turn ON**.
- [ ] Ensure volume is audible for Text-to-Speech (TTS) announcements.
- [ ] Launch PRISM app (verify green "A11y Connected" badge on top right).

---

## 5-Minute Unedited Demonstration Script

| Step | Time | Action / Utterance | What to Show on Screen | Expected System Response / Microcopy |
|---|---|---|---|---|
| **1. Teach Flow** | 0:00 - 1:00 | Tap **Teach a Flow**.<br>Enter/Speak: *"Order a Margherita pizza from Domino's on Zomato."* | Show Listening/Teaching screen.<br>Tap through Zomato: Search bar → Domino's card → Add button → Cart screen. Stop before payment. | TTS: *"Okay. I'll watch your actions."*<br>Tap **Stop & Save**.<br>TTS: *"Learned: Order Margherita pizza from Domino's on Zomato"*.<br>Show Learned Flows card with slot chips `{item}`, `{restaurant}`, `{platform}`. |
| **2. Exact Replay** | 1:00 - 1:40 | Tap Mic or speak:<br>*"Order a Margherita pizza from Domino's on Zomato."* | Return to Home / app switches to Zomato.<br>PRISM automatically types "Margherita pizza", taps Domino's, taps Add to cart. | TTS: *"Starting workflow..."* → *"On it."*<br>Step tracker advances cleanly to completion without manual touch. |
| **3. Paraphrase Understanding** | 1:40 - 2:20 | Speak:<br>*"Get me a margherita from dominos."* | Replay initiates immediately.<br>Observe IntentMatcher mapping to `order_food` with paraphrase confidence. | TTS: *"Got it — finding your flow..."* → *"On it."*<br>Replays flow identically with parameter binding. |
| **4. Altered Slot (Item)** | 2:20 - 3:00 | Speak:<br>*"Order a Farmhouse pizza from Domino's."* | Item parameter dynamically binds to `"Farmhouse pizza"`.<br>Search box inputs `"Farmhouse pizza"`. | Parameter binder replaces `{item}` value in search input live. |
| **5. Altered Slot (Quantity / Choice)** | 3:00 - 3:30 | Speak:<br>*"Order 2 Margherita pizzas from Domino's on Zomato."* | Slot extractor extracts `quantity=2`. | Action executor binds quantity increment. |
| **6. Unexpected UI / Recovery** | 3:30 - 4:00 | Trigger UI change (e.g. pop-up or element shift). | System displays: `Recovery: Working around a change...`<br>If ambiguous, displays Clarification Sheet: *"Which outlet? Tap 'MG Road' or 'Indiranagar'"*. | Multi-modal recovery succeeds either via speech ("Indiranagar") or tapping the option chip. |
| **7. Payment Boundary Handoff** | 4:00 - 4:30 | Workflow proceeds to final checkout / UPI selection. | Full-screen Credential Handoff card appears with distinctive security shield.<br>**ZERO** clicks or text dispatched on the payment screen. | TTS: *"Your turn — I've reached the payment screen."*<br>User manual payment prompt displayed. |
| **8. Unknown Intent** | 4:30 - 5:00 | Speak unknown command:<br>*"Book a flight to Paris on MakeMyTrip."* | PRISM displays Unknown Intent card with clean CTA. | TTS: *"I haven't learned this yet. Want to teach me?"*<br>Shows **Teach this Flow** button. |

---

## Evaluation Alignment
* Step 1 demonstrates **T1** & **T8** (Teach & Store, Room persistence).
* Step 2 demonstrates **T2** (Unattended exact replay).
* Step 3 demonstrates **T3** (Paraphrased voice understanding).
* Steps 4–5 demonstrate **T4**, **T5**, **T6**, **T10** (Dynamic item, quantity, and address slot generalization).
* Step 6 demonstrates **T7**, **T12**, **B3** (State verification, recovery, stuck detection & multi-modal clarification).
* Step 7 demonstrates **T11** (Strict Credential & Payment boundary handoff, 0 payment touches, -10 penalty guard).
* Step 8 demonstrates **T12** (Unknown intent handling & graceful handoff).
