package com.samsung.prism.teachable.ai

import android.content.Context
import android.util.Log
import com.samsung.prism.teachable.generalization.ExtractedParameters
import com.samsung.prism.teachable.generalization.UniversalDomainExtractor
import com.samsung.prism.teachable.generalization.WorkflowGeneralizer
import com.samsung.prism.teachable.model.ExpectedStateTransition
import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.model.SlotSchema
import com.samsung.prism.teachable.model.StepTarget
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.model.WorkflowStatus
import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.stuck.ClarificationGenerator
import com.samsung.prism.teachable.stuck.ClarificationQuestion
import com.samsung.prism.teachable.stuck.StuckContext
import com.samsung.prism.teachable.teaching.ActionType
import com.samsung.prism.teachable.teaching.TeachingSession
import com.samsung.prism.teachable.utility.SystemUtilityAction
import com.samsung.prism.teachable.utility.SystemUtilityParser
import com.samsung.prism.teachable.voice.IntentMatchResult
import com.samsung.prism.teachable.voice.IntentMatcher

class GenAiManager(
    val configStore: GeminiConfigStore,
    val apiClient: GeminiApiClient = GeminiApiClient(),
    private val localIntentMatcher: IntentMatcher = IntentMatcher(),
    private val localGeneralizer: WorkflowGeneralizer = WorkflowGeneralizer(),
    private val localClarificationGenerator: ClarificationGenerator = ClarificationGenerator()
) {
    private val tag = "GenAiManager"

    val isGenAiActive: Boolean
        get() = configStore.hasValidKey()

    suspend fun testKey(key: String, model: String = configStore.selectedModel): Result<GeminiValidationResult> {
        val result = apiClient.testConnectionWithAutoModel(key, model)
        if (result.isSuccess) {
            val valResult = result.getOrThrow()
            configStore.selectedModel = valResult.activeModel
            if (valResult.availableModels.isNotEmpty()) {
                configStore.cachedAvailableModels = valResult.availableModels
            }
            configStore.lastValidationStatus = "Valid (${valResult.activeModel})"
            configStore.lastValidationTimestamp = System.currentTimeMillis()
        } else {
            val err = result.exceptionOrNull()?.message ?: "Validation failed"
            configStore.lastValidationStatus = "Error: $err"
            configStore.lastValidationTimestamp = System.currentTimeMillis()
        }
        return result
    }

    /**
     * Resolves voice input to a workflow. First attempts Gemini GenAI if configured,
     * otherwise smoothly falls back to local IntentMatcher.
     */
    suspend fun matchIntent(
        utterance: String,
        workflows: List<Workflow>
    ): IntentMatchResult {
        val key = configStore.apiKey
        if (isGenAiActive && !key.isNullOrBlank()) {
            val geminiResult = apiClient.understandUtterance(
                utterance = utterance,
                workflows = workflows,
                apiKey = key,
                model = configStore.selectedModel
            )

            if (geminiResult != null && geminiResult.matchedWorkflowId != null) {
                val matchedWorkflow = workflows.find { it.id == geminiResult.matchedWorkflowId }
                if (matchedWorkflow != null) {
                    Log.i(tag, "Gemini matched workflow: ${matchedWorkflow.originalUtterance} with confidence ${geminiResult.confidence}")
                    return IntentMatchResult.Matched(
                        workflow = matchedWorkflow,
                        confidence = geminiResult.confidence,
                        matchType = geminiResult.matchType
                    )
                }
            }
        }

        // Instant local fallback
        return localIntentMatcher.match(utterance, workflows)
    }

    /**
     * Extracts dynamic parameters, target application, and action from user's voice goal.
     * First attempts Gemini GenAI if configured, otherwise falls back to UniversalDomainExtractor.
     */
    suspend fun extractParametersFromGoal(utterance: String): ExtractedParameters {
        val key = configStore.apiKey
        if (isGenAiActive && !key.isNullOrBlank()) {
            val geminiResult = apiClient.extractGoalParameters(
                utterance = utterance,
                apiKey = key,
                model = configStore.selectedModel
            )
            if (geminiResult != null) {
                Log.i(tag, "Gemini extracted parameters: ${geminiResult.toUiChips()}")
                return geminiResult
            }
        }

        // Instant local fallback
        return UniversalDomainExtractor.extract(utterance)
    }

    /**
     * Intelligently parses system utilities using Gemini GenAI.
     * Handles complex natural language, relative times ("wake me up in 3hours"), and exact AM/PM bindings.
     * Automatically falls back to local SystemUtilityParser when offline or without API key.
     */
    suspend fun parseSystemUtility(utterance: String): SystemUtilityAction? {
        val key = configStore.apiKey
        if (isGenAiActive && !key.isNullOrBlank()) {
            val geminiAction = apiClient.parseSystemUtilityWithGemini(
                utterance = utterance,
                apiKey = key,
                model = configStore.selectedModel
            )
            if (geminiAction != null) {
                Log.i(tag, "Gemini parsed system utility: $geminiAction")
                return geminiAction
            }
        }

        // Instant local fallback
        return SystemUtilityParser.parse(utterance)
    }

    /**
     * Synthesizes workflow schema and steps using Gemini GenAI.
     * Takes all recorded steps/actions from the teaching demonstration, sends them to Gemini,
     * and receives a complete synthesized Workflow.
     * Automatically falls back to local WorkflowGeneralizer when offline or no API key is set.
     */
    suspend fun generalizeWorkflow(session: TeachingSession): Workflow {
        val baseWorkflow = localGeneralizer.generalize(session)
        val key = configStore.apiKey

        if (!isGenAiActive || key.isNullOrBlank()) {
            Log.i(tag, "GenAI inactive or no API key, using local workflow generalizer")
            return baseWorkflow
        }

        val allEvents = session.rawActions.ifEmpty { session.retainedActions }
        val query = session.originalUtterance.ifBlank { "toggle airplane mode from settings" }
        Log.i(tag, "Sending ALL ${allEvents.size} events to Gemini with query: '$query'")
        val geminiWorkflow = apiClient.synthesizeCompleteWorkflow(
            session = session,
            apiKey = key,
            model = configStore.selectedModel
        ) ?: return baseWorkflow

        return try {
            val workflowId = baseWorkflow.id
            val retained = allEvents

            // 1. Build slot schema from Gemini or fallback to local
            val slotDefs = if (geminiWorkflow.slots.isNotEmpty()) {
                geminiWorkflow.slots.map {
                    SlotDefinition(
                        name = it.name,
                        type = it.type,
                        required = it.required,
                        defaultValue = it.defaultValue
                    )
                }
            } else {
                baseWorkflow.slotSchema.slots
            }
            val slotSchema = SlotSchema(slotDefs)

            // 2. Deduce supported packages from Gemini + observed session packages
            val supportedPkgs = (geminiWorkflow.supportedPackages + baseWorkflow.supportedPackages)
                .filter { it.isNotBlank() && it != "com.samsung.prism.teachable" && it != "com.android.systemui" }
                .distinct()
                .ifEmpty { baseWorkflow.supportedPackages }

            // 3. Assemble workflow steps
            val synthesizedSteps = if (geminiWorkflow.steps.isNotEmpty()) {
                geminiWorkflow.steps.mapIndexed { idx, gStep ->
                    // Match to raw demonstration action: by target text match first, then by stepOrder, then by idx
                    val rawAction = retained.find { action ->
                        val t = gStep.targetText?.lowercase()?.trim()
                        !t.isNullOrBlank() && (action.targetNode.text?.lowercase()?.trim() == t ||
                                action.targetNode.contentDescription?.lowercase()?.trim() == t)
                    } ?: retained.getOrNull(gStep.stepOrder) ?: retained.getOrNull(idx)

                    val rootBounds = rawAction?.screenBefore?.rootNode?.bounds
                    val sw = if (rootBounds != null && rootBounds.width > 0) rootBounds.width else 1080
                    val sh = if (rootBounds != null && rootBounds.height > 0) rootBounds.height else 2400

                    val target = if (rawAction != null) {
                        val (rx, ry) = if (!rawAction.targetNode.bounds.isEmpty()) {
                            rawAction.targetNode.bounds.normalizedCenter(sw, sh)
                        } else Pair(null, null)

                        StepTarget(
                            resourceId = gStep.targetResourceId ?: rawAction.targetNode.resourceId,
                            contentDescription = gStep.targetContentDescription ?: rawAction.targetNode.contentDescription,
                            text = gStep.targetText ?: rawAction.targetNode.text,
                            semanticRole = gStep.targetSemanticRole ?: rawAction.targetNode.semanticRole,
                            className = rawAction.targetNode.className,
                            parentContext = gStep.targetParentContext ?: rawAction.targetNode.parentContext,
                            screenSignature = rawAction.screenBefore.screenSignature,
                            boundsRelativeX = rx,
                            boundsRelativeY = ry,
                            domainConcept = gStep.domainConcept ?: StepTarget.inferDomainConcept(rawAction.targetNode)
                        )
                    } else {
                        StepTarget(
                            resourceId = gStep.targetResourceId,
                            contentDescription = gStep.targetContentDescription,
                            text = gStep.targetText,
                            semanticRole = gStep.targetSemanticRole,
                            parentContext = gStep.targetParentContext,
                            domainConcept = gStep.domainConcept
                        )
                    }

                    val actionTypeEnum = runCatching {
                        ActionType.valueOf(gStep.actionType.uppercase())
                    }.getOrDefault(rawAction?.actionType ?: ActionType.CLICK)

                    val isLast = idx == geminiWorkflow.steps.size - 1
                    val isBoundary = gStep.isBoundary || (isLast && session.truncatedAtBoundary)

                    val transition = ExpectedStateTransition(
                        expectedTextSubstring = gStep.expectedTextSubstring
                            ?: (if (gStep.slotBinding != null) "{${gStep.slotBinding}}" else (gStep.inputText ?: gStep.targetText ?: rawAction?.targetNode?.text)),
                        expectedRole = gStep.targetSemanticRole ?: rawAction?.targetNode?.semanticRole,
                        expectedPackageName = gStep.expectedPackage ?: rawAction?.screenAfter?.packageName ?: rawAction?.packageName ?: supportedPkgs.firstOrNull(),
                        expectedSignatureChange = true,
                        slotBoundKey = gStep.slotBinding
                    )

                    WorkflowStep(
                        id = "step_$idx",
                        workflowId = workflowId,
                        stepOrder = idx,
                        actionType = actionTypeEnum,
                        inputText = gStep.inputText ?: rawAction?.inputText,
                        target = target,
                        expectedStateTransition = transition,
                        isBoundary = isBoundary,
                        slotBinding = gStep.slotBinding
                    )
                }
            } else {
                baseWorkflow.steps
            }

            val finalWorkflow = Workflow(
                id = workflowId,
                intentTag = geminiWorkflow.intentTag.ifBlank { baseWorkflow.intentTag },
                originalUtterance = session.originalUtterance,
                generalizedIntent = geminiWorkflow.generalizedIntent.ifBlank { baseWorkflow.generalizedIntent },
                supportedPackages = supportedPkgs,
                slotSchema = slotSchema,
                steps = synthesizedSteps,
                status = WorkflowStatus.ACTIVE,
                createdAt = session.startTime,
                updatedAt = System.currentTimeMillis()
            )

            Log.i(tag, "Successfully synthesized workflow from Gemini: '${finalWorkflow.intentTag}' with ${finalWorkflow.steps.size} steps (generalizedIntent: '${finalWorkflow.generalizedIntent}')")
            finalWorkflow
        } catch (e: Exception) {
            Log.w(tag, "Failed to assemble Gemini synthesized workflow, falling back to local base workflow", e)
            baseWorkflow
        }
    }

    /**
     * Generates a clarification question when stuck. Uses Gemini GenAI for conversational
     * questions if enabled, or falls back to local deterministic rule.
     */
    suspend fun generateStuckClarification(context: StuckContext): ClarificationQuestion {
        val key = configStore.apiKey
        if (isGenAiActive && !key.isNullOrBlank()) {
            val geminiQuestion = apiClient.generateStuckClarification(
                context = context,
                apiKey = key,
                model = configStore.selectedModel
            )
            if (geminiQuestion != null) {
                return geminiQuestion
            }
        }

        return localClarificationGenerator.generateQuestion(context)
    }

    companion object {
        @Volatile
        private var instance: GenAiManager? = null

        fun getInstance(context: Context): GenAiManager {
            return instance ?: synchronized(this) {
                instance ?: GenAiManager(GeminiConfigStore(context.applicationContext)).also {
                    instance = it
                }
            }
        }
    }
}
