package com.samsung.prism.teachable.stuck

import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot

data class StuckContext(
    val workflowId: String,
    val step: WorkflowStep,
    val currentSnapshot: UiSnapshot,
    val failureReason: String,
    val visibleCandidates: List<UiNode> = emptyList()
)

data class ClarificationOption(
    val id: String,
    val label: String,
    val targetNode: UiNode? = null,
    val actionType: ClarificationActionType
)

enum class ClarificationActionType {
    TAP_ALTERNATIVE,
    SKIP_STEP,
    ABORT
}

data class ClarificationQuestion(
    val questionText: String,
    val ttsPrompt: String,
    val options: List<ClarificationOption>
)

sealed class ClarificationResult {
    data class ResumeWithNode(val node: UiNode) : ClarificationResult()
    data object SkipStep : ClarificationResult()
    data object AbortWorkflow : ClarificationResult()
}
