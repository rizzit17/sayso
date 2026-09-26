package com.samsung.prism.teachable.stuck

import com.samsung.prism.teachable.observation.UiNode

class ClarificationHandler {

    fun handleOptionSelection(option: ClarificationOption): ClarificationResult {
        return when (option.actionType) {
            ClarificationActionType.TAP_ALTERNATIVE -> {
                option.targetNode?.let { ClarificationResult.ResumeWithNode(it) } ?: ClarificationResult.SkipStep
            }
            ClarificationActionType.SKIP_STEP -> ClarificationResult.SkipStep
            ClarificationActionType.ABORT -> ClarificationResult.AbortWorkflow
        }
    }

    private val stopWords = setOf("the", "a", "an", "i", "want", "please", "one", "this", "that", "choose", "select", "tap", "go", "with")

    fun handleVoiceResponse(
        spokenResponse: String,
        question: ClarificationQuestion
    ): ClarificationResult {
        val lower = spokenResponse.trim().lowercase()

        // 1. Check for Skip
        if (lower.contains("skip") || lower.contains("pass") || lower.contains("next")) {
            return ClarificationResult.SkipStep
        }

        // 2. Check for Abort / Cancel
        if (lower.contains("abort") || lower.contains("cancel") || lower.contains("stop") || lower.contains("quit")) {
            return ClarificationResult.AbortWorkflow
        }

        // 3. Check for Alternative Option match
        val spokenWords = lower.split(Regex("[^a-zA-Z0-9]+")).filter { it.length >= 3 && it !in stopWords }

        for (opt in question.options) {
            if (opt.actionType == ClarificationActionType.TAP_ALTERNATIVE && opt.targetNode != null) {
                val nodeText = (opt.targetNode.text ?: "").lowercase()
                val labelText = opt.label.lowercase()

                if (nodeText.isNotBlank() && (lower.contains(nodeText) || nodeText.contains(lower))) {
                    return ClarificationResult.ResumeWithNode(opt.targetNode)
                }
                if (lower.contains(labelText) || labelText.contains(lower)) {
                    return ClarificationResult.ResumeWithNode(opt.targetNode)
                }

                // Keyword overlap (e.g. spoken location or modifier matching candidate label)
                val optWords = (nodeText + " " + labelText).split(Regex("[^a-zA-Z0-9]+"))
                    .filter { it.length >= 3 && it !in stopWords }
                if (spokenWords.any { sw -> optWords.any { ow -> sw == ow || ow.contains(sw) || sw.contains(ow) } }) {
                    return ClarificationResult.ResumeWithNode(opt.targetNode)
                }
            }
        }

        // Default to abort if user input cannot be resolved
        return ClarificationResult.AbortWorkflow
    }
}
