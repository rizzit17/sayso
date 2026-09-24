package com.samsung.prism.teachable.voice

import com.samsung.prism.teachable.model.Workflow

enum class MatchType {
    EXACT,
    PARAPHRASE,
    GENERALIZED
}

sealed class IntentMatchResult {
    data class Matched(
        val workflow: Workflow,
        val confidence: Double,
        val matchType: MatchType
    ) : IntentMatchResult()

    data class Ambiguous(
        val candidates: List<Pair<Workflow, Double>>
    ) : IntentMatchResult()

    data object Unknown : IntentMatchResult()
}
