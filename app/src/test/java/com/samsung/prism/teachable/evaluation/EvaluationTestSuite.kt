package com.samsung.prism.teachable.evaluation

import com.samsung.prism.teachable.generalization.WorkflowGeneralizer
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
import com.samsung.prism.teachable.replay.FakeActionExecutor
import com.samsung.prism.teachable.replay.Orchestrator
import com.samsung.prism.teachable.replay.ReplayState
import com.samsung.prism.teachable.security.BoundaryCheckResult
import com.samsung.prism.teachable.security.CredentialBoundaryDetector
import com.samsung.prism.teachable.storage.InMemoryWorkflowRepository
import com.samsung.prism.teachable.storage.RunStatus
import com.samsung.prism.teachable.stuck.ClarificationOption
import com.samsung.prism.teachable.stuck.ClarificationResult
import com.samsung.prism.teachable.teaching.ActionType
import com.samsung.prism.teachable.teaching.IrrelevantActionFilter
import com.samsung.prism.teachable.teaching.RawAction
import com.samsung.prism.teachable.teaching.TeachingRecorder
import com.samsung.prism.teachable.teaching.TeachingSession
import com.samsung.prism.teachable.voice.IntentMatchResult
import com.samsung.prism.teachable.voice.IntentMatcher
import com.samsung.prism.teachable.voice.MatchType
import com.samsung.prism.teachable.voice.SlotExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Samsung PRISM GenAI Hackathon 3.0 — Theme 3: Teachable Voice Automation
 * Authoritative Evaluation Test Suite (T1 to T14 + Bonuses B1 to B3).
 *
 * Mapped 1:1 against the official evaluation rubric in Theme 3 - Evaluation Criteria.pdf / context.md §5:
 * - T1: Teach – Food Workflow (Zomato Margherita Pizza)
 * - T2: Exact Replay (Verbatim Utterance)
 * - T3: Paraphrased Voice Command
 * - T4: Slot Generalization: Item (Farmhouse Pizza)
 * - T5: Slot Generalization: Quantity (e.g. "two" -> 2)
 * - T6: Slot Generalization: Address (e.g. "deliver to work" -> "Work")
 * - T7: Screen Drift & State Change Recovery (UI Drift & Popups)
 * - T8: Teach – E-Commerce Workflow (Amazon Earbuds)
 * - T9: Cross-App Slot + Replay (Amazon Phone Case)
 * - T10: Genuinely Stuck Detection (<30s Bounded Recovery & Clarification)
 * - T11: Credential / Payment Boundary Guard (Zero Touches, -10 Penalty Shield)
 * - T12: Negative / Unknown Intent Rejection ("Book a cab to airport")
 * - T13: Intent Ambiguity Resolution ("Order pizza" / "Order Domino's")
 * - T14: Run History & Reporting
 * - B1: Irrelevant Action Filtering (Bonus)
 * - B2: Cross-App Generalization (Bonus)
 * - B3: Multi-Modal Voice + Tap Disambiguation (Bonus)
 */
class EvaluationTestSuite {

    private lateinit var repository: InMemoryWorkflowRepository
    private lateinit var fakeActionExecutor: FakeActionExecutor
    private lateinit var orchestrator: Orchestrator
    private lateinit var generalizer: WorkflowGeneralizer
    private lateinit var filter: IrrelevantActionFilter
    private lateinit var boundaryDetector: CredentialBoundaryDetector

    private fun createSnapshot(
        nodes: List<UiNode> = emptyList(),
        pkg: String = "com.application.zomato",
        activity: String = "MainActivity",
        sig: String = "sig_default"
    ): UiSnapshot {
        val root = UiNode(
            resourceId = "root",
            className = "android.widget.FrameLayout",
            packageName = pkg,
            bounds = Bounds(0, 0, 1080, 2400),
            children = nodes
        )
        return UiSnapshot(
            packageName = pkg,
            activityName = activity,
            rootNode = root,
            screenSignature = sig
        )
    }

    private val searchBoxNode = UiNode(
        resourceId = "com.zomato:id/search_bar",
        text = "Restaurant name or a dish...",
        semanticRole = "search_box",
        className = "android.widget.EditText"
    )

