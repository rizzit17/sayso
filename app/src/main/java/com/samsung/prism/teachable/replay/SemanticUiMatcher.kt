package com.samsung.prism.teachable.replay

import com.samsung.prism.teachable.model.StepTarget
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class SemanticUiMatcher(
    var matchThreshold: Double = 0.70
) {
    // 8-signal hierarchy weights per systemdesign.md §7.2
    val w1 = 0.30 // exact resource-id
    val w2 = 0.25 // semantic text / content-description
    val w3 = 0.15 // semantic role
    val w4 = 0.10 // screen signature context
    val w5 = 0.08 // parent context / class hierarchy
    val w6 = 0.05 // sibling / interactive context
    val w7 = 0.05 // scroll container position
    val w8 = 0.02 // normalized center distance

    fun findBestMatch(
        target: StepTarget,
        snapshot: UiSnapshot,
        threshold: Double = matchThreshold
    ): MatchCandidate? {
        val allNodes = snapshot.allNodes
        if (allNodes.isEmpty()) return null

        val candidates = allNodes.map { node ->
            scoreNode(target, node, snapshot.screenSignature)
        }.sortedDescending() // Uses MatchCandidate compareTo with tie-breaking

        val best = candidates.firstOrNull() ?: return null
        return if (best.score >= threshold) best else null
    }

    fun scoreNode(
        target: StepTarget,
        candidate: UiNode,
        currentScreenSig: String? = null
    ): MatchCandidate {
        val signals = mutableMapOf<String, Double>()

        // w1: Resource ID (0.30)
        val s1 = computeResourceIdScore(target.resourceId, candidate.resourceId)
        signals["w1_resource_id"] = s1 * w1

        // w2: Semantic text / content-description (0.25)
        val s2 = computeTextScore(target, candidate)
        signals["w2_text"] = s2 * w2

        // w3: Semantic role (0.15)
        val s3 = computeRoleScore(target.semanticRole, candidate.semanticRole)
        signals["w3_role"] = s3 * w3

        // w4: Screen signature context (0.10)
        val s4 = computeScreenSigScore(target.screenSignature, currentScreenSig)
        signals["w4_screen_sig"] = s4 * w4

        // w5: Parent context / class hierarchy (0.08)
        val s5 = computeHierarchyScore(target, candidate)
        signals["w5_hierarchy"] = s5 * w5

        // w6: Sibling / interactive context (0.05)
        val s6 = computeInteractiveScore(target, candidate)
        signals["w6_context"] = s6 * w6

        // w7: Scroll container relative position (0.05)
        val s7 = computeScrollScore(target, candidate)
        signals["w7_scroll"] = s7 * w7

        // w8: Normalized center distance (0.02)
        val s8 = computeCenterDistanceScore(target, candidate)
        signals["w8_coordinates"] = s8 * w8

        val totalScore = signals.values.sum()

        return MatchCandidate(
            node = candidate,
            score = min(1.0, totalScore),
            signals = signals
        )
    }

    private fun computeResourceIdScore(targetId: String?, candId: String?): Double {
        if (targetId == null && candId == null) return 1.0
        if (targetId == null) return 0.8
        if (candId == null) return 0.25
        if (targetId == candId) return 1.0

        val targetSuffix = targetId.substringAfterLast(":id/")
        val candSuffix = candId.substringAfterLast(":id/")
        if (targetSuffix.isNotEmpty() && targetSuffix == candSuffix) {
            return 0.75
        }
        return 0.0
    }

    private fun computeTextScore(target: StepTarget, cand: UiNode): Double {
        val targetText = target.text?.trim()?.lowercase()
        val targetDesc = target.contentDescription?.trim()?.lowercase()

        val candText = cand.text?.trim()?.lowercase()
        val candDesc = cand.contentDescription?.trim()?.lowercase()

        val normTargetText = targetText?.replace(Regex("[^a-z0-9\\s]"), "")?.replace(Regex("\\s+"), " ")?.trim()
        val normTargetDesc = targetDesc?.replace(Regex("[^a-z0-9\\s]"), "")?.replace(Regex("\\s+"), " ")?.trim()

        val normCandText = candText?.replace(Regex("[^a-z0-9\\s]"), "")?.replace(Regex("\\s+"), " ")?.trim()
        val normCandDesc = candDesc?.replace(Regex("[^a-z0-9\\s]"), "")?.replace(Regex("\\s+"), " ")?.trim()

        var best = 0.0

        // 1. Text vs Text
        if (!normTargetText.isNullOrEmpty() && !normCandText.isNullOrEmpty()) {
            if (normTargetText == normCandText) return 1.0
            if (normCandText.contains(normTargetText) || normTargetText.contains(normCandText)) {
                best = max(best, 0.90)
            } else {
                best = max(best, stringSimilarity(normTargetText, normCandText))
            }
        }

        // 2. Text vs ContentDesc
        if (!normTargetText.isNullOrEmpty() && !normCandDesc.isNullOrEmpty()) {
            if (normTargetText == normCandDesc) return 1.0
            if (normCandDesc.contains(normTargetText) || normTargetText.contains(normCandDesc)) {
                best = max(best, 0.90)
            } else {
                best = max(best, stringSimilarity(normTargetText, normCandDesc))
            }
        }

        // 3. ContentDesc vs ContentDesc
        if (!normTargetDesc.isNullOrEmpty() && !normCandDesc.isNullOrEmpty()) {
            if (normTargetDesc == normCandDesc) return 1.0
            if (normCandDesc.contains(normTargetDesc) || normTargetDesc.contains(normCandDesc)) {
                best = max(best, 0.90)
            } else {
                best = max(best, stringSimilarity(normTargetDesc, normCandDesc))
            }
        }

        // 4. ContentDesc vs Text
        if (!normTargetDesc.isNullOrEmpty() && !normCandText.isNullOrEmpty()) {
            if (normTargetDesc == normCandText) return 1.0
            if (normCandText.contains(normTargetDesc) || normTargetDesc.contains(normCandText)) {
                best = max(best, 0.90)
            } else {
                best = max(best, stringSimilarity(normTargetDesc, normCandText))
            }
        }

        return best
    }

    private fun computeRoleScore(targetRole: String?, candRole: String?): Double {
        if (targetRole == null && candRole == null) return 0.8
        if (targetRole == null) return 0.8
        if (targetRole == candRole) return 1.0
        return 0.0
    }

    private fun computeScreenSigScore(targetSig: String?, currentSig: String?): Double {
        if (targetSig == null) return 0.8
        return if (targetSig == currentSig) 1.0 else 0.2
    }

    private fun computeHierarchyScore(target: StepTarget, cand: UiNode): Double {
        if (target.className == null && target.parentContext == null) return 0.8
        var score = 0.0
        if (target.className != null && target.className == cand.className) {
            score += 0.6
        }
        if (target.parentContext != null && target.parentContext == cand.parentContext) {
            score += 0.4
        }
        return score
    }

    private fun computeInteractiveScore(target: StepTarget, cand: UiNode): Double {
        // If candidate is interactive when target was a button/input
        if (cand.clickable || cand.semanticRole == "button" || cand.semanticRole == "search_box") {
            return 1.0
        }
        return 0.5
    }

    private fun computeScrollScore(target: StepTarget, cand: UiNode): Double {
        if (cand.scrollable || cand.parentContext?.contains("RecyclerView") == true || cand.parentContext?.contains("ScrollView") == true) {
            return 1.0
        }
        return 0.5
    }

    private fun computeCenterDistanceScore(target: StepTarget, cand: UiNode): Double {
        val tx = target.boundsRelativeX ?: return 0.5
        val ty = target.boundsRelativeY ?: return 0.5

        val (cx, cy) = cand.bounds.normalizedCenter(1080, 2400)

        val dx = (tx - cx).toDouble()
        val dy = (ty - cy).toDouble()
        val dist = sqrt(dx * dx + dy * dy)
        // Normalize by max diagonal sqrt(2) ≈ 1.414
        val maxDist = sqrt(2.0)
        return max(0.0, 1.0 - (dist / maxDist))
    }

    private fun stringSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val dist = levenshtein(s1, s2)
        return max(0.0, 1.0 - (dist.toDouble() / maxLen.toDouble()))
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }
}
