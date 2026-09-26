package com.samsung.prism.teachable.ai

import android.util.Log
import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.stuck.ClarificationActionType
import com.samsung.prism.teachable.stuck.ClarificationOption
import com.samsung.prism.teachable.stuck.ClarificationQuestion
import com.samsung.prism.teachable.stuck.StuckContext
import com.samsung.prism.teachable.teaching.TeachingSession
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

class GeminiApiClient(
    private val connectTimeoutMs: Int = 8000,
    private val readTimeoutMs: Int = 12000
) {
    private val tag = "GeminiApiClient"
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    /**
     * Tests the connection with the given API key and model by sending a ping prompt.
     */
    suspend fun testConnection(
        apiKey: String,
        model: String = GeminiConfigStore.DEFAULT_MODEL
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be blank"))
        }

        try {
            val endpoint = "$baseUrl/$model:generateContent?key=$trimmedKey"
            val requestBody = JSONObject().apply {
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

            val (statusCode, responseText) = executePost(endpoint, requestBody.toString())
            if (statusCode in 200..299) {
                val json = JSONObject(responseText)
                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    Result.success("Connection successful! Google Gemini is active.")
                } else {
                    Result.success("Connection verified (HTTP $statusCode).")
                }
            } else {
                val errorMsg = extractErrorMessage(responseText, statusCode)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.w(tag, "Gemini testConnection failed", e)
            Result.failure(e)
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

            // Find matching candidate node if recommended
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
                  "matchType": "EXACT" | "PARAPHRASE" | "GENERALIZED" | "UNKNOWN",
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

    private fun callGeminiForJson(
        apiKey: String,
        model: String,
        prompt: String
    ): JSONObject? {
        val trimmedKey = apiKey.trim()
        val endpoint = "$baseUrl/$model:generateContent?key=$trimmedKey"

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
                put("maxOutputTokens", 1024)
                put("responseMimeType", "application/json")
            })
        }

        val (statusCode, responseText) = executePost(endpoint, requestBody.toString())
        if (statusCode !in 200..299) {
            Log.w(tag, "Gemini API error ($statusCode): $responseText")
            return null
        }

        val json = JSONObject(responseText)
        val candidates = json.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null

        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null

        val rawText = parts.getJSONObject(0).optString("text", "").trim()
        val cleanedText = cleanJsonMarkdown(rawText)

        return runCatching { JSONObject(cleanedText) }.getOrNull()
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
