package com.samsung.prism.teachable.generalization

import com.samsung.prism.teachable.model.ExpectedStateTransition
import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.model.SlotSchema
import com.samsung.prism.teachable.model.StepTarget
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.model.WorkflowStatus
import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.teaching.ActionType
import com.samsung.prism.teachable.teaching.TeachingSession
import java.util.UUID

class WorkflowGeneralizer {

    fun generalize(session: TeachingSession): Workflow {
        val retained = session.retainedActions
        if (retained.isEmpty()) {
            throw IllegalArgumentException("Cannot generalize degenerate workflow with zero retained actions")
        }

        val workflowId = UUID.randomUUID().toString()
        val utterance = session.originalUtterance
        val detectedSlots = extractCandidateSlots(utterance, session)

        val steps = mutableListOf<WorkflowStep>()
        for ((index, action) in retained.withIndex()) {
            val stepId = "step_$index"

            // Determine if this step binds to one of our detected slots
            val binding = findSlotBinding(action, detectedSlots)
            val isLast = index == retained.size - 1
            val isBoundary = isLast && session.truncatedAtBoundary

            val rootBounds = action.screenBefore.rootNode?.bounds
            val sw = if (rootBounds != null && rootBounds.width > 0) rootBounds.width else 1080
            val sh = if (rootBounds != null && rootBounds.height > 0) rootBounds.height else 2400

            val target = StepTarget.fromNode(
                node = action.targetNode,
                screenSig = action.screenBefore.screenSignature,
                screenWidth = sw,
                screenHeight = sh
            )

            val transition = ExpectedStateTransition(
                expectedTextSubstring = if (binding != null) "{$binding}" else action.inputText ?: action.targetNode.text,
                expectedRole = action.targetNode.semanticRole,
                expectedPackageName = action.screenAfter?.packageName ?: action.packageName,
                expectedSignatureChange = true,
                slotBoundKey = binding
            )

            steps.add(
                WorkflowStep(
                    id = stepId,
                    workflowId = workflowId,
                    stepOrder = index,
                    actionType = action.actionType,
                    inputText = action.inputText,
                    target = target,
                    expectedStateTransition = transition,
                    isBoundary = isBoundary,
                    slotBinding = binding
                )
            )
        }

        // Generate generalized canonical intent representation
        val (intentTag, canonicalIntent) = generateCanonicalIntent(utterance, detectedSlots)
        val supportedPackages = retained.map { it.packageName }.distinct()

        return Workflow(
            id = workflowId,
            intentTag = intentTag,
            originalUtterance = utterance,
            generalizedIntent = canonicalIntent,
            supportedPackages = supportedPackages,
            slotSchema = SlotSchema(detectedSlots),
            steps = steps,
            status = WorkflowStatus.ACTIVE,
            createdAt = session.startTime,
            updatedAt = System.currentTimeMillis()
        )
    }

    private val numberWords = mapOf(
        "one" to 1, "a" to 1, "an" to 1, "two" to 2, "three" to 3,
        "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
    )

    private fun extractCandidateSlots(
        utterance: String,
        session: TeachingSession
    ): List<SlotDefinition> {
        val lower = utterance.lowercase().trim()
        val slots = mutableListOf<SlotDefinition>()
        val existingSlotNames = mutableSetOf<String>()

        // 1. Dynamic Platform Extraction from preposition: "on <Platform>", "in <Platform>", "via <Platform>"
        val platformRegex = Regex(
            "(?:on|in|using|via|app)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:and|with|to|deliver|for)|$)",
            RegexOption.IGNORE_CASE
        )
        val platformMatch = platformRegex.find(utterance)
        val platformValue = platformMatch?.groupValues?.get(1)?.trim()?.replaceFirstChar { it.uppercase() }
            ?: session.targetPackageHint?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() }

