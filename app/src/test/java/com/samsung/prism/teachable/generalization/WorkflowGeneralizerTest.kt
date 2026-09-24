package com.samsung.prism.teachable.generalization

import com.samsung.prism.teachable.observation.Bounds
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import com.samsung.prism.teachable.teaching.ActionType
import com.samsung.prism.teachable.teaching.RawAction
import com.samsung.prism.teachable.teaching.TeachingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowGeneralizerTest {

    private val generalizer = WorkflowGeneralizer()

    private fun createDummySnapshot(pkg: String, sig: String): UiSnapshot {
        val root = UiNode(
            resourceId = "root",
            className = "android.widget.FrameLayout",
            packageName = pkg,
            bounds = Bounds(0, 0, 1080, 2400)
        )
        return UiSnapshot(
            packageName = pkg,
            activityName = "MainActivity",
            rootNode = root,
            screenSignature = sig
        )
    }

    @Test
    fun testGeneralizeFoodDeliveryWorkflow() {
        val utterance = "Order Margherita pizza from Domino's on Zomato"
        val session = TeachingSession(
            originalUtterance = utterance,
            targetPackageHint = "com.application.zomato"
        )

        val before1 = createDummySnapshot("com.application.zomato", "sig_home")
        val searchBox = UiNode(
            resourceId = "search_bar",
            className = "android.widget.EditText",
            packageName = "com.application.zomato",
            bounds = Bounds(40, 100, 1040, 220),
            text = "Restaurant name or a dish...",
            semanticRole = "search_box"
        )
        val action1 = RawAction(
            packageName = "com.application.zomato",
            actionType = ActionType.SET_TEXT,
            inputText = "Margherita pizza",
            targetNode = searchBox,
            screenBefore = before1
        )

        val before2 = createDummySnapshot("com.application.zomato", "sig_results")
        val dominosTile = UiNode(
            resourceId = "rest_dominos",
            className = "android.view.ViewGroup",
            packageName = "com.application.zomato",
            bounds = Bounds(40, 300, 1040, 600),
            text = "Domino's Pizza",
            contentDescription = "Domino's Pizza delivery in 25 mins",
            semanticRole = "card"
        )
        val action2 = RawAction(
            packageName = "com.application.zomato",
            actionType = ActionType.CLICK,
            targetNode = dominosTile,
            screenBefore = before2
        )

        session.rawActions.add(action1)
        session.rawActions.add(action2)

        val workflow = generalizer.generalize(session)

        assertEquals("order_food", workflow.intentTag)
        assertEquals("Order {item} from {restaurant} on {platform}", workflow.generalizedIntent)
        assertEquals(2, workflow.steps.size)

        // Verify detected slots in schema
        val schema = workflow.slotSchema
        assertNotNull(schema.getSlot("item"))
        assertEquals("Margherita pizza", schema.getSlot("item")?.defaultValue)
        assertNotNull(schema.getSlot("restaurant"))
        assertEquals("Domino's", schema.getSlot("restaurant")?.defaultValue)
        assertNotNull(schema.getSlot("platform"))
        assertEquals("Zomato", schema.getSlot("platform")?.defaultValue)
        assertNotNull(schema.getSlot("quantity"))
        assertNotNull(schema.getSlot("address"))

        // Step 1 should be bound to slot 'item'
        assertEquals("item", workflow.steps[0].slotBinding)
        assertTrue(workflow.steps[0].isParameterized)

        // Step 2 should be bound to slot 'restaurant'
        assertEquals("restaurant", workflow.steps[1].slotBinding)
        assertTrue(workflow.steps[1].isParameterized)

        // Step targets
        assertNotNull(workflow.steps[0].target)
        assertEquals("sig_home", workflow.steps[0].target.screenSignature)
        assertEquals("sig_results", workflow.steps[1].target.screenSignature)
    }

    @Test
    fun testGeneralizeEcommerceWorkflow() {
        val utterance = "Search for headphones on Amazon and add to cart"
        val session = TeachingSession(
            originalUtterance = utterance,
            targetPackageHint = "in.amazon.mShop.android.shopping"
        )

        val before = createDummySnapshot("in.amazon.mShop.android.shopping", "sig_amazon")
        val searchBox = UiNode(
            resourceId = "search_box",
            className = "android.widget.EditText",
            packageName = "in.amazon.mShop.android.shopping",
            bounds = Bounds(40, 100, 1040, 220),
            text = "Search Amazon.in",
            semanticRole = "search_box"
        )
        val action = RawAction(
            packageName = "in.amazon.mShop.android.shopping",
            actionType = ActionType.SET_TEXT,
            inputText = "Headphones",
            targetNode = searchBox,
            screenBefore = before
        )

        session.rawActions.add(action)

        val workflow = generalizer.generalize(session)

        assertEquals("ecommerce_search", workflow.intentTag)
        assertEquals("Search for {item} on {platform} and add to cart", workflow.generalizedIntent)
        assertNotNull(workflow.slotSchema.getSlot("item"))
        assertEquals("Headphones", workflow.slotSchema.getSlot("item")?.defaultValue)
        assertNotNull(workflow.slotSchema.getSlot("platform"))
        assertEquals("Amazon", workflow.slotSchema.getSlot("platform")?.defaultValue)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDegenerateSessionThrows() {
        val session = TeachingSession(
            originalUtterance = "Empty session",
            targetPackageHint = "com.test"
        )
        generalizer.generalize(session)
    }

    @Test
    fun testBoundaryPropagation() {
        val session = TeachingSession(
            originalUtterance = "Order pizza from Domino's on Zomato",
            targetPackageHint = "com.application.zomato"
        )
        session.truncatedAtBoundary = true

        val before = createDummySnapshot("com.application.zomato", "sig_cart")
        val placeOrderBtn = UiNode(
            resourceId = "place_order_btn",
            className = "android.widget.Button",
            packageName = "com.application.zomato",
            bounds = Bounds(40, 2000, 1040, 2200),
            text = "Select Payment Method",
            semanticRole = "button"
        )
        session.rawActions.add(
            RawAction(
                packageName = "com.application.zomato",
                actionType = ActionType.CLICK,
                targetNode = placeOrderBtn,
                screenBefore = before
            )
        )

        val workflow = generalizer.generalize(session)
        assertTrue(workflow.steps.last().isBoundary)
    }
}
