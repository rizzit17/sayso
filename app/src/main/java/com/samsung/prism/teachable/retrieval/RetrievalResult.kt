package com.samsung.prism.teachable.retrieval

import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.voice.MatchType

data class WorkflowCandidate(
    val workflow: Workflow,
    val confidence: Double
)

sealed class RetrievalResult {
    data class Selected(
        val workflow: Workflow,
        val confidence: Double,
        val matchType: MatchType
    ) : RetrievalResult()

    data class Ambiguous(
        val candidates: List<WorkflowCandidate>
    ) : RetrievalResult()

    data object Unknown : RetrievalResult()

    data object NoActiveWorkflows : RetrievalResult()
}
