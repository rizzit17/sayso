package com.samsung.prism.teachable.replay

import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.security.CredentialBoundaryDetector

class FakeActionExecutor(
    override val minInterActionDelayMs: Long = 10L,
    private val detector: CredentialBoundaryDetector = CredentialBoundaryDetector()
) : IActionExecutor {

    val executedClicks = mutableListOf<UiNode>()
    val executedTexts = mutableListOf<Pair<UiNode, String>>()
    val executedScrolls = mutableListOf<Pair<Boolean, UiNode?>>()

    override suspend fun executeClick(target: UiNode): Boolean {
        if (detector.isNodeDangerous(target)) {
            throw SecurityException("Security boundary triggered on target node: [${target.semanticRole}] '${target.text ?: target.contentDescription}'! Execution blocked.")
        }
        executedClicks.add(target)
        return true
    }

    override suspend fun executeSetText(target: UiNode, text: String): Boolean {
        if (detector.isNodeDangerous(target)) {
            throw SecurityException("Security boundary triggered on target node: [${target.semanticRole}] '${target.text ?: target.contentDescription}'! Execution blocked.")
        }
        executedTexts.add(Pair(target, text))
        return true
    }

    override suspend fun executeScroll(forward: Boolean, target: UiNode?): Boolean {
        executedScrolls.add(Pair(forward, target))
        return true
    }

    override suspend fun executeGestureClick(x: Float, y: Float): Boolean {
        return true
    }
}
