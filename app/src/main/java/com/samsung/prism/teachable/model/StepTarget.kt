package com.samsung.prism.teachable.model

import com.samsung.prism.teachable.observation.Bounds
import com.samsung.prism.teachable.observation.UiNode
import org.json.JSONObject

data class StepTarget(
    val resourceId: String? = null,
    val contentDescription: String? = null,
    val text: String? = null,
    val semanticRole: String? = null,
    val className: String? = null,
    val parentContext: String? = null,
    val screenSignature: String? = null,
    val boundsRelativeX: Float? = null,
    val boundsRelativeY: Float? = null,
    val domainConcept: String? = null // For Bonus B2 cross-app generalization (e.g. "add_to_cart_button")
) {

    fun toJson(): JSONObject = JSONObject().apply {
        resourceId?.let { put("resourceId", it) }
        contentDescription?.let { put("contentDescription", it) }
        text?.let { put("text", it) }
        semanticRole?.let { put("semanticRole", it) }
        className?.let { put("className", it) }
        parentContext?.let { put("parentContext", it) }
        screenSignature?.let { put("screenSignature", it) }
        boundsRelativeX?.let { put("boundsRelativeX", it.toDouble()) }
        boundsRelativeY?.let { put("boundsRelativeY", it.toDouble()) }
        domainConcept?.let { put("domainConcept", it) }
    }

    companion object {
        fun fromNode(
            node: UiNode,
            screenSig: String? = null,
            screenWidth: Int = 1080,
            screenHeight: Int = 2400,
            domainConcept: String? = null
        ): StepTarget {
            val (rx, ry) = if (!node.bounds.isEmpty()) {
                node.bounds.normalizedCenter(screenWidth, screenHeight)
            } else {
                Pair(null, null)
            }

            return StepTarget(
                resourceId = node.resourceId,
                contentDescription = node.contentDescription,
                text = node.text,
                semanticRole = node.semanticRole,
                className = node.className,
                parentContext = node.parentContext,
                screenSignature = screenSig,
                boundsRelativeX = rx,
                boundsRelativeY = ry,
                domainConcept = domainConcept ?: inferDomainConcept(node)
            )
        }

        fun inferDomainConcept(node: UiNode): String? {
            val allText = ((node.text ?: "") + " " + (node.contentDescription ?: "") + " " + (node.resourceId ?: "")).lowercase()
            return when {
                allText.contains("add to cart") || allText.contains("add to bag") || (allText.contains("add") && node.semanticRole == "button") -> "add_to_cart_button"
                allText.contains("buy now") || allText.contains("proceed to checkout") -> "checkout_button"
                allText.contains("search") && (node.semanticRole == "search_box" || node.semanticRole == "input_field") -> "search_bar"
                allText.contains("cart") || allText.contains("basket") -> "cart_icon"
                else -> null
            }
        }

        fun fromJson(json: JSONObject): StepTarget {
            return StepTarget(
                resourceId = json.optString("resourceId").takeIf { it.isNotEmpty() },
                contentDescription = json.optString("contentDescription").takeIf { it.isNotEmpty() },
                text = json.optString("text").takeIf { it.isNotEmpty() },
                semanticRole = json.optString("semanticRole").takeIf { it.isNotEmpty() },
                className = json.optString("className").takeIf { it.isNotEmpty() },
                parentContext = json.optString("parentContext").takeIf { it.isNotEmpty() },
                screenSignature = json.optString("screenSignature").takeIf { it.isNotEmpty() },
                boundsRelativeX = if (json.has("boundsRelativeX")) json.optDouble("boundsRelativeX").toFloat() else null,
                boundsRelativeY = if (json.has("boundsRelativeY")) json.optDouble("boundsRelativeY").toFloat() else null,
                domainConcept = json.optString("domainConcept").takeIf { it.isNotEmpty() }
            )
        }
    }
}
