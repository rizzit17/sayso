package com.samsung.prism.teachable.voice

import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.model.SlotSchema
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.model.WorkflowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceUnderstandingTest {

    private val intentMatcher = IntentMatcher()
    private val slotExtractor = SlotExtractor()

    private val foodWorkflow = Workflow(
        id = "wf_food_1",
        intentTag = "order_food",
        originalUtterance = "Order Margherita pizza from Domino's on Zomato",
        generalizedIntent = "Order {item} from {restaurant} on {platform}",
        supportedPackages = listOf("com.application.zomato"),
        slotSchema = SlotSchema(
            listOf(
                SlotDefinition(name = "item", type = "string", required = true, defaultValue = "Margherita pizza"),
                SlotDefinition(name = "restaurant", type = "string", required = true, defaultValue = "Domino's"),
                SlotDefinition(name = "platform", type = "enum", required = true, defaultValue = "Zomato", enumValues = listOf("Zomato", "Swiggy")),
                SlotDefinition(name = "quantity", type = "integer", required = false, defaultValue = "1"),
                SlotDefinition(name = "address", type = "enum", required = false, defaultValue = "Home", enumValues = listOf("Home", "Work"))
            )
        ),
        status = WorkflowStatus.ACTIVE
    )

    private val amazonWorkflow = Workflow(
        id = "wf_amazon_1",
        intentTag = "ecommerce_search",
        originalUtterance = "Search for headphones on Amazon and add to cart",
        generalizedIntent = "Search for {item} on {platform} and add to cart",
        supportedPackages = listOf("in.amazon.mShop.android.shopping"),
        slotSchema = SlotSchema(
            listOf(
                SlotDefinition(name = "item", type = "string", required = true, defaultValue = "Headphones"),
                SlotDefinition(name = "platform", type = "enum", required = true, defaultValue = "Amazon", enumValues = listOf("Amazon", "Flipkart"))
            )
        ),
        status = WorkflowStatus.ACTIVE
    )

    @Test
    fun testExactMatch() {
        val workflows = listOf(foodWorkflow, amazonWorkflow)
        val result = intentMatcher.match("Order Margherita pizza from Domino's on Zomato", workflows)

        assertTrue(result is IntentMatchResult.Matched)
        val matched = result as IntentMatchResult.Matched
        assertEquals("wf_food_1", matched.workflow.id)
        assertEquals(MatchType.EXACT, matched.matchType)
        assertTrue(matched.confidence >= 0.95)
    }

    @Test
    fun testParaphrasedMatch() {
        val workflows = listOf(foodWorkflow, amazonWorkflow)
        // Paraphrase with different syntax and extra words
        val result = intentMatcher.match("Please get me a Margherita pizza from Domino's with Zomato", workflows)

        assertTrue("Expected matched result but was $result", result is IntentMatchResult.Matched)
        val matched = result as IntentMatchResult.Matched
        assertEquals("wf_food_1", matched.workflow.id)
        assertTrue(matched.confidence >= 0.50)
    }

    @Test
    fun testOutOfDomainRejection() {
        val workflows = listOf(foodWorkflow, amazonWorkflow)
        val result = intentMatcher.match("What is the weather outside in Seattle today?", workflows)

        assertTrue(result is IntentMatchResult.Unknown)
    }

    @Test
    fun testAmbiguousIntentDetection() {
        val zomatoWf = Workflow(
            id = "wf_zomato",
            intentTag = "order_dominos",
            originalUtterance = "Order Domino's pizza on Zomato",
            generalizedIntent = "Order Domino's pizza on Zomato",
            status = WorkflowStatus.ACTIVE
        )
        val swiggyWf = Workflow(
            id = "wf_swiggy",
            intentTag = "order_dominos",
            originalUtterance = "Order Domino's pizza on Swiggy",
            generalizedIntent = "Order Domino's pizza on Swiggy",
            status = WorkflowStatus.ACTIVE
        )

        // Utterance doesn't mention which platform
        val result = intentMatcher.match("Order Domino's pizza please", listOf(zomatoWf, swiggyWf))

        assertTrue("Expected Ambiguous but got $result", result is IntentMatchResult.Ambiguous)
        val ambiguous = result as IntentMatchResult.Ambiguous
        assertEquals(2, ambiguous.candidates.size)
    }

    @Test
    fun testSlotExtractorTaughtDefaults() {
        val utterance = "Order Margherita pizza from Domino's on Zomato"
        val extraction = slotExtractor.extractSlots(foodWorkflow, utterance)

        assertTrue(extraction.isComplete)
        assertEquals("Margherita pizza", extraction.boundSlots["item"])
        assertEquals("Domino's", extraction.boundSlots["restaurant"])
        assertEquals("Zomato", extraction.boundSlots["platform"])
        assertEquals(1, extraction.boundSlots["quantity"])
        assertEquals("Home", extraction.boundSlots["address"])
    }

    @Test
    fun testSlotExtractorAlteredValues() {
        // T10 evaluation criteria test: Altered slot values ("Farmhouse pizza", quantity 2, Work address)
        val utterance = "Order two Farmhouse pizza from Domino's on Zomato to office"
        val extraction = slotExtractor.extractSlots(foodWorkflow, utterance)

        assertTrue(extraction.isComplete)
        assertEquals("Farmhouse pizza", extraction.boundSlots["item"])
        assertEquals("Domino's", extraction.boundSlots["restaurant"])
        assertEquals("Zomato", extraction.boundSlots["platform"])
        assertEquals(2, extraction.boundSlots["quantity"])
        assertEquals("Work", extraction.boundSlots["address"])
    }

    @Test
    fun testSlotExtractorDetectsMissingSlot() {
        val strictWorkflow = Workflow(
            id = "wf_strict",
            intentTag = "custom_search",
            originalUtterance = "Find product",
            generalizedIntent = "Find {search_term}",
            slotSchema = SlotSchema(
                listOf(
                    SlotDefinition(name = "search_term", type = "string", required = true, defaultValue = null)
                )
            ),
            status = WorkflowStatus.ACTIVE
        )

        val extraction = slotExtractor.extractSlots(strictWorkflow, "Find")
        assertFalse(extraction.isComplete)
        assertTrue(extraction.missingRequiredSlots.contains("search_term"))
    }
}
