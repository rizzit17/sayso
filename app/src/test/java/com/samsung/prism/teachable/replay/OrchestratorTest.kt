package com.samsung.prism.teachable.replay

import com.samsung.prism.teachable.model.ExpectedStateTransition
import com.samsung.prism.teachable.model.SlotDefinition
import com.samsung.prism.teachable.model.SlotSchema
import com.samsung.prism.teachable.model.StepTarget
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.model.WorkflowStatus
import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.observation.Bounds
import com.samsung.prism.teachable.observation.UiNode
import com.samsung.prism.teachable.observation.UiSnapshot
import com.samsung.prism.teachable.storage.InMemoryWorkflowRepository
import com.samsung.prism.teachable.storage.RunStatus
import com.samsung.prism.teachable.teaching.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrchestratorTest {

    private lateinit var repository: InMemoryWorkflowRepository
    private lateinit var fakeActionExecutor: FakeActionExecutor
    private lateinit var orchestrator: Orchestrator

    private val searchBoxNode = UiNode(
        resourceId = "com.zomato:id/search_bar",
        text = "Restaurant name or a dish...",
        semanticRole = "search_box",
        className = "android.widget.EditText"
    )

    private val dominosCardNode = UiNode(
        resourceId = "com.zomato:id/restaurant_title",
        text = "Domino's Pizza",
        semanticRole = "card",
        clickable = true
    )

    private val pizzaWorkflow = Workflow(
        id = "wf_pizza_order",
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
        steps = listOf(
            WorkflowStep(
                id = "step_search",
                workflowId = "wf_pizza_order",
                stepOrder = 0,
                actionType = ActionType.SET_TEXT,
                inputText = "{item}",
                target = StepTarget(
                    resourceId = "com.zomato:id/search_bar",
                    text = "Restaurant name or a dish...",
                    semanticRole = "search_box"
                ),
                expectedStateTransition = ExpectedStateTransition(
                    expectedTextSubstring = "{item}"
                ),
                slotBinding = "item"
            ),
            WorkflowStep(
                id = "step_select_restaurant",
                workflowId = "wf_pizza_order",
                stepOrder = 1,
                actionType = ActionType.CLICK,
                target = StepTarget(
                    resourceId = "com.zomato:id/restaurant_title",
                    text = "Domino's Pizza",
                    semanticRole = "card"
                ),
                expectedStateTransition = ExpectedStateTransition(
                    expectedTextSubstring = "Domino's"
                ),
                slotBinding = "restaurant"
            )
        ),
        status = WorkflowStatus.ACTIVE
    )

    @Before
    fun setUp() = runBlocking {
        repository = InMemoryWorkflowRepository()
        repository.save(pizzaWorkflow)
        fakeActionExecutor = FakeActionExecutor()
        orchestrator = Orchestrator(
            repository = repository,
            actionExecutor = fakeActionExecutor
        )
    }

    private fun createSnapshot(nodes: List<UiNode>, sig: String = "sig"): UiSnapshot {
        val root = UiNode(
            resourceId = "root",
            className = "android.widget.FrameLayout",
            bounds = Bounds(0, 0, 1080, 2400),
            children = nodes
        )
        return UiSnapshot(
            packageName = "com.application.zomato",
            activityName = "MainActivity",
            rootNode = root,
            screenSignature = sig
        )
    }

    @Test
    fun testSuccessfulReplay() {
        runBlocking {
            var stepCall = 0
            val snapshotProvider = {
                stepCall++
                if (stepCall <= 2) {
                    createSnapshot(listOf(searchBoxNode), "sig_home")
                } else {
                    createSnapshot(listOf(dominosCardNode), "sig_results")
                }
            }

            val result = orchestrator.execute("Order Margherita pizza from Domino's on Zomato", snapshotProvider)

            assertEquals(RunStatus.COMPLETED, result.status)
            assertEquals(ReplayState.COMPLETED, orchestrator.state.value)
            assertEquals(2, result.stepResults.size)
            assertEquals("Margherita pizza", fakeActionExecutor.executedTexts.firstOrNull()?.second)
            assertEquals(dominosCardNode, fakeActionExecutor.executedClicks.firstOrNull())
        }
    }

    @Test
    fun testAlteredSlotReplay() {
        runBlocking {
            var stepCall = 0
            val snapshotProvider = {
                stepCall++
                if (stepCall <= 2) {
                    createSnapshot(listOf(searchBoxNode), "sig_home")
                } else {
                    createSnapshot(listOf(dominosCardNode), "sig_results")
                }
            }

            // T10: Changed slot value from Margherita pizza to Farmhouse pizza
            val result = orchestrator.execute("Order Farmhouse pizza from Domino's on Zomato", snapshotProvider)

            assertEquals(RunStatus.COMPLETED, result.status)
            assertEquals("Farmhouse pizza", fakeActionExecutor.executedTexts.firstOrNull()?.second)
        }
    }

    @Test
    fun testCredentialBoundaryHalt() {
        runBlocking {
            // Screen has payment options / UPI PIN / Pay Now
            val paymentNode = UiNode(
                text = "Select Payment Method: Google Pay, PhonePe, UPI PIN",
                semanticRole = "button"
            )
            val snapshotProvider = {
                createSnapshot(listOf(paymentNode), "sig_payment")
            }

            val result = orchestrator.execute("Order Margherita pizza from Domino's on Zomato", snapshotProvider)

            assertEquals(RunStatus.COMPLETED_TO_BOUNDARY, result.status)
            assertEquals(ReplayState.STOPPED_AT_BOUNDARY, orchestrator.state.value)
            assertNotNull(orchestrator.boundaryNotification.value)
            assertTrue(orchestrator.boundaryNotification.value!!.contains("Payment or Credential screen reached"))
            // Zero actions dispatched on payment screen!
            assertEquals(0, fakeActionExecutor.executedClicks.size)
            assertEquals(0, fakeActionExecutor.executedTexts.size)
        }
    }

    @Test
    fun testUnknownWorkflowFailsGracefully() {
        runBlocking {
            val result = orchestrator.execute("Book a flight from Bangalore to Delhi")
            assertEquals(RunStatus.FAILED, result.status)
            assertEquals(ReplayState.FAILED, orchestrator.state.value)
        }
    }

    @Test
    fun testAssociatedAppLaunchedBeforeWorkflowReplay() = runBlocking {
        val fakeLauncher = FakeAppLauncher()
        val testOrchestrator = Orchestrator(
            repository = repository,
            actionExecutor = fakeActionExecutor,
            appLauncher = fakeLauncher
        )

        val snapshotProvider = {
            createSnapshot(listOf(searchBoxNode, dominosCardNode), "sig_test")
        }

        testOrchestrator.executeWorkflow(pizzaWorkflow, snapshotProvider = snapshotProvider)

        assertTrue("Expected com.application.zomato to be launched", fakeLauncher.launchedPackages.contains("com.application.zomato"))
    }

    @Test
    fun testSettingsAppResolvedAndLaunchedFromUtteranceAndSteps() = runBlocking {
        val settingsWorkflow = Workflow(
            id = "wf_wifi_toggle",
            intentTag = "toggle_wifi",
            originalUtterance = "Turn off Wi-Fi in network settings",
            generalizedIntent = "Turn off Wi-Fi in network settings",
            supportedPackages = emptyList(), // Intentionally empty to test fallback inference
            steps = listOf(
                WorkflowStep(
                    id = "step_net",
                    workflowId = "wf_wifi_toggle",
                    stepOrder = 0,
                    actionType = ActionType.CLICK,
                    target = StepTarget(text = "Network & internetMobile, Wi-Fi, hotspot")
                )
            )
        )
        repository.save(settingsWorkflow)

        val fakeLauncher = FakeAppLauncher()
        val testOrchestrator = Orchestrator(
            repository = repository,
            actionExecutor = fakeActionExecutor,
            appLauncher = fakeLauncher
        )

        val settingsNode = UiNode(text = "Network & internetMobile, Wi-Fi, hotspot", clickable = true)
        val snapshotProvider = {
            UiSnapshot(
                packageName = "com.android.settings",
                rootNode = UiNode(children = listOf(settingsNode))
            )
        }

        testOrchestrator.executeWorkflow(settingsWorkflow, snapshotProvider = snapshotProvider)

        assertTrue(
            "Expected com.android.settings to be launched for settings workflow",
            fakeLauncher.launchedPackages.contains("com.android.settings")
        )
    }

    @Test
    fun testMultiStepAirplaneModeWorkflowReplay() = runBlocking {
        val airplaneWorkflow = Workflow(
            id = "wf_airplane_mode",
            intentTag = "toggle_airplane",
            originalUtterance = "Turn on airplane mode",
            generalizedIntent = "Turn on airplane mode",
            supportedPackages = listOf("com.android.settings"),
            steps = listOf(
                WorkflowStep(
                    id = "step_network",
                    workflowId = "wf_airplane_mode",
                    stepOrder = 0,
                    actionType = ActionType.CLICK,
                    target = StepTarget(text = "Network & internetMobile, Wi-Fi, hotspot")
                ),
                WorkflowStep(
                    id = "step_airplane",
                    workflowId = "wf_airplane_mode",
                    stepOrder = 1,
                    actionType = ActionType.CLICK,
                    target = StepTarget(
                        text = "Airplane mode",
                        className = "android.widget.Switch",
                        semanticRole = "switch"
                    )
                )
            )
        )
        repository.save(airplaneWorkflow)

        val netNode = UiNode(text = "Network & internetMobile, Wi-Fi, hotspot", clickable = true)
        val airplaneRow = UiNode(
            className = "android.widget.LinearLayout",
            clickable = true,
            children = listOf(
                UiNode(text = "Airplane mode", className = "android.widget.TextView", clickable = false),
                UiNode(resourceId = "android:id/switch_widget", className = "android.widget.Switch", clickable = true)
            )
        )

        var callCount = 0
        val snapshotProvider = {
            callCount++
            if (callCount <= 2) {
                // Step 0: root settings screen
                UiSnapshot(
                    packageName = "com.android.settings",
                    rootNode = UiNode(children = listOf(netNode))
                )
            } else {
                // Step 1: network & internet sub-page
                UiSnapshot(
                    packageName = "com.android.settings",
                    rootNode = UiNode(children = listOf(airplaneRow))
                )
            }
        }

        val testOrchestrator = Orchestrator(
            repository = repository,
            actionExecutor = fakeActionExecutor
        )

        val result = testOrchestrator.executeWorkflow(airplaneWorkflow, snapshotProvider = snapshotProvider)

        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals(ReplayState.COMPLETED, testOrchestrator.state.value)
        assertEquals(2, result.stepResults.size)
        assertEquals(2, fakeActionExecutor.executedClicks.size)
    }

    class FakeAppLauncher : IAppLauncher {
        val launchedPackages = mutableListOf<String>()
        var minimizedToHome = false
        var broughtSaysoToFront = false

        override suspend fun launchApp(packageName: String): Boolean {
            launchedPackages.add(packageName)
            return true
        }

        override suspend fun minimizeToHome(): Boolean {
            minimizedToHome = true
            return true
        }

        override suspend fun bringSaysoToFront(): Boolean {
            broughtSaysoToFront = true
            return true
        }
    }
}
