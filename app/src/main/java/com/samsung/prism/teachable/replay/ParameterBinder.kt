package com.samsung.prism.teachable.replay

import com.samsung.prism.teachable.model.ExpectedStateTransition
import com.samsung.prism.teachable.model.StepTarget
import com.samsung.prism.teachable.model.WorkflowStep

class ParameterBinder {

    fun bindStep(step: WorkflowStep, params: Map<String, Any>): WorkflowStep {
        if (params.isEmpty()) return step

        val boundInput = step.inputText?.let { replaceTokens(it, params) }
        val boundTarget = bindTarget(step.target, params)
        val boundTransition = bindTransition(step.expectedStateTransition, params)

        return step.copy(
            inputText = boundInput,
            target = boundTarget,
            expectedStateTransition = boundTransition
        )
    }

    fun bindTarget(target: StepTarget, params: Map<String, Any>): StepTarget {
        return target.copy(
            text = target.text?.let { replaceTokens(it, params) },
            contentDescription = target.contentDescription?.let { replaceTokens(it, params) }
        )
    }

    fun bindTransition(transition: ExpectedStateTransition, params: Map<String, Any>): ExpectedStateTransition {
        return transition.copy(
            expectedTextSubstring = transition.expectedTextSubstring?.let { replaceTokens(it, params) }
        )
    }

    fun replaceTokens(template: String, params: Map<String, Any>): String {
        var result = template
        for ((key, value) in params) {
            val token = "{$key}"
            if (result.contains(token)) {
                result = result.replace(token, value.toString())
            }
        }
        return result
    }
}
