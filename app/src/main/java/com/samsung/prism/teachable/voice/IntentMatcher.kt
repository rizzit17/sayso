package com.samsung.prism.teachable.voice

import com.samsung.prism.teachable.model.Workflow
import kotlin.math.max
import kotlin.math.min

class IntentMatcher(
    val confidenceHigh: Double = 0.80,
    val confidenceLow: Double = 0.45,
    val ambiguityDelta: Double = 0.08
) {
    private val stopWords = setOf(
        "a", "an", "the", "and", "or", "to", "from", "on", "in", "at",
        "for", "by", "with", "please", "can", "you", "me", "my", "app",
        "get", "want", "would", "like"
    )

    fun match(utterance: String, workflows: List<Workflow>): IntentMatchResult {
        val trimmed = utterance.trim()
        if (trimmed.isEmpty() || workflows.isEmpty()) {
            return IntentMatchResult.Unknown
        }

        val normalizedInput = normalize(trimmed)
        val inputTokens = tokenize(normalizedInput)

        val scored = workflows.map { workflow ->
            val score = computeSimilarity(normalizedInput, inputTokens, workflow)
            Pair(workflow, score)
        }.sortedByDescending { it.second }

        if (scored.isEmpty()) {
            return IntentMatchResult.Unknown
        }

        val top = scored[0]
        val topScore = top.second

        // Rejection if below low threshold
        if (topScore < confidenceLow) {
            return IntentMatchResult.Unknown
        }

        // Check for ambiguity if there's a runner-up
        if (scored.size > 1) {
            val runnerUp = scored[1]
            if (runnerUp.second >= confidenceLow && (topScore - runnerUp.second) <= ambiguityDelta) {
                return IntentMatchResult.Ambiguous(scored.take(3))
            }
        }

        val matchType = when {
            topScore >= 0.95 -> MatchType.EXACT
            topScore >= confidenceHigh -> MatchType.PARAPHRASE
            else -> MatchType.GENERALIZED
        }

        return IntentMatchResult.Matched(
            workflow = top.first,
            confidence = topScore,
            matchType = matchType
        )
    }

    private fun computeSimilarity(
        normalizedInput: String,
        inputTokens: Set<String>,
        workflow: Workflow
    ): Double {
        val normalizedOriginal = normalize(workflow.originalUtterance)
        // 1. Direct exact or substring match
        if (normalizedInput == normalizedOriginal) {
            return 1.0
        }

        // 2. Token Jaccard similarity against original utterance
        val originalTokens = tokenize(normalizedOriginal)
        val jaccardOriginal = jaccard(inputTokens, originalTokens)

        // 3. Token Jaccard similarity against generalized intent (e.g. "Order {item} from {restaurant} on {platform}")
        val generalizedNoSlots = workflow.generalizedIntent
            .replace(Regex("\\{[a-zA-Z0-9_]+\\}"), " ")
        val generalizedTokens = tokenize(normalize(generalizedNoSlots))
        val jaccardGeneralized = if (generalizedTokens.isNotEmpty()) {
            jaccard(inputTokens, generalizedTokens)
        } else {
            0.0
        }

        // 4. Normalized Levenshtein similarity against original utterance
        val levSim = levenshteinSimilarity(normalizedInput, normalizedOriginal)

        // 5. Check slot keyword coverage
        var slotCoverageBonus = 0.0
        val slots = workflow.slotSchema.slots
        if (slots.isNotEmpty()) {
            var matchedSlots = 0
            for (slot in slots) {
                val defVal = slot.defaultValue?.let { normalize(it) } ?: ""
                if (defVal.isNotBlank()) {
                    val defTokens = tokenize(defVal)
                    if (defTokens.any { inputTokens.contains(it) } || normalizedInput.contains(defVal)) {
                        matchedSlots++
                    }
                }
            }
            slotCoverageBonus = (matchedSlots.toDouble() / slots.size.toDouble()) * 0.15
        }

        // Weighted combination
        val tokenMax = max(jaccardOriginal, jaccardGeneralized)
        val composite = (0.50 * tokenMax) + (0.35 * levSim) + slotCoverageBonus

        return min(1.0, composite)
    }

    fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun tokenize(text: String): Set<String> {
        return text.split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() && !stopWords.contains(it) }
            .toSet()
    }

    private fun jaccard(s1: Set<String>, s2: Set<String>): Double {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val intersection = s1.intersect(s2).size
        val union = s1.union(s2).size
        return intersection.toDouble() / union.toDouble()
    }

    private fun levenshteinSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty()) return 0.0
        if (s2.isEmpty()) return 0.0

        val dist = levenshteinDistance(s1, s2)
        val maxLen = max(s1.length, s2.length)
        return 1.0 - (dist.toDouble() / maxLen.toDouble())
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) {
            dp[i][0] = i
        }
        for (j in 0..s2.length) {
            dp[0][j] = j
        }

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[s1.length][s2.length]
    }
}
