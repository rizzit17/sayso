package com.samsung.prism.teachable.storage

import com.samsung.prism.teachable.model.ExpectedStateTransition
import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.model.SlotSchema
import com.samsung.prism.teachable.model.StepTarget
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.model.WorkflowStatus
import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.teaching.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkflowSerializationAndRepoTest {

    @Test
    fun testStepTargetJsonRoundtrip() {
        val target = StepTarget(
            resourceId = "com.zomato:id/search_bar",
            text = "Search dishes",
            contentDescription = "Search bar",
            semanticRole = "search_box",
            className = "android.widget.EditText",
            parentContext = "LinearLayout",
            screenSignature = "sig_12345",
            boundsRelativeX = 0.5f,
            boundsRelativeY = 0.1f,
            domainConcept = "search_input"
        )

        val json = target.toJson()
        val restored = StepTarget.fromJson(json)

        assertEquals(target.resourceId, restored.resourceId)
        assertEquals(target.text, restored.text)
        assertEquals(target.contentDescription, restored.contentDescription)
        assertEquals(target.semanticRole, restored.semanticRole)
        assertEquals(target.className, restored.className)
        assertEquals(target.parentContext, restored.parentContext)
        assertEquals(target.screenSignature, restored.screenSignature)
        assertEquals(target.boundsRelativeX, restored.boundsRelativeX)
        assertEquals(target.boundsRelativeY, restored.boundsRelativeY)
        assertEquals(target.domainConcept, restored.domainConcept)
    }

    @Test
    fun testSlotSchemaJsonRoundtrip() {
        val slots = listOf(
            SlotDefinition(name = "item", type = "string", required = true, defaultValue = "Pizza"),
            SlotDefinition(name = "platform", type = "enum", required = true, defaultValue = "Zomato", enumValues = listOf("Zomato", "Swiggy")),
            SlotDefinition(name = "quantity", type = "integer", required = false, defaultValue = "1")
        )
        val schema = SlotSchema(slots)

        val json = schema.toJson()
        val restored = SlotSchema.fromJson(json)

        assertEquals(3, restored.slots.size)
        assertEquals("Pizza", restored.getSlot("item")?.defaultValue)
        assertEquals("Zomato", restored.getSlot("platform")?.defaultValue)
        assertEquals(listOf("Zomato", "Swiggy"), restored.getSlot("platform")?.enumValues)
        assertEquals("1", restored.getSlot("quantity")?.defaultValue)
    }

    @Test
    fun testWorkflowJsonRoundtrip() {
        val step = WorkflowStep(
            id = "step_0",
            workflowId = "wf_1",
            stepOrder = 0,
            actionType = ActionType.SET_TEXT,
            inputText = "Margherita",
            target = StepTarget(
                resourceId = "search_bar",
                text = "Search",
                semanticRole = "search_box"
            ),
            expectedStateTransition = ExpectedStateTransition(
                expectedTextSubstring = "{item}",
                expectedRole = "search_box",
                expectedPackageName = "com.application.zomato",
                slotBoundKey = "item"
            ),
            isBoundary = false,
            slotBinding = "item"
        )

        val workflow = Workflow(
            id = "wf_1",
            intentTag = "order_food",
            originalUtterance = "Order Margherita from Domino's",
            generalizedIntent = "Order {item} from {restaurant}",
            supportedPackages = listOf("com.application.zomato"),
            slotSchema = SlotSchema(listOf(SlotDefinition(name = "item", defaultValue = "Margherita"))),
            steps = listOf(step),
            status = WorkflowStatus.ACTIVE
        )

        val json = workflow.toJson()
        val restored = Workflow.fromJson(json)

        assertEquals(workflow.id, restored.id)
        assertEquals(workflow.intentTag, restored.intentTag)
        assertEquals(workflow.originalUtterance, restored.originalUtterance)
        assertEquals(workflow.generalizedIntent, restored.generalizedIntent)
        assertEquals(workflow.supportedPackages, restored.supportedPackages)
        assertEquals(workflow.status, restored.status)
        assertEquals(1, restored.steps.size)

        val restoredStep = restored.steps[0]
        assertEquals("step_0", restoredStep.id)
        assertEquals(ActionType.SET_TEXT, restoredStep.actionType)
        assertEquals("Margherita", restoredStep.inputText)
        assertEquals("item", restoredStep.slotBinding)
        assertEquals("{item}", restoredStep.expectedStateTransition.expectedTextSubstring)
    }

    @Test
    fun testInMemoryWorkflowRepository() = runBlocking {
        val repo = InMemoryWorkflowRepository()

        val wf1 = Workflow(
            id = "wf_1",
            intentTag = "order_pizza",
            originalUtterance = "Order pizza",
            generalizedIntent = "Order {item}",
            status = WorkflowStatus.ACTIVE,
            updatedAt = 1000L
        )
        val wf2 = Workflow(
            id = "wf_2",
            intentTag = "buy_book",
            originalUtterance = "Buy book",
            generalizedIntent = "Buy {item}",
            status = WorkflowStatus.ACTIVE,
            updatedAt = 2000L
        )

        repo.save(wf1)
        repo.save(wf2)

        val active = repo.findActive()
        assertEquals(2, active.size)
        // Order by updatedAt desc
        assertEquals("wf_2", active[0].id)
        assertEquals("wf_1", active[1].id)

        val retrieved = repo.findById("wf_1")
        assertNotNull(retrieved)
        assertEquals("Order pizza", retrieved?.originalUtterance)

        // Record runs
        val run1 = RunResult(
            runId = "run_1",
            workflowId = "wf_1",
            status = RunStatus.COMPLETED
        )
        val run2 = RunResult(
            runId = "run_2",
            workflowId = "wf_1",
            status = RunStatus.COMPLETED_TO_BOUNDARY
        )
        repo.recordRun(run1)
        repo.recordRun(run2)

        val lastRunWf1 = repo.lastRun("wf_1")
        assertNotNull(lastRunWf1)
        assertEquals("run_2", lastRunWf1?.runId)

        val recent = repo.getRecentRuns(10)
        assertEquals(2, recent.size)

        // Delete workflow
        repo.delete("wf_1")
        assertNull(repo.findById("wf_1"))
        assertEquals(1, repo.findActive().size)
    }
}
