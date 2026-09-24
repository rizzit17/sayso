---
name: Sayso
colors:
  surface: '#faf8fd'
  surface-dim: '#dbd9de'
  surface-bright: '#faf8fd'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f5f3f7'
  surface-container: '#efedf2'
  surface-container-high: '#e9e7ec'
  surface-container-highest: '#e3e2e6'
  on-surface: '#1b1b1f'
  on-surface-variant: '#454650'
  inverse-surface: '#303034'
  inverse-on-surface: '#f2f0f4'
  outline: '#767681'
  outline-variant: '#c6c5d1'
  surface-tint: '#4e5a99'
  primary: '#3b4784'
  on-primary: '#ffffff'
  primary-container: '#535f9e'
  on-primary-container: '#dbdeff'
  inverse-primary: '#b9c3ff'
  secondary: '#714e97'
  on-secondary: '#ffffff'
  secondary-container: '#d5aefe'
  on-secondary-container: '#5f3d84'
  tertiary: '#703f00'
  on-tertiary: '#ffffff'
  tertiary-container: '#915405'
  on-tertiary-container: '#ffd9b9'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dee1ff'
  primary-fixed-dim: '#b9c3ff'
  on-primary-fixed: '#041452'
  on-primary-fixed-variant: '#36427f'
  secondary-fixed: '#efdbff'
  secondary-fixed-dim: '#dbb8ff'
  on-secondary-fixed: '#2a034f'
  on-secondary-fixed-variant: '#58367d'
  tertiary-fixed: '#ffdcbf'
  tertiary-fixed-dim: '#ffb873'
  on-tertiary-fixed: '#2d1600'
  on-tertiary-fixed-variant: '#6a3b00'
  background: '#faf8fd'
  on-background: '#1b1b1f'
  surface-variant: '#e3e2e6'
typography:
  display-lg:
    fontFamily: Roboto Flex
    fontSize: 57px
    fontWeight: '400'
    lineHeight: 64px
    letterSpacing: -0.25px
  display-md:
    fontFamily: Roboto Flex
    fontSize: 45px
    fontWeight: '400'
    lineHeight: 52px
  headline-lg:
    fontFamily: Roboto Flex
    fontSize: 32px
    fontWeight: '400'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: Roboto Flex
    fontSize: 28px
    fontWeight: '400'
    lineHeight: 36px
  headline-md:
    fontFamily: Roboto Flex
    fontSize: 28px
    fontWeight: '400'
    lineHeight: 36px
  headline-sm:
    fontFamily: Roboto Flex
    fontSize: 24px
    fontWeight: '400'
    lineHeight: 32px
  title-lg:
    fontFamily: Roboto Flex
    fontSize: 22px
    fontWeight: '500'
    lineHeight: 28px
  title-md:
    fontFamily: Roboto Flex
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 24px
    letterSpacing: 0.15px
  title-sm:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.1px
  body-lg:
    fontFamily: Roboto Flex
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.5px
  body-md:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.25px
  body-sm:
    fontFamily: Roboto Flex
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.4px
  label-lg:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.1px
  label-md:
    fontFamily: Roboto Flex
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
  label-sm:
    fontFamily: Roboto Flex
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.5rem
  gutter-lg: 1.5rem
  margin: 1rem
  margin-tablet: 1.5rem
  margin-desktop: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system delivers the native feel of modern Android and Google Pixel experiences for voice-driven orchestration. Built entirely on the Material You (M3) design philosophy, the interface conveys intelligence, warmth, and effortless capability. The personality is conversational, attentive, and deeply respectful of user trust—critical for an assistant learning complex personal workflows and handling credentials.

Visual hallmarks include:
- Soft, expressive pill-shaped surfaces and tactile touch targets.
- Dynamic tonal layer shifts that visually signal runtime state transitions (Listening, Clarifying, Complete).
- Ample negative space combined with low-stress, rounded visual weight.
- Clear structural hierarchy without reliance on aggressive outlines or artificial skeuomorphism.

## Colors

The palette derives from an adaptive cool periwinkle-indigo core (`#535F9E`), supplemented by specialized functional tonal families aligned to conversational execution phases:

- **Primary (`#535F9E` / Periwinkle Indigo):** Core interactive components, active tabs, primary FABs, confirmed actions, and base assistant presence.
- **Secondary (`#76539C` / Warm Violet):** Activates during **Teaching & Listening** states. Conveys active synthesis, receptive machine learning, and auditory intake.
- **Tertiary (`#8C5000` / Muted Amber):** Triggers during **Waiting, Disambiguation, & Clarification** queries. Serves as a mild, unalarmed prompt requiring user voice input.
- **Success / Completed Semantic (`#2E6A44` / Soft Emerald):** Signals successful routine capture and step verification.
- **Credential Handoff Semantic (`#7B5804` on `#FFF8F0` Container):** An elevated warm sand tone distinctly indicating sensitive access authorization handoffs.
- **Surface Architecture:** Follows standard M3 surface container levels (`surface-dim`, `surface`, `surface-bright`, `surface-container-lowest` through `surface-container-highest`) ensuring seamless tonal shift without high-contrast black/white jarring transitions.

## Typography