        if (!platformValue.isNullOrBlank() && platformValue.length > 1 && !platformValue.equals("the", ignoreCase = true)) {
            val enumCandidates = mutableListOf(platformValue)

            slots.add(
                SlotDefinition(
                    name = "platform",
                    type = "enum",
                    required = true,
                    defaultValue = platformValue,
                    enumValues = enumCandidates.distinct()
                )
            )
            existingSlotNames.add("platform")
        }

        // 2. Dynamic Restaurant / Store / Source Extraction from preposition: "from <Store>", "at <Store>"
        val storeRegex = Regex(
            "(?:from|at)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|via|app|to|deliver|and)|$)",
            RegexOption.IGNORE_CASE
        )
        val storeMatch = storeRegex.find(utterance)
        val storeValue = storeMatch?.groupValues?.get(1)?.trim()?.let { cleanEntity(it) }
        if (!storeValue.isNullOrBlank() && storeValue.length > 1 && !existingSlotNames.contains("platform") || storeValue != platformValue) {
            if (!storeValue.isNullOrBlank() && !storeValue.equals(platformValue, ignoreCase = true)) {
                slots.add(
                    SlotDefinition(
                        name = "restaurant",
                        type = "string",
                        required = true,
                        defaultValue = storeValue
                    )
                )
                existingSlotNames.add("restaurant")
            }
        }

        // 3. Dynamic Item Extraction (Prioritize text typed by user during demonstration, else parse from utterance)
        val typedAction = session.retainedActions.firstOrNull {
            it.actionType == ActionType.SET_TEXT && !it.inputText.isNullOrBlank()
        }
        val itemFromTyped = typedAction?.inputText?.trim()
        val itemValue = if (!itemFromTyped.isNullOrBlank()) {
            cleanEntity(itemFromTyped)
        } else {
            extractItemFromUtterance(utterance, platformValue, storeValue)
        }

        if (!itemValue.isNullOrBlank()) {
            slots.add(
                SlotDefinition(
                    name = "item",
                    type = "string",
                    required = true,
                    defaultValue = itemValue
                )
            )
            existingSlotNames.add("item")
        }

        // 4. Dynamic Quantity / Number Extraction
        val quantityValue = extractQuantity(lower)
        if (quantityValue != null || lower.contains("order") || lower.contains("buy")) {
            slots.add(
                SlotDefinition(
                    name = "quantity",
                    type = "integer",
                    required = false,
                    defaultValue = (quantityValue ?: 1).toString()
                )
            )
            existingSlotNames.add("quantity")
        }

        // 5. Dynamic Address / Destination Extraction
        val addressRegex = Regex(
            "(?:deliver to|to)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|using|via|app|from|and)|$)",
            RegexOption.IGNORE_CASE
        )
        val addressMatch = addressRegex.find(utterance)
        val addressCandidate = addressMatch?.groupValues?.get(1)?.trim()?.let { cleanEntity(it) }
        val excludedLocations = setOf("cart", "bag", "checkout", "basket", "wishlist", "buy", "order")
        val addressValue = if (!addressCandidate.isNullOrBlank() &&
            addressCandidate.lowercase() !in excludedLocations &&
            addressCandidate != platformValue &&
            addressCandidate != storeValue
        ) {
            addressCandidate
        } else if (lower.contains("order") || lower.contains("deliver")) {
            "Home"
        } else {
            null
        }

        if (addressValue != null) {
            slots.add(
                SlotDefinition(
                    name = "address",
                    type = "enum",
                    required = false,
                    defaultValue = addressValue,
                    enumValues = listOf("Home", "Work")
                )
            )
            existingSlotNames.add("address")
        }

