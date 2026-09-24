package com.samsung.prism.teachable.model

import org.json.JSONArray
import org.json.JSONObject

data class SlotDefinition(
    val name: String,
    val type: String = "string", // "string", "integer", "enum"
    val required: Boolean = true,
    val defaultValue: String? = null,
    val sourceStepId: String? = null,
    val enumValues: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("type", type)
        put("required", required)
        defaultValue?.let { put("defaultValue", it) }
        sourceStepId?.let { put("sourceStepId", it) }
        if (enumValues.isNotEmpty()) {
            val arr = JSONArray()
            enumValues.forEach { arr.put(it) }
            put("enumValues", arr)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SlotDefinition {
            val enums = mutableListOf<String>()
            val arr = json.optJSONArray("enumValues")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    enums.add(arr.getString(i))
                }
            }
            return SlotDefinition(
                name = json.getString("name"),
                type = json.optString("type", "string"),
                required = json.optBoolean("required", true),
                defaultValue = json.optString("defaultValue").takeIf { it.isNotEmpty() },
                sourceStepId = json.optString("sourceStepId").takeIf { it.isNotEmpty() },
                enumValues = enums
            )
        }
    }
}

data class SlotSchema(
    val slots: List<SlotDefinition> = emptyList()
) {
    fun getSlot(name: String): SlotDefinition? = slots.find { it.name.equals(name, ignoreCase = true) }

    fun toJson(): JSONObject = JSONObject().apply {
        val arr = JSONArray()
        slots.forEach { arr.put(it.toJson()) }
        put("slots", arr)
    }

    companion object {
        val EMPTY = SlotSchema(emptyList())

        fun fromJson(json: JSONObject): SlotSchema {
            val list = mutableListOf<SlotDefinition>()
            val arr = json.optJSONArray("slots")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    list.add(SlotDefinition.fromJson(arr.getJSONObject(i)))
                }
            }
            return SlotSchema(list)
        }
    }
}
