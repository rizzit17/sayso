package com.samsung.prism.teachable.replay

import com.samsung.prism.teachable.model.ExpectedStateTransition
import com.samsung.prism.teachable.model.StepTarget
import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.observation.Bounds
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import com.samsung.prism.teachable.teaching.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticReplayTest {

    private val matcher = SemanticUiMatcher()
    private val binder = ParameterBinder()

    private fun createSnapshotWithNodes(nodes: List<UiNode>, screenSig: String = "sig_screen"): UiSnapshot {
        val root = UiNode(
            resourceId = "root",
            className = "android.widget.FrameLayout",
            bounds = Bounds(0, 0, 1080, 2400),
            children = nodes
        )
        return UiSnapshot(
            packageName = "com.application.zomato",
            activityName = "MainActivity",
            rootNode = root,
            screenSignature = screenSig
        )
    }

    @Test
    fun testStandardMatch() {
        val target = StepTarget(
            resourceId = "com.zomato:id/btn_add",
            text = "Add",
            semanticRole = "button",
            className = "android.widget.Button",
            screenSignature = "sig_menu",
            boundsRelativeX = 0.8f,
            boundsRelativeY = 0.4f
        )

        val candidate = UiNode(
            resourceId = "com.zomato:id/btn_add",
            text = "Add",
            semanticRole = "button",
            className = "android.widget.Button",
            bounds = Bounds(800, 900, 1000, 1000),
            clickable = true
        )

        val snapshot = createSnapshotWithNodes(listOf(candidate), screenSig = "sig_menu")
        val match = matcher.findBestMatch(target, snapshot)

        assertNotNull(match)
        assertTrue(match!!.score >= 0.85)
        assertEquals(candidate, match.node)
    }

    @Test
    fun testUiDrift_ShiftedPosition() {
        // T8 / UI Drift test: Position shifted significantly (e.g. by 400px down due to promotional banner)
        val target = StepTarget(
            resourceId = "com.zomato:id/dish_title",
            text = "Margherita Pizza",
            semanticRole = "text_view",
            className = "android.widget.TextView",
            screenSignature = "sig_menu",
            boundsRelativeX = 0.3f,
            boundsRelativeY = 0.2f
        )

        val candidateShifted = UiNode(
            resourceId = "com.zomato:id/dish_title",
            text = "Margherita Pizza",
            semanticRole = "text_view",
            className = "android.widget.TextView",
            bounds = Bounds(50, 1200, 500, 1300), // Moved from y=480 down to y=1200
            clickable = true
        )

        val snapshot = createSnapshotWithNodes(listOf(candidateShifted), screenSig = "sig_menu")
        val match = matcher.findBestMatch(target, snapshot)

        assertNotNull("Should match despite significant vertical shift", match)
        assertTrue(match!!.score >= 0.70)
        assertEquals(candidateShifted, match.node)
    }

    @Test
    fun testUiDrift_ChangedResourceId() {
        // Obfuscated or regenerated resourceId in a new app version
        val target = StepTarget(
            resourceId = "com.amazon:id/rs_search_src_text",
            text = "Search Amazon",
            semanticRole = "search_box",
            className = "android.widget.EditText",
            screenSignature = "sig_home"
        )

        val candidateNewId = UiNode(
            resourceId = "com.amazon:id/nav_search_input", // ID changed
            text = "Search Amazon",
            semanticRole = "search_box",
            className = "android.widget.EditText",
            clickable = true
        )

        val snapshot = createSnapshotWithNodes(listOf(candidateNewId), screenSig = "sig_home")
        val match = matcher.findBestMatch(target, snapshot, threshold = 0.55)

        assertNotNull("Should match by semantic text, role, and class despite changed resourceId", match)
        assertTrue(match!!.score >= 0.55)
    }

    @Test
    fun testUiDrift_TextCapitalizedAndPunctuation() {
        val target = StepTarget(
            text = "Add to Cart",
            semanticRole = "button"
        )

        val candidate = UiNode(
            text = "ADD TO CART!",
            semanticRole = "button",
            clickable = true
        )

        val snapshot = createSnapshotWithNodes(listOf(candidate))
        val match = matcher.findBestMatch(target, snapshot)

        assertNotNull(match)
        assertEquals(candidate, match!!.node)
    }

    @Test
    fun testCoordinateOnlyMatchingForbidden() {
        // A node is at the exact same location, but has completely different text, role, and ID
        val target = StepTarget(
            resourceId = "com.app:id/checkout_button",
            text = "Checkout",
            semanticRole = "button",
            boundsRelativeX = 0.5f,
            boundsRelativeY = 0.9f
        )

        val unrelatedBanner = UiNode(
            resourceId = "com.app:id/promo_banner",
            text = "50% off on first order",
            semanticRole = "card",
            className = "android.widget.ImageView",
            bounds = Bounds(40, 2100, 1040, 2250) // center is ~(0.5, 0.9)
        )

        val snapshot = createSnapshotWithNodes(listOf(unrelatedBanner))
        val match = matcher.findBestMatch(target, snapshot)

        assertNull("Coordinate-only match must NOT be accepted when semantic signals fail", match)
    }

    @Test
    fun testParameterBinder() {
        val step = WorkflowStep(
            id = "s1",
            workflowId = "wf1",
            stepOrder = 0,
            actionType = ActionType.SET_TEXT,
            inputText = "{item}",
            target = StepTarget(
                text = "{restaurant}",
                contentDescription = "Order {item} from {restaurant}"
            ),
            expectedStateTransition = ExpectedStateTransition(
                expectedTextSubstring = "{item}"
            ),
            slotBinding = "item"
        )

        val params = mapOf(
            "item" to "Farmhouse pizza",
            "restaurant" to "Domino's"
        )

        val bound = binder.bindStep(step, params)

        assertEquals("Farmhouse pizza", bound.inputText)
        assertEquals("Domino's", bound.target.text)
        assertEquals("Order Farmhouse pizza from Domino's", bound.target.contentDescription)
        assertEquals("Farmhouse pizza", bound.expectedStateTransition.expectedTextSubstring)
    }

    @Test(expected = SecurityException::class)
    fun testActionExecutorBlocksPassword() {
        runBlocking {
            val executor = ActionExecutor()
            val passwordNode = UiNode(
                resourceId = "com.bank:id/password_input",
                isPassword = true,
                semanticRole = "input"
            )
            executor.executeClick(passwordNode)
        }
    }

    @Test(expected = SecurityException::class)
    fun testActionExecutorBlocksCvvOrPin() {
        runBlocking {
            val executor = ActionExecutor()
            val pinNode = UiNode(
                text = "Enter UPI PIN",
                semanticRole = "input"
            )
            executor.executeClick(pinNode)
        }
    }
}
