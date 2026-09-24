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

            val target = StepTarget.fromNode(
                node = action.targetNode,
                screenSig = action.screenBefore.screenSignature
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

    private fun extractCandidateSlots(
        utterance: String,
        session: TeachingSession
    ): List<SlotDefinition> {
        val lower = utterance.lowercase()
        val slots = mutableListOf<SlotDefinition>()

        // 1. Food Delivery Domain Heuristic (per systemdesign.md §5.3)
        if (lower.contains("pizza") || lower.contains("food") || lower.contains("zomato") || lower.contains("domino")) {
            // Item slot
            val itemValue = extractFoodItem(lower)
            if (itemValue != null) {
                slots.add(
                    SlotDefinition(
                        name = "item",
                        type = "string",
                        required = true,
                        defaultValue = itemValue
                    )
                )
            }

            // Restaurant slot
            val restaurantValue = extractRestaurant(lower)
            if (restaurantValue != null) {
                slots.add(
                    SlotDefinition(
                        name = "restaurant",
                        type = "string",
                        required = true,
                        defaultValue = restaurantValue
                    )
                )
            }

            // Platform slot
            if (lower.contains("zomato")) {
                slots.add(
                    SlotDefinition(
                        name = "platform",
                        type = "enum",
                        required = true,
                        defaultValue = "Zomato",
                        enumValues = listOf("Zomato", "Swiggy")
                    )
                )
            }

            // Quantity slot
            slots.add(
                SlotDefinition(
                    name = "quantity",
                    type = "integer",
                    required = false,
                    defaultValue = "1"
                )
            )

            // Address slot
            val addressValue = if (lower.contains("work")) "Work" else "Home"
            slots.add(
                SlotDefinition(
                    name = "address",
                    type = "enum",
                    required = false,
                    defaultValue = addressValue,
                    enumValues = listOf("Home", "Work")
                )
            )
        }
        // 2. E-Commerce Domain Heuristic
        else if (lower.contains("search") || lower.contains("amazon") || lower.contains("buy") || lower.contains("order")) {
            val searchTerm = extractSearchTerm(lower)
            slots.add(
                SlotDefinition(
                    name = "item",
                    type = "string",
                    required = true,
                    defaultValue = searchTerm ?: "item"
                )
            )

            if (lower.contains("amazon")) {
                slots.add(
                    SlotDefinition(
                        name = "platform",
                        type = "enum",
                        required = true,
                        defaultValue = "Amazon",
                        enumValues = listOf("Amazon", "Myntra", "Flipkart")
                    )
                )
            }
        }

        return slots
    }

    private fun findSlotBinding(
        action: com.samsung.prism.teachable.teaching.RawAction,
        slots: List<SlotDefinition>
    ): String? {
        val typedText = action.inputText?.lowercase() ?: ""
        val nodeText = (action.targetNode.text ?: "").lowercase()
        val nodeDesc = (action.targetNode.contentDescription ?: "").lowercase()
        val hasTyped = typedText.isNotBlank()
        for (slot in slots) {
            val defaultVal = slot.defaultValue?.lowercase()?.trim() ?: continue
            if (defaultVal.isBlank()) continue

            // 1. Direct text or description match
            if (nodeText.contains(defaultVal) || nodeDesc.contains(defaultVal)) {
                return slot.name
            }

            // 2. Typed text matches slot default value
            if (hasTyped && (typedText.contains(defaultVal) || defaultVal.contains(typedText))) {
                return slot.name
            }
        }

        // Generic role bindings
        if (action.actionType == ActionType.SET_TEXT && action.targetNode.semanticRole == "search_box") {
            return slots.find { it.name == "item" || it.name == "search_term" }?.name
        }

        return null
    }

    private fun extractFoodItem(lower: String): String? {
        val matchers = listOf(
            Regex("(order|get|want)\\s+(a\\s+|an\\s+|two\\s+)?([a-z\\s]+?)(pizza)?\\s+from"),
            Regex("([a-z\\s]+?)\\s+pizza")
        )
        for (m in matchers) {
            val match = m.find(lower)
            if (match != null) {
                val groupVal = match.groupValues.last { it.isNotBlank() && it != "pizza" }.trim()
                if (groupVal.length > 2 && !groupVal.contains("order")) {
                    return groupVal.replaceFirstChar { it.uppercase() } + (if (!groupVal.contains("pizza")) " pizza" else "")
                }
            }
        }
        return if (lower.contains("margherita")) "Margherita pizza" else null
    }

    private fun extractRestaurant(lower: String): String? {
        return when {
            lower.contains("domino") -> "Domino's"
            lower.contains("subway") -> "Subway"
            lower.contains("burger king") -> "Burger King"
            else -> null
        }
    }

    private fun extractSearchTerm(lower: String): String? {
        val regex = Regex("search\\s+(for\\s+)?([a-z\\s]+?)\\s+(on|and)")
        val match = regex.find(lower)
        return match?.groupValues?.get(2)?.trim()?.replaceFirstChar { it.uppercase() }
    }

    private fun generateCanonicalIntent(
        utterance: String,
        slots: List<SlotDefinition>
    ): Pair<String, String> {
        val lower = utterance.lowercase()
        return when {
            lower.contains("pizza") || lower.contains("domino") || lower.contains("zomato") -> {
                Pair("order_food", "Order {item} from {restaurant} on {platform}")
            }
            lower.contains("amazon") || lower.contains("search") -> {
                Pair("ecommerce_search", "Search for {item} on {platform} and add to cart")
            }
            else -> {
                Pair("general_automation", utterance)
            }
        }
    }
}
