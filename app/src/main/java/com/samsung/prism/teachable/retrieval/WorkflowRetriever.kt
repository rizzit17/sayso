package com.samsung.prism.teachable.retrieval

import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.storage.IWorkflowRepository
import com.samsung.prism.teachable.voice.IntentMatchResult
import com.samsung.prism.teachable.voice.IntentMatcher
import com.samsung.prism.teachable.voice.MatchType
import kotlin.math.max
import kotlin.math.sqrt

class WorkflowRetriever(
    private val repository: IWorkflowRepository,
    val intentMatcher: IntentMatcher = IntentMatcher(
        confidenceHigh = 0.80,
        confidenceLow = 0.45,
        ambiguityDelta = 0.10
    )
) {

    suspend fun retrieve(
        utterance: String,
        queryEmbedding: FloatArray? = null
    ): RetrievalResult {
        val trimmed = utterance.trim()
        if (trimmed.isEmpty()) return RetrievalResult.Unknown

        val activeWorkflows = repository.findActive()
        if (activeWorkflows.isEmpty()) {
            return RetrievalResult.NoActiveWorkflows
        }

        // If query embedding and stored workflow embeddings are available, combine embedding similarity
        if (queryEmbedding != null && activeWorkflows.any { it.intentEmbedding != null }) {
            return retrieveWithEmbeddings(trimmed, queryEmbedding, activeWorkflows)
        }

        // Offline / fallback token and edit-distance matching
        return when (val match = intentMatcher.match(trimmed, activeWorkflows)) {
            is IntentMatchResult.Matched -> {
                RetrievalResult.Selected(
                    workflow = match.workflow,
                    confidence = match.confidence,
                    matchType = match.matchType
                )
            }
            is IntentMatchResult.Ambiguous -> {
                val candidates = match.candidates.map {
                    WorkflowCandidate(workflow = it.first, confidence = it.second)
                }
                RetrievalResult.Ambiguous(candidates)
            }
            is IntentMatchResult.Unknown -> {
                RetrievalResult.Unknown
            }
        }
    }

    private fun retrieveWithEmbeddings(
        utterance: String,
        queryEmbedding: FloatArray,
        activeWorkflows: List<Workflow>
    ): RetrievalResult {
        val scored = activeWorkflows.map { wf ->
            val tokenMatch = intentMatcher.match(utterance, listOf(wf))
            val tokenScore = if (tokenMatch is IntentMatchResult.Matched) tokenMatch.confidence else 0.0

            val embScore = wf.intentEmbedding?.let { cosineSimilarity(queryEmbedding, it) } ?: tokenScore
            // Hybrid score: 60% embedding, 40% symbolic
            val combined = (0.60 * embScore) + (0.40 * tokenScore)
            Pair(wf, combined)
        }.sortedByDescending { it.second }

        if (scored.isEmpty() || scored[0].second < intentMatcher.confidenceLow) {
            return RetrievalResult.Unknown
        }

        val top = scored[0]
        if (scored.size > 1) {
            val runnerUp = scored[1]
            if (runnerUp.second >= intentMatcher.confidenceLow && (top.second - runnerUp.second) <= intentMatcher.ambiguityDelta) {
                return RetrievalResult.Ambiguous(
                    scored.take(3).map { WorkflowCandidate(it.first, it.second) }
                )
            }
        }

        val matchType = when {
            top.second >= 0.95 -> MatchType.EXACT
            top.second >= intentMatcher.confidenceHigh -> MatchType.PARAPHRASE
            else -> MatchType.GENERALIZED
        }

        return RetrievalResult.Selected(
            workflow = top.first,
            confidence = top.second,
            matchType = matchType
        )
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Double {
        if (v1.size != v2.size || v1.isEmpty()) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0) dot / denom else 0.0
    }
}
