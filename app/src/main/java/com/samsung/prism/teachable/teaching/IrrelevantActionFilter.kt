package com.samsung.prism.teachable.teaching

import android.util.Log
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
        "decline call", "reject call", "incoming call", "end call", "answer call",
        "dismiss notification"
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
        val isTargetApp = (hint != null && (targetPkg.contains(hint, ignoreCase = true) || hint.contains(targetPkg, ignoreCase = true))) ||
                isIntentRelated(session.originalUtterance, targetPkg) ||
                targetPkg.contains("settings", ignoreCase = true)

        val isInterruptionPkg = !isTargetApp && interruptionPackages.any { targetPkg.contains(it, ignoreCase = true) }
        val matchesInterruptionWord = interruptionKeywords.any { lowerText.contains(it) }

        if (isInterruptionPkg || (matchesInterruptionWord && !isIntentRelated(session.originalUtterance, lowerText))) {
            val reason = if (matchesInterruptionWord) "Call/system action ignored" else "Switched to phone/system app"
            Log.i(TAG, "Filtering action ($reason): ${action.semanticDescription} on $targetPkg")
            return FilterEvaluation(
                isRelevant = false,
                relevanceScore = 0.05f,
                reason = reason
            )
        }

        // 2. Package continuity & SaySo return check
        val isLauncher = isLauncherPackage(targetPkg)
        val expectedPkg = session.targetPackageHint ?: session.rawActions.firstOrNull {
            !isLauncherPackage(it.packageName) && it.packageName != "com.samsung.prism.teachable" && !it.isFiltered
        }?.packageName

        // Filter out actions where the user is switching back to SaySo via Recents / Launcher
        val isReturningToSayso = targetPkg == "com.samsung.prism.teachable" ||
                lowerText.contains("prism teachable") ||
                lowerText.contains("sayso") ||
                lowerText.contains("recent") ||
                lowerText.contains("overview")
        if (isReturningToSayso) {
            val reason = "User switching apps or returning to SaySo"
            Log.i(TAG, "Filtering action ($reason): ${action.semanticDescription} on $targetPkg")
            return FilterEvaluation(
                isRelevant = false,
                relevanceScore = 0.0f,
                reason = reason
            )
        }

        if (expectedPkg != null) {
            val isMatchingExpected = targetPkg == expectedPkg ||
                    targetPkg.contains(expectedPkg, ignoreCase = true) ||
                    expectedPkg.contains(targetPkg, ignoreCase = true) ||
                    (targetPkg.contains("settings", ignoreCase = true) && expectedPkg.contains("settings", ignoreCase = true))

            if (!isLauncher && !isMatchingExpected) {
                // Allow system permission dialogs, system keyboards, and settings sub-packages
                val isSystemDialog = targetPkg == "com.google.android.packageinstaller" ||
                        targetPkg == "com.android.permissioncontroller" ||
                        targetPkg == "android" ||
                        targetPkg.contains("settings", ignoreCase = true) ||
                        (targetPkg.contains("systemui", ignoreCase = true) && !isReturningToSayso)

                if (!isSystemDialog) {
                    val reason = "Action outside target application ($targetPkg)"
                    Log.i(TAG, "Filtering action ($reason): ${action.semanticDescription}")
                    return FilterEvaluation(
                        isRelevant = false,
                        relevanceScore = 0.20f,
                        reason = reason
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
            val isImmediatelyPreceding = session.rawActions.lastOrNull() === lastRetained
            val role = lastRetained.targetNode.semanticRole?.lowercase() ?: ""
            val cls = lastRetained.targetNode.className?.lowercase() ?: ""
            val isToggleOrSwitch = role in setOf("switch", "checkbox", "radio_button", "toggle") ||
                    cls.contains("switch") || cls.contains("checkbox") || cls.contains("compoundbutton")

            val isBackOrClose = lowerText.contains("close") || lowerText.contains("back") ||
                    lowerText.contains("cancel") || action.targetNode.resourceId?.contains("close") == true
            val returnedToPriorState = screenAfter.screenSignature == lastRetained.screenBefore.screenSignature

            if (!isToggleOrSwitch && isImmediatelyPreceding && isBackOrClose && returnedToPriorState) {
                // The prior tap was an accidental branch that was cancelled
                lastRetained.isFiltered = true
                lastRetained.filterReason = "Cancelled accidental tap"
                Log.i(TAG, "Retroactively filtered action #${session.rawActions.indexOf(lastRetained) + 1} (${lastRetained.semanticDescription}) as accidental tap, triggered by: ${action.semanticDescription}")
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

    companion object {
        private const val TAG = "TeachingRecorder"
    }
}
