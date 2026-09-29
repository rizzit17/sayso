package com.samsung.prism.teachable.teaching

import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IrrelevantActionFilterTest {

    private val filter = IrrelevantActionFilter()

    @Test
    fun testSwitchClickNeverRetroactivelyCancelled() {
        val session = TeachingSession(
            originalUtterance = "Toggle Airplane Mode From Settings",
            targetPackageHint = "com.android.settings"
        )

        val settingsScreen = UiSnapshot(
            packageName = "com.android.settings",
            screenSignature = "sig_settings_main"
        )

        // 1. Legitimate action: tap Airplane Mode switch (toggle/switch semantic role)
        val switchNode = UiNode(
            resourceId = "com.android.settings:id/switch_widget",
            text = "Airplane mode",
            className = "android.widget.Switch",
            semanticRole = "switch",
            packageName = "com.android.settings",
            clickable = true,
            isChecked = true
        )
        val switchAction = RawAction(
            packageName = "com.android.settings",
            actionType = ActionType.CLICK,
            targetNode = switchNode,
            screenBefore = settingsScreen,
            // Even if the screen after toggling has the exact same structural signature:
            screenAfter = settingsScreen
        )
        val evalSwitch = filter.evaluate(switchAction, session, settingsScreen, settingsScreen)
        assertTrue(evalSwitch.isRelevant)
        session.rawActions.add(switchAction)

        // 2. User presses Back or Close to leave or return
        val backNode = UiNode(
            resourceId = "android:id/home",
            text = "Back",
            className = "android.widget.ImageView",
            packageName = "com.android.settings",
            clickable = true
        )
        val backAction = RawAction(
            packageName = "com.android.settings",
            actionType = ActionType.CLICK,
            targetNode = backNode,
            screenBefore = settingsScreen,
            screenAfter = settingsScreen
        )

        val evalBack = filter.evaluate(backAction, session, settingsScreen, settingsScreen)
        // Back action returns to prior state signature, BUT switchAction MUST NOT be cancelled!
        assertFalse("Switch action must NEVER be retroactively cancelled as accidental tap", switchAction.isFiltered)
    }

    @Test
    fun testNonToggleAccidentalTapIsCancelled_B1Passes() {
        val session = TeachingSession(
            originalUtterance = "Search for shoes on Amazon",
            targetPackageHint = "com.amazon.mShop.android.shopping"
        )

        val mainScreen = UiSnapshot(
            packageName = "com.amazon.mShop.android.shopping",
            screenSignature = "sig_main"
        )
        val promoScreen = UiSnapshot(
            packageName = "com.amazon.mShop.android.shopping",
            screenSignature = "sig_promo_popup"
        )

        // User accidentally taps promo banner (non-toggle button / card)
        val bannerNode = UiNode(
            text = "Discount Ad",
            semanticRole = "button",
            className = "android.widget.Button",
            packageName = "com.amazon.mShop.android.shopping",
            clickable = true
        )
        val accidentalTap = RawAction(
            packageName = "com.amazon.mShop.android.shopping",
            actionType = ActionType.CLICK,
            targetNode = bannerNode,
            screenBefore = mainScreen,
            screenAfter = promoScreen
        )
        session.rawActions.add(accidentalTap)

        // User immediately taps Close, returning to mainScreen signature
        val closeNode = UiNode(
            resourceId = "com.amazon.mShop.android.shopping:id/close_btn",
            text = "Close",
            semanticRole = "button",
            packageName = "com.amazon.mShop.android.shopping",
            clickable = true
        )
        val closeAction = RawAction(
            packageName = "com.amazon.mShop.android.shopping",
            actionType = ActionType.CLICK,
            targetNode = closeNode,
            screenBefore = promoScreen,
            screenAfter = mainScreen
        )

        val evalClose = filter.evaluate(closeAction, session, promoScreen, mainScreen)
        assertFalse(evalClose.isRelevant)
        assertTrue("Prior accidental tap must be retroactively filtered", accidentalTap.isFiltered)
        assertEquals("Cancelled accidental tap", accidentalTap.filterReason)
    }

    @Test
    fun testRetroactiveCancellationOnlyAppliesToImmediatelyPrecedingAction() {
        val session = TeachingSession(
            originalUtterance = "Browse products",
            targetPackageHint = "com.example.shop"
        )
        val screen1 = UiSnapshot(packageName = "com.example.shop", screenSignature = "sig_1")
        val screen2 = UiSnapshot(packageName = "com.example.shop", screenSignature = "sig_2")

        val tap1 = RawAction(
            packageName = "com.example.shop",
            actionType = ActionType.CLICK,
            targetNode = UiNode(text = "Categories", semanticRole = "button", clickable = true),
            screenBefore = screen1,
            screenAfter = screen2
        )
        session.rawActions.add(tap1)

        val tap2 = RawAction(
            packageName = "com.example.shop",
            actionType = ActionType.CLICK,
            targetNode = UiNode(text = "Electronics", semanticRole = "button", clickable = true),
            screenBefore = screen2,
            screenAfter = screen2
        )
        session.rawActions.add(tap2)

        // Now an action containing back/close arrives with screenAfter == screen1 (tap1's before),
        // but tap1 was NOT the immediately preceding action (tap2 was).
        val backAction = RawAction(
            packageName = "com.example.shop",
            actionType = ActionType.CLICK,
            targetNode = UiNode(text = "Back", clickable = true),
            screenBefore = screen2,
            screenAfter = screen1
        )
        filter.evaluate(backAction, session, screen2, screen1)
        // tap1 should not be cancelled because it was not immediately preceding
        assertFalse(tap1.isFiltered)
    }
}
