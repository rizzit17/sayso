package com.samsung.prism.teachable.voice

import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.model.Workflow

class SlotExtractor {

    private val numberWords = mapOf(
        "one" to 1, "a" to 1, "an" to 1, "single" to 1,
        "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
    )

    fun extractSlots(workflow: Workflow, utterance: String): SlotExtractionResult {
        val bound = mutableMapOf<String, Any>()
        val missing = mutableListOf<String>()
        val lower = utterance.lowercase().trim()

        for (slot in workflow.slotSchema.slots) {
            val extracted = extractSingleSlot(slot, lower, utterance)
            if (extracted != null) {
                bound[slot.name] = extracted
            } else if (slot.defaultValue != null) {
                // Fall back to taught default value
                bound[slot.name] = coerceValue(slot, slot.defaultValue)
            } else if (slot.required) {
                missing.add(slot.name)
            }
        }

        return SlotExtractionResult(
            boundSlots = bound,
            missingRequiredSlots = missing
        )
    }

    private fun extractSingleSlot(
        slot: SlotDefinition,
        lower: String,
        rawUtterance: String
    ): Any? {
        // 1. Enum slots (e.g. platform: Zomato, Swiggy; address: Home, Work)
        if (slot.type == "enum" && !slot.enumValues.isNullOrEmpty()) {
            for (enumVal in slot.enumValues) {
                if (lower.contains(enumVal.lowercase())) {
                    return enumVal
                }
            }
        }

        // 2. Integer slots (e.g. quantity)
        if (slot.type == "integer" || slot.name == "quantity") {
            // Check numeric digits
            val digitMatch = Regex("\\b(\\d+)\\b").find(lower)
            if (digitMatch != null) {
                return digitMatch.groupValues[1].toIntOrNull() ?: 1
            }
            // Check word numbers
            for ((word, num) in numberWords) {
                if (Regex("\\b$word\\b").containsMatchIn(lower)) {
                    return num
                }
            }
        }

        // 3. Restaurant slot
        if (slot.name == "restaurant") {
            val restaurantRegex = Regex(
                "(?:from|at)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|app|to)|$)",
                RegexOption.IGNORE_CASE
            )
            val match = restaurantRegex.find(rawUtterance)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotEmpty() && !candidate.equals("zomato", ignoreCase = true) && !candidate.equals("swiggy", ignoreCase = true)) {
                    return candidate
                }
            }
            if (lower.contains("domino")) return "Domino's"
            if (lower.contains("subway")) return "Subway"
            if (lower.contains("burger king")) return "Burger King"
            if (lower.contains("mcdonald")) return "McDonald's"
        }

        // 4. Item / Search term slot
        if (slot.name == "item" || slot.name == "search_term") {
            // E.g. "Order Farmhouse pizza from Domino's" or "Buy Sony headphones on Amazon"
            val itemRegexList = listOf(
                Regex(
                    "(?:order|get|buy|want)\\s+(?:(?:a|an|one|two|three|four|five|\\d+)\\s+)?([a-zA-Z0-9'\\s]+?)(?:\\s+(?:from|at|on|using|to)|$)",
                    RegexOption.IGNORE_CASE
                ),
                Regex(
                    "(?:search\\s+for|find)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|and|using)|$)",
                    RegexOption.IGNORE_CASE
                )
            )
            for (regex in itemRegexList) {
                val match = regex.find(rawUtterance)
                if (match != null) {
                    val extractedItem = match.groupValues[1].trim()
                    if (extractedItem.isNotEmpty() && extractedItem.length > 2) {
                        return cleanExtractedItem(extractedItem)
                    }
                }
            }

            // Check if default value appears directly in utterance
            val defaultVal = slot.defaultValue
            if (defaultVal != null && lower.contains(defaultVal.lowercase())) {
                return defaultVal
            }
        }

        // 5. Address slot
        if (slot.name == "address") {
            if (lower.contains("work") || lower.contains("office")) return "Work"
            if (lower.contains("home") || lower.contains("house")) return "Home"
        }

        return null
    }

    private fun cleanExtractedItem(raw: String): String {
        var cleaned = raw
        val prefixesToRemove = listOf("a ", "an ", "the ", "some ")
        for (prefix in prefixesToRemove) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length)
            }
        }
        return cleaned.trim().replaceFirstChar { it.uppercase() }
    }

    private fun coerceValue(slot: SlotDefinition, value: String): Any {
        return when (slot.type) {
            "integer" -> value.toIntOrNull() ?: 1
            "boolean" -> value.toBoolean()
            "float" -> value.toDoubleOrNull() ?: 0.0
            else -> value
        }
    }
}
