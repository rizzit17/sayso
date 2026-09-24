package com.samsung.prism.teachable.replay

import com.samsung.prism.teachable.observation.UiNode

data class MatchCandidate(
    val node: UiNode,
    val score: Double,
    val signals: Map<String, Double> = emptyMap()
) : Comparable<MatchCandidate> {
    override fun compareTo(other: MatchCandidate): Int {
        // Tie-breaking: text score first, then overall score
        val textScoreA = signals["w2_text"] ?: 0.0
        val textScoreB = other.signals["w2_text"] ?: 0.0
        val textComp = textScoreA.compareTo(textScoreB)
        return if (textComp != 0) {
            textComp
        } else {
            score.compareTo(other.score)
        }
    }
}
