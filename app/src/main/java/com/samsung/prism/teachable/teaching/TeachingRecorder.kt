package com.samsung.prism.teachable.teaching

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.samsung.prism.teachable.observation.Bounds
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import com.samsung.prism.teachable.observation.UiTreeCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TeachingRecorder(
    private val filter: IrrelevantActionFilter = IrrelevantActionFilter(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _currentSession = MutableStateFlow<TeachingSession?>(null)
    val currentSession: StateFlow<TeachingSession?> = _currentSession.asStateFlow()

    private val _actionStream = MutableSharedFlow<RawAction>(extraBufferCapacity = 64)
    val actionStream: SharedFlow<RawAction> = _actionStream.asSharedFlow()

    private var previousSnapshot: UiSnapshot? = null

    val isRecording: Boolean get() = _currentSession.value?.status == SessionStatus.RECORDING

    fun startSession(utterance: String, targetPackageHint: String? = null): TeachingSession {
        val session = TeachingSession(
            originalUtterance = utterance,
            targetPackageHint = targetPackageHint
        )
        _currentSession.value = session
        previousSnapshot = null
        Log.i(TAG, "Started teaching session: ${session.sessionId} for '$utterance'")
        return session
    }

    /**
     * Records an explicitly observed or simulated action.
     */
    fun recordAction(
        actionType: ActionType,
        targetNode: UiNode,
        inputText: String? = null,
        before: UiSnapshot,
        after: UiSnapshot
    ): RawAction {
        val session = _currentSession.value ?: throw IllegalStateException("No active teaching session")

        val targetPkg = targetNode.packageName?.takeIf { it.isNotBlank() }
            ?: before.packageName.ifEmpty { after.packageName.ifEmpty { "" } }

        val action = RawAction(
            packageName = targetPkg,
            actionType = actionType,
            inputText = inputText,
            targetNode = targetNode,
            screenBefore = before,
            screenAfter = after
        )

        // Evaluate action relevance (Bonus B1)
        val eval = filter.evaluate(action, session, before, after)
        action.isFiltered = !eval.isRelevant
        action.filterReason = eval.reason
        action.relevanceScore = eval.relevanceScore

        session.rawActions.add(action)
        previousSnapshot = after

        scope.launch {
            _actionStream.emit(action)
        }
        Log.i(TAG, "Recorded action: ${action.semanticDescription} (filtered=${action.isFiltered})")
        return action
    }

    /**
     * Invoked from AccessibilityService event listener when user interacts with UI.
     */
    fun handleAccessibilityEvent(event: AccessibilityEvent) {
        val session = _currentSession.value ?: return
        if (session.status != SessionStatus.RECORDING) return

        val pkg = event.packageName?.toString() ?: ""
        // Do not record internal SaySo interactions (e.g. Stop & Save or dismiss buttons)
        if (pkg == "com.samsung.prism.teachable") return

        // Do not record user switching back to SaySo via Recents / App Switcher
        val eventSummary = (event.text.joinToString(" ") + " " + (event.contentDescription ?: "")).lowercase()
        if (eventSummary.contains("prism teachable") || eventSummary.contains("sayso")) {
            return
        }

        // Do not record system navigation bar clicks (Recents, Overview, Home)
        if (pkg == "com.android.systemui") {
            val resId = try { event.source?.viewIdResourceName?.lowercase() ?: "" } catch (_: Exception) { "" }
            if (eventSummary.contains("recent") || eventSummary.contains("overview") || eventSummary.contains("home") ||
                resId.contains("recent") || resId.contains("overview") || resId.contains("home")
            ) {
                return
            }
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg.isNotBlank()) {
                    previousSnapshot = UiTreeCapture.captureCurrentScreen()
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                val before = previousSnapshot ?: UiTreeCapture.captureCurrentScreen()
                val targetNode = extractNodeFromEvent(event)
                val after = UiTreeCapture.captureCurrentScreen()
                recordAction(ActionType.CLICK, targetNode, null, before, after)
            }

            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                val before = previousSnapshot ?: UiTreeCapture.captureCurrentScreen()
                val targetNode = extractNodeFromEvent(event)
                val after = UiTreeCapture.captureCurrentScreen()
                recordAction(ActionType.LONG_CLICK, targetNode, null, before, after)
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val text = event.text.joinToString("")
                if (text.isNotBlank()) {
                    val before = previousSnapshot ?: UiTreeCapture.captureCurrentScreen()
                    val targetNode = extractNodeFromEvent(event)
                    val after = UiTreeCapture.captureCurrentScreen()
                    recordAction(ActionType.SET_TEXT, targetNode, text, before, after)
                }
            }
        }
    }

    fun stopSession(truncatedAtBoundary: Boolean = false): TeachingSession {
        val session = _currentSession.value ?: throw IllegalStateException("No active session to stop")
        
        // Automatically prune any trailing actions on launcher / systemui / recents / SaySo
        pruneTrailingReturningActions(session)

        session.truncatedAtBoundary = truncatedAtBoundary
        session.status = if (truncatedAtBoundary) {
            SessionStatus.TRUNCATED_AT_BOUNDARY
        } else {
            SessionStatus.COMPLETED
        }
        _currentSession.value = session
        Log.i(TAG, "Teaching session stopped: ${session.sessionId}, retained actions: ${session.retainedActions.size}")
        return session
    }

    /**
     * Prunes actions that were performed merely to bring SaySo back to the foreground to stop teaching.
     */
    private fun pruneTrailingReturningActions(session: TeachingSession) {
        while (session.rawActions.isNotEmpty()) {
            val last = session.rawActions.last()
            val pkg = last.packageName.lowercase()
            val text = ((last.targetNode.text ?: "") + " " + (last.targetNode.contentDescription ?: "")).lowercase()
            val isReturningAction = pkg == "com.samsung.prism.teachable" ||
                    text.contains("prism teachable") ||
                    text.contains("sayso") ||
                    text.contains("recent") ||
                    text.contains("overview") ||
                    (pkg.contains("systemui") && (text.contains("home") || text.contains("back") || text.contains("recent"))) ||
                    (pkg.contains("launcher") && (text.contains("clear") || text.contains("prism") || text.contains("sayso")))

            if (isReturningAction) {
                Log.i(TAG, "Pruned trailing app-switch/recents action: ${last.semanticDescription}")
                session.rawActions.removeAt(session.rawActions.size - 1)
            } else {
                break
            }
        }
    }

    fun cancelSession(): TeachingSession? {
        val session = _currentSession.value ?: return null
        session.status = SessionStatus.CANCELLED
        _currentSession.value = null
        Log.i(TAG, "Teaching session cancelled: ${session.sessionId}")
        return session
    }

    private fun extractNodeFromEvent(event: AccessibilityEvent): UiNode {
        val source = try {
            event.source
        } catch (e: Exception) {
            null
        }

        if (source != null) {
            try {
                val rect = android.graphics.Rect()
                source.getBoundsInScreen(rect)
                var text = source.text?.toString() ?: event.text.joinToString("").takeIf { it.isNotBlank() }
                var desc = source.contentDescription?.toString() ?: event.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                val cls = source.className?.toString() ?: event.className?.toString()
                val pkg = source.packageName?.toString() ?: event.packageName?.toString()
                val resId = source.viewIdResourceName

                // If text and desc are both blank (e.g. on a Switch, Toggle or CheckBox), look up hierarchy for label
                if (text.isNullOrBlank() && desc.isNullOrBlank()) {
                    val label = findLabelInHierarchy(source)
                    if (!label.isNullOrBlank()) {
                        text = label
                    }
                }

                return UiNode(
                    resourceId = resId,
                    text = text,
                    contentDescription = desc,
                    className = cls,
                    packageName = pkg,
                    clickable = source.isClickable,
                    scrollable = source.isScrollable,
                    enabled = source.isEnabled,
                    bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom)
                )
            } finally {
                try {
                    @Suppress("DEPRECATION")
                    source.recycle()
                } catch (_: Exception) {}
            }
        }

        val text = event.text.joinToString("").takeIf { it.isNotBlank() }
        val desc = event.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        val cls = event.className?.toString()
        val pkg = event.packageName?.toString()

        return UiNode(
            text = text,
            contentDescription = desc,
            className = cls,
            packageName = pkg,
            clickable = true,
            bounds = Bounds.ZERO
        )
    }

    private fun findLabelInHierarchy(node: android.view.accessibility.AccessibilityNodeInfo, depth: Int = 0): String? {
        if (depth > 2) return null
        val parent = try { node.parent } catch (_: Exception) { null } ?: return null
        try {
            for (i in 0 until parent.childCount) {
                val child = try { parent.getChild(i) } catch (_: Exception) { null } ?: continue
                try {
                    val ct = child.text?.toString()
                    val cd = child.contentDescription?.toString()
                    if (!ct.isNullOrBlank()) {
                        return ct
                    }
                    if (!cd.isNullOrBlank()) {
                        return cd
                    }
                } finally {
                    @Suppress("DEPRECATION")
                    try { child.recycle() } catch (_: Exception) {}
                }
            }
            return findLabelInHierarchy(parent, depth + 1)
        } finally {
            @Suppress("DEPRECATION")
            try { parent.recycle() } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "TeachingRecorder"
        val instance by lazy { TeachingRecorder() }
    }
}
