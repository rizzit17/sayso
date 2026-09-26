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

        // 5. Address / Location / Destination slot
        if (slot.name == "address" || slot.name == "destination" || slot.name == "location") {
            if (!slot.enumValues.isNullOrEmpty()) {
                val matched = matchEnumSynonym(lower, slot.enumValues)
                if (matched != null) {
                    return matched
                }
            }
            val addressRegex = Regex(
                "(?:deliver to|to|at|directions to|route to)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|app|from)|$)",
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

        // 6. Setting slot (e.g. "Airplane mode", "WiFi", "Bluetooth", "Dark theme")
        if (slot.name == "setting") {
            val settingMatch = Regex(
                "(?:toggle|turn\\s+on|turn\\s+off|enable|disable|set|switch)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:in|on|using|via|app)|$)",
                RegexOption.IGNORE_CASE
            ).find(rawUtterance)
            settingMatch?.groupValues?.get(1)?.trim()?.let {
                if (it.isNotEmpty()) return cleanExtractedItem(it)
            }
            val defaultVal = slot.defaultValue
            if (defaultVal != null && lower.contains(defaultVal.lowercase())) {
                return defaultVal
            }
        }

        // 7. Recipient / Contact slot (e.g. "Mom", "John", "Sarah")
        if (slot.name == "recipient" || slot.name == "contact") {
            val recipientMatch = Regex(
                "(?:to|contact|message)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:saying|with|that|on|in|using)|$)",
                RegexOption.IGNORE_CASE
            ).find(rawUtterance)
            recipientMatch?.groupValues?.get(1)?.trim()?.let {
                if (it.isNotEmpty()) return cleanExtractedItem(it)
            }
            val defaultVal = slot.defaultValue
            if (defaultVal != null && lower.contains(defaultVal.lowercase())) {
                return defaultVal
            }
        }

        // 8. Message / Body slot
        if (slot.name == "message" || slot.name == "body") {
            val messageMatch = Regex(
                "(?:saying|that|message)\\s+[\"']?(.+?)[\"']?$",
                RegexOption.IGNORE_CASE
            ).find(rawUtterance)
            messageMatch?.groupValues?.get(1)?.trim()?.let {
                if (it.isNotEmpty()) return it
            }
            val defaultVal = slot.defaultValue
            if (defaultVal != null) return defaultVal
        }

        // 9. Media / Song / Audio title
        if (slot.name == "media" || slot.name == "song") {
            val mediaMatch = Regex(
                "(?:play|listen\\s+to)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|by)|$)",
                RegexOption.IGNORE_CASE
            ).find(rawUtterance)
            mediaMatch?.groupValues?.get(1)?.trim()?.let {
                if (it.isNotEmpty()) return cleanExtractedItem(it)
            }
            val defaultVal = slot.defaultValue
            if (defaultVal != null && lower.contains(defaultVal.lowercase())) {
                return defaultVal
            }
        }

        // 10. Time / Alarm slot
        if (slot.name == "time" || slot.name == "alarm") {
            val timeMatch = Regex(
                "(?:for|at)\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|\\d+\\s*(?:minutes?|hours?|seconds?))",
                RegexOption.IGNORE_CASE
            ).find(rawUtterance)
            timeMatch?.groupValues?.get(1)?.trim()?.let {
                if (it.isNotEmpty()) return it
            }
            val defaultVal = slot.defaultValue
            if (defaultVal != null && lower.contains(defaultVal.lowercase())) {
                return defaultVal
            }
        }

        // 11. Generic query slot
        if (slot.name == "query") {
            val queryMatch = Regex(
                "(?:search\\s+for|look\\s+up|find)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using)|$)",
                RegexOption.IGNORE_CASE
            ).find(rawUtterance)
            queryMatch?.groupValues?.get(1)?.trim()?.let {
                if (it.isNotEmpty()) return cleanExtractedItem(it)
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
