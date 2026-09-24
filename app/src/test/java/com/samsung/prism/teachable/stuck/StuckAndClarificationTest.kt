package com.samsung.prism.teachable.stuck

import com.samsung.prism.teachable.model.StepTarget
import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.observation.Bounds
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import com.samsung.prism.teachable.replay.RecoveryStage
import com.samsung.prism.teachable.teaching.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StuckAndClarificationTest {

    private val detector = StuckDetector(maxSameScreenCount = 3)
    private val generator = ClarificationGenerator()
    private val handler = ClarificationHandler()

    private fun createDummySnapshot(nodes: List<UiNode> = emptyList(), sig: String = "sig_screen"): UiSnapshot {
        val root = UiNode(
            resourceId = "root",
            className = "android.widget.FrameLayout",
            bounds = Bounds(0, 0, 1080, 2400),
            children = nodes
        )
        return UiSnapshot(
            packageName = "com.amazon",
            activityName = "ProductDetailActivity",
            rootNode = root,
            screenSignature = sig
        )
    }

    private val dummyStep = WorkflowStep(
        id = "s_cart",
        workflowId = "wf_1",
        stepOrder = 2,
        actionType = ActionType.CLICK,
        target = StepTarget(
            text = "Add to Cart",
            semanticRole = "button"
        )
    )

    @Test
    fun testStuckDetectionOnStage5Escalate() {
        val snapshot = createDummySnapshot()
        val context = detector.buildStuckContext(
            workflowId = "wf_1",
            step = dummyStep,
            currentSnapshot = snapshot,
            recoveryStage = RecoveryStage.STAGE_5_ESCALATE,
            failureReason = "Element not found after all strategies"
        )

        assertNotNull("Should build stuck context on Stage 5", context)
        assertEquals("wf_1", context!!.workflowId)
        assertEquals("Element not found after all strategies", context.failureReason)
    }

    @Test
    fun testStuckDetectionOnScreenLoop() {
        detector.recordScreen("sig_stuck")
        detector.recordScreen("sig_stuck")
        detector.recordScreen("sig_stuck") // 3rd consecutive attempt

        assertTrue(detector.isStuckDueToScreenLoop())

        val snapshot = createDummySnapshot(sig = "sig_stuck")
        val context = detector.buildStuckContext(
            workflowId = "wf_1",
            step = dummyStep,
            currentSnapshot = snapshot,
            recoveryStage = RecoveryStage.STAGE_1_RESNAPSHOT,
            failureReason = "Loop detected"
        )

        assertNotNull("Should detect stuck due to screen loop", context)
        assertTrue(context!!.failureReason.contains("Persisted on same screen"))
    }

    @Test
    fun testStuckDetectionDoesNotTriggerPrematurely() {
        val snapshot = createDummySnapshot()
        val context = detector.buildStuckContext(
            workflowId = "wf_1",
            step = dummyStep,
            currentSnapshot = snapshot,
            recoveryStage = RecoveryStage.STAGE_2_DISMISS_OVERLAY,
            failureReason = "Temporary overlay"
        )

        assertNull("Should not trigger stuck prematurely before escalation or loop", context)
    }

    @Test
    fun testClarificationQuestionGeneration() {
        val notifyMeNode = UiNode(
            resourceId = "btn_notify",
            text = "Notify Me",
            semanticRole = "button",
            clickable = true
        )
        val snapshot = createDummySnapshot(nodes = listOf(notifyMeNode))

        val context = StuckContext(
            workflowId = "wf_1",
            step = dummyStep,
            currentSnapshot = snapshot,
            failureReason = "Target 'Add to Cart' not found",
            visibleCandidates = listOf(notifyMeNode)
        )

        val question = generator.generateQuestion(context)

        assertTrue(question.questionText.contains("Add to Cart"))
        assertTrue(question.questionText.contains("Notify Me"))
        assertTrue(question.ttsPrompt.contains("Add to Cart"))

        assertEquals(3, question.options.size) // Option 1: Tap 'Notify Me', Option 2: Skip, Option 3: Abort
        assertEquals(ClarificationActionType.TAP_ALTERNATIVE, question.options[0].actionType)
        assertEquals(ClarificationActionType.SKIP_STEP, question.options[1].actionType)
        assertEquals(ClarificationActionType.ABORT, question.options[2].actionType)
    }

    @Test
    fun testClarificationHandlerTapsAlternative() {
        val node = UiNode(text = "Notify Me", clickable = true)
        val option = ClarificationOption(
            id = "alt_0",
            label = "Tap 'Notify Me'",
            targetNode = node,
            actionType = ClarificationActionType.TAP_ALTERNATIVE
        )

        val result = handler.handleOptionSelection(option)
        assertTrue(result is ClarificationResult.ResumeWithNode)
        assertEquals(node, (result as ClarificationResult.ResumeWithNode).node)
    }

    @Test
    fun testClarificationHandlerSkipAndAbort() {
        val skipOpt = ClarificationOption(id = "skip", label = "Skip", actionType = ClarificationActionType.SKIP_STEP)
        val abortOpt = ClarificationOption(id = "abort", label = "Abort", actionType = ClarificationActionType.ABORT)

        assertEquals(ClarificationResult.SkipStep, handler.handleOptionSelection(skipOpt))
        assertEquals(ClarificationResult.AbortWorkflow, handler.handleOptionSelection(abortOpt))
    }

    @Test
    fun testClarificationHandlerVoiceResponses() {
        val notifyNode = UiNode(text = "Notify Me")
        val question = ClarificationQuestion(
            questionText = "What to do?",
            ttsPrompt = "What to do?",
            options = listOf(
                ClarificationOption("1", "Tap 'Notify Me'", notifyNode, ClarificationActionType.TAP_ALTERNATIVE),
                ClarificationOption("2", "Skip", actionType = ClarificationActionType.SKIP_STEP),
                ClarificationOption("3", "Abort", actionType = ClarificationActionType.ABORT)
            )
        )

        val skipVoice = handler.handleVoiceResponse("Please skip this step", question)
        assertEquals(ClarificationResult.SkipStep, skipVoice)

        val abortVoice = handler.handleVoiceResponse("Cancel the order and abort", question)
        assertEquals(ClarificationResult.AbortWorkflow, abortVoice)

        val altVoice = handler.handleVoiceResponse("Tap on Notify Me please", question)
        assertTrue(altVoice is ClarificationResult.ResumeWithNode)
        assertEquals(notifyNode, (altVoice as ClarificationResult.ResumeWithNode).node)
    }
}
