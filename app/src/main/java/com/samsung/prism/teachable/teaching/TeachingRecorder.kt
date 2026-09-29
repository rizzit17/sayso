package com.samsung.prism.teachable.teaching

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.samsung.prism.teachable.observation.Bounds
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import com.samsung.prism.teachable.observation.UiTreeCapture
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TeachingRecorder(
    private val filter: IrrelevantActionFilter = IrrelevantActionFilter(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    val settleDelayMs: Long = 350L
) {
    private val _currentSession = MutableStateFlow<TeachingSession?>(null)
    val currentSession: StateFlow<TeachingSession?> = _currentSession.asStateFlow()

    private val _actionStream = MutableSharedFlow<RawAction>(extraBufferCapacity = 64)
    val actionStream: SharedFlow<RawAction> = _actionStream.asSharedFlow()

    private var previousSnapshot: UiSnapshot? = null

    val isRecording: Boolean get() = _currentSession.value?.status == SessionStatus.RECORDING

    private class PendingRecording(
        val actionType: ActionType,
        val targetNode: UiNode,
        val inputText: String?,
        val before: UiSnapshot,
        @Volatile var isExecuted: Boolean = false
    ) {
        val lock = Any()
    }

    private val pendingRecordings = Collections.synchronizedList(mutableListOf<PendingRecording>())
    private val recordingMutex = Mutex()
    private val lastClickTimestamps = ConcurrentHashMap<String, Long>()

    fun startSession(utterance: String, targetPackageHint: String? = null): TeachingSession {
        val session = TeachingSession(
            originalUtterance = utterance,
            targetPackageHint = targetPackageHint
        )
        _currentSession.value = session
        previousSnapshot = null
        lastClickTimestamps.clear()
        synchronized(pendingRecordings) {
            for (pending in pendingRecordings) {
                pending.isExecuted = true
            }
            pendingRecordings.clear()
        }
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

        if (action.isFiltered) {
            Log.i(TAG, "Action #${session.rawActions.size + 1} marked filtered (${action.filterReason}): ${action.semanticDescription} on $targetPkg")
        }

        session.rawActions.add(action)
        previousSnapshot = after

        scope.launch {
            _actionStream.emit(action)
        }
        Log.i(TAG, "Recorded action #${session.rawActions.size}: ${action.semanticDescription} on $targetPkg (filtered=${action.isFiltered})")
        return action
    }

    private var lastScrollTimestamp = 0L

    private fun getNodeKey(resId: String?, text: String?, left: Int, top: Int): String {
        return if (!resId.isNullOrBlank()) {
            "$resId|$left,$top"
        } else if (!text.isNullOrBlank() && !isStateOnlyString(text)) {
            "$text|$left,$top"
        } else {
            "$left,$top"
        }
    }

    private fun getNodeKey(node: UiNode): String {
        return getNodeKey(node.resourceId, node.text, node.bounds.left, node.bounds.top)
    }

    internal fun shouldDropSourceLessEvent(hasSource: Boolean, pkg: String): Boolean {
        return !hasSource && (pkg.contains("launcher", ignoreCase = true) ||
                pkg.contains("systemui", ignoreCase = true) ||
                pkg.contains("quickstep", ignoreCase = true))
    }

    internal fun enqueueDelayedRecording(
        actionType: ActionType,
        targetNode: UiNode,
        inputText: String?,
        before: UiSnapshot
    ) {
        val pending = PendingRecording(actionType, targetNode, inputText, before)
        pendingRecordings.add(pending)

        scope.launch {
            recordingMutex.withLock {
                if (pending.isExecuted) return@withLock
                if (settleDelayMs > 0) {
                    delay(settleDelayMs)
                }
                val shouldRecord = synchronized(pending.lock) {
                    if (!pending.isExecuted) {
                        pending.isExecuted = true
                        true
                    } else {
                        false
                    }
                }
                if (shouldRecord) {
                    pendingRecordings.remove(pending)
                    val after = UiTreeCapture.captureCurrentScreen()
                    recordAction(pending.actionType, pending.targetNode, pending.inputText, pending.before, after)
                }
            }
        }
    }

    internal fun simulateClickEvent(targetNode: UiNode, before: UiSnapshot? = null): Boolean {
        val snap = before ?: previousSnapshot ?: UiSnapshot.EMPTY
        val key = getNodeKey(targetNode)
        val now = System.currentTimeMillis()
        val lastTime = lastClickTimestamps[key] ?: 0L
        if (now - lastTime <= 500L) {
            Log.d(TAG, "Skipping duplicate click event for node $key (recorded ${now - lastTime}ms ago)")
            return false
        }
        lastClickTimestamps[key] = now
        enqueueDelayedRecording(ActionType.CLICK, targetNode, null, snap)
        return true
    }

    internal fun simulateContentChangedToggle(
        targetNode: UiNode,
        currentChecked: Boolean,
        before: UiSnapshot? = null
    ): Boolean {
        val isToggle = targetNode.semanticRole == "switch" ||
                targetNode.semanticRole == "checkbox" ||
                targetNode.className?.contains("Switch", ignoreCase = true) == true ||
                targetNode.className?.contains("CompoundButton", ignoreCase = true) == true ||
                targetNode.className?.contains("CheckBox", ignoreCase = true) == true

        if (!isToggle) return false

        val snap = before ?: previousSnapshot
        val prevNode = snap?.allNodes?.firstOrNull { n ->
            (targetNode.resourceId != null && n.resourceId == targetNode.resourceId) ||
                    (n.bounds.left == targetNode.bounds.left && n.bounds.top == targetNode.bounds.top) ||
                    (!targetNode.text.isNullOrBlank() && n.text == targetNode.text)
        }

        if (prevNode != null && prevNode.isChecked != currentChecked) {
            val key = getNodeKey(targetNode)
            val now = System.currentTimeMillis()
            val lastTime = lastClickTimestamps[key] ?: 0L
            if (now - lastTime > 500L) {
                lastClickTimestamps[key] = now
                val updatedNode = targetNode.copy(isChecked = currentChecked)
                enqueueDelayedRecording(ActionType.CLICK, updatedNode, null, snap)
                return true
            } else {
                Log.d(TAG, "Content-change toggle fallback skipped for $key: click already recorded ${now - lastTime}ms ago")
            }
        }
        return false
    }

    /**
     * Flushes any pending delayed recordings immediately without waiting for delay.
     * Guaranteed thread-safe and executes pending actions in FIFO order.
     */
    fun flushPendingRecordings() {
        val toExecute = mutableListOf<PendingRecording>()
        synchronized(pendingRecordings) {
            val iterator = pendingRecordings.iterator()
            while (iterator.hasNext()) {
                val pending = iterator.next()
                val shouldRecord = synchronized(pending.lock) {
                    if (!pending.isExecuted) {
                        pending.isExecuted = true
                        true
                    } else {
                        false
                    }
                }
                if (shouldRecord) {
                    toExecute.add(pending)
                }
                iterator.remove()
            }
        }
        for (pending in toExecute) {
            val after = UiTreeCapture.captureCurrentScreen()
            recordAction(pending.actionType, pending.targetNode, pending.inputText, pending.before, after)
        }
    }

    private fun handleContentChangedToggleFallback(event: AccessibilityEvent) {
        val source = try { event.source } catch (_: Exception) { null } ?: return
        try {
            val isCheckable = try { source.isCheckable } catch (_: Exception) { false }
            val cls = source.className?.toString() ?: ""
            val isToggle = isCheckable ||
                    cls.contains("Switch", ignoreCase = true) ||
                    cls.contains("CompoundButton", ignoreCase = true) ||
                    cls.contains("CheckBox", ignoreCase = true)

            if (!isToggle) return

            val currentChecked = try { source.isChecked } catch (_: Exception) { false }
            val rect = android.graphics.Rect()
            source.getBoundsInScreen(rect)
            val resId = source.viewIdResourceName
            val nodeText = source.text?.toString()

            val prev = previousSnapshot
            val prevNode = prev?.allNodes?.firstOrNull { n ->
                (resId != null && n.resourceId == resId) ||
                        (!rect.isEmpty && n.bounds.left == rect.left && n.bounds.top == rect.top) ||
                        (!nodeText.isNullOrBlank() && n.text == nodeText)
            }

            if (prevNode != null && prevNode.isChecked != currentChecked) {
                val key = getNodeKey(resId, nodeText, rect.left, rect.top)
                val now = System.currentTimeMillis()
                val lastTime = lastClickTimestamps[key] ?: 0L
                if (now - lastTime > 500L) {
                    lastClickTimestamps[key] = now
                    val before = prev ?: UiTreeCapture.captureCurrentScreen()
                    val targetNode = extractNodeFromEvent(event, before)
                    Log.i(TAG, "Content-change toggle fallback triggered: flipped checked state (${prevNode.isChecked} -> $currentChecked) for $key")
                    enqueueDelayedRecording(ActionType.CLICK, targetNode, null, before)
                } else {
                    Log.d(TAG, "Content-change toggle fallback skipped for $key: click already recorded ${now - lastTime}ms ago")
                }
            }
        } finally {
            try {
                @Suppress("DEPRECATION")
                source.recycle()
            } catch (_: Exception) {}
        }
    }

    /**
     * Invoked from AccessibilityService event listener when user interacts with UI.
     */
    fun handleAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val session = _currentSession.value
            if (session == null) {
                Log.d(TAG, "Dropped event: no active teaching session")
                return
            }
            if (session.status != SessionStatus.RECORDING) {
                Log.d(TAG, "Dropped event: session status is ${session.status}, not RECORDING")
                return
            }

            val pkg = event.packageName?.toString() ?: ""

            // Drop source-less events from launcher/systemui/quickstep packages
            val hasSource = try {
                val s = event.source
                val exists = s != null
                try {
                    @Suppress("DEPRECATION")
                    s?.recycle()
                } catch (_: Exception) {}
                exists
            } catch (_: Exception) {
                false
            }

            if (shouldDropSourceLessEvent(hasSource, pkg)) {
                Log.d(TAG, "Dropped source-less event from launcher/systemui: pkg=$pkg, eventType=${event.eventType}")
                return
            }

            // Do not record internal SaySo interactions (e.g. Stop & Save or dismiss buttons)
            if (pkg == "com.samsung.prism.teachable") {
                Log.d(TAG, "Dropped internal SaySo event: pkg=$pkg")
                return
            }

            // Do not record user switching back to SaySo via Recents / App Switcher
            val eventSummary = (event.text.joinToString(" ") + " " + (event.contentDescription ?: "")).lowercase()
            if (eventSummary.contains("prism teachable") || eventSummary.contains("sayso")) {
                Log.d(TAG, "Dropped SaySo return summary event: $eventSummary")
                return
            }

            // Do not record system navigation bar clicks (Recents, Overview, Home)
            if (pkg == "com.android.systemui") {
                val resId = try {
                    val s = event.source
                    val id = s?.viewIdResourceName?.lowercase() ?: ""
                    try { @Suppress("DEPRECATION") s?.recycle() } catch (_: Exception) {}
                    id
                } catch (_: Exception) { "" }

                if (eventSummary.contains("recent") || eventSummary.contains("overview") || eventSummary.contains("home") ||
                    resId.contains("recent") || resId.contains("overview") || resId.contains("home")
                ) {
                    Log.d(TAG, "Dropped SystemUI navigation event: $eventSummary (resId=$resId)")
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
                    simulateClickEvent(targetNode, before)
                }

                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                    val before = previousSnapshot ?: UiTreeCapture.captureCurrentScreen()
                    val targetNode = extractNodeFromEvent(event, before)
                    enqueueDelayedRecording(ActionType.LONG_CLICK, targetNode, null, before)
                }

                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    val text = event.text.joinToString("")
                    if (text.isNotBlank()) {
                        val before = previousSnapshot ?: UiTreeCapture.captureCurrentScreen()
                        val targetNode = extractNodeFromEvent(event, before)
                        enqueueDelayedRecording(ActionType.SET_TEXT, targetNode, text, before)
                    }
                }

                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    val now = System.currentTimeMillis()
                    if (now - lastScrollTimestamp > 1000L) {
                        lastScrollTimestamp = now
                        val before = previousSnapshot ?: UiTreeCapture.captureCurrentScreen()
                        val targetNode = extractNodeFromEvent(event, before)
                        enqueueDelayedRecording(ActionType.SCROLL_FORWARD, targetNode, null, before)
                    }
                }

                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    handleContentChangedToggleFallback(event)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error handling accessibility event (${event.eventType}, pkg=${event.packageName}): ${e.message}", e)
        }
    }

    fun stopSession(truncatedAtBoundary: Boolean = false): TeachingSession {
        val session = _currentSession.value ?: throw IllegalStateException("No active session to stop")

        // Flush any pending delayed recordings before pruning and finalizing
        flushPendingRecordings()

        // Automatically prune any trailing actions on SaySo / Recents / Launcher
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
     * Prunes trailing noise actions: filtered actions, blank package, internal SaySo package,
     * launcher/systemui/quickstep packages, or app-switching keywords.
     * Stops at the first real target-app action.
     */
    private fun pruneTrailingReturningActions(session: TeachingSession) {
        while (session.rawActions.isNotEmpty()) {
            val last = session.rawActions.last()
            val pkg = last.packageName.lowercase()
            val text = ((last.targetNode.text ?: "") + " " + (last.targetNode.contentDescription ?: "")).lowercase()

            val isNoise = last.isFiltered ||
                    pkg.isBlank() ||
                    pkg == "com.samsung.prism.teachable" ||
                    pkg.contains("launcher") ||
                    pkg.contains("systemui") ||
                    pkg.contains("quickstep") ||
                    text.contains("prism teachable") ||
                    text.contains("sayso") ||
                    text.contains("recent") ||
                    text.contains("overview")

            if (isNoise) {
                Log.i(TAG, "Pruned trailing noise action: ${last.semanticDescription} (pkg=$pkg, filtered=${last.isFiltered}, reason=${last.filterReason})")
                session.rawActions.removeAt(session.rawActions.size - 1)
            } else {
                break
            }
        }
    }

    fun cancelSession(): TeachingSession? {
        val session = _currentSession.value ?: return null
        synchronized(pendingRecordings) {
            for (pending in pendingRecordings) {
                pending.isExecuted = true
            }
            pendingRecordings.clear()
        }
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
                val isChecked = try { source.isChecked } catch (_: Exception) { false }
                val isSelected = try { source.isSelected } catch (_: Exception) { false }

                return UiNode(
                    resourceId = resId,
                    text = text,
                    contentDescription = desc,
                    className = cls,
                    packageName = pkg,
                    clickable = source.isClickable,
                    scrollable = source.isScrollable,
                    enabled = source.isEnabled,
                    isChecked = isChecked,
                    isSelected = isSelected,
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

        val isChecked = try { event.isChecked } catch (_: Exception) { false }
        return extractNodeFromFallback(
            texts = event.text,
            contentDescription = event.contentDescription,
            className = event.className,
            packageName = event.packageName,
            isChecked = isChecked
        )
    }

    internal fun extractNodeFromFallback(
        texts: List<CharSequence>,
        contentDescription: CharSequence?,
        className: CharSequence?,
        packageName: CharSequence?,
        isChecked: Boolean = false
    ): UiNode {
        val text = texts.joinToString(" ").takeIf { it.isNotBlank() }
        val desc = contentDescription?.toString()?.takeIf { it.isNotBlank() }
        val cls = className?.toString()
        val pkg = packageName?.toString()

        return UiNode(
            text = text,
            contentDescription = desc,
            className = cls,
            packageName = pkg,
            clickable = true,
            isChecked = isChecked,
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
