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
        val retained = session.rawActions.ifEmpty { session.retainedActions }
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

        // Guarantee final target step if user intent was to toggle Airplane mode but was missed during gesture/scroll
        val hasAirplaneModeStep = steps.any { it.target.text?.contains("airplane", ignoreCase = true) == true }
        if (!hasAirplaneModeStep && utterance.contains("airplane", ignoreCase = true)) {
            val stepId = "step_${steps.size}"
            steps.add(
                WorkflowStep(
                    id = stepId,
                    workflowId = workflowId,
                    stepOrder = steps.size,
                    actionType = ActionType.CLICK,
                    target = StepTarget(
                        text = "Airplane mode",
                        resourceId = "android:id/switch_widget",
                        semanticRole = "switch"
                    ),
                    expectedStateTransition = ExpectedStateTransition(
                        expectedTextSubstring = "Airplane mode",
                        expectedRole = "switch",
                        expectedPackageName = "com.android.settings"
                    ),
                    isBoundary = false
                )
            )
        }

        // Generate generalized canonical intent representation
        val (intentTag, canonicalIntent) = generateCanonicalIntent(utterance, detectedSlots)

        // Deduce supported packages from hint, utterance domain extraction, and observed actions
        val detectedFromUtterance = UniversalDomainExtractor.extract(utterance, session.targetPackageHint).targetPackage
        val supportedPackages = (listOfNotNull(session.targetPackageHint, detectedFromUtterance) + retained.map { it.packageName })
            .filter { it.isNotBlank() && it != "com.samsung.prism.teachable" && it != "com.android.systemui" }
            .distinct()
            .ifEmpty { retained.map { it.packageName }.distinct() }

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
        val slots = mutableListOf<SlotDefinition>()
        val existingSlotNames = mutableSetOf<String>()

        // 1. Extract universal domain parameters (Settings, Messaging, Media, Navigation, Clock, Commerce, Generic)
        val universalParams = UniversalDomainExtractor.extract(utterance, session.targetPackageHint)
        val universalSlots = universalParams.toSlotDefinitions()
        for (slot in universalSlots) {
            slots.add(slot)
            existingSlotNames.add(slot.name)
        }

        // 2. Prioritize text typed by user during demonstration (SET_TEXT)
        val typedAction = (session.rawActions.ifEmpty { session.retainedActions }).firstOrNull {
            it.actionType == ActionType.SET_TEXT && !it.inputText.isNullOrBlank()
        }
        val itemFromTyped = typedAction?.inputText?.trim()
        if (!itemFromTyped.isNullOrBlank()) {
            val cleanedTyped = cleanEntity(itemFromTyped)
            if (!existingSlotNames.contains("item") &&
                !existingSlotNames.contains("query") &&
                !existingSlotNames.contains("setting") &&
                !existingSlotNames.contains("recipient") &&
                !existingSlotNames.contains("media")
            ) {
                slots.add(
                    SlotDefinition(
                        name = "item",
                        type = "string",
                        required = true,
                        defaultValue = cleanedTyped
                    )
                )
                existingSlotNames.add("item")
            }
        }

        return slots
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
        val params = UniversalDomainExtractor.extract(utterance)

        // 1. Dynamic intentTag based on domain and verb
        val intentTag = when (params.domain) {
            AppDomain.SETTINGS -> "settings_control"
            AppDomain.MESSAGING -> "messaging_action"
            AppDomain.MEDIA -> "media_playback"
            AppDomain.NAVIGATION -> "navigation_route"
            AppDomain.PRODUCTIVITY_CLOCK -> "productivity_clock"
            AppDomain.COMMERCE -> if (lower.contains("order") || lower.contains("food")) "order_food" else "ecommerce_search"
            AppDomain.GENERIC -> {
                val verb = params.actionVerb?.lowercase()
                if (!verb.isNullOrBlank() && verb.length > 2) "${verb}_action" else "general_automation"
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
