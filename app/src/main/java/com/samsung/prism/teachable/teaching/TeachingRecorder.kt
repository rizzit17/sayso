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
        Log.i(TAG, "Recorded action #${session.rawActions.size}: ${action.semanticDescription} on $targetPkg (filtered=${action.isFiltered})")
        return action
    }

    private var lastScrollTimestamp = 0L

    /**
     * Invoked from AccessibilityService event listener when user interacts with UI.
     */
    fun handleAccessibilityEvent(event: AccessibilityEvent) {
        try {
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

            Log.d(TAG, "Teaching event captured: type=${event.eventType}, pkg=$pkg, text=${event.text}")

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    if (pkg.isNotBlank()) {
                        previousSnapshot = UiTreeCapture.captureCurrentScreen()
                    }
                }

                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                    val before = previousSnapshot ?: UiTreeCapture.captureCurrentScreen()
                    val targetNode = extractNodeFromEvent(event, before)
                    val after = UiTreeCapture.captureCurrentScreen()
                    recordAction(ActionType.CLICK, targetNode, null, before, after)
                }

                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                    val before = previousSnapshot ?: UiTreeCapture.captureCurrentScreen()
                    val targetNode = extractNodeFromEvent(event, before)
                    val after = UiTreeCapture.captureCurrentScreen()
                    recordAction(ActionType.LONG_CLICK, targetNode, null, before, after)
                }

                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    val text = event.text.joinToString("")
                    if (text.isNotBlank()) {
                        val before = previousSnapshot ?: UiTreeCapture.captureCurrentScreen()
                        val targetNode = extractNodeFromEvent(event, before)
                        val after = UiTreeCapture.captureCurrentScreen()
                        recordAction(ActionType.SET_TEXT, targetNode, text, before, after)
                    }
                }

                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    val now = System.currentTimeMillis()
                    if (now - lastScrollTimestamp > 1000L) {
                        lastScrollTimestamp = now
                        val before = previousSnapshot ?: UiTreeCapture.captureCurrentScreen()
                        val targetNode = extractNodeFromEvent(event, before)
                        val after = UiTreeCapture.captureCurrentScreen()
                        recordAction(ActionType.SCROLL_FORWARD, targetNode, null, before, after)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error handling accessibility event: ${e.message}", e)
        }
    }

    fun stopSession(truncatedAtBoundary: Boolean = false): TeachingSession {
        val session = _currentSession.value ?: throw IllegalStateException("No active session to stop")
        
        // Automatically prune any trailing actions on SaySo itself
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
                    (pkg.contains("systemui") && (text.contains("home") || text.contains("back") || text.contains("recent") || text.contains("overview"))) ||
                    (pkg.contains("launcher") && (text.contains("clear") || text.contains("prism") || text.contains("sayso") || text.contains("recent")))

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

    private fun extractNodeFromEvent(event: AccessibilityEvent, before: UiSnapshot? = null): UiNode {
        val source = try {
            event.source
        } catch (e: Exception) {
            null
        }

        if (source != null) {
            try {
                val rect = android.graphics.Rect()
                source.getBoundsInScreen(rect)
                var text = source.text?.toString()?.takeIf { it.isNotBlank() } ?: event.text.joinToString(" ").takeIf { it.isNotBlank() }
                var desc = source.contentDescription?.toString()?.takeIf { it.isNotBlank() } ?: event.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                val cls = source.className?.toString() ?: event.className?.toString()
                val pkg = source.packageName?.toString() ?: event.packageName?.toString()
                val resId = source.viewIdResourceName

                val semanticRole = when {
                    cls?.contains("Switch", ignoreCase = true) == true ||
                    cls?.contains("CompoundButton", ignoreCase = true) == true ||
                    cls?.contains("CheckBox", ignoreCase = true) == true ||
                    resId?.contains("switch", ignoreCase = true) == true -> "switch"
                    cls?.contains("Button", ignoreCase = true) == true -> "button"
                    cls?.contains("EditText", ignoreCase = true) == true -> "input_field"
                    cls?.contains("ImageView", ignoreCase = true) == true -> "image"
                    else -> if (source.isClickable) "button" else null
                }

                // If text/desc is blank or only a state indicator ("ON"/"OFF"), search hierarchy for the semantic row label
                val isTextMissingOrStateOnly = text.isNullOrBlank() || isStateOnlyString(text)
                val isDescMissingOrStateOnly = desc.isNullOrBlank() || isStateOnlyString(desc)

                if (isTextMissingOrStateOnly && isDescMissingOrStateOnly) {
                    val label = runCatching { findLabelInHierarchy(source) }.getOrNull()
                    if (!label.isNullOrBlank()) {
                        text = label
                    }
                }

                // If still missing or state-only, search before snapshot for nodes sharing the same horizontal row band
                if ((text.isNullOrBlank() || isStateOnlyString(text)) && before != null && !rect.isEmpty) {
                    val centerY = rect.centerY()
                    val matchingRowNode = before.allNodes.firstOrNull { n ->
                        !n.text.isNullOrBlank() && !isStateOnlyString(n.text) &&
                                n.bounds.bottom >= rect.top && n.bounds.top <= rect.bottom &&
                                n.bounds.left < rect.left
                    } ?: before.allNodes.firstOrNull { n ->
                        !n.text.isNullOrBlank() && !isStateOnlyString(n.text) &&
                                n.bounds.bottom >= rect.top && n.bounds.top <= rect.bottom
                    } ?: before.allNodes.firstOrNull { n ->
                        !n.text.isNullOrBlank() && !isStateOnlyString(n.text) &&
                                kotlin.math.abs(n.bounds.centerY - centerY) < 100
                    }
                    if (matchingRowNode != null) {
                        text = matchingRowNode.text
                    }
                }

                val parentContext = text

                return UiNode(
                    resourceId = resId,
                    text = text,
                    contentDescription = desc,
                    className = cls,
                    packageName = pkg,
                    clickable = source.isClickable,
                    scrollable = source.isScrollable,
                    enabled = source.isEnabled,
                    semanticRole = semanticRole,
                    parentContext = parentContext,
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

    private fun isStateOnlyString(s: String?): Boolean {
        if (s.isNullOrBlank()) return true
        val trimmed = s.trim().lowercase()
        return trimmed in setOf("on", "off", "checked", "not checked", "true", "false", "switch", "toggle", "button")
    }

    private fun findTextInNodeOrDescendants(node: android.view.accessibility.AccessibilityNodeInfo, depth: Int = 0): String? {
        if (depth > 4) return null
        val resId = node.viewIdResourceName
        if (resId?.contains("title", ignoreCase = true) == true) {
            val t = node.text?.toString()?.takeIf { it.isNotBlank() && !isStateOnlyString(it) }
            if (t != null) return t
        }

        val t = node.text?.toString()?.takeIf { it.isNotBlank() && !isStateOnlyString(it) }
        if (t != null) return t
        val cd = node.contentDescription?.toString()?.takeIf { it.isNotBlank() && !isStateOnlyString(it) }
        if (cd != null) return cd

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            try {
                val found = findTextInNodeOrDescendants(child, depth + 1)
                if (!found.isNullOrBlank()) return found
            } finally {
                @Suppress("DEPRECATION")
                try { child.recycle() } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun findLabelInHierarchy(node: android.view.accessibility.AccessibilityNodeInfo, depth: Int = 0): String? {
        if (depth > 3) return null
        val parent = try { node.parent } catch (_: Exception) { null } ?: return null
        try {
            val parentCls = parent.className?.toString() ?: ""
            if (parentCls.contains("RecyclerView", ignoreCase = true) ||
                parentCls.contains("ListView", ignoreCase = true) ||
                parentCls.contains("ScrollView", ignoreCase = true)
            ) {
                return null
            }

            // 1. Look for explicit title child
            for (i in 0 until parent.childCount) {
                val child = try { parent.getChild(i) } catch (_: Exception) { null } ?: continue
                try {
                    val childResId = child.viewIdResourceName
                    if (childResId?.contains("title", ignoreCase = true) == true) {
                        val t = child.text?.toString()?.takeIf { it.isNotBlank() && !isStateOnlyString(it) }
                        if (t != null) return t
                    }
                } finally {
                    @Suppress("DEPRECATION")
                    try { child.recycle() } catch (_: Exception) {}
                }
            }

            // 2. Look for any child with non-state text
            for (i in 0 until parent.childCount) {
                val child = try { parent.getChild(i) } catch (_: Exception) { null } ?: continue
                try {
                    val found = findTextInNodeOrDescendants(child)
                    if (!found.isNullOrBlank() && !isStateOnlyString(found)) {
                        return found
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
