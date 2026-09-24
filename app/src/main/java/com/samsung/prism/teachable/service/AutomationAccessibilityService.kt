package com.samsung.prism.teachable.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Privileged AccessibilityService required for:
 * 1. UI Tree Capture during Teaching and Replay
 * 2. Gesture and Action Dispatching (click, text input, scroll)
 * 3. Pre-action Credential/Payment Boundary Detection
 */
class AutomationAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AutomationAccessibilityService connected")
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            com.samsung.prism.teachable.teaching.TeachingRecorder.instance.handleAccessibilityEvent(event)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AutomationAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "AutomationAccessibilityService destroyed")
        if (instance == this) {
            instance = null
            _isConnected.value = false
        }
    }

    companion object {
        private const val TAG = "PrismA11yService"

        @Volatile
        var instance: AutomationAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun isServiceBound(): Boolean = instance != null
    }
}
