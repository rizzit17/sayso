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
        previousSnapshot = UiTreeCapture.captureCurrentScreen()
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

        val action = RawAction(
            packageName = before.packageName.ifEmpty { targetNode.packageName ?: "" },
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

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val before = previousSnapshot ?: UiTreeCapture.captureCurrentScreen()
                val targetNode = extractNodeFromEvent(event)
                val after = UiTreeCapture.captureCurrentScreen()
                recordAction(ActionType.CLICK, targetNode, null, before, after)
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

    private fun extractNodeFromEvent(event: AccessibilityEvent): UiNode {
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

    companion object {
        private const val TAG = "TeachingRecorder"
        val instance by lazy { TeachingRecorder() }
    }
}
