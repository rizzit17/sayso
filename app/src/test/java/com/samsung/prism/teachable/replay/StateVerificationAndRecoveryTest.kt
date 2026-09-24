package com.samsung.prism.teachable.replay

import com.samsung.prism.teachable.model.ExpectedStateTransition
import com.samsung.prism.teachable.observation.Bounds
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateVerificationAndRecoveryTest {

    private val verifier = StateVerifier()

    private fun createSnapshot(
        pkg: String = "com.application.zomato",
        sig: String = "sig_1",
        nodes: List<UiNode> = emptyList()
    ): UiSnapshot {
        val root = UiNode(
            resourceId = "root",
            className = "android.widget.FrameLayout",
            packageName = pkg,
            bounds = Bounds(0, 0, 1080, 2400),
            children = nodes
        )
        return UiSnapshot(
            packageName = pkg,
            activityName = "MainActivity",
            rootNode = root,
            screenSignature = sig
        )
    }

    @Test
    fun testVerificationPasses() {
        val transition = ExpectedStateTransition(
            expectedTextSubstring = "Cart",
            expectedPackageName = "com.application.zomato",
            expectedSignatureChange = true
        )

        val before = createSnapshot(sig = "sig_menu")
        val cartNode = UiNode(
            resourceId = "com.zomato:id/cart_view",
            text = "View Cart (1 item)",
            semanticRole = "button"
        )
        val after = createSnapshot(sig = "sig_cart_updated", nodes = listOf(cartNode))

        val result = verifier.verify(transition, before, after)
        assertTrue(result.verified)
    }

    @Test
    fun testVerificationFailsOnNoSignatureChange() {
        val transition = ExpectedStateTransition(
            expectedSignatureChange = true
        )

        val before = createSnapshot(sig = "sig_same")
        val after = createSnapshot(sig = "sig_same")

        val result = verifier.verify(transition, before, after)
        assertFalse(result.verified)
        assertTrue(result.failureReason!!.contains("Screen state did not change"))
    }

    @Test
    fun testVerificationFailsOnMissingText() {
        val transition = ExpectedStateTransition(
            expectedTextSubstring = "Order Placed",
            expectedSignatureChange = true
        )

        val before = createSnapshot(sig = "sig_cart")
        val node = UiNode(text = "Review Order")
        val after = createSnapshot(sig = "sig_review", nodes = listOf(node))

        val result = verifier.verify(transition, before, after)
        assertFalse(result.verified)
        assertTrue(result.failureReason!!.contains("Expected text substring"))
    }

    @Test
    fun testVerificationFailsOnPackageMismatch() {
        val transition = ExpectedStateTransition(
            expectedPackageName = "com.application.zomato",
            expectedSignatureChange = false
        )

        val before = createSnapshot(pkg = "com.application.zomato")
        val after = createSnapshot(pkg = "com.android.settings")

        val result = verifier.verify(transition, before, after)
        assertFalse(result.verified)
        assertTrue(result.failureReason!!.contains("Package mismatch"))
    }

    @Test
    fun testRecoveryManagerStrategyProgression() {
        // Allow up to 5 attempts to test all 5 stages in order
        val manager = RecoveryManager(maxAttemptsPerStep = 5)

        // Stage 1: Resnapshot
        val a1 = manager.nextRecoveryAction(null)
        assertEquals(RecoveryStage.STAGE_1_RESNAPSHOT, a1.stage)
        assertEquals(500L, a1.waitDelayMs)

        // Stage 2: Dismiss Overlay
        val closeBtn = UiNode(
            resourceId = "btn_close",
            text = "Dismiss",
            clickable = true
        )
        val snapshotWithPopup = createSnapshot(nodes = listOf(closeBtn))
        val a2 = manager.nextRecoveryAction(snapshotWithPopup)
        assertEquals(RecoveryStage.STAGE_2_DISMISS_OVERLAY, a2.stage)
        assertNotNull(a2.dismissNode)
        assertEquals(closeBtn, a2.dismissNode)

        // Stage 3: Relax Threshold
        val a3 = manager.nextRecoveryAction(null)
        assertEquals(RecoveryStage.STAGE_3_RELAX_THRESHOLD, a3.stage)
        assertEquals(0.55, a3.relaxedThreshold!!, 0.001)

        // Stage 4: Alternate State / Back navigation
        val a4 = manager.nextRecoveryAction(null)
        assertEquals(RecoveryStage.STAGE_4_ALTERNATE_STATE, a4.stage)
        assertTrue(a4.needsBackPress)

        // Stage 5: Escalate
        val a5 = manager.nextRecoveryAction(null)
        assertEquals(RecoveryStage.STAGE_5_ESCALATE, a5.stage)
    }

    @Test
    fun testRecoveryManagerMaxAttemptsEscalates() {
        val manager = RecoveryManager(maxAttemptsPerStep = 2)

        val a1 = manager.nextRecoveryAction(null)
        assertEquals(RecoveryStage.STAGE_1_RESNAPSHOT, a1.stage)

        val a2 = manager.nextRecoveryAction(null)
        assertEquals(RecoveryStage.STAGE_2_DISMISS_OVERLAY, a2.stage)

        // Attempt 3 exceeds maxAttemptsPerStep (2), should jump straight to Stage 5
        val a3 = manager.nextRecoveryAction(null)
        assertEquals(RecoveryStage.STAGE_5_ESCALATE, a3.stage)
    }

    @Test
    fun testRecoveryManagerResetForNewStep() {
        val manager = RecoveryManager(maxAttemptsPerStep = 2)

        manager.nextRecoveryAction(null)
        manager.nextRecoveryAction(null)

        manager.resetForNewStep()

        val freshAction = manager.nextRecoveryAction(null)
        assertEquals(RecoveryStage.STAGE_1_RESNAPSHOT, freshAction.stage)
    }
}