        return slots
    }

    private fun extractItemFromUtterance(
        utterance: String,
        platform: String?,
        store: String?
    ): String? {
        val itemRegexList = listOf(
            Regex("(?:order|buy|get|want)\\s+(?:(?:a|an|the|some|one|two|three|four|five|\\d+)\\s+)?([a-zA-Z0-9'\\s]+?)(?:\\s+(?:from|at|on|in|using|to|deliver|and)|$)", RegexOption.IGNORE_CASE),
            Regex("(?:search\\s+for|search|find)\\s+([a-zA-Z0-9'\\s]+?)(?:\\s+(?:on|in|and|using)|$)", RegexOption.IGNORE_CASE)
        )
        for (regex in itemRegexList) {
            val match = regex.find(utterance)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.length > 2 && candidate != platform && candidate != store) {
                    return cleanEntity(candidate)
                }
            }
        }
        return null
    }

    private fun extractQuantity(lower: String): Int? {
        val digitMatch = Regex("\\b(\\d+)\\b").find(lower)
        if (digitMatch != null) {
            return digitMatch.groupValues[1].toIntOrNull()
        }
        for ((word, num) in numberWords) {
            if (Regex("\\b$word\\b").containsMatchIn(lower) && word != "a" && word != "an") {
                return num
            }
        }
        return null
    }

    private fun cleanEntity(raw: String): String {
        var cleaned = raw.trim()
        val prefixes = listOf("a ", "an ", "the ", "some ")
        for (prefix in prefixes) {
            if (cleaned.startsWith(prefix, ignoreCase = true)) {
                cleaned = cleaned.substring(prefix.length).trim()
            }
        }
        return cleaned.replaceFirstChar { it.uppercase() }
    }

    private fun findSlotBinding(
        action: com.samsung.prism.teachable.teaching.RawAction,
        slots: List<SlotDefinition>
    ): String? {
        val typedText = action.inputText?.lowercase()?.trim() ?: ""
        val nodeText = (action.targetNode.text ?: "").lowercase().trim()
        val nodeDesc = (action.targetNode.contentDescription ?: "").lowercase().trim()
        val hasTyped = typedText.isNotBlank()

        for (slot in slots) {
            val defaultVal = slot.defaultValue?.lowercase()?.trim() ?: continue
            if (defaultVal.isBlank()) continue

            // 1. Typed text matches slot value (strongest signal for user parameter input)
            if (hasTyped && (typedText.contains(defaultVal) || defaultVal.contains(typedText))) {
                return slot.name
            }

            // 2. Direct non-empty text or description match against slot value
            if ((nodeText.isNotEmpty() && (nodeText.contains(defaultVal) || (defaultVal.length > 3 && defaultVal.contains(nodeText)))) ||
                (nodeDesc.isNotEmpty() && (nodeDesc.contains(defaultVal) || (defaultVal.length > 3 && defaultVal.contains(nodeDesc))))) {
                return slot.name
            }
        }

        // Generic role fallback: text input on a search box is bound to item or search_term
        if (action.actionType == ActionType.SET_TEXT) {
            return slots.find { it.name == "item" || it.name == "search_term" }?.name
        }

        return null
    }

    private fun generateCanonicalIntent(
        utterance: String,
        slots: List<SlotDefinition>
    ): Pair<String, String> {
        val lower = utterance.lowercase().trim()

        // 1. Dynamic intentTag based on primary action verb
        val intentTag = when {
            lower.contains("order") || lower.contains("food") -> "order_food"
            lower.contains("search") || lower.contains("shop") || lower.contains("cart") || lower.contains("buy") -> "ecommerce_search"
            else -> {
                val firstWord = lower.substringBefore(' ').trim()
                if (firstWord.length > 2) "${firstWord}_action" else "general_automation"
            }
        }

        // 2. Generalized Intent Template generated dynamically by slot token substitution
        var template = utterance
        // Sort slots by default value length descending to avoid partial substring collisions
        val sortedSlots = slots.filter { !it.defaultValue.isNullOrBlank() }
            .sortedByDescending { it.defaultValue!!.length }

        for (slot in sortedSlots) {
            val value = slot.defaultValue ?: continue
            // Case-insensitive replacement of value with {slot.name}
            val pattern = Regex(Regex.escape(value), RegexOption.IGNORE_CASE)
            template = pattern.replace(template, "{${slot.name}}")
        }

        return Pair(intentTag, template)
    }
}
