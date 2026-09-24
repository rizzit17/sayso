package com.samsung.prism.teachable.retrieval

import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.model.SlotSchema
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.model.WorkflowStatus
import com.samsung.prism.teachable.storage.InMemoryWorkflowRepository
import com.samsung.prism.teachable.voice.MatchType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkflowRetrieverTest {

    private lateinit var repository: InMemoryWorkflowRepository
    private lateinit var retriever: WorkflowRetriever

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
                SlotDefinition(name = "platform", type = "enum", required = true, defaultValue = "Zomato", enumValues = listOf("Zomato", "Swiggy"))
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
                SlotDefinition(name = "platform", type = "enum", required = true, defaultValue = "Amazon")
            )
        ),
        status = WorkflowStatus.ACTIVE
    )

    @Before
    fun setUp() {
        repository = InMemoryWorkflowRepository()
        retriever = WorkflowRetriever(repository)
    }

    @Test
    fun testEmptyRepositoryReturnsNoActiveWorkflows() = runBlocking {
        val result = retriever.retrieve("Order pizza")
        assertTrue(result is RetrievalResult.NoActiveWorkflows)
    }

    @Test
    fun testExactMatchRetrieval() = runBlocking {
        repository.save(foodWorkflow)
        repository.save(amazonWorkflow)

        val result = retriever.retrieve("Order Margherita pizza from Domino's on Zomato")
        assertTrue(result is RetrievalResult.Selected)
        val selected = result as RetrievalResult.Selected
        assertEquals("wf_food_1", selected.workflow.id)
        assertEquals(MatchType.EXACT, selected.matchType)
        assertTrue(selected.confidence >= 0.95)
    }

    @Test
    fun testParaphraseRetrieval() = runBlocking {
        repository.save(foodWorkflow)
        repository.save(amazonWorkflow)

        val result = retriever.retrieve("Please get me Margherita pizza from Domino's with Zomato")
        assertTrue("Expected Selected, got $result", result is RetrievalResult.Selected)
        val selected = result as RetrievalResult.Selected
        assertEquals("wf_food_1", selected.workflow.id)
        assertTrue(selected.confidence >= 0.50)
    }

    @Test
    fun testOutOfDomainRetrieval() = runBlocking {
        repository.save(foodWorkflow)
        repository.save(amazonWorkflow)

        val result = retriever.retrieve("Set an alarm for 6 AM tomorrow morning")
        assertTrue(result is RetrievalResult.Unknown)
    }

    @Test
    fun testAmbiguousRetrieval() = runBlocking {
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
        repository.save(zomatoWf)
        repository.save(swiggyWf)

        val result = retriever.retrieve("Order Domino's pizza")
        assertTrue("Expected Ambiguous, got $result", result is RetrievalResult.Ambiguous)
        val ambiguous = result as RetrievalResult.Ambiguous
        assertEquals(2, ambiguous.candidates.size)
    }

    @Test
    fun testEmbeddingRetrieval() = runBlocking {
        val emb1 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val emb2 = floatArrayOf(0.0f, 1.0f, 0.0f)

        val wf1 = foodWorkflow.copy(intentEmbedding = emb1)
        val wf2 = amazonWorkflow.copy(intentEmbedding = emb2)
        repository.save(wf1)
        repository.save(wf2)

        // Query vector close to emb1
        val queryEmb = floatArrayOf(0.95f, 0.05f, 0.0f)
        val result = retriever.retrieve("food order", queryEmbedding = queryEmb)

        assertTrue(result is RetrievalResult.Selected)
        val selected = result as RetrievalResult.Selected
        assertEquals("wf_food_1", selected.workflow.id)
    }
}
