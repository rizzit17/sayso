package com.samsung.prism.teachable.ai

import android.util.Log
import com.samsung.prism.teachable.generalization.AppDomain
import com.samsung.prism.teachable.generalization.ExtractedParameters
import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.stuck.ClarificationActionType
import com.samsung.prism.teachable.stuck.ClarificationOption
import com.samsung.prism.teachable.stuck.ClarificationQuestion
import com.samsung.prism.teachable.stuck.StuckContext
import com.samsung.prism.teachable.teaching.TeachingSession
import com.samsung.prism.teachable.utility.SystemUtilityAction
import com.samsung.prism.teachable.voice.MatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.HttpsURLConnection

data class GeminiIntentResult(
    val matchedWorkflowId: String?,
    val confidence: Double,
    val matchType: MatchType,
    val extractedSlots: Map<String, String>,
    val explanation: String
)

data class GeminiSlotSynthesis(
    val name: String,
    val type: String = "string",
    val required: Boolean = true,
    val defaultValue: String? = null,
    val enumValues: List<String> = emptyList()
)

data class GeminiSchemaResult(
    val generalizedIntent: String,
    val slots: List<GeminiSlotSynthesis>,
    val stepBindings: Map<Int, String> // step index (0-based) -> slot name
)

data class GeminiStepSynthesis(
    val stepOrder: Int,
    val actionType: String,
    val targetText: String? = null,
    val targetContentDescription: String? = null,
    val targetResourceId: String? = null,
    val targetSemanticRole: String? = null,
    val targetParentContext: String? = null,
    val domainConcept: String? = null,
    val inputText: String? = null,
    val slotBinding: String? = null,
    val isBoundary: Boolean = false,
    val expectedTextSubstring: String? = null,
    val expectedPackage: String? = null,
    val explanation: String? = null
)

data class GeminiWorkflowResult(
    val intentTag: String,
    val generalizedIntent: String,
    val supportedPackages: List<String>,
    val slots: List<GeminiSlotSynthesis>,
    val steps: List<GeminiStepSynthesis>
)

data class GeminiValidationResult(
    val isSuccess: Boolean,
    val message: String,
    val activeModel: String,
    val availableModels: List<String> = emptyList()
)

