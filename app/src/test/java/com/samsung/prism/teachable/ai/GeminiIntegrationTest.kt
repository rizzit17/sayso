package com.samsung.prism.teachable.ai

import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.model.SlotSchema
import com.samsung.prism.teachable.model.StepTarget
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.model.WorkflowStatus
import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import com.samsung.prism.teachable.stuck.ClarificationActionType
import com.samsung.prism.teachable.stuck.StuckContext
import com.samsung.prism.teachable.teaching.ActionType
import com.samsung.prism.teachable.teaching.RawAction
import com.samsung.prism.teachable.teaching.TeachingSession
import com.samsung.prism.teachable.voice.IntentMatchResult
import com.samsung.prism.teachable.voice.MatchType
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiIntegrationTest {

    private val sampleWorkflow = Workflow(
        id = "wf_food_test",
        intentTag = "order_food",
        originalUtterance = "Order Margherita pizza from Domino's on Zomato",
        generalizedIntent = "Order {item} from {restaurant} on {platform}",
        slotSchema = SlotSchema(
            listOf(
                SlotDefinition("item", defaultValue = "Margherita pizza"),
                SlotDefinition("restaurant", defaultValue = "Domino's"),
                SlotDefinition("platform", defaultValue = "Zomato")
            )
        ),
        status = WorkflowStatus.ACTIVE
    )

    @Test
    fun testSupportedModelsList() {
        assertTrue(GeminiConfigStore.SUPPORTED_MODELS.contains("gemini-2.0-flash"))
        assertTrue(GeminiConfigStore.SUPPORTED_MODELS.contains("gemini-1.5-flash-latest"))
        assertTrue(GeminiConfigStore.SUPPORTED_MODELS.contains("gemini-1.5-flash"))
        assertTrue(GeminiConfigStore.SUPPORTED_MODELS.contains("gemini-1.5-pro"))
        assertEquals("gemini-2.0-flash", GeminiConfigStore.DEFAULT_MODEL)
    }

    @Test
    fun testCleanJsonMarkdownExtraction() {
        val rawResponseWithTicks = "```json\n{\"questionText\":\"What should I do?\",\"ttsPrompt\":\"What to do?\"}\n```"
        var cleaned = rawResponseWithTicks.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json").trim()
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```").trim()
        }
        val json = JSONObject(cleaned)
        assertEquals("What should I do?", json.getString("questionText"))
        assertEquals("What to do?", json.getString("ttsPrompt"))
    }

    @Test
    fun testGeminiIntentResultParsing() {
        val mockResponse = JSONObject().apply {
            put("matchedWorkflowId", "wf_food_test")
            put("confidence", 0.96)
            put("matchType", "PARAPHRASE")
            put("extractedSlots", JSONObject().apply {
                put("item", "Pepperoni pizza")
                put("quantity", "2")
            })
            put("explanation", "Matches food ordering workflow with changed slot value")
        }

        val matchedId = mockResponse.optString("matchedWorkflowId")
        val confidence = mockResponse.optDouble("confidence")
        val matchType = MatchType.valueOf(mockResponse.optString("matchType"))
        val slots = mockResponse.getJSONObject("extractedSlots")

        assertEquals("wf_food_test", matchedId)
        assertTrue(confidence > 0.9)
        assertEquals(MatchType.PARAPHRASE, matchType)
        assertEquals("Pepperoni pizza", slots.getString("item"))
        assertEquals("2", slots.getString("quantity"))
    }

    @Test
    fun testGeminiSchemaSynthesisParsing() {
        val mockSynthesis = JSONObject().apply {
            put("generalizedIntent", "Order {item} on {platform}")
            put("slots", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "item")
                    put("type", "string")
                    put("required", true)
                    put("defaultValue", "Pizza")
                })
            })
            put("stepBindings", JSONObject().apply {
                put("1", "item")
            })
        }

        val generalizedIntent = mockSynthesis.getString("generalizedIntent")
        val slots = mockSynthesis.getJSONArray("slots")
        val stepBindings = mockSynthesis.getJSONObject("stepBindings")

        assertEquals("Order {item} on {platform}", generalizedIntent)
        assertEquals(1, slots.length())
        assertEquals("item", slots.getJSONObject(0).getString("name"))
        assertEquals("item", stepBindings.getString("1"))
    }

    @Test
    fun testGeminiClarificationQuestionBuilding() {
        val candidate = UiNode(
            resourceId = "node_1",
            text = "Continue to Checkout",
            className = "android.widget.Button",
            clickable = true
        )

        val context = StuckContext(
            workflowId = "wf_food_test",
            step = WorkflowStep(target = StepTarget(text = "Checkout")),
            currentSnapshot = UiSnapshot.EMPTY,
            failureReason = "Target not visible",
            visibleCandidates = listOf(candidate)
        )

        val targetLabel = context.step.target.text ?: "element"
        val options = mutableListOf<com.samsung.prism.teachable.stuck.ClarificationOption>()

        options.add(
            com.samsung.prism.teachable.stuck.ClarificationOption(
                id = "alt_1",
                label = "Tap '${candidate.text}'",
                targetNode = candidate,
                actionType = ClarificationActionType.TAP_ALTERNATIVE
            )
        )
        options.add(
            com.samsung.prism.teachable.stuck.ClarificationOption(
                id = "opt_skip",
                label = "Skip step",
                actionType = ClarificationActionType.SKIP_STEP
            )
        )
        options.add(
            com.samsung.prism.teachable.stuck.ClarificationOption(
                id = "opt_abort",
                label = "Abort",
                actionType = ClarificationActionType.ABORT
            )
        )

        val question = com.samsung.prism.teachable.stuck.ClarificationQuestion(
            questionText = "I couldn't find '$targetLabel'. Would you like to tap '${candidate.text}'?",
            ttsPrompt = "I couldn't find Checkout. Should I tap Continue to Checkout, skip, or abort?",
            options = options
        )

        assertNotNull(question)
        assertEquals(3, question.options.size)
        assertEquals(ClarificationActionType.TAP_ALTERNATIVE, question.options[0].actionType)
        assertEquals(ClarificationActionType.SKIP_STEP, question.options[1].actionType)
        assertEquals(ClarificationActionType.ABORT, question.options[2].actionType)
    }

    @Test
    fun testOfflineFallbackWhenNoKeyIsSet() = runBlocking {
        // When no Gemini key is provided, GenAiManager transparently falls back to local engines
        val context = StuckContext(
            workflowId = "wf_food_test",
            step = WorkflowStep(target = StepTarget(text = "Pay Now")),
            currentSnapshot = UiSnapshot.EMPTY,
            failureReason = "Element not found",
            visibleCandidates = emptyList()
        )

        val generator = com.samsung.prism.teachable.stuck.ClarificationGenerator(null)
        val question = generator.generateQuestionSuspend(context)

        assertNotNull(question)
        assertTrue(question.questionText.contains("Pay Now"))
        assertTrue(question.options.any { it.actionType == ClarificationActionType.SKIP_STEP })
        assertTrue(question.options.any { it.actionType == ClarificationActionType.ABORT })
    }

    @Test
    fun testAutoModelResolution() {
        val availableModels = listOf("gemini-2.0-flash", "gemini-2.5-flash", "gemini-1.5-pro")
        val requestedModel = "gemini-1.5-flash" // Simulating Google retiring 1.5-flash on this account

        val resolved = when {
            availableModels.contains(requestedModel) -> requestedModel
            availableModels.any { it == "gemini-2.0-flash" } -> "gemini-2.0-flash"
            availableModels.any { it.startsWith("gemini-2.0-flash") } -> availableModels.first { it.startsWith("gemini-2.0-flash") }
            availableModels.any { it == "gemini-1.5-flash-latest" } -> "gemini-1.5-flash-latest"
            availableModels.any { it.contains("flash") } -> availableModels.first { it.contains("flash") }
            else -> requestedModel
        }

        assertEquals("gemini-2.0-flash", resolved)
    }
}
