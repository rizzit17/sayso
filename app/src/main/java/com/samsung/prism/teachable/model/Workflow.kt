package com.samsung.prism.teachable.model

import com.samsung.prism.teachable.teaching.ActionType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class WorkflowStatus {
    DRAFT,
    ACTIVE,
    DEPRECATED
}

data class ExpectedStateTransition(
    val expectedTextSubstring: String? = null,
    val expectedRole: String? = null,
    val expectedPackageName: String? = null,
    val expectedSignatureChange: Boolean = true,
    val slotBoundKey: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        expectedTextSubstring?.let { put("expectedTextSubstring", it) }
        expectedRole?.let { put("expectedRole", it) }
        expectedPackageName?.let { put("expectedPackageName", it) }
        put("expectedSignatureChange", expectedSignatureChange)
        slotBoundKey?.let { put("slotBoundKey", it) }
    }

    companion object {
        val DEFAULT = ExpectedStateTransition(expectedSignatureChange = true)

        fun fromJson(json: JSONObject?): ExpectedStateTransition {
            if (json == null) return DEFAULT
            return ExpectedStateTransition(
                expectedTextSubstring = json.optString("expectedTextSubstring").takeIf { it.isNotEmpty() },
                expectedRole = json.optString("expectedRole").takeIf { it.isNotEmpty() },
                expectedPackageName = json.optString("expectedPackageName").takeIf { it.isNotEmpty() },
                expectedSignatureChange = json.optBoolean("expectedSignatureChange", true),
                slotBoundKey = json.optString("slotBoundKey").takeIf { it.isNotEmpty() }
            )
        }
    }
}

data class WorkflowStep(
    val id: String = UUID.randomUUID().toString(),
    val workflowId: String = "",
    val stepOrder: Int = 0,
    val actionType: ActionType = ActionType.CLICK,
    val inputText: String? = null,
    val target: StepTarget = StepTarget(),
    val expectedStateTransition: ExpectedStateTransition = ExpectedStateTransition.DEFAULT,
    val isBoundary: Boolean = false,
    val slotBinding: String? = null // Name of slot if parameterized (e.g. "item", "restaurant", "quantity", "address", "search_term")
) {
    val isParameterized: Boolean get() = slotBinding != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("workflowId", workflowId)
        put("stepOrder", stepOrder)
        put("actionType", actionType.name)
        inputText?.let { put("inputText", it) }
        put("target", target.toJson())
        put("expectedStateTransition", expectedStateTransition.toJson())
        put("isBoundary", isBoundary)
        slotBinding?.let { put("slotBinding", it) }
    }

    companion object {
        fun fromJson(json: JSONObject): WorkflowStep {
            return WorkflowStep(
                id = json.optString("id", UUID.randomUUID().toString()),
                workflowId = json.optString("workflowId", ""),
                stepOrder = json.optInt("stepOrder", 0),
                actionType = ActionType.valueOf(json.optString("actionType", "CLICK")),
                inputText = json.optString("inputText").takeIf { it.isNotEmpty() },
                target = StepTarget.fromJson(json.getJSONObject("target")),
                expectedStateTransition = ExpectedStateTransition.fromJson(json.optJSONObject("expectedStateTransition")),
                isBoundary = json.optBoolean("isBoundary", false),
                slotBinding = json.optString("slotBinding").takeIf { it.isNotEmpty() }
            )
        }
    }
}

data class Workflow(
    val id: String = UUID.randomUUID().toString(),
    val intentTag: String,
    val originalUtterance: String,
    val generalizedIntent: String,
    val intentEmbedding: FloatArray? = null,
    val supportedPackages: List<String> = emptyList(),
    val slotSchema: SlotSchema = SlotSchema.EMPTY,
    val steps: List<WorkflowStep> = emptyList(),
    val status: WorkflowStatus = WorkflowStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1
) {
    val primaryPackage: String? get() = supportedPackages.firstOrNull { it.isNotBlank() && it != "com.samsung.prism.teachable" }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Workflow
        return id == other.id && version == other.version
    }

    override fun hashCode(): Int = id.hashCode()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("intentTag", intentTag)
        put("originalUtterance", originalUtterance)
        put("generalizedIntent", generalizedIntent)
        put("status", status.name)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("version", version)

        val pkgs = JSONArray()
        supportedPackages.forEach { pkgs.put(it) }
        put("supportedPackages", pkgs)

        put("slotSchema", slotSchema.toJson())

        val stepsArr = JSONArray()
        steps.forEach { stepsArr.put(it.toJson()) }
        put("steps", stepsArr)
    }

    companion object {
        fun fromJson(json: JSONObject): Workflow {
            val pkgs = mutableListOf<String>()
            val pkgArr = json.optJSONArray("supportedPackages")
            if (pkgArr != null) {
                for (i in 0 until pkgArr.length()) {
                    pkgs.add(pkgArr.getString(i))
                }
            }

            val stepList = mutableListOf<WorkflowStep>()
            val stepsArr = json.optJSONArray("steps")
            if (stepsArr != null) {
                for (i in 0 until stepsArr.length()) {
                    stepList.add(WorkflowStep.fromJson(stepsArr.getJSONObject(i)))
                }
            }

            return Workflow(
                id = json.optString("id", UUID.randomUUID().toString()),
                intentTag = json.optString("intentTag", ""),
                originalUtterance = json.optString("originalUtterance", ""),
                generalizedIntent = json.optString("generalizedIntent", ""),
                supportedPackages = pkgs,
                slotSchema = json.optJSONObject("slotSchema")?.let { SlotSchema.fromJson(it) } ?: SlotSchema.EMPTY,
                steps = stepList,
                status = WorkflowStatus.valueOf(json.optString("status", "ACTIVE")),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                version = json.optInt("version", 1)
            )
        }
    }
}
