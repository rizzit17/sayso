package com.samsung.prism.teachable.teaching

import com.samsung.prism.teachable.observation.Bounds
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

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

    @Test
    fun testHomeScreenMinimizationAndLauncherTransition() {
        // User starts teaching without explicit target package hint (minimized to home screen)
        val session = recorder.startSession("Toggle Airplane Mode")
        assertTrue(recorder.isRecording)

        // 1. User is on home screen launcher and taps Settings icon
        val launcherNode = UiNode(
            text = "Settings",
            packageName = "com.google.android.apps.nexuslauncher",
            clickable = true
        )
        val launcherSnap = UiSnapshot(packageName = "com.google.android.apps.nexuslauncher")
        val settingsSnap1 = UiSnapshot(packageName = "com.android.settings")

        val launchAction = recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = launcherNode,
            before = launcherSnap,
            after = settingsSnap1
        )
        // Launcher tap is evaluated without blocking subsequent app actions
        assertEquals("com.google.android.apps.nexuslauncher", launchAction.packageName)

        // 2. User is now in Settings and taps "Network & internet"
        val networkNode = UiNode(
            resourceId = "com.android.settings:id/network_settings",
            text = "Network & internet",
            packageName = "com.android.settings",
            clickable = true
        )
        val settingsSnap2 = UiSnapshot(packageName = "com.android.settings")

        val networkAction = recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = networkNode,
            before = settingsSnap1,
            after = settingsSnap2
        )
        assertFalse(networkAction.isFiltered)
        assertEquals("com.android.settings", networkAction.packageName)
        assertEquals("com.android.settings", session.targetPackageHint)

        // 3. User toggles Airplane Mode switch
        val switchNode = UiNode(
            resourceId = "com.android.settings:id/switch_airplane",
            text = "Airplane mode",
            packageName = "com.android.settings",
            clickable = true
        )
        val airplaneAction = recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = switchNode,
            before = settingsSnap2,
            after = settingsSnap2
        )
        assertFalse(airplaneAction.isFiltered)

        val stopped = recorder.stopSession()
        assertEquals(SessionStatus.COMPLETED, stopped.status)
        assertTrue(stopped.retainedActions.size >= 2)
    }

    @Test
    fun testTrailingRecentsActionsPrunedOnStop() {
        val session = recorder.startSession("Toggle Airplane Mode", "com.android.settings")
        val snap = UiSnapshot(packageName = "com.android.settings")

        // 1. User taps Network & internet
        recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = UiNode(text = "Network & internet", packageName = "com.android.settings", clickable = true),
            before = snap,
            after = snap
        )

        // 2. User taps Airplane Mode (with summary "Turn off mobile, Wi-Fi, and Bluetooth")
        val airplaneAction = recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = UiNode(
                text = "Airplane mode",
                contentDescription = "Turn off mobile, Wi-Fi, and Bluetooth",
                packageName = "com.android.settings",
                clickable = true
            ),
            before = snap,
            after = snap
        )
        assertFalse(airplaneAction.isFiltered)

        // 3. User taps Recents button to switch back to SaySo
        val recentsAction = recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = UiNode(
                text = "Recents",
                packageName = "com.android.systemui",
                className = "android.widget.ImageView",
                clickable = true
            ),
            before = snap,
            after = snap
        )

        // 4. User taps SaySo card in Recents
        val saysoCardAction = recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = UiNode(
                text = "Prism Teachable AutomationClear",
                packageName = "com.google.android.apps.nexuslauncher",
                className = "android.widget.FrameLayout",
                clickable = true
            ),
            before = snap,
            after = snap
        )

        // Verify recents and SaySo card were filtered or pruned
        val stopped = recorder.stopSession()
        // The trailing actions on Recents and SaySo must be pruned!
        assertEquals(2, stopped.retainedActions.size)
        assertEquals("Network & internet", stopped.retainedActions[0].targetNode.text)
        assertEquals("Airplane mode", stopped.retainedActions[1].targetNode.text)
    }

    @Test
    fun testCancelTeachingSession() {
        val session = recorder.startSession("Test Cancel Flow")
        assertTrue(recorder.isRecording)
        val cancelled = recorder.cancelSession()
        assertEquals(SessionStatus.CANCELLED, cancelled?.status)
        assertFalse(recorder.isRecording)
    }

    @Test
    fun testSourceLessEventFromLauncherOrSystemUiIsIgnored() {
        // Dropped for launcher/systemui/quickstep packages when hasSource is false
        assertTrue(recorder.shouldDropSourceLessEvent(hasSource = false, pkg = "com.google.android.apps.nexuslauncher"))
        assertTrue(recorder.shouldDropSourceLessEvent(hasSource = false, pkg = "com.android.systemui"))
        assertTrue(recorder.shouldDropSourceLessEvent(hasSource = false, pkg = "com.android.quickstep"))

        // Not dropped if source is present
        assertFalse(recorder.shouldDropSourceLessEvent(hasSource = true, pkg = "com.google.android.apps.nexuslauncher"))
        assertFalse(recorder.shouldDropSourceLessEvent(hasSource = true, pkg = "com.android.systemui"))

        // Not dropped for target app even if source is absent
        assertFalse(recorder.shouldDropSourceLessEvent(hasSource = false, pkg = "com.android.settings"))
    }

    @Test
    fun testFallbackTextJoinUsesSpaceSeparator() {
        // When source is null, text list ["Settings", "Clear all"] must be joined with space separator
        val extracted = recorder.extractNodeFromFallback(
            texts = listOf("Settings", "Clear all"),
            contentDescription = null,
            className = "android.widget.FrameLayout",
            packageName = "com.google.android.apps.nexuslauncher",
            isChecked = false
        )
        assertEquals("Settings Clear all", extracted.text)
        assertFalse("Must NOT join without spaces as 'SettingsClear'", extracted.text == "SettingsClear")
    }

    @Test
    fun testTrailingNoiseStackedPrunedOnStop() {
        val session = recorder.startSession("Toggle Airplane Mode", "com.android.settings")
        val snap = UiSnapshot(packageName = "com.android.settings")

        // 1. Real target app action: Network tab click
        recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = UiNode(text = "Network & internet", packageName = "com.android.settings", clickable = true),
            before = snap,
            after = snap
        )

        // 2. Real target app action: Airplane mode switch toggle
        recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = UiNode(
                text = "Airplane mode",
                packageName = "com.android.settings",
                clickable = true,
                isChecked = true,
                semanticRole = "switch"
            ),
            before = snap,
            after = snap
        )

        // 3. Stacked noise 1: SystemUI recents click
        recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = UiNode(text = "Recents", packageName = "com.android.systemui", clickable = true),
            before = snap,
            after = snap
        )

        // 4. Stacked noise 2: Launcher "Settings Clear all" click
        recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = UiNode(text = "Settings Clear all", packageName = "com.google.android.apps.nexuslauncher", clickable = true),
            before = snap,
            after = snap
        )

        // 5. Stacked noise 3: Blank package click
        val blankSnap = UiSnapshot(packageName = "")
        recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = UiNode(text = "Unknown", packageName = "", clickable = true),
            before = blankSnap,
            after = blankSnap
        )

        // 6. Stacked noise 4: SaySo stop/return card click
        recorder.recordAction(
            actionType = ActionType.CLICK,
            targetNode = UiNode(text = "Stop & Save", packageName = "com.samsung.prism.teachable", clickable = true),
            before = snap,
            after = snap
        )

        assertEquals(6, session.rawActions.size)

        // Pruning should loop from the tail, strip all 4 stacked noise actions, and retain the 2 real Settings actions
        val stopped = recorder.stopSession()
        assertEquals(2, stopped.retainedActions.size)
        assertEquals("Network & internet", stopped.retainedActions[0].targetNode.text)
        assertEquals("com.android.settings", stopped.retainedActions[0].packageName)
        assertEquals("Airplane mode", stopped.retainedActions[1].targetNode.text)
        assertEquals("com.android.settings", stopped.retainedActions[1].packageName)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testAfterSnapshotCapturedPostDelay() = runTest {
        val delayedRecorder = TeachingRecorder(
            filter = filter,
            scope = this,
            settleDelayMs = 350L
        )
        val session = delayedRecorder.startSession("Test Settle Delay", "com.android.settings")
        val snapBefore = UiSnapshot(packageName = "com.android.settings")

        val buttonNode = UiNode(
            text = "Airplane mode",
            packageName = "com.android.settings",
            clickable = true
        )
        delayedRecorder.enqueueDelayedRecording(
            actionType = ActionType.CLICK,
            targetNode = buttonNode,
            inputText = null,
            before = snapBefore
        )

        // Before delay advances, action has not executed
        assertEquals(0, session.rawActions.size)

        // Advance virtual time by 200ms - still not executed
        testScheduler.advanceTimeBy(200)
        assertEquals(0, session.rawActions.size)

        // Advance virtual time to complete the 350ms settle window
        testScheduler.advanceTimeBy(200)
        testScheduler.runCurrent()
        assertEquals(1, session.rawActions.size)
        assertEquals("Airplane mode", session.rawActions[0].targetNode.text)
    }

    @Test
    fun testToggleFallbackContentChangeFlippedCheckedState() {
        val session = recorder.startSession("Toggle Airplane Mode", "com.android.settings")
        val switchNode = UiNode(
            resourceId = "com.android.settings:id/switch_widget",
            text = "Airplane mode",
            className = "android.widget.Switch",
            semanticRole = "switch",
            packageName = "com.android.settings",
            clickable = true,
            isChecked = false,
            bounds = Bounds(800, 300, 950, 400)
        )
        val beforeSnap = UiSnapshot(
            packageName = "com.android.settings",
            rootNode = switchNode
        )

        // Content-change with flipped checked state (false -> true) records exactly one CLICK
        val handled = recorder.simulateContentChangedToggle(
            targetNode = switchNode,
            currentChecked = true,
            before = beforeSnap
        )
        assertTrue("Content change with flipped state should be handled", handled)
        recorder.flushPendingRecordings()
        assertEquals(1, session.rawActions.size)
        assertEquals(ActionType.CLICK, session.rawActions[0].actionType)
        assertTrue(session.rawActions[0].targetNode.isChecked)
    }

    @Test
    fun testToggleClickAndContentChangePairDoesNotDoubleRecord() {
        val session = recorder.startSession("Toggle Airplane Mode", "com.android.settings")
        val switchNode = UiNode(
            resourceId = "com.android.settings:id/switch_widget",
            text = "Airplane mode",
            className = "android.widget.Switch",
            semanticRole = "switch",
            packageName = "com.android.settings",
            clickable = true,
            isChecked = false,
            bounds = Bounds(800, 300, 950, 400)
        )
        val beforeSnap = UiSnapshot(
            packageName = "com.android.settings",
            rootNode = switchNode
        )

        // 1. User physically taps switch -> CLICK recorded
        val clickHandled = recorder.simulateClickEvent(switchNode, beforeSnap)
        assertTrue(clickHandled)

        // 2. Secondary content change arrives right after click (within 500ms) with flipped checked state
        val duplicateContentChange = recorder.simulateContentChangedToggle(
            targetNode = switchNode,
            currentChecked = true,
            before = beforeSnap
        )
        assertFalse("Duplicate toggle event within 500ms must be skipped", duplicateContentChange)

        recorder.flushPendingRecordings()
        // Exactly ONE CLICK action recorded for the physical tap
        assertEquals(1, session.rawActions.size)
        assertEquals(ActionType.CLICK, session.rawActions[0].actionType)
    }

    @Test
    fun testExtractNodeFromFallbackAssignsSwitchRole() {
        val fallbackNode = recorder.extractNodeFromFallback(
            texts = listOf("ON"),
            contentDescription = null,
            className = "android.widget.Switch",
            packageName = "com.android.settings",
            isChecked = true
        )
        assertEquals("switch", fallbackNode.semanticRole)
        assertTrue(fallbackNode.isChecked)
        assertEquals("ON", fallbackNode.text)
    }

    @Test
    fun testPruneTrailingReturningActionsRemovesLauncherAndRecents() {
        val session = recorder.startSession("Toggle Airplane Mode", "com.android.settings")
        val snap = UiSnapshot(packageName = "com.android.settings")

        // 1. Valid settings action: click Network
        val netNode = UiNode(text = "Network & internet", packageName = "com.android.settings", clickable = true)
        recorder.recordAction(ActionType.CLICK, netNode, null, snap, snap)

        // 2. Valid settings action: click Airplane mode
        val switchNode = UiNode(
            text = "Airplane mode",
            className = "android.widget.Switch",
            semanticRole = "switch",
            packageName = "com.android.settings",
            clickable = true,
            isChecked = true
        )
        recorder.recordAction(ActionType.CLICK, switchNode, null, snap, snap)

        // 3. User switches back via Recents (launcher / systemui)
        val recentsNode = UiNode(
            resourceId = "recent_apps",
            text = "Recents",
            className = "android.widget.ImageView",
            packageName = "com.google.android.apps.nexuslauncher",
            clickable = true
        )
        recorder.recordAction(ActionType.CLICK, recentsNode, null, snap, snap)

        // Stop session: trailing Recents action must be pruned
        val stopped = recorder.stopSession()
        assertEquals(2, stopped.retainedActions.size)
        assertEquals("Network & internet", stopped.retainedActions[0].targetNode.text)
        assertEquals("Airplane mode", stopped.retainedActions[1].targetNode.text)
    }

    @Test
    fun testTouchJitterScrollCancelledBySubsequentClick() {
        val session = recorder.startSession("Toggle Airplane Mode", "com.android.settings")
        val snap = UiSnapshot(packageName = "com.android.settings")

        // 1. Incidental scroll action enqueued or recorded right before click
        val scrollNode = UiNode(text = "Google", packageName = "com.android.settings", scrollable = true)
        recorder.recordAction(ActionType.SCROLL_FORWARD, scrollNode, null, snap, snap)

        // 2. Click immediately following scroll on target item
        val clickNode = UiNode(text = "Network & internet", packageName = "com.android.settings", clickable = true)
        recorder.simulateClickEvent(clickNode, snap)

        // The prior scroll action should be retroactively filtered as touch jitter
        assertTrue("Prior scroll must be filtered as touch jitter", session.rawActions[0].isFiltered)
        assertEquals("Incidental touch jitter before click", session.rawActions[0].filterReason)
    }
}
