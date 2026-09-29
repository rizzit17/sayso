package com.samsung.prism.teachable.observation

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.samsung.prism.teachable.service.AutomationAccessibilityService

object UiTreeCapture {

    private const val MAX_DEPTH = 35

    /**
     * Captures a complete snapshot of the current active window from the AccessibilityService.
     * Returns UiSnapshot.EMPTY if service is not connected or no active window exists.
     */
    fun captureCurrentScreen(): UiSnapshot {
        val service = AutomationAccessibilityService.instance ?: return UiSnapshot.EMPTY
        val root = try {
            service.rootInActiveWindow
        } catch (e: Exception) {
            null
        } ?: return UiSnapshot.EMPTY

        return captureFromNodeInfo(root)
    }

    /**
     * Walks an AccessibilityNodeInfo tree and converts it into our serializable UiSnapshot model.
     */
    fun captureFromNodeInfo(rootInfo: AccessibilityNodeInfo): UiSnapshot {
        val pkg = rootInfo.packageName?.toString() ?: ""
        val tempRect = Rect()
        val rootNode = walkNode(rootInfo, depth = 0, parentDesc = null, tempRect = tempRect)

        return UiSnapshot(
            timestamp = System.currentTimeMillis(),
            packageName = pkg,
            activityName = null,
            rootNode = rootNode
        )
    }

    private fun walkNode(
        info: AccessibilityNodeInfo?,
        depth: Int,
        parentDesc: String?,
        tempRect: Rect
    ): UiNode? {
        if (info == null || depth > MAX_DEPTH) return null

        info.getBoundsInScreen(tempRect)
        val bounds = Bounds(tempRect.left, tempRect.top, tempRect.right, tempRect.bottom)

        val text = info.text?.toString()?.takeIf { it.isNotBlank() }
        val desc = info.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        val resId = info.viewIdResourceName?.takeIf { it.isNotBlank() }
        val clsName = info.className?.toString()
        val role = inferSemanticRole(clsName, info.isClickable, info.isScrollable, text, desc, resId)

        val currentDesc = text ?: desc ?: role

        val childCount = info.childCount
        val children = mutableListOf<UiNode>()
        if (childCount > 0) {
            for (i in 0 until childCount) {
                val childInfo = try {
                    info.getChild(i)
                } catch (e: Exception) {
                    null
                }
                if (childInfo != null) {
                    val childNode = walkNode(childInfo, depth + 1, currentDesc, tempRect)
                    if (childNode != null) {
                        children.add(childNode)
                    }
                }
            }
        }

        val isChecked = try { info.isChecked } catch (_: Exception) { false }
        val isSelected = try { info.isSelected } catch (_: Exception) { false }

        return UiNode(
            resourceId = resId,
            text = text,
            contentDescription = desc,
            className = clsName,
            packageName = info.packageName?.toString(),
            bounds = bounds,
            clickable = info.isClickable,
            isPassword = info.isPassword,
            inputType = info.inputType,
            enabled = info.isEnabled,
            focused = info.isFocused,
            scrollable = info.isScrollable,
            isChecked = isChecked,
            isSelected = isSelected,
            semanticRole = role,
            parentContext = parentDesc,
            children = children
        )
    }

    /**
     * Inferred role (button, input, checkbox, list_item, search_box, etc.) per systemdesign.md §6.1.
     */
    fun inferSemanticRole(
        className: String?,
        isClickable: Boolean,
        isScrollable: Boolean,
        text: String?,
        contentDescription: String?,
        resourceId: String?
    ): String {
        val cls = className?.lowercase() ?: ""
        val id = resourceId?.lowercase() ?: ""
        val allText = ((text ?: "") + " " + (contentDescription ?: "")).lowercase()

        return when {
            cls.contains("edittext") || cls.contains("textinput") || id.contains("search") || id.contains("edit") -> {
                if (id.contains("search") || allText.contains("search")) "search_box" else "input_field"
            }
            cls.contains("checkbox") || cls.contains("check") -> "checkbox"
            cls.contains("radio") -> "radio_button"
            cls.contains("switch") -> "switch"
            cls.contains("button") || (isClickable && (id.contains("btn") || id.contains("button"))) -> "button"
            cls.contains("image") && isClickable -> "icon_button"
            cls.contains("image") -> "image"
            isScrollable || cls.contains("scroll") || cls.contains("recycler") || cls.contains("listview") -> "scroll_container"
            isClickable && (id.contains("item") || id.contains("row") || id.contains("card")) -> "list_item"
            isClickable -> "clickable_element"
            !text.isNullOrBlank() -> "text_label"
            else -> "container"
        }
    }
}
