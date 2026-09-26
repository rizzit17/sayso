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
            val extracted = extractSingleSlot(slot, lower, utterance, workflow.slotSchema)
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
        rawUtterance: String,
        schema: com.samsung.prism.teachable.model.SlotSchema? = null
    ): Any? {
        // 1. Enum slots (e.g. platform, address, category enums)
        if (slot.type == "enum" && !slot.enumValues.isNullOrEmpty()) {
            val matched = matchEnumSynonym(lower, slot.enumValues)
            if (matched != null) {
                return matched
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

        // 3. Restaurant / Store / Source slot
        if (slot.name == "restaurant" || slot.name == "store" || slot.name == "merchant") {
            val restaurantRegex = Regex(
                "(?:from|at)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|app|to|deliver)|$)",
                RegexOption.IGNORE_CASE
            )
            val match = restaurantRegex.find(rawUtterance)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                val isPlatformName = schema?.slots?.any { otherSlot ->
                    otherSlot.name == "platform" && (
                        otherSlot.defaultValue.equals(candidate, ignoreCase = true) ||
                        otherSlot.enumValues.any { it.equals(candidate, ignoreCase = true) }
                    )
                } ?: false

                if (candidate.isNotEmpty() && !isPlatformName) {
                    return candidate
                }
            }
            val defaultVal = slot.defaultValue
            if (defaultVal != null && lower.contains(defaultVal.lowercase())) {
                return defaultVal
            }
        }

        // 4. Item / Search term slot
        if (slot.name == "item" || slot.name == "search_term") {
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

        // 5. Address / Location slot
        if (slot.name == "address" || slot.name == "destination" || slot.name == "location") {
            if (!slot.enumValues.isNullOrEmpty()) {
                val matched = matchEnumSynonym(lower, slot.enumValues)
                if (matched != null) {
                    return matched
                }
            }
            val addressRegex = Regex(
                "(?:deliver to|to|at)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|app|from)|$)",
                RegexOption.IGNORE_CASE
            )
            val match = addressRegex.find(rawUtterance)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotEmpty()) {
                    if (!slot.enumValues.isNullOrEmpty()) {
                        val enumMatched = matchEnumSynonym(candidate, slot.enumValues)
                        if (enumMatched != null) {
                            return enumMatched
                        }
                    }
                    return candidate.replaceFirstChar { it.uppercase() }
                }
            }
            val defaultVal = slot.defaultValue
            if (defaultVal != null && lower.contains(defaultVal.lowercase())) {
                return defaultVal
            }
        }

        return null
    }

    private fun matchEnumSynonym(spoken: String, enumValues: List<String>): String? {
        val s = spoken.lowercase().trim()
        val words = s.split(Regex("[^a-zA-Z0-9]+")).toSet()
        for (enumVal in enumValues) {
            val ev = enumVal.lowercase()
            if (ev in words) return enumVal
            if (ev == "work" && (words.contains("office") || words.contains("workplace"))) return enumVal
            if (ev == "home" && (words.contains("home") || words.contains("house") || words.contains("apartment") || words.contains("flat"))) return enumVal
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
