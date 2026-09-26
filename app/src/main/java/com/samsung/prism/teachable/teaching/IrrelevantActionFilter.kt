package com.samsung.prism.teachable.teaching

import com.samsung.prism.teachable.observation.UiSnapshot

data class FilterEvaluation(
    val isRelevant: Boolean,
    val relevanceScore: Float,
    val reason: String?
)

class IrrelevantActionFilter(
    private val relevanceThreshold: Float = 0.35f
) {

    /**
     * Known system interruption packages (phone calls, dialers, quick settings)
     * that represent unintended user tangents during teaching (Bonus B1).
     */
    private val interruptionPackages = setOf(
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.whatsapp", // third-party call popups
        "com.android.systemui.statusbar"
    )

    private val interruptionKeywords = listOf(
        "decline", "reject", "incoming call", "end call", "answer", "mute",
        "dismiss notification", "clear all", "battery saver", "turn off"
    )

    fun evaluate(
        action: RawAction,
        session: TeachingSession,
        screenBefore: UiSnapshot,
        screenAfter: UiSnapshot?
    ): FilterEvaluation {
        val targetPkg = action.packageName
        val nodeText = (action.targetNode.text ?: "") + " " + (action.targetNode.contentDescription ?: "")
        val lowerText = nodeText.lowercase()

        // 1. Phone Call / Dialer interruption check (Bonus B1 primary use-case)
        val hint = session.targetPackageHint
        val isTargetApp = (hint != null && targetPkg.contains(hint, ignoreCase = true)) ||
                isIntentRelated(session.originalUtterance, targetPkg)

        val isInterruptionPkg = !isTargetApp && interruptionPackages.any { targetPkg.contains(it, ignoreCase = true) }
        val matchesInterruptionWord = interruptionKeywords.any { lowerText.contains(it) }

        if (isInterruptionPkg || (!isTargetApp && matchesInterruptionWord && !isIntentRelated(session.originalUtterance, lowerText))) {
            return FilterEvaluation(
                isRelevant = false,
                relevanceScore = 0.05f,
                reason = if (matchesInterruptionWord) "Call/system action ignored" else "Switched to phone/system app"
            )
        }

        // 2. Package continuity check
        val isLauncher = isLauncherPackage(targetPkg)
        val expectedPkg = session.targetPackageHint ?: session.rawActions.firstOrNull {
            !isLauncherPackage(it.packageName) && it.packageName != "com.samsung.prism.teachable" && !it.isFiltered
        }?.packageName

        if (expectedPkg != null) {
            if (!isLauncher && targetPkg != expectedPkg) {
                // Allow system permission dialogs or system keyboards
                val isSystemDialog = targetPkg == "com.google.android.packageinstaller" ||
                        targetPkg == "com.android.permissioncontroller" ||
                        targetPkg == "android"

                if (!isSystemDialog) {
                    return FilterEvaluation(
                        isRelevant = false,
                        relevanceScore = 0.20f,
                        reason = "Action outside target application ($targetPkg)"
                    )
                }
            }
        } else {
            if (!isLauncher && targetPkg != "com.samsung.prism.teachable" && targetPkg.isNotBlank()) {
                session.targetPackageHint = targetPkg
            }
        }

        // 3. State reversibility check: accidental tap immediately undone with no net state change
        val lastRetained = session.rawActions.lastOrNull { !it.isFiltered }
        if (lastRetained != null && screenAfter != null) {
            val isBackOrClose = lowerText.contains("close") || lowerText.contains("back") ||
                    lowerText.contains("cancel") || action.targetNode.resourceId?.contains("close") == true
            val returnedToPriorState = screenAfter.screenSignature == lastRetained.screenBefore.screenSignature

            if (isBackOrClose && returnedToPriorState) {
                // The prior tap was an accidental branch that was cancelled
                lastRetained.isFiltered = true
                lastRetained.filterReason = "Cancelled accidental tap"
                return FilterEvaluation(
                    isRelevant = false,
                    relevanceScore = 0.15f,
                    reason = "Back action returning to prior state"
                )
            }
        }

        return FilterEvaluation(
            isRelevant = true,
            relevanceScore = 1.0f,
            reason = null
        )
    }

    private fun isIntentRelated(utterance: String, text: String): Boolean {
        val u = utterance.lowercase()
        val words = text.split(Regex("[^a-zA-Z0-9]+")).filter { it.length > 3 }
        return words.any { u.contains(it.lowercase()) }
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("launcher") ||
                lower.contains("quickstep") ||
                lower.contains("systemui") ||
                lower.contains("home") ||
                lower.contains("nexuslauncher") ||
                pkg == "android"
    }
}
