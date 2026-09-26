package com.samsung.prism.teachable.stuck

import com.samsung.prism.teachable.ai.GenAiManager

class ClarificationGenerator(
    private val genAiManager: GenAiManager? = null
) {

    suspend fun generateQuestionSuspend(context: StuckContext): ClarificationQuestion {
        if (genAiManager?.isGenAiActive == true) {
            return genAiManager.generateStuckClarification(context)
        }
        return generateQuestion(context)
    }

    fun generateQuestion(context: StuckContext): ClarificationQuestion {
        val targetLabel = context.step.target.text
            ?: context.step.target.contentDescription
            ?: context.step.target.semanticRole
            ?: "element"

        val options = mutableListOf<ClarificationOption>()

        // 1. Suggest available alternatives on screen (up to 2)
        val validCandidates = context.visibleCandidates
            .filter { !it.text.isNullOrBlank() }
            .take(2)

        for ((idx, candidate) in validCandidates.withIndex()) {
            val label = candidate.text?.trim() ?: "Option ${idx + 1}"
            options.add(
                ClarificationOption(
                    id = "alt_$idx",
                    label = "Tap '$label'",
                    targetNode = candidate,
                    actionType = ClarificationActionType.TAP_ALTERNATIVE
                )
            )
        }

        // 2. Skip option
        options.add(
            ClarificationOption(
                id = "opt_skip",
                label = "Skip this step and continue",
                actionType = ClarificationActionType.SKIP_STEP
            )
        )

        // 3. Abort option
        options.add(
            ClarificationOption(
                id = "opt_abort",
                label = "Abort automation",
                actionType = ClarificationActionType.ABORT
            )
        )

        val questionText = if (validCandidates.isNotEmpty()) {
            val candidateNames = validCandidates.joinToString(" or ") { "'${it.text}'" }
            "I'm looking for '$targetLabel', but only see $candidateNames. How should I proceed?"
        } else {
            "I'm stuck trying to interact with '$targetLabel' (${context.failureReason}). How should I proceed?"
        }

        val ttsPrompt = "I'm looking for $targetLabel, but couldn't find it. Should I skip, abort, or tap an alternative?"

        return ClarificationQuestion(
            questionText = questionText,
            ttsPrompt = ttsPrompt,
            options = options
        )
    }
}

