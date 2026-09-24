package com.samsung.prism.teachable.observation

import org.json.JSONArray
import org.json.JSONObject

data class UiNode(
    val resourceId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val bounds: Bounds = Bounds.ZERO,
    val clickable: Boolean = false,
    val isPassword: Boolean = false,
    val inputType: Int = 0,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val scrollable: Boolean = false,
    val semanticRole: String? = null,
    val parentContext: String? = null,
    val children: List<UiNode> = emptyList()
) {

    /**
     * Human-readable concise descriptor for developer inspection and AI tie-breaking prompts.
     */
    fun toDescriptor(): String {
        val label = when {
            !text.isNullOrBlank() -> "'$text'"
            !contentDescription.isNullOrBlank() -> "'$contentDescription'"
            else -> "<unlabeled>"
        }
        val roleStr = semanticRole ?: (className?.substringAfterLast('.') ?: "View")
        val idStr = resourceId?.let { " id=${it.substringAfterLast(":id/")}" } ?: ""
        val clickStr = if (clickable) " [clickable]" else ""
        val pwStr = if (isPassword) " [PASSWORD]" else ""
        return "[$roleStr] $label$idStr$clickStr$pwStr (${bounds.left},${bounds.top}-${bounds.right},${bounds.bottom})"
    }

    /**
     * Flatten this node and all of its descendants recursively into a flat list.
     */
    fun flatten(): List<UiNode> {
        val list = mutableListOf<UiNode>()
        fun traverse(node: UiNode) {
            list.add(node)
            for (child in node.children) {
                traverse(child)
            }
        }
        traverse(this)
        return list
    }

    fun toJson(): JSONObject = JSONObject().apply {
        resourceId?.let { put("resourceId", it) }
        text?.let { put("text", it) }
        contentDescription?.let { put("contentDescription", it) }
        className?.let { put("className", it) }
        packageName?.let { put("packageName", it) }
        put("bounds", bounds.toJson())
        if (clickable) put("clickable", true)
        if (isPassword) put("isPassword", true)
        if (inputType != 0) put("inputType", inputType)
        if (!enabled) put("enabled", false)
        if (focused) put("focused", true)
        if (scrollable) put("scrollable", true)
        semanticRole?.let { put("semanticRole", it) }
        parentContext?.let { put("parentContext", it) }
        if (children.isNotEmpty()) {
            val childrenArray = JSONArray()
            children.forEach { childrenArray.put(it.toJson()) }
            put("children", childrenArray)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): UiNode {
            val childrenList = mutableListOf<UiNode>()
            val childrenJson = json.optJSONArray("children")
            if (childrenJson != null) {
                for (i in 0 until childrenJson.length()) {
                    val childObj = childrenJson.optJSONObject(i)
                    if (childObj != null) {
                        childrenList.add(fromJson(childObj))
                    }
                }
            }

            return UiNode(
                resourceId = json.optString("resourceId").takeIf { it.isNotEmpty() },
                text = json.optString("text").takeIf { it.isNotEmpty() },
                contentDescription = json.optString("contentDescription").takeIf { it.isNotEmpty() },
                className = json.optString("className").takeIf { it.isNotEmpty() },
                packageName = json.optString("packageName").takeIf { it.isNotEmpty() },
                bounds = Bounds.fromJson(json.optJSONObject("bounds")),
                clickable = json.optBoolean("clickable", false),
                isPassword = json.optBoolean("isPassword", false),
                inputType = json.optInt("inputType", 0),
                enabled = json.optBoolean("enabled", true),
                focused = json.optBoolean("focused", false),
                scrollable = json.optBoolean("scrollable", false),
                semanticRole = json.optString("semanticRole").takeIf { it.isNotEmpty() },
                parentContext = json.optString("parentContext").takeIf { it.isNotEmpty() },
                children = childrenList
            )
        }
    }
}
