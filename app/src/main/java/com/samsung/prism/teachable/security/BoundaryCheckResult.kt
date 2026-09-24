package com.samsung.prism.teachable.security

data class BoundaryCheckResult(
    val isBoundary: Boolean,
    val layerTriggered: Int = 0,
    val reason: String = "Safe",
    val detectedKeywords: List<String> = emptyList()
) {
    companion object {
        val SAFE = BoundaryCheckResult(isBoundary = false)
    }
}
