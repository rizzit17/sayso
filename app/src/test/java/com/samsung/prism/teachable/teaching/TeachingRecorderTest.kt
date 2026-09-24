package com.samsung.prism.teachable.teaching

import com.samsung.prism.teachable.observation.Bounds
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeachingRecorderTest {

    private val filter = IrrelevantActionFilter()
    private val recorder = TeachingRecorder(filter = filter)

    @Test
    fun testTeachingSessionRecordingAndRetention() {
        val session = recorder.startSession("Order a Margherita pizza from Domino's on Zomato", "com.application.zomato")
        assertTrue(recorder.isRecording)

        val searchNode = UiNode(
            resourceId = "com.application.zomato:id/search_bar",
            text = "Search",
            packageName = "com.application.zomato",
            clickable = true
        )
        val snap1 = UiSnapshot(packageName = "com.application.zomato", rootNode = searchNode)
        val snap2 = UiSnapshot(packageName = "com.application.zomato", rootNode = searchNode.copy(text = "Domino's"))

        val action1 = recorder.recordAction(
            actionType = ActionType.SET_TEXT,
            targetNode = searchNode,
            inputText = "Domino's",
            before = snap1,
            after = snap2
        )

        assertFalse(action1.isFiltered)
        assertEquals("Typed 'Domino's' into 'Search'", action1.semanticDescription)
        assertEquals(1, session.retainedActions.size)

        val stopped = recorder.stopSession(truncatedAtBoundary = false)
        assertEquals(SessionStatus.COMPLETED, stopped.status)
        assertFalse(recorder.isRecording)
    }

    @Test
    fun testIrrelevantActionFilterPhoneCallInterruption_BonusB1() {
        // Taught intent: Order food
        val session = TeachingSession(
            originalUtterance = "Order a Margherita pizza from Domino's on Zomato",
            targetPackageHint = "com.application.zomato"
        )

        val zomatoSnap = UiSnapshot(packageName = "com.application.zomato")

        // 1. Legitimate action: tap on Domino's
        val dominosNode = UiNode(
            resourceId = "com.application.zomato:id/restaurant_dominos",
            text = "Domino's Pizza",
            packageName = "com.application.zomato",
            clickable = true
        )
        val dominosAction = RawAction(
            packageName = "com.application.zomato",
            actionType = ActionType.CLICK,
            targetNode = dominosNode,
            screenBefore = zomatoSnap,
            screenAfter = zomatoSnap
        )
        val evalDominos = filter.evaluate(dominosAction, session, zomatoSnap, zomatoSnap)
        assertTrue(evalDominos.isRelevant)

        // 2. Incoming call occurs: user taps "Decline" in phone dialer app (Bonus B1)
        val dialerSnap = UiSnapshot(packageName = "com.google.android.dialer")
        val declineNode = UiNode(
            resourceId = "com.google.android.dialer:id/decline_button",
            text = "Decline call",
            packageName = "com.google.android.dialer",
            clickable = true
        )
        val callAction = RawAction(
            packageName = "com.google.android.dialer",
            actionType = ActionType.CLICK,
            targetNode = declineNode,
            screenBefore = dialerSnap,
            screenAfter = zomatoSnap
        )
        val evalCall = filter.evaluate(callAction, session, dialerSnap, zomatoSnap)
        assertFalse(evalCall.isRelevant)
        assertTrue(evalCall.reason?.contains("Call") == true || evalCall.reason?.contains("phone") == true)
        assertTrue(evalCall.relevanceScore < 0.35f)

        // 3. User resumes: taps "Margherita Pizza" in Zomato
        val pizzaNode = UiNode(
            resourceId = "com.application.zomato:id/pizza_item",
            text = "Margherita Pizza",
            packageName = "com.application.zomato",
            clickable = true
        )
        val pizzaAction = RawAction(
            packageName = "com.application.zomato",
            actionType = ActionType.CLICK,
            targetNode = pizzaNode,
            screenBefore = zomatoSnap,
            screenAfter = zomatoSnap
        )
        val evalPizza = filter.evaluate(pizzaAction, session, zomatoSnap, zomatoSnap)
        assertTrue(evalPizza.isRelevant)
    }

    @Test
    fun testIrrelevantActionFilterAccidentalTapAndUndo() {
        val session = TeachingSession(
            originalUtterance = "Search for headphones on Amazon",
            targetPackageHint = "com.amazon.mShop.android.shopping"
        )

        val initialScreen = UiSnapshot(
            packageName = "com.amazon.mShop.android.shopping",
            screenSignature = "sig_initial"
        )
        val promoScreen = UiSnapshot(
            packageName = "com.amazon.mShop.android.shopping",
            screenSignature = "sig_promo"
        )

        // User accidentally taps promo banner
        val promoNode = UiNode(text = "Daily Deals Banner", clickable = true)
        val accidentalAction = RawAction(
            packageName = "com.amazon.mShop.android.shopping",
            actionType = ActionType.CLICK,
            targetNode = promoNode,
            screenBefore = initialScreen,
            screenAfter = promoScreen
        )
        session.rawActions.add(accidentalAction)

        // User immediately clicks Close/Back, returning to sig_initial
        val closeNode = UiNode(text = "Close", resourceId = "com.amazon.mShop.android.shopping:id/close", clickable = true)
        val backAction = RawAction(
            packageName = "com.amazon.mShop.android.shopping",
            actionType = ActionType.CLICK,
            targetNode = closeNode,
            screenBefore = promoScreen,
            screenAfter = initialScreen
        )

        val evalBack = filter.evaluate(backAction, session, promoScreen, initialScreen)
        assertFalse(evalBack.isRelevant)
        assertTrue(accidentalAction.isFiltered) // prior accidental tap marked filtered!
        assertEquals("Cancelled accidental tap", accidentalAction.filterReason)
    }
}