class GeminiApiClient(
    private val connectTimeoutMs: Int = 8000,
    private val readTimeoutMs: Int = 12000
) {
    private val tag = "GeminiApiClient"
    private val apiHost = "https://generativelanguage.googleapis.com"
    private val apiVersions = listOf("v1beta", "v1")

    /**
     * Lists all models available to the given API key that support generateContent.
     */
    suspend fun fetchAvailableModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be blank"))
        }

        var lastError = "Could not list models"
        for (version in apiVersions) {
            val endpoint = "$apiHost/$version/models?key=$trimmed"
            try {
                val (statusCode, responseText) = executeGet(endpoint)
                if (statusCode in 200..299) {
                    val json = JSONObject(responseText)
                    val modelsArr = json.optJSONArray("models") ?: continue
                    val modelsList = mutableListOf<String>()
                    for (i in 0 until modelsArr.length()) {
                        val mObj = modelsArr.getJSONObject(i)
                        val rawName = mObj.optString("name")
                        val modelId = rawName.removePrefix("models/")
                        val methods = mObj.optJSONArray("supportedGenerationMethods")
                        var supportsGenerate = false
                        if (methods != null) {
                            for (j in 0 until methods.length()) {
                                if (methods.optString(j) == "generateContent") {
                                supportsGenerate = true
                                break
                            }
                        }
                    }
                    if (supportsGenerate && modelId.isNotBlank()) {
                        modelsList.add(modelId)
                    }
                }
                if (modelsList.isNotEmpty()) {
                    return@withContext Result.success(modelsList)
                }
            } else {
                lastError = extractErrorMessage(responseText, statusCode)
            }
        } catch (e: Exception) {
            lastError = e.message ?: "Connection error"
        }
    }

    Result.failure(Exception(lastError))
}

    /**
     * Tests connection by discovering available models and executing a test prompt.
     * Automatically resolves to a valid, working model if the requested one is retired or renamed.
     */
    suspend fun testConnectionWithAutoModel(
        apiKey: String,
        requestedModel: String = GeminiConfigStore.DEFAULT_MODEL
    ): Result<GeminiValidationResult> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be blank"))
        }

        // 1. Discover models via ModelService.ListModels
        val modelsResult = fetchAvailableModels(trimmedKey)
        val availableModels = modelsResult.getOrNull() ?: emptyList()

        if (modelsResult.isFailure && availableModels.isEmpty()) {
            val err = modelsResult.exceptionOrNull()?.message ?: "Failed to reach Google Gemini API"
            return@withContext Result.failure(Exception(err))
        }

        // 2. Resolve best working model
        val resolvedModel = when {
            availableModels.contains(requestedModel) -> requestedModel
            availableModels.any { it == "gemini-2.0-flash" } -> "gemini-2.0-flash"
            availableModels.any { it.startsWith("gemini-2.0-flash") } -> availableModels.first { it.startsWith("gemini-2.0-flash") }
            availableModels.any { it == "gemini-1.5-flash-latest" } -> "gemini-1.5-flash-latest"
            availableModels.any { it.contains("flash") } -> availableModels.first { it.contains("flash") }
            availableModels.any { it.startsWith("gemini-1.5") } -> availableModels.first { it.startsWith("gemini-1.5") }
            availableModels.isNotEmpty() -> availableModels.first()
            else -> requestedModel
        }

        // 3. Ping the resolved model with generateContent
        val testRequestBody = JSONObject().apply {
            val partsArray = JSONArray().apply {
                put(JSONObject().apply { put("text", "Respond strictly with the single word: OK") })
            }
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply { put("parts", partsArray) })
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 10)
                put("temperature", 0.0)
            })
        }

        var pingSuccess = false
        var lastPingError = ""

        for (version in apiVersions) {
            val endpoint = "$apiHost/$version/models/$resolvedModel:generateContent?key=$trimmedKey"
            try {
                val (statusCode, responseText) = executePost(endpoint, testRequestBody.toString())
                if (statusCode in 200..299) {
                    pingSuccess = true
                    break
                } else {
                    lastPingError = extractErrorMessage(responseText, statusCode)
                }
            } catch (e: Exception) {
                lastPingError = e.message ?: "Connection error"
            }
        }

        if (pingSuccess) {
            val note = if (resolvedModel != requestedModel) {
                "Switched from '$requestedModel' to available '$resolvedModel'."
            } else ""
            val msg = "Connection successful! Active model: $resolvedModel. $note"
            Result.success(
                GeminiValidationResult(
                    isSuccess = true,
                    message = msg.trim(),
                    activeModel = resolvedModel,
                    availableModels = availableModels
                )
            )
        } else {
            Result.failure(Exception("Model $resolvedModel test failed: $lastPingError"))
        }
    }

    /**
     * Backward-compatible testConnection method.
     */
    suspend fun testConnection(
        apiKey: String,
        model: String = GeminiConfigStore.DEFAULT_MODEL
    ): Result<String> {
        val res = testConnectionWithAutoModel(apiKey, model)
        return if (res.isSuccess) {
            Result.success(res.getOrThrow().message)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    /**
     * Generates a conversational clarification question when SaySo gets stuck during replay.
     */
    suspend fun generateStuckClarification(
        context: StuckContext,
        apiKey: String,
        model: String = GeminiConfigStore.DEFAULT_MODEL
    ): ClarificationQuestion? = withContext(Dispatchers.IO) {
        try {
            val targetLabel = context.step.target.text
                ?: context.step.target.contentDescription
                ?: context.step.target.semanticRole
                ?: "element"

            val candidateListStr = context.visibleCandidates
                .filter { !it.text.isNullOrBlank() }
                .take(4)
                .joinToString(", ") { "'${it.text?.trim()}'" }

            val prompt = """
                You are SaySo, an autonomous on-device voice assistant for Android.
                You are currently replaying an automation flow, but you got stuck at a step.
                Target element SaySo was looking for: "$targetLabel"
                Failure reason: "${context.failureReason}"
                Elements currently visible on screen: [$candidateListStr]
                
                Generate a helpful, concise clarification question to ask the user.
                Respond strictly with a JSON object:
                {
                  "questionText": "Conversational text to display in UI (1-2 sentences)",
                  "ttsPrompt": "Concise prompt for Text-to-Speech audio",
                  "suggestedAlternativeText": "The best matching alternative text from visible elements, or null"
                }
            """.trimIndent()

            val responseJson = callGeminiForJson(apiKey, model, prompt) ?: return@withContext null

            val questionText = responseJson.optString("questionText").takeIf { it.isNotBlank() }
                ?: "I'm looking for '$targetLabel', but couldn't find it. How should I proceed?"
            val ttsPrompt = responseJson.optString("ttsPrompt").takeIf { it.isNotBlank() }
                ?: "I can't find '$targetLabel'. Should I tap an alternative, skip, or abort?"
            val suggestedAlt = responseJson.optString("suggestedAlternativeText").takeIf { it.isNotBlank() && it != "null" }

            val options = mutableListOf<ClarificationOption>()

            val matchedCandidate = context.visibleCandidates.firstOrNull {
                suggestedAlt != null && (it.text?.contains(suggestedAlt, ignoreCase = true) == true)
            } ?: context.visibleCandidates.firstOrNull { !it.text.isNullOrBlank() }

            if (matchedCandidate != null) {
                options.add(
                    ClarificationOption(
                        id = "alt_gemini_0",
                        label = "Tap '${matchedCandidate.text?.trim()}'",
                        targetNode = matchedCandidate,
                        actionType = ClarificationActionType.TAP_ALTERNATIVE
                    )
                )
            }

            options.add(
                ClarificationOption(
                    id = "opt_skip",
                    label = "Skip this step and continue",
                    actionType = ClarificationActionType.SKIP_STEP
                )
            )
            options.add(
                ClarificationOption(
                    id = "opt_abort",
                    label = "Abort automation",
                    actionType = ClarificationActionType.ABORT
                )
            )

            ClarificationQuestion(
                questionText = questionText,
                ttsPrompt = ttsPrompt,
                options = options
            )
        } catch (e: Exception) {
            Log.w(tag, "generateStuckClarification failed", e)
            null
        }
    }

    /**
     * Synthesizes a complete, production-ready Workflow directly from recorded demonstration steps using Gemini.
     */
    suspend fun synthesizeCompleteWorkflow(
        session: TeachingSession,
        apiKey: String,
        model: String = GeminiConfigStore.DEFAULT_MODEL
    ): GeminiWorkflowResult? = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        val allEvents = session.rawActions.ifEmpty { session.retainedActions }
        if (trimmedKey.isEmpty() || allEvents.isEmpty()) {
            return@withContext null
        }

        val userQuery = session.originalUtterance.ifBlank { "toggle airplane mode from settings" }
        Log.i(tag, "synthesizeCompleteWorkflow: sending all ${allEvents.size} events to Gemini with query: '$userQuery'")

        try {
            val actionsJson = JSONArray().apply {
                allEvents.forEachIndexed { idx, action ->
                    put(JSONObject().apply {
                        put("stepIndex", idx)
                        put("actionType", action.actionType.name)
                        put("packageName", action.packageName)
                        put("semanticDescription", action.semanticDescription)
                        if (!action.inputText.isNullOrBlank()) {
                            put("inputText", action.inputText)
                        }
                        val nodeObj = JSONObject().apply {
                            action.targetNode.text?.takeIf { it.isNotBlank() }?.let { put("text", it) }
                            action.targetNode.contentDescription?.takeIf { it.isNotBlank() }?.let { put("contentDescription", it) }
                            action.targetNode.resourceId?.takeIf { it.isNotBlank() }?.let { put("resourceId", it) }
                            action.targetNode.className?.takeIf { it.isNotBlank() }?.let { put("className", it) }
                            action.targetNode.semanticRole?.takeIf { it.isNotBlank() }?.let { put("semanticRole", it) }
                            action.targetNode.parentContext?.takeIf { it.isNotBlank() }?.let { put("parentContext", it) }
                            put("clickable", action.targetNode.clickable)
                            if (!action.targetNode.bounds.isEmpty()) {
                                put("bounds", JSONObject().apply {
                                    put("left", action.targetNode.bounds.left)
                                    put("top", action.targetNode.bounds.top)
                                    put("right", action.targetNode.bounds.right)
                                    put("bottom", action.targetNode.bounds.bottom)
                                })
                            }
                        }
                        put("targetElement", nodeObj)
                        action.screenBefore.activityName?.takeIf { it.isNotBlank() }?.let { put("screenActivity", it) }
                        put("screenSignature", action.screenBefore.screenSignature)
                    })
                }
            }

            val prompt = """
                You are SaySo AI Workflow Synthesizer, an autonomous on-device Android task automation and learning intelligence system.
                The user taught SaySo a new mobile workflow by demonstrating the steps on their Android device.
                
                User voice command / query: "$userQuery"
                Target app hint: "${session.targetPackageHint ?: "com.android.settings"}"
                Total events captured: ${allEvents.size}
                
                Here are ALL the recorded user interaction events and screen states:
                $actionsJson
                
                Your task:
                The user's goal is: "$userQuery".
                Analyze ALL the captured demonstration events above. Identify the key navigation and action steps needed to fulfill "$userQuery", filter out any accidental or redundant taps, and synthesize a complete, production-ready, generalized Android automation Workflow.
                
                Instructions:
                1. INTENT & DOMAIN GENERALIZATION:
                   - "intentTag": Short, canonical snake_case tag describing the specific task (e.g. "settings_toggle_airplane_mode", "food_order_cart", "send_whatsapp_message", "clock_set_alarm").
                   - "generalizedIntent": A generalized voice command template using {slot_name} placeholders for any variable parameters (e.g. "Turn {state} airplane mode", "Order {item} from {restaurant}", "Set alarm for {time}"). If no parameters are variable, make it a clean, natural intent phrase.
                   - "supportedPackages": A list of Android package names involved in this workflow (e.g. ["com.android.settings"]).
                
                2. PARAMETERS & SLOTS:
                   - Extract any dynamic parameters from the voice command and the user's typed inputs (e.g., query, item, quantity, contact, state, time, address).
                   - "slots": Array of slot definitions:
                     [
                       {
                         "name": "slot_name",
                         "type": "string",
                         "required": true,
                         "defaultValue": "the default or demonstrated value"
                       }
                     ]
                
                3. WORKFLOW STEPS & END-TO-END COMPLETION:
                   - The synthesized workflow MUST successfully achieve the user's end goal: "$userQuery".
                   - Filter out accidental touch jitter or spurious scroll events (e.g. SCROLL_FORWARD on lists or headers) when the target item is already on that screen.
                   - CRITICAL REQUIREMENT FOR GOAL COMPLETION:
                     If the user's query is to toggle or adjust a setting (e.g. "toggle airplane mode from settings", "turn on airplane mode", "turn off wi-fi", "bluetooth"), the workflow MUST contain the complete, actionable path ending in toggling that target setting!
                     For example, for "toggle airplane mode from settings":
                     Step 0: CLICK "Network & internet" (or "Connections" depending on OS)
                     Step 1: CLICK "Airplane mode" (resourceId: "android:id/switch_widget" or "android:id/title", semanticRole: "switch")
                     Even if the user's recorded demonstration stopped right before the switch was clicked or registered touch jitter as a scroll, YOU MUST INCLUDE the final target click step so the workflow actually fulfills the user's goal!
                   - Review each recorded step in sequence.
                   - Consolidate or filter out any accidental misclicks or duplicate taps if present.
                   - For every executable step, produce:
                     - "stepOrder": 0-indexed sequence number (0, 1, 2, ...)
                     - "actionType": One of "CLICK", "SET_TEXT", "SCROLL_FORWARD", "SCROLL_BACKWARD", "LONG_CLICK"
                     - "packageName": Target package name (e.g. "com.android.settings")
                     - "target":
                       - "text": Primary display text or label to find (e.g. "Network & internet", "Airplane mode")
                       - "contentDescription": Accessibility description if relevant (or null)
                       - "resourceId": Android view resource ID if relevant (e.g. "android:id/title", "android:id/switch_widget")
                       - "semanticRole": e.g. "button", "switch", "input_field", "list_item", "checkbox"
                       - "parentContext": Surrounding header/parent text to disambiguate the item
                       - "domainConcept": Semantic domain concept if applicable (e.g. "network_settings", "airplane_mode_switch", "search_bar")
                     - "inputText": Text to type if SET_TEXT, or null
                     - "slotBinding": The name of the slot bound to this step (e.g. "item", "query", "state") if variable, or null
                     - "isBoundary": true ONLY if this step involves a critical irreversible action (e.g. payment confirmation, place order, delete data); false for normal navigation/toggles
                     - "expectedTransition":
                       - "expectedTextSubstring": Key text expected to appear after this action (or null)
                       - "expectedPackage": Package expected after this action
                     - "explanation": 1-line explanation of what this step does
                
                Respond STRICTLY with valid JSON matching this exact structure:
                {
                  "intentTag": "...",
                  "generalizedIntent": "...",
                  "supportedPackages": ["..."],
                  "slots": [
                    {
                      "name": "...",
                      "type": "string",
                      "required": true,
                      "defaultValue": "..."
                    }
                  ],
                  "steps": [
                    {
                      "stepOrder": 0,
                      "actionType": "CLICK",
                      "packageName": "...",
                      "target": {
                        "text": "...",
                        "contentDescription": null,
                        "resourceId": "...",
                        "semanticRole": "...",
                        "parentContext": "...",
                        "domainConcept": "..."
                      },
                      "inputText": null,
                      "slotBinding": null,
                      "isBoundary": false,
                      "expectedTransition": {
                        "expectedTextSubstring": "...",
                        "expectedPackage": "..."
                      },
                      "explanation": "..."
                    }
                  ]
                }
            """.trimIndent()

            val responseJson = callGeminiForJson(trimmedKey, model, prompt, maxTokens = 4096) ?: return@withContext null

            val intentTag = responseJson.optString("intentTag").takeIf { it.isNotBlank() } ?: "custom_automation"
            val generalizedIntent = responseJson.optString("generalizedIntent").takeIf { it.isNotBlank() } ?: session.originalUtterance

            val supportedPkgs = mutableListOf<String>()
            val pkgsArr = responseJson.optJSONArray("supportedPackages")
            if (pkgsArr != null) {
                for (i in 0 until pkgsArr.length()) {
                    val p = pkgsArr.optString(i)
                    if (p.isNotBlank()) supportedPkgs.add(p)
                }
            }

            val slotsList = mutableListOf<GeminiSlotSynthesis>()
            val slotsArr = responseJson.optJSONArray("slots")
            if (slotsArr != null) {
                for (i in 0 until slotsArr.length()) {
                    val sObj = slotsArr.getJSONObject(i)
                    slotsList.add(
                        GeminiSlotSynthesis(
                            name = sObj.getString("name"),
                            type = sObj.optString("type", "string"),
                            required = sObj.optBoolean("required", true),
                            defaultValue = sObj.optString("defaultValue").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }

            val stepsList = mutableListOf<GeminiStepSynthesis>()
            val stepsArr = responseJson.optJSONArray("steps")
            if (stepsArr != null) {
                for (i in 0 until stepsArr.length()) {
                    val stepObj = stepsArr.getJSONObject(i)
                    val targetObj = stepObj.optJSONObject("target")
                    val transObj = stepObj.optJSONObject("expectedTransition")

                    val targetText = targetObj?.optString("text")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: stepObj.optString("targetText").takeIf { it.isNotBlank() && it != "null" }
                    val targetDesc = targetObj?.optString("contentDescription")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: stepObj.optString("targetContentDescription").takeIf { it.isNotBlank() && it != "null" }
                    val targetResId = targetObj?.optString("resourceId")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: stepObj.optString("targetResourceId").takeIf { it.isNotBlank() && it != "null" }
                    val targetRole = targetObj?.optString("semanticRole")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: stepObj.optString("targetSemanticRole").takeIf { it.isNotBlank() && it != "null" }
                    val targetParent = targetObj?.optString("parentContext")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: stepObj.optString("targetParentContext").takeIf { it.isNotBlank() && it != "null" }
                    val domainConcept = targetObj?.optString("domainConcept")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: stepObj.optString("domainConcept").takeIf { it.isNotBlank() && it != "null" }

                    val expectedText = transObj?.optString("expectedTextSubstring")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: stepObj.optString("expectedTextSubstring").takeIf { it.isNotBlank() && it != "null" }
                    val expectedPkg = transObj?.optString("expectedPackage")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: stepObj.optString("expectedPackage").takeIf { it.isNotBlank() && it != "null" }

                    stepsList.add(
                        GeminiStepSynthesis(
                            stepOrder = stepObj.optInt("stepOrder", i),
                            actionType = stepObj.optString("actionType", "CLICK"),
                            targetText = targetText,
                            targetContentDescription = targetDesc,
                            targetResourceId = targetResId,
                            targetSemanticRole = targetRole,
                            targetParentContext = targetParent,
                            domainConcept = domainConcept,
                            inputText = stepObj.optString("inputText").takeIf { it.isNotBlank() && it != "null" },
                            slotBinding = stepObj.optString("slotBinding").takeIf { it.isNotBlank() && it != "null" },
                            isBoundary = stepObj.optBoolean("isBoundary", false),
                            expectedTextSubstring = expectedText,
                            expectedPackage = expectedPkg,
                            explanation = stepObj.optString("explanation").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }

            GeminiWorkflowResult(
                intentTag = intentTag,
                generalizedIntent = generalizedIntent,
                supportedPackages = supportedPkgs,
                slots = slotsList,
                steps = stepsList
            )
        } catch (e: Exception) {
            Log.w(tag, "synthesizeCompleteWorkflow failed", e)
            null
        }
    }

    /**
     * Synthesizes a generalized workflow schema from recorded actions.
     */
    suspend fun synthesizeWorkflowSchema(
        session: TeachingSession,
        apiKey: String,
        model: String = GeminiConfigStore.DEFAULT_MODEL
    ): GeminiSchemaResult? = withContext(Dispatchers.IO) {
        try {
            val actionsJson = JSONArray().apply {
                session.retainedActions.forEachIndexed { idx, action ->
                    put(JSONObject().apply {
                        put("stepIndex", idx)
                        put("actionType", action.actionType.name)
                        put("nodeText", action.targetNode.text)
                        put("nodeDescription", action.targetNode.contentDescription)
                        put("inputText", action.inputText)
                    })
                }
            }

            val prompt = """
                Analyze this recorded mobile task demonstration:
                User voice command: "${session.originalUtterance}"
                Target app: "${session.targetPackageHint ?: "Unknown"}"
                Recorded actions: $actionsJson
                
                Identify any dynamic parameters/slots (e.g. item name, quantity, search query, location) in the voice command and actions.
                Respond strictly in JSON:
                {
                  "generalizedIntent": "Intent template with {slots} e.g. 'Order {item} on {app}'",
                  "slots": [
                    {
                      "name": "slot_name",
                      "type": "string",
                      "required": true,
                      "defaultValue": "value from demo"
                    }
                  ],
                  "stepBindings": {
                    "0": "slot_name"
                  }
                }
            """.trimIndent()

            val responseJson = callGeminiForJson(apiKey, model, prompt) ?: return@withContext null

            val generalizedIntent = responseJson.optString("generalizedIntent").takeIf { it.isNotBlank() }
                ?: session.originalUtterance

            val slotsList = mutableListOf<GeminiSlotSynthesis>()
            val slotsArr = responseJson.optJSONArray("slots")
            if (slotsArr != null) {
                for (i in 0 until slotsArr.length()) {
                    val sObj = slotsArr.getJSONObject(i)
                    slotsList.add(
                        GeminiSlotSynthesis(
                            name = sObj.getString("name"),
                            type = sObj.optString("type", "string"),
                            required = sObj.optBoolean("required", true),
                            defaultValue = sObj.optString("defaultValue").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }

            val stepBindingsMap = mutableMapOf<Int, String>()
            val bindingsObj = responseJson.optJSONObject("stepBindings")
            if (bindingsObj != null) {
                val keys = bindingsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val stepIdx = k.toIntOrNull()
                    val slotName = bindingsObj.optString(k)
                    if (stepIdx != null && slotName.isNotBlank()) {
                        stepBindingsMap[stepIdx] = slotName
                    }
                }
            }

            GeminiSchemaResult(
                generalizedIntent = generalizedIntent,
                slots = slotsList,
                stepBindings = stepBindingsMap
            )
        } catch (e: Exception) {
            Log.w(tag, "synthesizeWorkflowSchema failed", e)
            null
        }
    }

    /**
     * Matches a natural voice utterance against learned workflows using Gemini GenAI.
     */
    suspend fun understandUtterance(
        utterance: String,
        workflows: List<Workflow>,
        apiKey: String,
        model: String = GeminiConfigStore.DEFAULT_MODEL
    ): GeminiIntentResult? = withContext(Dispatchers.IO) {
        if (workflows.isEmpty()) return@withContext null

        try {
            val workflowsJson = JSONArray().apply {
                workflows.forEach { wf ->
                    put(JSONObject().apply {
                        put("id", wf.id)
                        put("originalUtterance", wf.originalUtterance)
                        put("generalizedIntent", wf.generalizedIntent)
                        put("slots", JSONArray().apply {
                            wf.slotSchema.slots.forEach { put(it.name) }
                        })
                    })
                }
            }

            val prompt = """
                You are the speech-to-intent engine for SaySo Android Voice Assistant.
                User voice input: "$utterance"
                Available learned workflows: $workflowsJson
                
                Task:
                1. Determine if the user voice input matches any learned workflow (exact match, semantic paraphrase, or changed slot value).
                2. If matched, extract any parameter slot values present in the utterance.
                3. If no workflow matches, return matchedWorkflowId as null.
                
                Respond strictly in JSON:
                {
                  "matchedWorkflowId": "id-or-null",
                  "confidence": 0.95,
                  "matchType": "EXACT" | "PARAPHRASE" | "GENERALIZED",
                  "extractedSlots": {
                    "slotName": "extractedValue"
                  },
                  "explanation": "Brief reasoning"
                }
            """.trimIndent()

            val responseJson = callGeminiForJson(apiKey, model, prompt) ?: return@withContext null

            val matchedId = responseJson.optString("matchedWorkflowId").takeIf { it.isNotBlank() && it != "null" }
            val confidence = responseJson.optDouble("confidence", 0.0)
            val matchTypeStr = responseJson.optString("matchType", "GENERALIZED")
            val matchType = runCatching { MatchType.valueOf(matchTypeStr) }.getOrDefault(MatchType.GENERALIZED)

            val slotsMap = mutableMapOf<String, String>()
            val slotsObj = responseJson.optJSONObject("extractedSlots")
            if (slotsObj != null) {
                val keys = slotsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = slotsObj.optString(k)
                    if (v.isNotBlank()) {
                        slotsMap[k] = v
                    }
                }
            }

            val explanation = responseJson.optString("explanation", "")

            GeminiIntentResult(
                matchedWorkflowId = matchedId,
                confidence = confidence,
                matchType = matchType,
                extractedSlots = slotsMap,
                explanation = explanation
            )
        } catch (e: Exception) {
            Log.w(tag, "understandUtterance failed", e)
            null
        }
    }

    /**
     * Uses Gemini GenAI to extract target app, action, domain, and dynamic parameter entities
     * from any user spoken goal across all mobile app categories (Settings, Messaging, Media, Utilities, Maps, Shopping, etc.).
     */
    suspend fun extractGoalParameters(
        utterance: String,
        apiKey: String,
        model: String = GeminiConfigStore.DEFAULT_MODEL
    ): ExtractedParameters? = withContext(Dispatchers.IO) {
        if (utterance.isBlank()) return@withContext null

        try {
            val prompt = """
                You are the intelligent intent and entity extraction engine for SaySo, a universal Android voice automation assistant.
                The assistant supports automation across ALL kinds of apps on Android, including:
                - System Settings & Toggles (Airplane mode, Wi-Fi, Bluetooth, Hotspot, Display, Volume, Battery saver)
                - Messaging & Social (WhatsApp, Telegram, Messages, Gmail, Instagram)
                - Media & Entertainment (Spotify, YouTube, Music, Camera, Podcasts)
                - Productivity, Clock & Calendar (Alarms, Timers, Notes/Keep, Calendar events, Reminders, Calculator)
                - Navigation & Travel (Google Maps, Uber, Transit)
                - E-Commerce, Food & Services (Amazon, Zomato, Starbucks, Blinkit)
                - Any general Android app
                
                User spoken/written goal: "$utterance"
                
                Task:
                Analyze the command and extract:
                1. "appName": The name of the target application (e.g. "System Settings", "WhatsApp", "Spotify", "Clock", "YouTube", "Google Maps", "Amazon", or specific app mentioned).
                2. "targetPackage": Android package name if known (e.g. "com.android.settings", "com.whatsapp", "com.spotify.music", "com.google.android.deskclock", "com.google.android.youtube", "com.google.android.apps.maps", "com.amazon.mShop.android.shopping", or null).
                3. "actionVerb": The primary action verb (e.g. "Toggle", "Turn on", "Send message", "Play", "Set alarm", "Navigate", "Search", "Order", "Create note").
                4. "domain": One of "SETTINGS", "MESSAGING", "MEDIA", "PRODUCTIVITY_CLOCK", "NAVIGATION", "COMMERCE", "GENERIC".
                5. "parameters": A key-value object of all dynamic parameters found. Use standard descriptive keys based on the app domain:
                   - For Settings: "setting" (e.g. "Airplane Mode", "Wi-Fi", "Bluetooth")
                   - For Messaging: "recipient" (e.g. "Alex", "Mom"), "message" (e.g. "I'm on my way")
                   - For Media: "media" or "song" or "video" (e.g. "Bohemian Rhapsody", "documentary")
                   - For Clock/Calendar: "time" (e.g. "7:30 AM"), "duration" (e.g. "10 minutes"), "title" (e.g. "Team Sync")
                   - For Navigation: "destination" (e.g. "Central Station", "Airport")
                   - For Search: "query" (e.g. "pasta recipes")
                   - For Commerce: "item" (e.g. "Latte"), "store" (e.g. "Starbucks"), "quantity" (e.g. 1), "destination" (e.g. "Home")
                   - For Generic: "target" (the primary entity acted upon)
                
                Respond strictly in JSON format:
                {
                  "appName": "System Settings",
                  "targetPackage": "com.android.settings",
                  "actionVerb": "Toggle",
                  "domain": "SETTINGS",
                  "parameters": {
                    "setting": "Airplane Mode"
                  }
                }
            """.trimIndent()

            val json = callGeminiForJson(apiKey, model, prompt) ?: return@withContext null

            val appName = json.optString("appName").takeIf { it.isNotBlank() && it != "null" }
            val targetPkg = json.optString("targetPackage").takeIf { it.isNotBlank() && it != "null" }
            val actionVerb = json.optString("actionVerb").takeIf { it.isNotBlank() && it != "null" }
            val domainStr = json.optString("domain", "GENERIC")
            val domain = runCatching { AppDomain.valueOf(domainStr) }.getOrDefault(AppDomain.GENERIC)

            val paramsObj = json.optJSONObject("parameters")
            var setting: String? = null
            var recipient: String? = null
            var message: String? = null
            var media: String? = null
            var query: String? = null
            var time: String? = null
            var destination: String? = null
            var item: String? = null
            var store: String? = null
            var qty: Int? = null
            var target: String? = null

            if (paramsObj != null) {
                setting = paramsObj.optString("setting").takeIf { it.isNotBlank() && it != "null" }
                recipient = paramsObj.optString("recipient").takeIf { it.isNotBlank() && it != "null" }
                message = paramsObj.optString("message").takeIf { it.isNotBlank() && it != "null" }
                media = (paramsObj.optString("media").takeIf { it.isNotBlank() && it != "null" })
                    ?: paramsObj.optString("song").takeIf { it.isNotBlank() && it != "null" }
                    ?: paramsObj.optString("video").takeIf { it.isNotBlank() && it != "null" }
                query = paramsObj.optString("query").takeIf { it.isNotBlank() && it != "null" }
                time = (paramsObj.optString("time").takeIf { it.isNotBlank() && it != "null" })
                    ?: paramsObj.optString("duration").takeIf { it.isNotBlank() && it != "null" }
                destination = (paramsObj.optString("destination").takeIf { it.isNotBlank() && it != "null" })
                    ?: paramsObj.optString("location").takeIf { it.isNotBlank() && it != "null" }
                item = paramsObj.optString("item").takeIf { it.isNotBlank() && it != "null" }
                store = (paramsObj.optString("store").takeIf { it.isNotBlank() && it != "null" })
                    ?: paramsObj.optString("restaurant").takeIf { it.isNotBlank() && it != "null" }
                    ?: paramsObj.optString("merchant").takeIf { it.isNotBlank() && it != "null" }
                qty = paramsObj.optInt("quantity", 0).takeIf { it > 0 }
                target = paramsObj.optString("target").takeIf { it.isNotBlank() && it != "null" }
            }

            ExtractedParameters(
                appName = appName,
                targetPackage = targetPkg,
                actionVerb = actionVerb,
                primaryTarget = target ?: setting ?: media ?: query ?: item ?: recipient ?: destination,
                settingName = setting,
                recipient = recipient,
                messageContent = message,
                searchQuery = query,
                mediaTitle = media,
                timeOrDuration = time,
                destination = destination,
                itemName = item,
                storeOrSource = store,
                quantity = qty,
                domain = domain
            )
        } catch (e: Exception) {
            Log.w(tag, "extractGoalParameters via Gemini failed: ${e.message}")
            null
        }
    }

    /**
     * Uses Google Gemini GenAI to intelligently parse complex, natural, or relative device utility commands
     * (e.g. "wake me up in 3hours", "set alarm for 5:42 PM", "turn on flashlight", "remind me tomorrow to call doctor").
     */
    suspend fun parseSystemUtilityWithGemini(
        utterance: String,
        apiKey: String,
        model: String = GeminiConfigStore.DEFAULT_MODEL,
        currentDateTime: Calendar = Calendar.getInstance()
    ): SystemUtilityAction? = withContext(Dispatchers.IO) {
        if (utterance.isBlank()) return@withContext null

        try {
            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss, EEEE, hh:mm a", Locale.US).format(currentDateTime.time)
            val prompt = """
                You are the AI System Utility Interpreter for SaySo, a GenAI voice assistant running on Android.
                Current device date & time: "$timeFormat" (Timezone: ${TimeZone.getDefault().id}).
                
                User command: "$utterance"
                
                Task:
                Determine if this command is a built-in device utility action:
                - ALARM (e.g. "set alarm for 5:42 PM", "wake me up in 3hours", "alarm at 7", "show alarms")
                - TIMER (e.g. "set timer for 10 minutes", "start a 5 min timer", "show timers")
                - CALENDAR (e.g. "add reminder to call Mom at 5 PM tomorrow", "create event Team Sync at 3 PM on calendar")
                - FLASHLIGHT (e.g. "turn on flashlight", "torch on", "switch off torch")
                - SETTINGS (e.g. "open Wi-Fi settings", "bluetooth settings", "display settings", "open settings")
                
                If it is a 3rd-party app task (like ordering food on Zomato, booking an Uber, shopping on Amazon, social media) or general chat, set "isUtility": false.
                
                If it is a built-in utility:
                1. "ALARM":
                   - "hour": integer from 0 to 23 (24-hour format). CRITICAL: PM times must be 12-23 (e.g. 5:42 PM -> 17, 6 PM -> 18, 12 PM noon -> 12, 12 AM midnight -> 0).
                   - "minute": integer from 0 to 59.
                   - For relative expressions (e.g. "wake me up in 3hours", "in 45 minutes"), calculate current device time + duration to find target 24-hr hour and minute.
                   - "label": optional string label or null.
                   - "action": "SET_ALARM" or "SHOW_ALARMS".
                2. "TIMER":
                   - "durationSeconds": total seconds (integer).
                   - "label": optional timer label or null.
                   - "action": "SET_TIMER" or "SHOW_TIMERS".
                3. "CALENDAR":
                   - "title": event or reminder title.
                   - "startMillis": epoch timestamp in milliseconds for event start.
                   - "endMillis": epoch timestamp in milliseconds for event end.
                4. "FLASHLIGHT":
                   - "enable": boolean (true for on, false for off).
                5. "SETTINGS":
                   - "settingName": string (e.g. "Wi-Fi", "Bluetooth", "Display", "Sound", "Battery", "Settings").
                   - "intentAction": one of "android.settings.WIFI_SETTINGS", "android.settings.BLUETOOTH_SETTINGS", "android.settings.DISPLAY_SETTINGS", "android.settings.SOUND_SETTINGS", "android.settings.BATTERY_SAVER_SETTINGS", "android.settings.SETTINGS".
                
                Respond strictly in valid JSON:
                {
                  "isUtility": true,
                  "utilityType": "ALARM",
                  "action": "SET_ALARM",
                  "hour": 17,
                  "minute": 42,
                  "label": null
                }
                or
                {
                  "isUtility": false
                }
            """.trimIndent()

            val json = callGeminiForJson(apiKey, model, prompt) ?: return@withContext null
            val isUtility = json.optBoolean("isUtility", false)
            if (!isUtility) return@withContext null

            val utilityType = json.optString("utilityType", "").uppercase(Locale.ROOT)
            val actionType = json.optString("action", "").uppercase(Locale.ROOT)

            when (utilityType) {
                "ALARM" -> {
                    if (actionType == "SHOW_ALARMS" || json.optBoolean("showAlarms", false)) {
                        SystemUtilityAction.ShowAlarms
                    } else {
                        val hour = json.optInt("hour", -1)
                        val minute = json.optInt("minute", 0)
                        if (hour in 0..23 && minute in 0..59) {
                            val label = json.optString("label").takeIf { it.isNotBlank() && it != "null" }
                            SystemUtilityAction.SetAlarm(hour = hour, minute = minute, message = label)
                        } else null
                    }
                }
                "TIMER" -> {
                    if (actionType == "SHOW_TIMERS" || json.optBoolean("showTimers", false)) {
                        SystemUtilityAction.ShowTimers
                    } else {
                        val duration = json.optInt("durationSeconds", 0)
                        if (duration > 0) {
                            val label = json.optString("label").takeIf { it.isNotBlank() && it != "null" }
                            SystemUtilityAction.SetTimer(durationSeconds = duration, message = label)
                        } else null
                    }
                }
                "CALENDAR" -> {
                    val title = json.optString("title", "Event")
                    val startMillis = json.optLong("startMillis", System.currentTimeMillis() + 3600000)
                    val endMillis = json.optLong("endMillis", startMillis + 3600000)
                    SystemUtilityAction.AddCalendarEvent(title = title, startMillis = startMillis, endMillis = endMillis)
                }
                "FLASHLIGHT" -> {
                    val enable = json.optBoolean("enable", true)
                    SystemUtilityAction.ToggleFlashlight(enable = enable)
                }
                "SETTINGS" -> {
                    val settingName = json.optString("settingName", "Settings")
                    val intentAction = json.optString("intentAction", android.provider.Settings.ACTION_SETTINGS)
                    SystemUtilityAction.OpenSettings(settingName = settingName, intentAction = intentAction)
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(tag, "parseSystemUtilityWithGemini failed: ${e.message}")
            null
        }
    }

    private fun callGeminiForJson(
        apiKey: String,
        model: String,
        prompt: String,
        maxTokens: Int = 1024
    ): JSONObject? {
        val trimmedKey = apiKey.trim()
        val requestBody = JSONObject().apply {
            val partsArray = JSONArray().apply {
                put(JSONObject().apply { put("text", prompt) })
            }
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply { put("parts", partsArray) })
            }
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", maxTokens)
                put("responseMimeType", "application/json")
            })
        }

        for (version in apiVersions) {
            val endpoint = "$apiHost/$version/models/$model:generateContent?key=$trimmedKey"
            try {
                val (statusCode, responseText) = executePost(endpoint, requestBody.toString())
                if (statusCode in 200..299) {
                    val json = JSONObject(responseText)
                    val candidates = json.optJSONArray("candidates") ?: continue
                    if (candidates.length() == 0) continue

                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content") ?: continue
                    val parts = content.optJSONArray("parts") ?: continue
                    if (parts.length() == 0) continue

                    val rawText = parts.getJSONObject(0).optString("text", "").trim()
                    val cleanedText = cleanJsonMarkdown(rawText)

                    val parsed = runCatching { JSONObject(cleanedText) }.getOrNull()
                    if (parsed != null) return parsed
                } else {
                    Log.w(tag, "Gemini $version error ($statusCode): $responseText")
                }
            } catch (e: Exception) {
                Log.w(tag, "Gemini call exception on $version", e)
            }
        }
        return null
    }

    private fun cleanJsonMarkdown(text: String): String {
        var res = text.trim()
        if (res.startsWith("```json")) {
            res = res.removePrefix("```json").trim()
        } else if (res.startsWith("```")) {
            res = res.removePrefix("```").trim()
        }
        if (res.endsWith("```")) {
            res = res.removeSuffix("```").trim()
        }
        return res
    }

    private fun executeGet(endpoint: String): Pair<Int, String> {
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
        }

        val statusCode = conn.responseCode
        val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.let {
            BufferedReader(InputStreamReader(it, "UTF-8")).use { reader ->
                reader.readText()
            }
        } ?: ""

        conn.disconnect()
        return Pair(statusCode, responseText)
    }

    private fun executePost(endpoint: String, jsonBody: String): Pair<Int, String> {
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            doInput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
            writer.write(jsonBody)
            writer.flush()
        }

        val statusCode = conn.responseCode
        val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
        val responseText = stream?.let {
            BufferedReader(InputStreamReader(it, "UTF-8")).use { reader ->
                reader.readText()
            }
        } ?: ""

        conn.disconnect()
        return Pair(statusCode, responseText)
    }

    private fun extractErrorMessage(responseText: String, statusCode: Int): String {
        return try {
            val json = JSONObject(responseText)
            val error = json.optJSONObject("error")
            val message = error?.optString("message") ?: "HTTP $statusCode error"
            val status = error?.optString("status") ?: ""
            if (status.isNotBlank()) "$status: $message" else message
        } catch (_: Exception) {
            "API error (HTTP $statusCode): $responseText"
        }
    }
}
