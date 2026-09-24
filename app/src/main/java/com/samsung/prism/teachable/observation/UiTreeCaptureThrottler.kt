package com.samsung.prism.teachable.observation

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Throttles rapid accessibility event bursts (especially TYPE_WINDOW_CONTENT_CHANGED)
 * to one UiSnapshot per ~150ms window per systemdesign.md §21.
 */
class UiTreeCaptureThrottler(
    private val debounceMs: Long = 150L,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val _snapshots = MutableSharedFlow<UiSnapshot>(extraBufferCapacity = 16)
    val snapshots: SharedFlow<UiSnapshot> = _snapshots.asSharedFlow()

    private var debounceJob: Job? = null
    private var lastSnapshotTime: Long = 0L

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType = event.eventType

        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // Immediate capture on window state change or clicks
                debounceJob?.cancel()
                captureImmediate()
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Debounced capture
                val now = System.currentTimeMillis()
                if (now - lastSnapshotTime > debounceMs * 2) {
                    debounceJob?.cancel()
                    captureImmediate()
                } else {
                    debounceJob?.cancel()
                    debounceJob = scope.launch {
                        delay(debounceMs)
                        captureImmediate()
                    }
                }
            }
        }
    }

    private fun captureImmediate() {
        lastSnapshotTime = System.currentTimeMillis()
        scope.launch {
            val snapshot = UiTreeCapture.captureCurrentScreen()
            if (snapshot.nodeCount > 0) {
                _snapshots.emit(snapshot)
            }
        }
    }
}
