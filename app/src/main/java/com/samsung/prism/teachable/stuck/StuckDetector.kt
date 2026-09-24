package com.samsung.prism.teachable.stuck

import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import com.samsung.prism.teachable.replay.RecoveryStage

class StuckDetector(
    val maxSameScreenCount: Int = 3
) {
    private var lastScreenSignature: String? = null
    private var sameScreenCount = 0

    fun reset() {
        lastScreenSignature = null
        sameScreenCount = 0
    }

    fun recordScreen(signature: String) {
        if (signature == lastScreenSignature) {
            sameScreenCount++
        } else {
            lastScreenSignature = signature
            sameScreenCount = 1
        }
    }

    fun isStuckDueToScreenLoop(): Boolean = sameScreenCount >= maxSameScreenCount

    fun buildStuckContext(
        workflowId: String,
        step: WorkflowStep,
        currentSnapshot: UiSnapshot,
        recoveryStage: RecoveryStage,
        failureReason: String
    ): StuckContext? {
        val shouldTrigger = recoveryStage == RecoveryStage.STAGE_5_ESCALATE || isStuckDueToScreenLoop()

        if (!shouldTrigger) {
            return null
        }

        // Collect visible interactive candidates on the current screen for alternate suggestions
        val interactiveCandidates = currentSnapshot.allNodes
            .filter { (it.clickable || it.semanticRole == "button" || !it.text.isNullOrBlank()) && !it.isPassword }
            .take(5)

        val reason = if (isStuckDueToScreenLoop()) {
            "Persisted on same screen ($sameScreenCount attempts without progress)"
        } else {
            failureReason
        }

        return StuckContext(
            workflowId = workflowId,
            step = step,
            currentSnapshot = currentSnapshot,
            failureReason = reason,
            visibleCandidates = interactiveCandidates
        )
    }
}
