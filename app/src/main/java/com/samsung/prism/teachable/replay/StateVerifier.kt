package com.samsung.prism.teachable.replay

import com.samsung.prism.teachable.model.ExpectedStateTransition
import com.samsung.prism.teachable.observation.UiDiff
import com.samsung.prism.teachable.observation.UiSnapshot

data class VerificationResult(
    val verified: Boolean,
    val failureReason: String? = null
)

class StateVerifier {

    fun verify(
        transition: ExpectedStateTransition,
        screenBefore: UiSnapshot,
        screenAfter: UiSnapshot
    ): VerificationResult {
        // 1. Verify Package Name
        if (transition.expectedPackageName != null) {
            if (!screenAfter.packageName.equals(transition.expectedPackageName, ignoreCase = true)) {
                return VerificationResult(
                    verified = false,
                    failureReason = "Package mismatch: expected ${transition.expectedPackageName}, got ${screenAfter.packageName}"
                )
            }
        }

        // 2. Verify State Change
        if (transition.expectedSignatureChange) {
            val sigChanged = screenBefore.screenSignature != screenAfter.screenSignature
            val diff = UiDiff.compute(screenBefore, screenAfter)

            if (!sigChanged && diff.isEmpty()) {
                return VerificationResult(
                    verified = false,
                    failureReason = "Screen state did not change after action"
                )
            }
        }

        // 3. Verify Expected Text Substring
        val expectedText = transition.expectedTextSubstring?.trim()?.lowercase()
        if (!expectedText.isNullOrEmpty()) {
            val allNodes = screenAfter.allNodes
            val foundText = allNodes.any { node ->
                val nodeText = node.text?.lowercase() ?: ""
                val nodeDesc = node.contentDescription?.lowercase() ?: ""
                nodeText.contains(expectedText) || nodeDesc.contains(expectedText)
            }

            if (!foundText) {
                return VerificationResult(
                    verified = false,
                    failureReason = "Expected text substring '$expectedText' not found in post-action UI"
                )
            }
        }

        // 4. Verify Expected Role
        val expectedRole = transition.expectedRole
        if (!expectedRole.isNullOrEmpty()) {
            val allNodes = screenAfter.allNodes
            val foundRole = allNodes.any { it.semanticRole == expectedRole }
            if (!foundRole) {
                return VerificationResult(
                    verified = false,
                    failureReason = "Expected semantic role '$expectedRole' not found in post-action UI"
                )
            }
        }

        return VerificationResult(verified = true)
    }
}