Utilizing `Roboto Flex` throughout headlines, body copy, and UI metadata maintains exact alignment with modern Android execution. The variable capabilities enable precise optical adjustment across dynamic layouts. 

- **Display & Headlines:** Used for conversational prompts, automated routine titles, and status hero banners. Headlines use loose tracking at large scales to evoke the friendly, open posture of voice software.
- **Titles:** Standard for card headers, routine stage titles, and app-bar labeling. Medium weights provide immediate scannability when executing live workflows.
- **Body:** Calibrated for step-by-step instruction readouts, transcribed voice strings, and system activity logs.
- **Labels:** Strictly applied to interactive pills, assist chips, badge counters, navigation labels, and button actions. Always uppercase-agnostic to retain an organic, approachable tone.

## Layout & Spacing

Layout adheres to standard Android device window size classes: Compact (<600dp), Medium (600–839dp), and Expanded (840dp+). 

- **Compact (Phones):** Employs a 4-column fluid layout with a default outer margin of `margin` (16dp/1rem) and `gutter` (16dp). Vertical flow prioritizes thumb-zone access with lower-canvas action bars and bottom floating triggers.
- **Medium (Foldables / Tablets portrait):** Scales to an 8-column layout with `margin-tablet` (24dp) and split pane view for continuous voice input review alongside running automations.
- **Expanded (Tablets landscape / Desktop):** Expands to a 12-column structure with `margin-desktop` (32dp), adopting an M3 Navigation Rail on the start edge and multi-pane cards.
- **Spatial Rhythm:** Spacing is strictly built on an 8dp grid (sub-divided to 4dp for micro-alignments like chip internal padding and avatar offsets).

## Elevation & Depth

This design system uses **Material 3 tonal elevation** instead of stark, opaque drop shadows. Surfaces elevate by blending an opacity of the primary role color into the surface container, visually lifting the element closer to the light source.

- **Level 0 (Flat):** `surface` base color, no elevation tint, zero shadow. Used for base canvas backgrounds.
- **Level 1 (Card / Resting List Item):** `surface-container-low` with 5% primary color tint overlay. Crisp, warm distinction from the background without line borders.
- **Level 2 (Pill Chips / Active Items):** `surface-container` with 8% primary color tint overlay, coupled with an ultra-diffuse ambient shadow (`0 2px 6px rgba(45, 50, 75, 0.06)`).
- **Level 3 (Floating Action Button / Dialogs):** `surface-container-high` with 11% tint overlay and soft shadow (`0 4px 12px rgba(45, 50, 75, 0.1)`).
- **Level 4 & 5 (Modals / Handoff Overlays):** `surface-container-highest` with 14% tint overlay for mission-critical modal bottom sheets, such as secure Credential Handoff.

## Shapes

The design system incorporates expressive Material 3 corner geometry:

- **Pill (Full Curvature / 9999px):** Buttons, search bars, Assist chips, Filter chips, and segmented button containers.
- **Large Curvature (`rounded-xl` / 28dp):** Standard cards, dialogue boxes, sheets, and the main Voice Transcription Container.
- **Medium Curvature (`rounded-lg` / 16dp):** Small interactive modules, sub-step cards within a multi-action recipe, and input fields.
- **Small Curvature (`rounded` / 8dp):** Tooltips, snackbars, and status badges.

## Components

### Buttons & FABs
- **Primary Action (Extended FAB):** 56dp height, pill-shaped surface (`primary-container`), colored in dynamic periwinkle with `on-primary-container` text/icons. Transitions to a compact 56x56dp circular shape during canvas scrolling.
- **Filled Tonal Buttons:** Used for secondary actions (e.g., "Add Voice Step"). Pill shape, zero shadow, rendered in `secondary-container` with `on-secondary-container` typography.
- **Outlined Buttons:** Bordered exclusively with `outline-variant` (subtle 1dp stroke) and fully rounded pill caps.

### Chips (Assist, Filter, Input)
- 32dp height, fully pill-shaped.
- **Active Listening Mode:** Displays an animated microphone glyph inside a `secondary-container` surface.
- **Clarification Chip:** Tonal amber surface (`tertiary-container`) prompting contextual questions (e.g., "Which account?").

### Voice State Cards & Credential Handoff
- **Elevated Material Cards:** 28dp rounded corners, rendered in `surface-container-low` with 16dp internal padding.
- **Listening Card:** Displays dynamic sound-wave bars rendered in `secondary`, pulsing against a `secondary-container` subtle background.
- **Credential Handoff Card:** Highlighted card rendered in a warm neutral container (`#FEEFC3` / tonal sand) with an explicit lock icon and high-contrast callout for fingerprint/passkey entry.

### Lists & Step Automation Items
- Structured as one-line or three-line list items wrapped in individual `surface-container-lowest` sub-cards or separated by transparent gutters instead of full divider lines.
- Trailing icons indicate step health: Soft Emerald checkmarks for completed automated tasks, animated amber indicators for items awaiting audio input.

### Bottom Navigation Bar
- 80dp tall container (`surface-container`) hosting active indicators: pill-shaped active selection indicators (`primary-container`) with labeled icons underneath (`Roboto Flex` label-medium).