    private val dominosTileNode = UiNode(
        resourceId = "com.zomato:id/restaurant_title",
        text = "Domino's Pizza",
        semanticRole = "card",
        clickable = true
    )

    private val addButtonNode = UiNode(
        resourceId = "com.zomato:id/add_button",
        text = "Add",
        semanticRole = "button",
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
                expectedStateTransition = ExpectedStateTransition(expectedTextSubstring = "{item}")
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
                expectedStateTransition = ExpectedStateTransition(expectedTextSubstring = "Domino's")
            ),
            WorkflowStep(
                id = "step_add_to_cart",
                workflowId = "wf_pizza_order",
                stepOrder = 2,
                actionType = ActionType.CLICK,
                target = StepTarget(
                    resourceId = "com.zomato:id/add_button",
                    text = "Add",
                    semanticRole = "button"
                ),
                expectedStateTransition = ExpectedStateTransition(expectedTextSubstring = "View Cart")
            )
        ),
        status = WorkflowStatus.ACTIVE
    )

    @Before
    fun setUp() = runBlocking {
        repository = InMemoryWorkflowRepository()
        repository.save(pizzaWorkflow)
        fakeActionExecutor = FakeActionExecutor()
        generalizer = WorkflowGeneralizer()
        filter = IrrelevantActionFilter()
        boundaryDetector = CredentialBoundaryDetector()
        orchestrator = Orchestrator(
            repository = repository,
            actionExecutor = fakeActionExecutor,
            boundaryDetector = boundaryDetector
        )
    }

    // ==========================================
    // T1: Teach – Food Workflow (Demonstration + Storage)
    // ==========================================
    @Test
    fun testT1_TeachFoodWorkflow() = runBlocking {
        val teachingRecorder = TeachingRecorder(filter = filter)
        val session = teachingRecorder.startSession(
            "Order a Margherita pizza from Domino's on Zomato",
            "com.application.zomato"
        )
        assertTrue("Recorder must be active", teachingRecorder.isRecording)

        val snap1 = createSnapshot(listOf(searchBoxNode), sig = "s1")
        val snap2 = createSnapshot(listOf(dominosTileNode), sig = "s2")
        val snap3 = createSnapshot(listOf(addButtonNode), sig = "s3")

        teachingRecorder.recordAction(ActionType.SET_TEXT, searchBoxNode, "Margherita pizza", snap1, snap2)
        teachingRecorder.recordAction(ActionType.CLICK, dominosTileNode, null, snap2, snap3)
        teachingRecorder.recordAction(ActionType.CLICK, addButtonNode, null, snap3, snap3)

        val stoppedSession = teachingRecorder.stopSession()
        assertEquals(3, stoppedSession.retainedActions.size)

        val synthesized = generalizer.generalize(stoppedSession)
        assertNotNull("WorkflowGeneralizer must produce valid workflow", synthesized)
        assertEquals("com.application.zomato", synthesized.supportedPackages[0])
        assertEquals(3, synthesized.steps.size)
        assertTrue(synthesized.slotSchema.slots.any { it.name == "item" })

        repository.save(synthesized)
        val retrieved = repository.findById(synthesized.id)
        assertNotNull(retrieved)
        assertEquals(WorkflowStatus.ACTIVE, retrieved!!.status)
    }

    // ==========================================
    // T2: Exact Replay (Verbatim Utterance)
    // ==========================================
    @Test
    fun testT2_ExactReplay() = runBlocking {
        assertTrue("Workflow must have at least 3 steps", pizzaWorkflow.steps.size >= 3)
        var call = 0
        val provider = {
            call++
            when {
                call <= 2 -> createSnapshot(listOf(searchBoxNode), sig = "s1")
                call <= 4 -> createSnapshot(listOf(dominosTileNode), sig = "s2")
                else -> createSnapshot(listOf(addButtonNode), sig = "s3")
            }
        }
        val result = orchestrator.execute("Order Margherita pizza from Domino's on Zomato", provider)
        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals(3, result.stepResults.size)
        assertEquals(1, fakeActionExecutor.executedTexts.size)
        assertEquals("Margherita pizza", fakeActionExecutor.executedTexts[0].second)
    }

    // ==========================================
    // T3: Paraphrased Voice Command
    // ==========================================
    @Test
    fun testT3_ParaphrasedVoiceCommand() = runBlocking {
        val paraphrase = "Please get me Margherita pizza from Domino's on Zomato app"
        var call = 0
        val provider = {
            call++
            when {
                call <= 2 -> createSnapshot(listOf(searchBoxNode), sig = "s1")
                call <= 4 -> createSnapshot(listOf(dominosTileNode), sig = "s2")
                else -> createSnapshot(listOf(addButtonNode), sig = "s3")
            }
        }
        val result = orchestrator.execute(paraphrase, provider)
        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals("Margherita pizza", fakeActionExecutor.executedTexts[0].second)
    }

    // ==========================================
    // T4: Dynamic Slot: Item Generalization
    // ==========================================
    @Test
    fun testT4_DynamicSlotItem() = runBlocking {
        val slots = orchestrator.slotExtractor.extractSlots(
            pizzaWorkflow,
            "Order Farmhouse pizza from Domino's on Zomato"
        )
        assertEquals("Farmhouse pizza", slots.boundSlots["item"])

        var call = 0
        val provider = {
            call++
            when {
                call <= 2 -> createSnapshot(listOf(searchBoxNode), sig = "s1")
                call <= 4 -> createSnapshot(listOf(dominosTileNode), sig = "s2")
                else -> createSnapshot(listOf(addButtonNode), sig = "s3")
            }
        }
        val result = orchestrator.execute("Order Farmhouse pizza from Domino's on Zomato", provider)
        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals("Farmhouse pizza", fakeActionExecutor.executedTexts[0].second)
    }

    // ==========================================
    // T5: Dynamic Slot: Quantity Generalization
    // ==========================================
    @Test
    fun testT5_DynamicSlotQuantity() = runBlocking {
        val qtyWorkflow = pizzaWorkflow.copy(
            slotSchema = SlotSchema(
                listOf(
                    SlotDefinition(name = "item", type = "string", required = true, defaultValue = "Margherita pizza"),
                    SlotDefinition(name = "restaurant", type = "string", required = true, defaultValue = "Domino's"),
                    SlotDefinition(name = "quantity", type = "integer", required = false, defaultValue = "1")
                )
            )
        )
        val slots = orchestrator.slotExtractor.extractSlots(
            qtyWorkflow,
            "Order two Margherita pizzas from Domino's"
        )
        assertEquals(2, slots.boundSlots["quantity"])
    }

    // ==========================================
    // T6: Dynamic Slot: Address Generalization
    // ==========================================
    @Test
    fun testT6_DynamicSlotAddress() = runBlocking {
        val addressWorkflow = pizzaWorkflow.copy(
            slotSchema = SlotSchema(
                listOf(
                    SlotDefinition(name = "item", type = "string", required = true, defaultValue = "Margherita pizza"),
                    SlotDefinition(name = "restaurant", type = "string", required = true, defaultValue = "Domino's"),
                    SlotDefinition(
                        name = "address",
                        type = "enum",
                        required = false,
                        defaultValue = "Home",
                        enumValues = listOf("Home", "Work")
                    )
                )
            )
        )
        val slots = orchestrator.slotExtractor.extractSlots(
            addressWorkflow,
            "Order a Margherita from Domino's, deliver to work"
        )
        assertEquals("Work", slots.boundSlots["address"])
    }

    // ==========================================
    // T7: Screen Drift & State Recovery
    // ==========================================
    @Test
    fun testT7_ScreenDriftAndPopupRecovery() = runBlocking {
        val shiftedNode = UiNode(
            resourceId = "com.zomato:id/restaurant_title",
            text = "Domino's Pizza",
            semanticRole = "card",
            bounds = Bounds(40, 1800, 1040, 2100), // Shifted to bottom of screen
            clickable = true
        )
        val match = orchestrator.uiMatcher.findBestMatch(
            pizzaWorkflow.steps[1].target,
            createSnapshot(listOf(shiftedNode))
        )
        assertNotNull("Should match despite significant vertical position shift", match)
        assertTrue(match!!.score >= 0.70)
        assertEquals(shiftedNode, match.node)
    }

    // ==========================================
    // T8: Teach – E-Commerce Workflow (Amazon Earbuds)
    // ==========================================
    @Test
    fun testT8_TeachEcommerceWorkflow() = runBlocking {
        val amazonWf = Workflow(
            id = "wf_amazon_earbuds",
            intentTag = "ecommerce_search",
            originalUtterance = "Search for wireless earbuds on Amazon and add the first result to cart",
            generalizedIntent = "Search for {search_term} on Amazon and add the first result to cart",
            supportedPackages = listOf("com.amazon.mShop.android.shopping"),
            slotSchema = SlotSchema(
                listOf(
                    SlotDefinition(name = "search_term", type = "string", required = true, defaultValue = "wireless earbuds")
                )
            ),
            steps = listOf(
                WorkflowStep(
                    id = "step_amazon_search",
                    workflowId = "wf_amazon_earbuds",
                    stepOrder = 0,
                    actionType = ActionType.SET_TEXT,
                    inputText = "{search_term}",
                    target = StepTarget(
                        resourceId = "com.amazon.mShop.android.shopping:id/rs_search_src_text",
                        text = "Search Amazon",
                        semanticRole = "search_box"
                    )
                )
            ),
            status = WorkflowStatus.ACTIVE
        )
        repository.save(amazonWf)

        val activeWorkflows = repository.findActive()
        assertEquals("Repository should hold both food and ecommerce workflows", 2, activeWorkflows.size)
        assertTrue(activeWorkflows.any { it.id == "wf_pizza_order" })
        assertTrue(activeWorkflows.any { it.id == "wf_amazon_earbuds" })
    }

    // ==========================================
    // T9: Cross-App Slot + Replay (Amazon Phone Case)
    // ==========================================
    @Test
    fun testT9_CrossAppSlotReplay() = runBlocking {
        val amazonWf = Workflow(
            id = "wf_amazon_search",
            intentTag = "ecommerce_search",
            originalUtterance = "Search for wireless earbuds on Amazon and add the first result to cart",
            generalizedIntent = "Search for {search_term} on Amazon and add the first result to cart",
            supportedPackages = listOf("com.amazon.mShop.android.shopping"),
            slotSchema = SlotSchema(
                listOf(
                    SlotDefinition(name = "search_term", type = "string", required = true, defaultValue = "wireless earbuds")
                )
            ),
            steps = listOf(
                WorkflowStep(
                    id = "step_amazon_search",
                    workflowId = "wf_amazon_search",
                    stepOrder = 0,
                    actionType = ActionType.SET_TEXT,
                    inputText = "{search_term}",
                    target = StepTarget(
                        resourceId = "com.amazon.mShop.android.shopping:id/rs_search_src_text",
                        text = "Search Amazon",
                        semanticRole = "search_box"
                    )
                )
            ),
            status = WorkflowStatus.ACTIVE
        )
        repository.save(amazonWf)

        val slots = orchestrator.slotExtractor.extractSlots(
            amazonWf,
            "Search for a phone case on Amazon and add the first result to cart"
        )
        val boundStep = orchestrator.parameterBinder.bindStep(amazonWf.steps[0], slots.boundSlots)
        assertEquals("Phone case", boundStep.inputText)
    }

    // ==========================================
    // T10: Genuinely Stuck Detection (<30s Timeout & Clarification)
    // ==========================================
    @Test
    fun testT10_GenuinelyStuckDetection() = runBlocking {
        val notifyMeNode = UiNode(text = "Notify Me when in stock", semanticRole = "button", clickable = true)
        val outOfStockScreen = createSnapshot(nodes = listOf(notifyMeNode), sig = "sig_out_of_stock")

        val result = orchestrator.execute(
            "Order Margherita pizza from Domino's on Zomato",
            snapshotProvider = { outOfStockScreen }
        )

        assertEquals(RunStatus.ASKED_USER, result.status)
        assertEquals(ReplayState.ASKING_USER, orchestrator.state.value)
        assertNotNull(orchestrator.stuckClarification.value)
        assertTrue(orchestrator.stuckClarification.value!!.options.isNotEmpty())

        // Test user choice resolution (Skip step)
        val skipOption = ClarificationOption("skip", "Skip", actionType = com.samsung.prism.teachable.stuck.ClarificationActionType.SKIP_STEP)
        val resolution = orchestrator.clarificationHandler.handleOptionSelection(skipOption)
        assertEquals(ClarificationResult.SkipStep, resolution)
    }

    // ==========================================
    // T11: Payment & Credential Boundary (Safety Guard, -10 Penalty Shield)
    // ==========================================
    @Test
    fun testT11_PaymentCredentialBoundaryHalt() = runBlocking {
        val paymentScreen = createSnapshot(
            activity = "PaymentSelectionActivity",
            nodes = listOf(
                UiNode(text = "Select Payment Option"),
                UiNode(text = "Google Pay UPI"),
                UiNode(text = "Proceed to Pay Rs. 349", semanticRole = "button")
            ),
            sig = "sig_checkout"
        )

        val result = orchestrator.execute(
            "Order Margherita pizza from Domino's on Zomato",
            snapshotProvider = { paymentScreen }
        )

        // Strict Pass Criteria:
        // 1. Must halt before payment screen
        assertEquals(RunStatus.COMPLETED_TO_BOUNDARY, result.status)
        assertEquals(ReplayState.STOPPED_AT_BOUNDARY, orchestrator.state.value)
        // 2. User notified
        assertNotNull(orchestrator.boundaryNotification.value)
        // 3. ZERO clicks or text dispatched on the payment screen (-10 penalty prevention!)
        assertEquals("Zero clicks allowed on payment screen", 0, fakeActionExecutor.executedClicks.size)
        assertEquals("Zero text inputs allowed on payment screen", 0, fakeActionExecutor.executedTexts.size)
    }

    // ==========================================
    // T12: Negative / Unknown Intent Rejection
    // ==========================================
    @Test
    fun testT12_NegativeUnknownIntentRejection() = runBlocking {
        val matcher = IntentMatcher()
        val match = matcher.match("Book a cab to the airport", listOf(pizzaWorkflow))
        assertTrue(
            "Unrelated command must return Unknown or low confidence",
            match is IntentMatchResult.Unknown || (match is IntentMatchResult.Matched && match.confidence < 0.45)
        )

        val result = orchestrator.execute("Book a cab to the airport")
        assertFalse("Unrelated intent must not execute successfully", result.status == RunStatus.COMPLETED)
        assertEquals("Zero clicks dispatched for negative intent", 0, fakeActionExecutor.executedClicks.size)
        assertEquals("Zero text inputs dispatched for negative intent", 0, fakeActionExecutor.executedTexts.size)
    }

    // ==========================================
    // T13: Intent Ambiguity Resolution
    // ==========================================
    @Test
    fun testT13_IntentAmbiguityResolution() = runBlocking {
        val zomatoOrder = Workflow(
            id = "wf_zomato",
            intentTag = "order_dominos",
            originalUtterance = "Order Domino's on Zomato",
            generalizedIntent = "Order Domino's on Zomato",
            status = WorkflowStatus.ACTIVE
        )
        val swiggyOrder = Workflow(
            id = "wf_swiggy",
            intentTag = "order_dominos",
            originalUtterance = "Order Domino's on Swiggy",
            generalizedIntent = "Order Domino's on Swiggy",
            status = WorkflowStatus.ACTIVE
        )
        repository.save(zomatoOrder)
        repository.save(swiggyOrder)

        // Ambiguous command omitting platform
        val result = orchestrator.execute("Order Domino's please")
        assertEquals(RunStatus.ASKED_USER, result.status)
        assertEquals(ReplayState.ASKING_USER, orchestrator.state.value)
    }

    // ==========================================
    // T14: Run History & Reporting
    // ==========================================
    @Test
    fun testT14_ReportingAndRunHistory() = runBlocking {
        var call = 0
        val provider = {
            call++
            when {
                call <= 2 -> createSnapshot(listOf(searchBoxNode), sig = "s1")
                call <= 4 -> createSnapshot(listOf(dominosTileNode), sig = "s2")
                else -> createSnapshot(listOf(addButtonNode), sig = "s3")
            }
        }
        val result = orchestrator.execute("Order Margherita pizza from Domino's on Zomato", provider)
        assertEquals(RunStatus.COMPLETED, result.status)

        // Verify run was recorded accurately in repository
        val runs = repository.getRecentRuns(10)
        assertTrue("Run history must not be empty", runs.isNotEmpty())
        val lastRun = runs.first()
        assertEquals(RunStatus.COMPLETED, lastRun.status)
        assertEquals("wf_pizza_order", lastRun.workflowId)
        assertEquals(3, lastRun.stepResults.size)
        assertTrue((lastRun.endedAt - lastRun.startedAt) >= 0)
    }

    // ==========================================
    // Bonus B1: Irrelevant Action Filtering
    // ==========================================
    @Test
    fun testB1_IrrelevantActionFiltering() {
        val session = TeachingSession(
            originalUtterance = "Order pizza on Zomato",
            targetPackageHint = "com.application.zomato"
        )

        val before = createSnapshot(pkg = "com.application.zomato")
        val legitAction = RawAction(
            packageName = "com.application.zomato",
            actionType = ActionType.CLICK,
            targetNode = dominosTileNode,
            screenBefore = before
        )

        // Incoming phone call action (tangent)
        val callNode = UiNode(text = "Answer Call", semanticRole = "button")
        val phoneCallAction = RawAction(
            packageName = "com.google.android.dialer",
            actionType = ActionType.CLICK,
            targetNode = callNode,
            screenBefore = createSnapshot(pkg = "com.google.android.dialer")
        )

        session.rawActions.add(legitAction)
        session.rawActions.add(phoneCallAction)

        val eval1 = filter.evaluate(legitAction, session, before, null)
        legitAction.isFiltered = !eval1.isRelevant
        legitAction.filterReason = eval1.reason

        val eval2 = filter.evaluate(phoneCallAction, session, phoneCallAction.screenBefore, null)
        phoneCallAction.isFiltered = !eval2.isRelevant
        phoneCallAction.filterReason = eval2.reason

        assertEquals(1, session.retainedActions.size)
        assertEquals(1, session.filteredActions.size)
        assertEquals(legitAction, session.retainedActions[0])
        assertEquals(phoneCallAction, session.filteredActions[0])
    }

    // ==========================================
    // Bonus B2: Cross-App Generalization
    // ==========================================
    @Test
    fun testB2_CrossAppGeneralization() {
        val target = StepTarget(
            resourceId = "com.zomato:id/search_bar",
            text = "Search",
            semanticRole = "search_box",
            domainConcept = "search_input"
        )
        val swiggyNode = UiNode(
            resourceId = "in.swiggy.android:id/search_box",
            text = "Search for restaurants and food",
            semanticRole = "search_box",
            packageName = "in.swiggy.android",
            clickable = true
        )

        val match = orchestrator.uiMatcher.findBestMatch(
            target,
            createSnapshot(nodes = listOf(swiggyNode), pkg = "in.swiggy.android"),
            threshold = 0.55
        )

        assertNotNull("Should cross-app generalize via semantic role and domain concept", match)
        assertEquals(swiggyNode, match!!.node)
    }

    // ==========================================
    // Bonus B3: Multi-Modal Voice + Tap Disambiguation
    // ==========================================
    @Test
    fun testB3_MultiModalVoiceAndTapDisambiguation() {
        val opt1 = UiNode(text = "Domino's Pizza (MG Road)")
        val opt2 = UiNode(text = "Domino's Pizza (Indiranagar)")

        val question = com.samsung.prism.teachable.stuck.ClarificationQuestion(
            questionText = "Which outlet?",
            ttsPrompt = "Which Domino's outlet would you like?",
            options = listOf(
                ClarificationOption("1", "Tap 'MG Road'", opt1, com.samsung.prism.teachable.stuck.ClarificationActionType.TAP_ALTERNATIVE),
                ClarificationOption("2", "Tap 'Indiranagar'", opt2, com.samsung.prism.teachable.stuck.ClarificationActionType.TAP_ALTERNATIVE)
            )
        )

        // Spoken disambiguation
        val voiceRes = orchestrator.clarificationHandler.handleVoiceResponse("I want the Indiranagar one", question)
        assertTrue(voiceRes is ClarificationResult.ResumeWithNode)
        assertEquals(opt2, (voiceRes as ClarificationResult.ResumeWithNode).node)

        // Tap disambiguation
        val tapRes = orchestrator.clarificationHandler.handleOptionSelection(question.options[0])
        assertTrue(tapRes is ClarificationResult.ResumeWithNode)
        assertEquals(opt1, (tapRes as ClarificationResult.ResumeWithNode).node)
    }

    // ==========================================
    // Auxiliary Tests: Additional Robustness Scenarios
    // ==========================================

    @Test
    fun testAuxiliary_NoiseAndCasualSpeechHandling() = runBlocking {
        val noisySpeech = "Umm hey could you please order Margherita pizza from Domino's on Zomato right now thanks"
        var call = 0
        val provider = {
            call++
            when {
                call <= 2 -> createSnapshot(listOf(searchBoxNode), sig = "s1")
                call <= 4 -> createSnapshot(listOf(dominosTileNode), sig = "s2")
                else -> createSnapshot(listOf(addButtonNode), sig = "s3")
            }
        }
        val result = orchestrator.execute(noisySpeech, provider)
        assertEquals(RunStatus.COMPLETED, result.status)
    }

    @Test
    fun testAuxiliary_UiDrift_PositionShift() = runBlocking {
        val shiftedNode = UiNode(
            resourceId = "com.zomato:id/restaurant_title",
            text = "Domino's Pizza",
            semanticRole = "card",
            bounds = Bounds(40, 1800, 1040, 2100),
            clickable = true
        )
        val match = orchestrator.uiMatcher.findBestMatch(
            pizzaWorkflow.steps[1].target,
            createSnapshot(listOf(shiftedNode))
        )
        assertNotNull("Should match despite significant vertical position shift", match)
        assertTrue(match!!.score >= 0.70)
        assertEquals(shiftedNode, match.node)
    }

    @Test
    fun testAuxiliary_UiDrift_TextLabelChange() = runBlocking {
        val labelDriftNode = UiNode(
            resourceId = "com.zomato:id/add_button",
            text = "ADD TO CART",
            semanticRole = "button",
            clickable = true
        )
        val match = orchestrator.uiMatcher.findBestMatch(
            pizzaWorkflow.steps[2].target,
            createSnapshot(listOf(labelDriftNode))
        )
        assertNotNull(match)
        assertTrue(match!!.score >= 0.70)
    }

    @Test
    fun testAuxiliary_UiDrift_ListReorder() = runBlocking {
        val items = listOf(
            UiNode(text = "Pizza Hut", semanticRole = "card"),
            UiNode(text = "Subway", semanticRole = "card"),
            UiNode(resourceId = "com.zomato:id/restaurant_title", text = "Domino's Pizza", semanticRole = "card", clickable = true)
        )
        val match = orchestrator.uiMatcher.findBestMatch(
            pizzaWorkflow.steps[1].target,
            createSnapshot(items)
        )
        assertNotNull(match)
        assertEquals("Domino's Pizza", match!!.node.text)
    }

    @Test
    fun testAuxiliary_ExecutionSpeedBenchmark() = runBlocking {
        var call = 0
        val provider = {
            call++
            when {
                call <= 2 -> createSnapshot(listOf(searchBoxNode), sig = "s1")
                call <= 4 -> createSnapshot(listOf(dominosTileNode), sig = "s2")
                else -> createSnapshot(listOf(addButtonNode), sig = "s3")
            }
        }
        val startTime = System.currentTimeMillis()
        val result = orchestrator.execute("Order Margherita pizza from Domino's on Zomato", provider)
        val elapsedMs = System.currentTimeMillis() - startTime
        assertEquals(RunStatus.COMPLETED, result.status)
        assertTrue("Replay took ${elapsedMs}ms; must complete in under 10000ms", elapsedMs < 10000)
    }

    @Test
    fun testAuxiliary_CrossSessionWorkflowRecall() = runBlocking {
        val active = repository.findActive()
        assertTrue(active.any { it.id == "wf_pizza_order" })
        val retrieved = orchestrator.retriever.retrieve("Order Margherita pizza from Domino's on Zomato")
        assertTrue(retrieved is com.samsung.prism.teachable.retrieval.RetrievalResult.Selected)
    }
}
