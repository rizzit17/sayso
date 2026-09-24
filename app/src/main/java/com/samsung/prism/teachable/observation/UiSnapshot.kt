package com.samsung.prism.teachable.observation

import org.json.JSONObject
import java.security.MessageDigest

data class UiSnapshot(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String = "",
    val activityName: String? = null,
    val rootNode: UiNode? = null,
    val allNodes: List<UiNode> = rootNode?.flatten() ?: emptyList(),
    val screenSignature: String = generateSignature(packageName, allNodes)
) {
    val nodeCount: Int get() = allNodes.size

    fun findNodesByText(query: String, exact: Boolean = false): List<UiNode> {
        val q = query.trim().lowercase()
        return allNodes.filter { node ->
            val t = node.text?.lowercase() ?: ""
            val cd = node.contentDescription?.lowercase() ?: ""
            if (exact) {
                t == q || cd == q
            } else {
                t.contains(q) || cd.contains(q)
            }
        }
    }

    fun findNodesByResourceId(resourceId: String): List<UiNode> {
        return allNodes.filter { it.resourceId == resourceId }
    }

    fun findNodesByRole(role: String): List<UiNode> {
        return allNodes.filter { it.semanticRole.equals(role, ignoreCase = true) }
    }

    fun findClickableNodes(): List<UiNode> {
        return allNodes.filter { it.clickable }
    }

    fun containsPasswordOrCredentialField(): Boolean {
        return allNodes.any { it.isPassword }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("packageName", packageName)
        activityName?.let { put("activityName", it) }
        put("screenSignature", screenSignature)
        rootNode?.let { put("rootNode", it.toJson()) }
    }

    companion object {
        val EMPTY = UiSnapshot(timestamp = 0L, packageName = "")

        fun generateSignature(pkg: String, nodes: List<UiNode>): String {
            if (nodes.isEmpty()) return "$pkg:empty"
            val sb = StringBuilder(pkg).append('|')
            // Sample structural signatures (roles + IDs + clickable)
            for (node in nodes.take(50)) {
                sb.append(node.semanticRole ?: "V")
                node.resourceId?.let { sb.append(':').append(it.substringAfterLast('/')) }
                if (node.clickable) sb.append('!')
                sb.append(';')
            }
            return try {
                val digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().toByteArray())
                digest.take(8).joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                sb.toString().hashCode().toString(16)
            }
        }

        fun fromJson(json: JSONObject): UiSnapshot {
            val root = json.optJSONObject("rootNode")?.let { UiNode.fromJson(it) }
            val pkg = json.optString("packageName", "")
            val all = root?.flatten() ?: emptyList()
            val sig = json.optString("screenSignature").takeIf { it.isNotEmpty() }
                ?: generateSignature(pkg, all)

            return UiSnapshot(
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                packageName = pkg,
                activityName = json.optString("activityName").takeIf { it.isNotEmpty() },
                rootNode = root,
                allNodes = all,
                screenSignature = sig
            )
        }
    }
}
