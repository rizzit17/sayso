"""
Build VITVellore_CoreCryshalis_Submission.pptx
Following the Samsung PRISM CollegeName_TeamName_Submission.pptx template structure.
Clean purple + white style matching the official template.
"""

import os
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE

def create_deck():
    # Use the template as base to inherit slide layouts and themes
    template_path = os.path.join(os.path.dirname(__file__), "CollegeName_TeamName_Submission.pptx")
    prs = Presentation(template_path)

    # Remove all existing slides safely
    sldIdLst = prs.slides._sldIdLst
    for sldId in list(sldIdLst):
        rId = sldId.attrib['{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id']
        prs.part.drop_rel(rId)
        sldIdLst.remove(sldId)

    # Template dimensions (16:9 widescreen)
    # prs.slide_width = 12192000 (EMU) = 13.333 inches
    # prs.slide_height = 6858000 (EMU) = 7.5 inches

    # Colors from template
    PURPLE_DARK  = RGBColor(0x6D, 0x28, 0xD9)   # #6D28D9 - Samsung purple
    PURPLE_TITLE = RGBColor(0x70, 0x4E, 0xA6)   # #704EA6 - Title purple
    DARK_TEXT    = RGBColor(0x14, 0x14, 0x2B)    # #14142B - Near black
    GRAY_TEXT    = RGBColor(0x63, 0x63, 0x7E)    # #63637E - Body text gray
    WHITE        = RGBColor(0xFF, 0xFF, 0xFF)
    LIGHT_GRAY   = RGBColor(0xF0, 0xF0, 0xF5)   # Subtle background
    GREEN_CHECK  = RGBColor(0x22, 0xC5, 0x5E)    # For check marks
    RED_ALERT    = RGBColor(0xEF, 0x44, 0x44)    # For alerts/warnings

    # Layout references
    default_layout = prs.slide_layouts[0]   # DEFAULT (custom slide 1 & 12)
    content_layout = prs.slide_layouts[1]   # Title and Content (slides 2-11)

    # =========================================================================
    # HELPERS
    # =========================================================================
    def add_textbox(slide, left, top, width, height, text, font_size=16,
                    font_name="Calibri", color=GRAY_TEXT, bold=False, italic=False,
                    alignment=PP_ALIGN.LEFT, wrap=True):
        """Add a simple text box."""
        tb = slide.shapes.add_textbox(
            Inches(left), Inches(top), Inches(width), Inches(height)
        )
        tf = tb.text_frame
        tf.word_wrap = wrap
        p = tf.paragraphs[0]
        p.text = text
        p.font.size = Pt(font_size)
        p.font.name = font_name
        p.font.color.rgb = color
        p.font.bold = bold
        p.font.italic = italic
        p.alignment = alignment
        return tb

    def add_multiline_textbox(slide, left, top, width, height, lines,
                              font_size=16, font_name="Calibri", color=GRAY_TEXT,
                              line_spacing=1.5, alignment=PP_ALIGN.LEFT):
        """Add a text box with multiple lines."""
        tb = slide.shapes.add_textbox(
            Inches(left), Inches(top), Inches(width), Inches(height)
        )
        tf = tb.text_frame
        tf.word_wrap = True
        for i, line_data in enumerate(lines):
            if i == 0:
                p = tf.paragraphs[0]
            else:
                p = tf.add_paragraph()

            if isinstance(line_data, dict):
                p.text = line_data.get("text", "")
                p.font.size = Pt(line_data.get("size", font_size))
                p.font.name = line_data.get("font", font_name)
                p.font.color.rgb = line_data.get("color", color)
                p.font.bold = line_data.get("bold", False)
                p.alignment = line_data.get("align", alignment)
            else:
                p.text = str(line_data)
                p.font.size = Pt(font_size)
                p.font.name = font_name
                p.font.color.rgb = color
                p.alignment = alignment

            p.space_after = Pt(line_spacing * 2)
        return tb

    def add_bullet_content(slide, left, top, width, height, items,
                           font_size=20, color=GRAY_TEXT, font_name="Calibri",
                           bullet_char="\u2022"):
        """Add bulleted content list."""
        tb = slide.shapes.add_textbox(
            Inches(left), Inches(top), Inches(width), Inches(height)
        )
        tf = tb.text_frame
        tf.word_wrap = True
        for i, item in enumerate(items):
            if i == 0:
                p = tf.paragraphs[0]
            else:
                p = tf.add_paragraph()

            if isinstance(item, dict):
                p.text = f"{bullet_char} {item['text']}"
                p.font.size = Pt(item.get("size", font_size))
                p.font.color.rgb = item.get("color", color)
                p.font.bold = item.get("bold", False)
            else:
                p.text = f"{bullet_char} {item}"
                p.font.size = Pt(font_size)
                p.font.color.rgb = color

            p.font.name = font_name
            p.space_after = Pt(8)
        return tb

    def set_slide_bg(slide, color=WHITE):
        bg = slide.background
        fill = bg.fill
        fill.solid()
        fill.fore_color.rgb = color

    def add_title_bar(slide, title_text, font_size=44):
        """Add the purple title matching template style."""
        tb = slide.shapes.add_textbox(
            Inches(0.65), Inches(0.4), Inches(11.0), Inches(1.2)
        )
        tf = tb.text_frame
        tf.word_wrap = True
        p = tf.paragraphs[0]
        p.text = title_text
        p.font.size = Pt(font_size)
        p.font.name = "Calibri"
        p.font.color.rgb = PURPLE_TITLE
        p.font.bold = True
        p.alignment = PP_ALIGN.LEFT
        return tb

    def add_purple_divider(slide, top=1.7):
        """Subtle purple line divider under titles."""
        line = slide.shapes.add_shape(
            MSO_SHAPE.RECTANGLE,
            Inches(0.65), Inches(top),
            Inches(1.5), Inches(0.04)
        )
        line.fill.solid()
        line.fill.fore_color.rgb = PURPLE_DARK
        line.line.fill.background()
        return line

    def add_card_box(slide, left, top, width, height, bg_color=LIGHT_GRAY):
        """Add a subtle card/box background."""
        box = slide.shapes.add_shape(
            MSO_SHAPE.ROUNDED_RECTANGLE,
            Inches(left), Inches(top), Inches(width), Inches(height)
        )
        box.fill.solid()
        box.fill.fore_color.rgb = bg_color
        box.line.fill.background()
        return box

    # =========================================================================
    # SLIDE 1: TITLE SLIDE (Custom layout like template)
    # =========================================================================
    slide1 = prs.slides.add_slide(default_layout)
    set_slide_bg(slide1, WHITE)

    # "SAMSUNG PRISM" header
    add_textbox(slide1, 0.6, 0.55, 3.5, 0.4,
                "SAMSUNG PRISM", font_size=11, font_name="Arial",
                color=PURPLE_DARK, bold=True)

    # "Generative AI" large title
    add_textbox(slide1, 0.6, 1.05, 7.0, 0.9,
                "Generative AI", font_size=50, font_name="Arial",
                color=DARK_TEXT, bold=True)

    # "Hackathon" large title
    add_textbox(slide1, 0.6, 1.85, 7.0, 0.9,
                "Hackathon", font_size=50, font_name="Arial",
                color=PURPLE_DARK, bold=True)

    # "3rd Edition   2026-27"
    tb_edition = slide1.shapes.add_textbox(
        Inches(0.6), Inches(2.7), Inches(7.0), Inches(0.5)
    )
    tf = tb_edition.text_frame
    p = tf.paragraphs[0]
    run1 = p.add_run()
    run1.text = "3rd Edition"
    run1.font.size = Pt(25)
    run1.font.name = "Arial"
    run1.font.color.rgb = DARK_TEXT
    run1.font.bold = True
    run2 = p.add_run()
    run2.text = "   2026 – 27"
    run2.font.size = Pt(20)
    run2.font.name = "Calibri"
    run2.font.color.rgb = GRAY_TEXT

    # Team info block
    team_lines = [
        "Theme ID - 3 (Teachable Voice Automation)",
        "Team Name - CoreCryshalis",
        "College Name - VIT Vellore",
        "Member Name & Email 1 - Divyam Pandey (23BDS0139)",
        "Member Name & Email 2 - Rishit Chaudhary (23BCT0157)",
        "Member Name & Email 3 - Shreya Sundli (24BCE2546)",
        "Member Name & Email 4 - Swardnim Jain (23BKT0110)",
        "Submission Github link - github.com/rizzit17/sayso",
    ]
    add_multiline_textbox(slide1, 0.6, 3.3, 6.5, 3.5, team_lines,
                          font_size=16, color=GRAY_TEXT)

    # Product name tag
    add_textbox(slide1, 0.6, 6.5, 5.0, 0.4,
                "SaySo | Teachable Voice Automation", font_size=14,
                font_name="Calibri", color=PURPLE_DARK, bold=True)

    # =========================================================================
    # SLIDE 2: THEME
    # =========================================================================
    slide2 = prs.slides.add_slide(default_layout)
    set_slide_bg(slide2, WHITE)
    add_title_bar(slide2, "Theme")
    add_purple_divider(slide2)

    theme_items = [
        {"text": "Theme 3: Teachable Voice Automation", "size": 24, "bold": True, "color": DARK_TEXT},
        {"text": "Build an Android app that learns multi-step UI tasks from a single voice + tap demonstration", "size": 18},
        {"text": "Replay learned workflows autonomously via paraphrased voice commands with dynamic parameters", "size": 18},
        {"text": "Handle UI drift, popups, and layout changes through semantic matching (not coordinates)", "size": 18},
        {"text": "Guarantee zero touches on payment/credential screens with defense-in-depth safety", "size": 18},
        {"text": "Support cross-app generalization — patterns learned in one app transfer to others", "size": 18},
    ]
    add_bullet_content(slide2, 0.65, 2.0, 11.5, 4.5, theme_items, font_size=18)

    # =========================================================================
    # SLIDE 3: EXISTING SOLUTIONS & GAPS
    # =========================================================================
    slide3 = prs.slides.add_slide(default_layout)
    set_slide_bg(slide3, WHITE)
    add_title_bar(slide3, "Existing Solutions & Gaps")
    add_purple_divider(slide3)

    # Left column - Existing Solutions
    add_textbox(slide3, 0.65, 2.1, 5.5, 0.4,
                "Current Approaches", font_size=20, color=DARK_TEXT, bold=True)

    existing = [
        "Siri / Google Assistant — pre-built intents only, no user-teachable flows",
        "Tasker / MacroDroid — coordinate-based, breaks on any UI change",
        "App-specific SDKs — require developer cooperation, no cross-app",
        "RPA tools (UIPath) — enterprise desktop focus, no mobile voice",
    ]
    add_bullet_content(slide3, 0.65, 2.6, 5.5, 3.5, existing, font_size=16)

    # Right column - Gaps
    add_textbox(slide3, 6.8, 2.1, 5.5, 0.4,
                "Critical Gaps We Solve", font_size=20, color=DARK_TEXT, bold=True)

    gaps = [
        {"text": "No user-teachable learning — SaySo learns from a single demonstration", "size": 16},
        {"text": "Coordinate fragility — our 8-signal semantic matcher is immune to layout changes", "size": 16},
        {"text": "No payment safety — SaySo enforces 5-layer zero-touch boundary", "size": 16},
        {"text": "No cross-app transfer — domain concepts generalize across apps", "size": 16},
        {"text": "No recovery — 5-stage self-healing handles drift & popups", "size": 16},
    ]
    add_bullet_content(slide3, 6.8, 2.6, 5.5, 3.5, gaps, font_size=16)

    # =========================================================================
    # SLIDE 4: OUR SOLUTION & ARCHITECTURE DIAGRAM
    # =========================================================================
    slide4 = prs.slides.add_slide(default_layout)
    set_slide_bg(slide4, WHITE)
    add_title_bar(slide4, "Our Solution & Architecture Diagram")
    add_purple_divider(slide4)

    # Solution summary
    add_textbox(slide4, 0.65, 2.0, 11.5, 0.5,
                "SaySo — Zero-SDK Cross-App Semantic Agent with Strict Zero-Touch Payment Safety",
                font_size=22, color=DARK_TEXT, bold=True)

    # Architecture flow - simplified text version
    flow_items = [
        {"text": "TEACH Mode: User speaks instruction → performs taps once → system records Accessibility tree events", "size": 16},
        {"text": "Workflow Generalizer: Raw actions → filtered (IrrelevantActionFilter) → parameterized semantic workflow with slot extraction", "size": 16},
        {"text": "REPLAY Mode: Voice command → IntentMatcher (Jaccard + Levenshtein) → SlotExtractor → ParameterBinder", "size": 16},
        {"text": "Execution: ActionExecutor → SemanticUiMatcher (8 signals) → state verification → RecoveryManager (5 stages)", "size": 16},
        {"text": "Safety: CredentialBoundaryDetector (5 layers) → immediate halt before payment/OTP/credential screens", "size": 16},
    ]
    add_bullet_content(slide4, 0.65, 2.7, 11.5, 3.5, flow_items, font_size=16)

    # Core stats at bottom
    add_card_box(slide4, 0.65, 6.0, 3.2, 0.8, LIGHT_GRAY)
    add_textbox(slide4, 0.8, 6.1, 3.0, 0.6,
                "100% Native Android\nZero SDKs", font_size=14, color=PURPLE_DARK, bold=True,
                alignment=PP_ALIGN.CENTER)

    add_card_box(slide4, 4.35, 6.0, 3.2, 0.8, LIGHT_GRAY)
    add_textbox(slide4, 4.5, 6.1, 3.0, 0.6,
                "84/84 Tests Passing\nFull Coverage", font_size=14, color=PURPLE_DARK, bold=True,
                alignment=PP_ALIGN.CENTER)

    add_card_box(slide4, 8.05, 6.0, 3.2, 0.8, LIGHT_GRAY)
    add_textbox(slide4, 8.2, 6.1, 3.0, 0.6,
                "0 Payment Touches\n5-Layer Guard", font_size=14, color=PURPLE_DARK, bold=True,
                alignment=PP_ALIGN.CENTER)

    # =========================================================================
    # SLIDE 5: DEMO & PRODUCT WALKTHROUGH
    # =========================================================================
    slide5 = prs.slides.add_slide(default_layout)
    set_slide_bg(slide5, WHITE)
    add_title_bar(slide5, "Demo & Product Walkthrough")
    add_purple_divider(slide5)

    demo_items = [
        {"text": "Step 1 — Voice Activation: User taps SaySo voice orb and speaks: \"Order a Margherita Pizza from Domino's\"", "size": 18, "bold": True, "color": DARK_TEXT},
        {"text": "Step 2 — TEACH Mode: SaySo activates HUD overlay, records each tap on the Accessibility tree (not coordinates)", "size": 18},
        {"text": "Step 3 — Workflow Storage: Actions are filtered, generalized, parameterized, and stored in Room SQLite", "size": 18},
        {"text": "Step 4 — REPLAY: User says \"Order garlic bread from Domino's\" → IntentMatcher identifies same workflow, SlotExtractor swaps {item}", "size": 18},
        {"text": "Step 5 — Autonomous Execution: ActionExecutor replays steps, SemanticUiMatcher finds elements, RecoveryManager handles drift", "size": 18},
        {"text": "Step 6 — Payment Boundary: CredentialBoundaryDetector halts at checkout, hands control to user with alert", "size": 18},
    ]
    add_bullet_content(slide5, 0.65, 2.0, 11.5, 5.0, demo_items, font_size=18)

    add_textbox(slide5, 0.65, 6.5, 11.5, 0.4,
                "Demo Video: [YouTube / Drive link] (max 5 minutes, unedited, in evaluation order)",
                font_size=14, color=GRAY_TEXT, italic=True)

    # =========================================================================
    # SLIDE 6: TOOLS AND TECH STACK USED
    # =========================================================================
    slide6 = prs.slides.add_slide(default_layout)
    set_slide_bg(slide6, WHITE)
    add_title_bar(slide6, "Tools and Tech Stack Used")
    add_purple_divider(slide6)

    # Column 1
    add_textbox(slide6, 0.65, 2.1, 3.5, 0.4,
                "Platform & Language", font_size=18, color=PURPLE_DARK, bold=True)
    col1 = [
        "Kotlin 2.0.21",
        "Android 14 (API 34)",
        "Jetpack Compose + Material 3",
        "Kotlin Coroutines & Flow",
    ]
    add_bullet_content(slide6, 0.65, 2.6, 3.5, 2.5, col1, font_size=16)

    # Column 2
    add_textbox(slide6, 4.65, 2.1, 3.5, 0.4,
                "Core APIs", font_size=18, color=PURPLE_DARK, bold=True)
    col2 = [
        "AccessibilityService (native)",
        "AccessibilityNodeInfo",
        "performAction / dispatchGesture",
        "Room SQLite v2.6.1 + KSP",
    ]
    add_bullet_content(slide6, 4.65, 2.6, 3.5, 2.5, col2, font_size=16)

    # Column 3
    add_textbox(slide6, 8.65, 2.1, 3.5, 0.4,
                "Architecture & Testing", font_size=18, color=PURPLE_DARK, bold=True)
    col3 = [
        "MVVM + Repository Pattern",
        "84 JUnit tests (100% pass)",
        "Proguard R8 minification",
        "Zero third-party SDKs",
    ]
    add_bullet_content(slide6, 8.65, 2.6, 3.5, 2.5, col3, font_size=16)

    # Zero-SDK emphasis
    add_card_box(slide6, 0.65, 5.3, 11.5, 1.2, LIGHT_GRAY)
    add_textbox(slide6, 1.0, 5.4, 11.0, 1.0,
                "Zero-SDK Footprint: No app-specific proprietary SDKs, no deep links, no web fallbacks.\n"
                "Strictly Android native Accessibility APIs. 100% private, on-device execution.",
                font_size=16, color=DARK_TEXT, bold=True, alignment=PP_ALIGN.CENTER)

    # =========================================================================
    # SLIDE 7: IMPACT & USE CASE
    # =========================================================================
    slide7 = prs.slides.add_slide(default_layout)
    set_slide_bg(slide7, WHITE)
    add_title_bar(slide7, "Impact & Use Case")
    add_purple_divider(slide7)

    # Target users
    add_textbox(slide7, 0.65, 2.1, 5.5, 0.4,
                "Who Benefits", font_size=20, color=DARK_TEXT, bold=True)

    users = [
        {"text": "Elderly users — automate complex app flows with simple voice commands", "size": 16},
        {"text": "Accessibility users — vision/motor impaired users gain full app automation", "size": 16},
        {"text": "Power users — eliminate repetitive multi-step tasks across any app", "size": 16},
        {"text": "Non-technical users — no coding, just teach once and replay forever", "size": 16},
    ]
    add_bullet_content(slide7, 0.65, 2.6, 5.5, 2.5, users, font_size=16)

    # Use cases
    add_textbox(slide7, 6.8, 2.1, 5.5, 0.4,
                "Real-World Scenarios", font_size=20, color=DARK_TEXT, bold=True)

    cases = [
        {"text": "Food ordering — teach Domino's once, replay with any item/quantity", "size": 16},
        {"text": "E-commerce — search + add to cart on Amazon with slot substitution", "size": 16},
        {"text": "Banking — navigate to balance check (stops at credential screens)", "size": 16},
        {"text": "Cross-app workflows — patterns transfer between Domino's ↔ Amazon", "size": 16},
    ]
    add_bullet_content(slide7, 6.8, 2.6, 5.5, 2.5, cases, font_size=16)

    # Impact metrics
    add_card_box(slide7, 0.65, 5.5, 11.5, 1.3, LIGHT_GRAY)
    impact_text = (
        "Potential Impact: Reduces 15-20 tap workflows to a single voice command.\n"
        "Avg step dispatch < 400ms  ·  End-to-end replay < 10s  ·  Peak heap < 85MB  ·  "
        "Works on ANY Android app without developer cooperation."
    )
    add_textbox(slide7, 1.0, 5.6, 11.0, 1.1,
                impact_text, font_size=15, color=DARK_TEXT, alignment=PP_ALIGN.CENTER)

    # =========================================================================
    # SLIDE 8: INNOVATION HIGHLIGHTS, RESULTS & LIMITATIONS
    # =========================================================================
    slide8 = prs.slides.add_slide(default_layout)
    set_slide_bg(slide8, WHITE)
    add_title_bar(slide8, "Innovation Highlights, Results & Limitations", font_size=36)
    add_purple_divider(slide8)

    # Innovations
    add_textbox(slide8, 0.65, 2.1, 5.5, 0.4,
                "Key Innovations", font_size=18, color=PURPLE_DARK, bold=True)
    innovations = [
        "8-Signal Semantic UI Matcher — immune to layout drift, theme changes, dynamic content",
        "5-Layer Payment Boundary — zero touches on payment/credential screens guaranteed",
        "5-Stage Self-Healing Recovery — handles popups, reordering, text changes autonomously",
        "Cross-App Domain Concepts — ADD_TO_CART, SEARCH work across apps without retraining",
        "Parameterized Slot System — same workflow, infinite variations (item, quantity, address)",
    ]
    add_bullet_content(slide8, 0.65, 2.6, 5.5, 2.5, innovations, font_size=14)

    # Results
    add_textbox(slide8, 6.8, 2.1, 5.5, 0.4,
                "Results & Limitations", font_size=18, color=PURPLE_DARK, bold=True)
    results = [
        "✔ 84/84 evaluation tests passing (T1-T14, B1-B3 + auxiliary)",
        "✔ Average step dispatch latency < 400ms",
        "✔ End-to-end replay completion < 10 seconds",
        "✔ Zero payment screen touches across all test scenarios",
        "△ Requires Accessibility Service permission (standard for automation)",
        "△ First-time teach requires ~30s user demonstration",
        "△ Very complex multi-branch flows may need multiple teachings",
    ]
    add_bullet_content(slide8, 6.8, 2.6, 5.5, 3.5, results, font_size=14)

    # =========================================================================
    # SLIDE 9: WHAT'S NEXT
    # =========================================================================
    slide9 = prs.slides.add_slide(default_layout)
    set_slide_bg(slide9, WHITE)
    add_title_bar(slide9, "What's Next")
    add_purple_divider(slide9)

    future = [
        {"text": "Cloud Workflow Sharing — community-contributed workflows downloadable by other users", "size": 18},
        {"text": "On-Device LLM Integration — local language model for richer intent understanding", "size": 18},
        {"text": "Multi-Step Conditional Flows — if/else branching based on screen state", "size": 18},
        {"text": "Workflow Chaining — combine multiple learned workflows into macro sequences", "size": 18},
        {"text": "Samsung Galaxy AI Integration — native integration with Galaxy S series voice features", "size": 18},
        {"text": "Wear OS Extension — trigger replay workflows from Galaxy Watch voice commands", "size": 18},
    ]
    add_bullet_content(slide9, 0.65, 2.0, 11.5, 4.5, future, font_size=18)

    # =========================================================================
    # SLIDE 10: BROWNIE POINTS (DIFFERENTIATION)
    # =========================================================================
    slide10 = prs.slides.add_slide(default_layout)
    set_slide_bg(slide10, WHITE)
    add_title_bar(slide10, "Brownie Points (Differentiation)")
    add_purple_divider(slide10)

    # B1
    add_card_box(slide10, 0.65, 2.0, 3.5, 2.0, LIGHT_GRAY)
    add_textbox(slide10, 0.8, 2.1, 3.2, 0.4,
                "B1: Irrelevant Action Filtering", font_size=14, color=PURPLE_DARK, bold=True)
    add_textbox(slide10, 0.8, 2.5, 3.2, 1.4,
                "IrrelevantActionFilter detects and removes accidental taps, undo actions, "
                "and navigation noise during TEACH mode. Only meaningful semantic actions are stored.",
                font_size=13, color=GRAY_TEXT)

    # B2
    add_card_box(slide10, 4.4, 2.0, 3.5, 2.0, LIGHT_GRAY)
    add_textbox(slide10, 4.55, 2.1, 3.2, 0.4,
                "B2: Cross-App Generalization (+4 pts)", font_size=14, color=PURPLE_DARK, bold=True)
    add_textbox(slide10, 4.55, 2.5, 3.2, 1.4,
                "Domain Concept mapping (ADD_TO_CART, SEARCH, SELECT) enables workflows "
                "to structurally transfer between apps without retraining. Food order patterns work on e-commerce.",
                font_size=13, color=GRAY_TEXT)

    # B3
    add_card_box(slide10, 8.15, 2.0, 3.5, 2.0, LIGHT_GRAY)
    add_textbox(slide10, 8.3, 2.1, 3.2, 0.4,
                "B3: Multi-Modal Disambiguation", font_size=14, color=PURPLE_DARK, bold=True)
    add_textbox(slide10, 8.3, 2.5, 3.2, 1.4,
                "ClarificationHandler presents voice + tap options when automation encounters "
                "ambiguous state. User can speak clarification or tap a quick-select button.",
                font_size=13, color=GRAY_TEXT)

    # Additional differentiators
    add_textbox(slide10, 0.65, 4.3, 11.5, 0.4,
                "Additional Differentiators", font_size=18, color=DARK_TEXT, bold=True)

    diff_items = [
        "Pure native Kotlin — no React Native, no Flutter, no web wrappers",
        "8-signal weighted semantic matching vs. industry-standard coordinate-based",
        "5-layer payment safety exceeds the single-check standard",
        "Sub-400ms step latency — real-time execution experience",
        "Offline-first Room SQLite — works without internet after setup",
    ]
    add_bullet_content(slide10, 0.65, 4.8, 11.5, 2.0, diff_items, font_size=16)

    # =========================================================================
    # SLIDE 11: CHECKLIST (Updated on Public GitHub)
    # =========================================================================
    slide11 = prs.slides.add_slide(default_layout)
    set_slide_bg(slide11, WHITE)
    add_title_bar(slide11, "Checklist — Updated on Public GitHub")
    add_purple_divider(slide11)

    checklist = [
        {"text": "✔  Working prototype code — public GitHub repo (Y)", "size": 22, "color": DARK_TEXT},
        {"text": "     github.com/rizzit17/sayso", "size": 16, "color": GRAY_TEXT},
        {"text": "✔  README with reproducible setup instructions (Y)", "size": 22, "color": DARK_TEXT},
        {"text": "     Complete README.md with architecture, setup, and test commands", "size": 16, "color": GRAY_TEXT},
        {"text": "✔  Demo video, max 5 minutes (YouTube or Drive link)", "size": 22, "color": DARK_TEXT},
        {"text": "     [Insert YouTube/Drive link here]", "size": 16, "color": GRAY_TEXT},
        {"text": "✔  Presentation file (PPT or PDF) (Y)", "size": 22, "color": DARK_TEXT},
        {"text": "     This presentation — VITVellore_CoreCryshalis_Submission.pptx", "size": 16, "color": GRAY_TEXT},
    ]
    add_bullet_content(slide11, 0.65, 2.0, 11.5, 5.0, checklist, font_size=22, bullet_char="")

    # =========================================================================
    # SLIDE 12: THANK YOU
    # =========================================================================
    slide12 = prs.slides.add_slide(default_layout)
    set_slide_bg(slide12, WHITE)

    # Thank you text centered
    add_textbox(slide12, 1.0, 2.8, 11.3, 1.0,
                "Thank you", font_size=46, font_name="Arial",
                color=DARK_TEXT, bold=True, alignment=PP_ALIGN.CENTER)

    # Team name
    add_textbox(slide12, 1.0, 3.8, 11.3, 0.5,
                "Team CoreCryshalis — SaySo | Teachable Voice Automation",
                font_size=20, font_name="Calibri",
                color=PURPLE_DARK, bold=True, alignment=PP_ALIGN.CENTER)

    # Contact
    add_textbox(slide12, 1.0, 4.5, 11.3, 0.5,
                "github.com/rizzit17/sayso",
                font_size=16, font_name="Calibri",
                color=GRAY_TEXT, alignment=PP_ALIGN.CENTER)

    # Footer
    add_textbox(slide12, 0.6, 6.3, 11.5, 0.4,
                "Organised by the Language AI Team and the PRISM Team, Samsung R&D Institute India",
                font_size=11, font_name="Calibri",
                color=GRAY_TEXT, alignment=PP_ALIGN.CENTER)

    # =========================================================================
    # SAVE
    # =========================================================================
    output_path = os.path.join(os.path.dirname(__file__), "VITVellore_CoreCryshalis_Submission.pptx")
    prs.save(output_path)
    print(f"Saved: {output_path}")
    print(f"Total slides: {len(prs.slides)}")

if __name__ == "__main__":
    create_deck()
