package com.samsung.prism.teachable.teaching

import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import org.json.JSONObject
import java.util.UUID

enum class ActionType {
    CLICK,
    SET_TEXT,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    LONG_CLICK
}

data class RawAction(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val actionType: ActionType,
    val inputText: String? = null,
    val targetNode: UiNode,
    val screenBefore: UiSnapshot,
    var screenAfter: UiSnapshot? = null,
    var isFiltered: Boolean = false,
    var filterReason: String? = null,
    var relevanceScore: Float = 1.0f
) {
    val semanticDescription: String
        get() {
            val targetLabel = targetNode.text
                ?: targetNode.contentDescription
                ?: targetNode.resourceId?.substringAfterLast(":id/")
                ?: targetNode.semanticRole
                ?: "element"

            return when (actionType) {
                ActionType.CLICK -> "Tapped '$targetLabel'"
                ActionType.SET_TEXT -> "Typed '${inputText ?: ""}' into '$targetLabel'"
                ActionType.SCROLL_FORWARD -> "Scrolled down on '$targetLabel'"
                ActionType.SCROLL_BACKWARD -> "Scrolled up on '$targetLabel'"
                ActionType.LONG_CLICK -> "Long-pressed '$targetLabel'"
            }
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("packageName", packageName)
        put("actionType", actionType.name)
        inputText?.let { put("inputText", it) }
        put("targetNode", targetNode.toJson())
        put("isFiltered", isFiltered)
        filterReason?.let { put("filterReason", it) }
        put("relevanceScore", relevanceScore.toDouble())
        put("semanticDescription", semanticDescription)
    }

    companion object {
        fun fromJson(json: JSONObject, before: UiSnapshot, after: UiSnapshot? = null): RawAction {
            return RawAction(
                id = json.optString("id", UUID.randomUUID().toString()),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                packageName = json.optString("packageName", ""),
                actionType = ActionType.valueOf(json.optString("actionType", "CLICK")),
                inputText = json.optString("inputText").takeIf { it.isNotEmpty() },
                targetNode = UiNode.fromJson(json.getJSONObject("targetNode")),
                screenBefore = before,
                screenAfter = after,
                isFiltered = json.optBoolean("isFiltered", false),
                filterReason = json.optString("filterReason").takeIf { it.isNotEmpty() },
                relevanceScore = json.optDouble("relevanceScore", 1.0).toFloat()
            )
        }
    }
}
