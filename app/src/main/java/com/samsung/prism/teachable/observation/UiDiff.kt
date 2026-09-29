package com.samsung.prism.teachable.observation

data class UiDiff(
    val packageChanged: Boolean,
    val newlyAppearedNodes: List<UiNode>,
    val disappearedNodes: List<UiNode>,
    val textChangedNodes: List<Pair<UiNode, UiNode>>,
    val checkChangedNodes: List<Pair<UiNode, UiNode>> = emptyList(),
    val significantChange: Boolean
) {
    val summary: String
        get() = buildString {
            if (packageChanged) append("Package changed; ")
            if (newlyAppearedNodes.isNotEmpty()) append("${newlyAppearedNodes.size} added; ")
            if (disappearedNodes.isNotEmpty()) append("${disappearedNodes.size} removed; ")
            if (textChangedNodes.isNotEmpty()) append("${textChangedNodes.size} texts changed; ")
            if (checkChangedNodes.isNotEmpty()) append("${checkChangedNodes.size} check states changed; ")
            if (isEmpty()) append("No change detected")
        }

    fun isEmpty(): Boolean = !packageChanged && newlyAppearedNodes.isEmpty() &&
            disappearedNodes.isEmpty() && textChangedNodes.isEmpty() && checkChangedNodes.isEmpty()

    companion object {
        fun compute(previous: UiSnapshot?, current: UiSnapshot): UiDiff {
            if (previous == null) {
                return UiDiff(
                    packageChanged = true,
                    newlyAppearedNodes = current.allNodes,
                    disappearedNodes = emptyList(),
                    textChangedNodes = emptyList(),
                    checkChangedNodes = emptyList(),
                    significantChange = current.allNodes.isNotEmpty()
                )
            }

            val pkgChanged = previous.packageName != current.packageName

            // Fast structural diff using signature & node fingerprints
            val prevFingerprints = previous.allNodes.associateBy { it.toFingerprint() }
            val currFingerprints = current.allNodes.associateBy { it.toFingerprint() }

            val added = mutableListOf<UiNode>()
            val textChanges = mutableListOf<Pair<UiNode, UiNode>>()
            val checkChanges = mutableListOf<Pair<UiNode, UiNode>>()

            for ((fp, currNode) in currFingerprints) {
                val prevNode = prevFingerprints[fp]
                if (prevNode == null) {
                    added.add(currNode)
                } else {
                    if (prevNode.text != currNode.text && (!prevNode.text.isNullOrBlank() || !currNode.text.isNullOrBlank())) {
                        textChanges.add(Pair(prevNode, currNode))
                    }
                    if (prevNode.isChecked != currNode.isChecked) {
                        checkChanges.add(Pair(prevNode, currNode))
                    }
                }
            }

            val removed = mutableListOf<UiNode>()
            for ((fp, prevNode) in prevFingerprints) {
                if (!currFingerprints.containsKey(fp)) {
                    removed.add(prevNode)
                }
            }

            val significant = pkgChanged ||
                    added.any { it.clickable || !it.text.isNullOrBlank() } ||
                    removed.any { it.clickable } ||
                    textChanges.isNotEmpty() ||
                    checkChanges.isNotEmpty()

            return UiDiff(
                packageChanged = pkgChanged,
                newlyAppearedNodes = added,
                disappearedNodes = removed,
                textChangedNodes = textChanges,
                checkChangedNodes = checkChanges,
                significantChange = significant
            )
        }

        private fun UiNode.toFingerprint(): String {
            return "${resourceId ?: ""}|${className ?: ""}|${bounds.left},${bounds.top}"
        }
    }
}
