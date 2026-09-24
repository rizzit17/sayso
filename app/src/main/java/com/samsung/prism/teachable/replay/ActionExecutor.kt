package com.samsung.prism.teachable.replay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.service.AutomationAccessibilityService
import kotlinx.coroutines.delay

interface IActionExecutor {
    suspend fun executeClick(target: UiNode): Boolean
    suspend fun executeSetText(target: UiNode, text: String): Boolean
    suspend fun executeScroll(forward: Boolean, target: UiNode?): Boolean
    suspend fun executeGestureClick(x: Float, y: Float): Boolean
    val minInterActionDelayMs: Long
}

class ActionExecutor(
    override val minInterActionDelayMs: Long = 300L
) : IActionExecutor {

    private val tag = "ActionExecutor"
    private var lastActionTimestamp: Long = 0

    override suspend fun executeClick(target: UiNode): Boolean {
        ensureSafety(target)
        enforceInterActionDelay()

        val service = AutomationAccessibilityService.instance ?: run {
            Log.w(tag, "AccessibilityService not bound; click cannot be dispatched")
            return false
        }

        val a11yNode = findA11yNode(service, target)
        if (a11yNode != null) {
            val clicked = a11yNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            a11yNode.recycle()
            if (clicked) {
                lastActionTimestamp = System.currentTimeMillis()
                return true
            }
        }

        // Fallback to gesture click at normalized center or bounds center
        val (cx, cy) = if (!target.bounds.isEmpty()) {
            Pair(target.bounds.centerX.toFloat(), target.bounds.centerY.toFloat())
        } else {
            Pair(null, null)
        }

        return if (cx != null && cy != null) {
            executeGestureClick(cx, cy)
        } else {
            false
        }
    }

    override suspend fun executeSetText(target: UiNode, text: String): Boolean {
        ensureSafety(target)
        enforceInterActionDelay()

        val service = AutomationAccessibilityService.instance ?: return false
        val a11yNode = findA11yNode(service, target) ?: return false

        // Focus first if needed
        a11yNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = a11yNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        a11yNode.recycle()
        if (success) {
            lastActionTimestamp = System.currentTimeMillis()
        }
        return success
    }

    override suspend fun executeScroll(forward: Boolean, target: UiNode?): Boolean {
        enforceInterActionDelay()

        val service = AutomationAccessibilityService.instance ?: return false
        val a11yNode = if (target != null) findA11yNode(service, target) else service.rootInActiveWindow

        if (a11yNode != null) {
            val action = if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            val success = a11yNode.performAction(action)
            a11yNode.recycle()
            if (success) {
                lastActionTimestamp = System.currentTimeMillis()
                return true
            }
        }
        return false
    }

    override suspend fun executeGestureClick(x: Float, y: Float): Boolean {
        enforceInterActionDelay()
        val service = AutomationAccessibilityService.instance ?: return false

        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        var dispatched = false
        try {
            dispatched = service.dispatchGesture(gesture, null, null)
            if (dispatched) {
                lastActionTimestamp = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to dispatch gesture click: ${e.message}")
        }
        return dispatched
    }

    private suspend fun enforceInterActionDelay() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastActionTimestamp
        if (elapsed < minInterActionDelayMs) {
            delay(minInterActionDelayMs - elapsed)
        }
    }

    private val detector = com.samsung.prism.teachable.security.CredentialBoundaryDetector()

    private fun ensureSafety(target: UiNode) {
        if (detector.isNodeDangerous(target)) {
            throw SecurityException("Security boundary triggered on target node: [${target.semanticRole}] '${target.text ?: target.contentDescription}'! Execution blocked.")
        }
    }

    private fun findA11yNode(service: AccessibilityService, target: UiNode): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null

        // 1. Try resourceId
        if (!target.resourceId.isNullOrEmpty()) {
            val byId = root.findAccessibilityNodeInfosByViewId(target.resourceId)
            if (!byId.isNullOrEmpty()) {
                return byId[0]
            }
        }

        // 2. Try text
        if (!target.text.isNullOrEmpty()) {
            val byText = root.findAccessibilityNodeInfosByText(target.text)
            if (!byText.isNullOrEmpty()) {
                return byText[0]
            }
        }

        return null
    }
}
