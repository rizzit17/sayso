package com.samsung.prism.teachable.replay

import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot

enum class RecoveryStage {
    STAGE_1_RESNAPSHOT,
    STAGE_2_DISMISS_OVERLAY,
    STAGE_3_RELAX_THRESHOLD,
    STAGE_4_ALTERNATE_STATE,
    STAGE_5_ESCALATE
}

data class RecoveryAction(
    val stage: RecoveryStage,
    val description: String,
    val dismissNode: UiNode? = null,
    val relaxedThreshold: Double? = null,
    val needsBackPress: Boolean = false,
    val waitDelayMs: Long = 0
)

class RecoveryManager(
    val maxAttemptsPerStep: Int = 2
) {
    private var currentStageIndex = 0
    private var stepAttempts = 0

    private val stages = listOf(
        RecoveryStage.STAGE_1_RESNAPSHOT,
        RecoveryStage.STAGE_2_DISMISS_OVERLAY,
        RecoveryStage.STAGE_3_RELAX_THRESHOLD,
        RecoveryStage.STAGE_4_ALTERNATE_STATE,
        RecoveryStage.STAGE_5_ESCALATE
    )

    fun resetForNewStep() {
        currentStageIndex = 0
        stepAttempts = 0
    }

    fun nextRecoveryAction(currentSnapshot: UiSnapshot?): RecoveryAction {
        stepAttempts++

        if (stepAttempts > maxAttemptsPerStep && currentStageIndex < 4) {
            // Escalate immediately if max attempts exceeded
            currentStageIndex = 4 // STAGE_5_ESCALATE
        }

        val stage = stages.getOrElse(currentStageIndex) { RecoveryStage.STAGE_5_ESCALATE }

        val action = when (stage) {
            RecoveryStage.STAGE_1_RESNAPSHOT -> {
                RecoveryAction(
                    stage = stage,
                    description = "Waiting 500ms for UI animations and re-capturing snapshot",
                    waitDelayMs = 500L
                )
            }
            RecoveryStage.STAGE_2_DISMISS_OVERLAY -> {
                val dismissButton = findOverlayDismissButton(currentSnapshot)
                if (dismissButton != null) {
                    RecoveryAction(
                        stage = stage,
                        description = "Attempting to dismiss detected overlay/popup via '${dismissButton.text ?: dismissButton.contentDescription ?: "X"}'",
                        dismissNode = dismissButton
                    )
                } else {
                    RecoveryAction(
                        stage = stage,
                        description = "Attempting back navigation to dismiss potential overlay dialog",
                        needsBackPress = true
                    )
                }
            }
            RecoveryStage.STAGE_3_RELAX_THRESHOLD -> {
                RecoveryAction(
                    stage = stage,
                    description = "Relaxing semantic matching threshold from 0.70 to 0.55",
                    relaxedThreshold = 0.55
                )
            }
            RecoveryStage.STAGE_4_ALTERNATE_STATE -> {
                RecoveryAction(
                    stage = stage,
                    description = "Triggering back navigation to re-anchor into previous screen state",
                    needsBackPress = true
                )
            }
            RecoveryStage.STAGE_5_ESCALATE -> {
                RecoveryAction(
                    stage = stage,
                    description = "Recovery exhausted; escalating to user clarification dialog"
                )
            }
        }

        // Advance to next stage for subsequent call
        if (currentStageIndex < stages.size - 1) {
            currentStageIndex++
        }

        return action
    }

    private fun findOverlayDismissButton(snapshot: UiSnapshot?): UiNode? {
        if (snapshot == null) return null
        val dismissKeywords = listOf(
            "close", "dismiss", "cancel", "not now", "skip", "later", "no thanks", "got it", "maybe later", "x"
        )

        val nodes = snapshot.allNodes
        for (node in nodes) {
            if (!node.clickable && node.semanticRole != "button" && node.semanticRole != "icon_button") continue

            val text = (node.text ?: "").trim().lowercase()
            val desc = (node.contentDescription ?: "").trim().lowercase()
            val resId = (node.resourceId ?: "").lowercase()

            if (dismissKeywords.any { it == text || it == desc || resId.endsWith(it) }) {
                return node
            }
        }
        return null
    }
}
