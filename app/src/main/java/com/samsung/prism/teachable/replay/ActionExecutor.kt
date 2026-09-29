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
            val switchNode = findSwitchInHierarchy(a11yNode)
            val isTargetSwitch = target.semanticRole == "switch" ||
                    target.className?.contains("Switch", ignoreCase = true) == true ||
                    target.resourceId?.contains("switch", ignoreCase = true) == true ||
                    switchNode != null

            // Special handling for toggle/switch controls:
            // Must target the actual switch widget or switch thumb directly
            if (switchNode != null) {
                val wasChecked = try { switchNode.isChecked } catch (_: Exception) { false }
                val switchRect = android.graphics.Rect()
                switchNode.getBoundsInScreen(switchRect)

                // 1. Try ACTION_CLICK on the switch node directly
                val clicked = switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                // Settle briefly and check if the switch state actually flipped
                delay(60)
                val stateChanged = try {
                    switchNode.refresh()
                    switchNode.isChecked != wasChecked
                } catch (_: Exception) {
                    false
                }

                if (stateChanged) {
                    Log.i(tag, "Switch toggled successfully via ACTION_CLICK: $wasChecked -> ${switchNode.isChecked}")
                    lastActionTimestamp = System.currentTimeMillis()
                    return true
                }

                // 2. If ACTION_CLICK did not change state, dispatch physical touch gesture directly on switch widget
                if (!switchRect.isEmpty) {
                    val gestureSuccess = executeGestureClick(
                        switchRect.centerX().toFloat(),
                        switchRect.centerY().toFloat()
                    )
                    Log.i(tag, "Dispatched physical gesture on switch: (${switchRect.centerX()}, ${switchRect.centerY()}), success=$gestureSuccess")
                    if (gestureSuccess) {
                        lastActionTimestamp = System.currentTimeMillis()
                        return true
                    }
                }

                if (clicked) {
                    lastActionTimestamp = System.currentTimeMillis()
                    return true
                }
            } else if (isTargetSwitch) {
                // Fallback to row right-edge where the toggle switch is placed in settings lists
                val rowContainer = climbToRowContainer(a11yNode)
                val rowRect = android.graphics.Rect()
                rowContainer.getBoundsInScreen(rowRect)
                if (!rowRect.isEmpty) {
                    val clickX = (rowRect.right - 80f).coerceAtLeast(rowRect.centerX().toFloat())
                    val clickY = rowRect.centerY().toFloat()
                    val gestureSuccess = executeGestureClick(clickX, clickY)
                    if (gestureSuccess) {
                        lastActionTimestamp = System.currentTimeMillis()
                        return true
                    }
                }
            }

            // 1. Direct click on node
            var clicked = a11yNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            // 2. If node is not clickable (e.g. TextView in a Preference row), climb parent and check siblings/controls
            if (!clicked) {
                var parent = a11yNode.parent
                var climbCount = 0
                while (parent != null && !clicked && climbCount < 4) {
                    if (parent.isClickable) {
                        clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    if (!clicked) {
                        // Check if parent container has a clickable switch/control widget
                        val clickableChild = findClickableDescendant(parent)
                        if (clickableChild != null && clickableChild != a11yNode) {
                            clicked = clickableChild.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                    }
                    if (!clicked) {
                        parent = parent.parent
                        climbCount++
                    }
                }
            }

            // 3. If still not clicked, check its own children for a clickable element
            if (!clicked) {
                val clickableChild = findClickableDescendant(a11yNode)
                if (clickableChild != null) {
                    clicked = clickableChild.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }

            if (clicked) {
                lastActionTimestamp = System.currentTimeMillis()
                return true
            }

            // 4. Fallback to physical gesture click using screen bounds of the located a11y node
            val nodeRect = android.graphics.Rect()
            a11yNode.getBoundsInScreen(nodeRect)
            if (!nodeRect.isEmpty) {
                val clickX = nodeRect.centerX().toFloat()
                val clickY = nodeRect.centerY().toFloat()
                val gestureSuccess = executeGestureClick(clickX, clickY)
                if (gestureSuccess) {
                    lastActionTimestamp = System.currentTimeMillis()
                    return true
                }
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

        // 1. Prefer text match when text is present (most distinct and human-aligned)
        if (!target.text.isNullOrEmpty()) {
            val byText = root.findAccessibilityNodeInfosByText(target.text)
            if (!byText.isNullOrEmpty()) {
                if (!target.bounds.isEmpty()) {
                    val tempRect = android.graphics.Rect()
                    val closest = byText.minByOrNull { node ->
                        node.getBoundsInScreen(tempRect)
                        val dx = tempRect.centerX() - target.bounds.centerX
                        val dy = tempRect.centerY() - target.bounds.centerY
                        dx * dx + dy * dy
                    }
                    if (closest != null) return closest
                }
                return byText[0]
            }
        }

        // 2. Try resourceId (disambiguating by bounds if multiple identical IDs exist, e.g. switch_widget)
        if (!target.resourceId.isNullOrEmpty()) {
            val byId = root.findAccessibilityNodeInfosByViewId(target.resourceId)
            if (!byId.isNullOrEmpty()) {
                if (!target.bounds.isEmpty()) {
                    val tempRect = android.graphics.Rect()
                    val closest = byId.minByOrNull { node ->
                        node.getBoundsInScreen(tempRect)
                        val dx = tempRect.centerX() - target.bounds.centerX
                        val dy = tempRect.centerY() - target.bounds.centerY
                        dx * dx + dy * dy
                    }
                    if (closest != null) return closest
                }
                return byId[0]
            }
        }

        // 3. Fallback: Find by contentDescription
        if (!target.contentDescription.isNullOrEmpty()) {
            fun findByDesc(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (node.contentDescription?.toString()?.contains(target.contentDescription, ignoreCase = true) == true) {
                    return node
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    val found = findByDesc(child)
                    if (found != null) return found
                }
                return null
            }
            val byDesc = findByDesc(root)
            if (byDesc != null) return byDesc
        }

        // 4. Fallback: Find node by bounds center
        if (!target.bounds.isEmpty()) {
            val cx = target.bounds.centerX
            val cy = target.bounds.centerY
            val tempRect = android.graphics.Rect()
            fun searchByBounds(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                node.getBoundsInScreen(tempRect)
                if (tempRect.contains(cx, cy)) {
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i) ?: continue
                        val inChild = searchByBounds(child)
                        if (inChild != null) return inChild
                    }
                    return node
                }
                return null
            }
            val byBounds = searchByBounds(root)
            if (byBounds != null) return byBounds
        }

        return null
    }

    private fun findClickableDescendant(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 4) return null
        if (node.isClickable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findClickableDescendant(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun isSwitchNode(node: AccessibilityNodeInfo): Boolean {
        val cls = node.className?.toString() ?: ""
        val resId = try { node.viewIdResourceName ?: "" } catch (_: Exception) { "" }
        val checkable = try { node.isCheckable } catch (_: Exception) { false }
        return checkable ||
                cls.contains("Switch", ignoreCase = true) ||
                cls.contains("CompoundButton", ignoreCase = true) ||
                cls.contains("CheckBox", ignoreCase = true) ||
                resId.contains("switch", ignoreCase = true)
    }

    private fun findSwitchDescendant(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
        if (depth > 5) return null
        if (isSwitchNode(node)) return node
        val count = try { node.childCount } catch (_: Exception) { 0 }
        for (i in 0 until count) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            if (isSwitchNode(child)) {
                return child
            }
            val found = findSwitchDescendant(child, depth + 1)
            if (found != null) {
                return found
            }
        }
        return null
    }

    private fun climbToRowContainer(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current = node
        var count = 0
        while (count < 4) {
            val p = try { current.parent } catch (_: Exception) { null } ?: break
            val cls = p.className?.toString() ?: ""
            if (cls.contains("RecyclerView", ignoreCase = true) ||
                cls.contains("ListView", ignoreCase = true) ||
                cls.contains("ScrollView", ignoreCase = true)
            ) {
                // p is the scroll container, so current is the row!
                break
            }
            current = p
            count++
        }
        return current
    }

    private fun findSwitchInHierarchy(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1. Is node itself a switch?
        if (isSwitchNode(node)) return node

        // 2. Is there a switch inside node?
        val inside = findSwitchDescendant(node)
        if (inside != null) return inside

        // 3. Check siblings by climbing to row container
        val row = climbToRowContainer(node)
        if (row != node) {
            val inRow = findSwitchDescendant(row)
            if (inRow != null) return inRow
        }

        // 4. Try ancestors directly
        var p = try { node.parent } catch (_: Exception) { null }
        var depth = 0
        while (p != null && depth < 4) {
            val inP = findSwitchDescendant(p)
            if (inP != null) return inP
            p = try { p.parent } catch (_: Exception) { null }
            depth++
        }

        return null
    }
}
