package com.samsung.prism.teachable.ai

import android.content.Context
import android.util.Log
import com.samsung.prism.teachable.generalization.ExtractedParameters
import com.samsung.prism.teachable.generalization.UniversalDomainExtractor
import com.samsung.prism.teachable.generalization.WorkflowGeneralizer
import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.model.SlotSchema
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.stuck.ClarificationGenerator
import com.samsung.prism.teachable.stuck.ClarificationQuestion
import com.samsung.prism.teachable.stuck.StuckContext
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
     * Synthesizes workflow schema. If Gemini is available, synthesizes rich generalized intents
     * and slots; otherwise uses deterministic local generalizer.
     */
    suspend fun generalizeWorkflow(session: TeachingSession): Workflow {
        val baseWorkflow = localGeneralizer.generalize(session)
        val key = configStore.apiKey

        if (!isGenAiActive || key.isNullOrBlank()) {
            return baseWorkflow
        }

        val geminiSynthesis = apiClient.synthesizeWorkflowSchema(
            session = session,
            apiKey = key,
            model = configStore.selectedModel
        ) ?: return baseWorkflow

        return try {
            val updatedSlots = if (geminiSynthesis.slots.isNotEmpty()) {
                val list = geminiSynthesis.slots.map {
                    SlotDefinition(
                        name = it.name,
                        type = it.type,
                        required = it.required,
                        defaultValue = it.defaultValue
                    )
                }
                SlotSchema(list)
            } else {
                baseWorkflow.slotSchema
            }

            val updatedSteps = baseWorkflow.steps.mapIndexed { idx, step ->
                val boundSlot = geminiSynthesis.stepBindings[idx] ?: step.slotBinding
                step.copy(slotBinding = boundSlot)
            }

            baseWorkflow.copy(
                generalizedIntent = geminiSynthesis.generalizedIntent,
                slotSchema = updatedSlots,
                steps = updatedSteps
            )
        } catch (e: Exception) {
            Log.w(tag, "Failed to apply Gemini schema synthesis, using local workflow", e)
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
