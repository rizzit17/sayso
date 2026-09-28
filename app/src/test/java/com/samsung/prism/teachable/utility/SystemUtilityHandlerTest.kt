package com.samsung.prism.teachable.utility

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
import com.samsung.prism.teachable.storage.InMemoryWorkflowRepository
import com.samsung.prism.teachable.storage.RunStatus
import com.samsung.prism.teachable.teaching.ActionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SystemUtilityHandlerTest {

    private lateinit var fakeUtilityHandler: FakeSystemUtilityHandler
    private lateinit var repository: InMemoryWorkflowRepository
    private lateinit var fakeActionExecutor: FakeActionExecutor
    private lateinit var orchestrator: Orchestrator

    @Before
    fun setUp() {
        fakeUtilityHandler = FakeSystemUtilityHandler()
        repository = InMemoryWorkflowRepository()
        fakeActionExecutor = FakeActionExecutor()
        orchestrator = Orchestrator(
            repository = repository,
            actionExecutor = fakeActionExecutor,
            systemUtilityHandler = fakeUtilityHandler
        )
    }

    // --- Parser Tests ---

    @Test
    fun testParseAlarmVariations() {
        // AM Alarm with colon
        val action1 = SystemUtilityParser.parse("Set an alarm for 7:00 AM")
        assertTrue(action1 is SystemUtilityAction.SetAlarm)
        assertEquals(7, (action1 as SystemUtilityAction.SetAlarm).hour)
        assertEquals(0, action1.minute)

        // PM Alarm with colon and message
        val action2 = SystemUtilityParser.parse("Set an alarm for 6:30 PM with message Workout")
        assertTrue(action2 is SystemUtilityAction.SetAlarm)
        assertEquals(18, (action2 as SystemUtilityAction.SetAlarm).hour)
        assertEquals(30, action2.minute)
        assertEquals("Workout", action2.message)

        // 5:42 PM exact test (uppercase, lowercase, p.m. with dot, space separated)
        val action542Pm = SystemUtilityParser.parse("set alarm for 5:42 PM")
        assertTrue(action542Pm is SystemUtilityAction.SetAlarm)
        val alarm542 = action542Pm as SystemUtilityAction.SetAlarm
        assertEquals(17, alarm542.hour)
        assertEquals(42, alarm542.minute)
        assertEquals("5:42 PM", alarm542.formattedTime)
        assertNull(alarm542.message) // "for 5:42 PM" must not be parsed as label!

        val action542Dot = SystemUtilityParser.parse("set alarm for 5:42 p.m.")
        assertTrue(action542Dot is SystemUtilityAction.SetAlarm)
        assertEquals(17, (action542Dot as SystemUtilityAction.SetAlarm).hour)
        assertEquals(42, action542Dot.minute)

        val action542Space = SystemUtilityParser.parse("set alarm for 5 42 pm")
        assertTrue(action542Space is SystemUtilityAction.SetAlarm)
        assertEquals(17, (action542Space as SystemUtilityAction.SetAlarm).hour)
        assertEquals(42, action542Space.minute)

        val action542Evening = SystemUtilityParser.parse("set alarm at 5:42 in the evening")
        assertTrue(action542Evening is SystemUtilityAction.SetAlarm)
        assertEquals(17, (action542Evening as SystemUtilityAction.SetAlarm).hour)
        assertEquals(42, action542Evening.minute)

        // Relative Alarm tests: "wake me up in 3hours" / "wake me up in 3 hours"
        val actionRelNoSpace = SystemUtilityParser.parse("wake me up in 3hours")
        assertTrue(actionRelNoSpace is SystemUtilityAction.SetAlarm)

        val actionRelSpace = SystemUtilityParser.parse("wake me up in 3 hours")
        assertTrue(actionRelSpace is SystemUtilityAction.SetAlarm)

        val actionRelMin = SystemUtilityParser.parse("wake me up in 30 minutes")
        assertTrue(actionRelMin is SystemUtilityAction.SetAlarm)

        // Simple text alarm
        val action3 = SystemUtilityParser.parse("Wake me up at 8 AM")
        assertTrue(action3 is SystemUtilityAction.SetAlarm)
        assertEquals(8, (action3 as SystemUtilityAction.SetAlarm).hour)
        assertEquals(0, action3.minute)

        // Show alarms
        val action4 = SystemUtilityParser.parse("Show alarms")
        assertTrue(action4 is SystemUtilityAction.ShowAlarms)
    }

    @Test
    fun testParseTimerVariations() {
        // Minutes timer
        val action1 = SystemUtilityParser.parse("Set a timer for 10 minutes")
        assertTrue(action1 is SystemUtilityAction.SetTimer)
        assertEquals(600, (action1 as SystemUtilityAction.SetTimer).durationSeconds)

        // Hour and minutes
        val action2 = SystemUtilityParser.parse("Set a timer for 1 hour and 30 minutes")
        assertTrue(action2 is SystemUtilityAction.SetTimer)
        assertEquals(5400, (action2 as SystemUtilityAction.SetTimer).durationSeconds)

        // Seconds timer
        val action3 = SystemUtilityParser.parse("Set a timer for 45 seconds")
        assertTrue(action3 is SystemUtilityAction.SetTimer)
        assertEquals(45, (action3 as SystemUtilityAction.SetTimer).durationSeconds)

        // Show timers
        val action4 = SystemUtilityParser.parse("Show timers")
        assertTrue(action4 is SystemUtilityAction.ShowTimers)
    }

    @Test
    fun testParseCalendarAndReminders() {
        val action1 = SystemUtilityParser.parse("Add a reminder to call Mom at 5 PM tomorrow")
        assertTrue(action1 is SystemUtilityAction.AddCalendarEvent)
        val calAction1 = action1 as SystemUtilityAction.AddCalendarEvent
        assertTrue(calAction1.title.contains("Call Mom", ignoreCase = true))
        assertNotNull(calAction1.startMillis)
        assertNotNull(calAction1.endMillis)

        val action2 = SystemUtilityParser.parse("Create event Team Standup on calendar at 10 AM")
        assertTrue(action2 is SystemUtilityAction.AddCalendarEvent)
        val calAction2 = action2 as SystemUtilityAction.AddCalendarEvent
        assertTrue(calAction2.title.contains("Team Standup", ignoreCase = true))
    }

    @Test
    fun testParseFlashlight() {
        val action1 = SystemUtilityParser.parse("Turn on flashlight")
        assertTrue(action1 is SystemUtilityAction.ToggleFlashlight)
        assertTrue((action1 as SystemUtilityAction.ToggleFlashlight).enable)

        val action2 = SystemUtilityParser.parse("Torch on")
        assertTrue(action2 is SystemUtilityAction.ToggleFlashlight)
        assertTrue((action2 as SystemUtilityAction.ToggleFlashlight).enable)

        val action3 = SystemUtilityParser.parse("Turn off flashlight")
        assertTrue(action3 is SystemUtilityAction.ToggleFlashlight)
        assertFalse((action3 as SystemUtilityAction.ToggleFlashlight).enable)

        val action4 = SystemUtilityParser.parse("Torch off")
        assertTrue(action4 is SystemUtilityAction.ToggleFlashlight)
        assertFalse((action4 as SystemUtilityAction.ToggleFlashlight).enable)
    }

    @Test
    fun testParseSettings() {
        val action1 = SystemUtilityParser.parse("Open Wi-Fi settings")
        assertTrue(action1 is SystemUtilityAction.OpenSettings)
        assertEquals("Wi-Fi", (action1 as SystemUtilityAction.OpenSettings).settingName)

        val action2 = SystemUtilityParser.parse("Open Bluetooth settings")
        assertTrue(action2 is SystemUtilityAction.OpenSettings)
        assertEquals("Bluetooth", (action2 as SystemUtilityAction.OpenSettings).settingName)
    }

    @Test
    fun testNonUtilityReturnsNull() {
        assertNull(SystemUtilityParser.parse("Order Margherita pizza from Domino's on Zomato"))
        assertNull(SystemUtilityParser.parse("Buy wireless headphones on Amazon"))
        assertNull(SystemUtilityParser.parse("Search for shoes on Flipkart"))
        assertNull(SystemUtilityParser.parse("Fly a rocket to the moon"))
    }

    // --- Orchestrator Fast-Path Integration Tests ---

    @Test
    fun testOrchestratorExecutesAlarmFastPath() = runBlocking {
        val result = orchestrator.execute("Set an alarm for 7:00 AM")

        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals("system_utility", result.workflowId)
        assertEquals(ReplayState.COMPLETED, orchestrator.state.value)
        assertEquals(1, fakeUtilityHandler.executedActions.size)
        val executed = fakeUtilityHandler.executedActions.first()
        assertTrue(executed is SystemUtilityAction.SetAlarm)
        assertEquals(7, (executed as SystemUtilityAction.SetAlarm).hour)

        // Check that run was logged in database
        val runs = repository.getRecentRuns(10)
        assertEquals(1, runs.size)
        assertEquals("system_utility", runs.first().workflowId)
    }

    @Test
    fun testOrchestratorExecutesTimerFastPath() = runBlocking {
        val result = orchestrator.execute("Set a timer for 5 minutes")

        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals("system_utility", result.workflowId)
        assertEquals(1, fakeUtilityHandler.executedActions.size)
        val executed = fakeUtilityHandler.executedActions.first()
        assertTrue(executed is SystemUtilityAction.SetTimer)
        assertEquals(300, (executed as SystemUtilityAction.SetTimer).durationSeconds)
    }

    @Test
    fun testOrchestratorExecutesCalendarFastPath() = runBlocking {
        val result = orchestrator.execute("Add a reminder to call Mom at 5 PM tomorrow")

        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals("system_utility", result.workflowId)
        val executed = fakeUtilityHandler.executedActions.first()
        assertTrue(executed is SystemUtilityAction.AddCalendarEvent)
    }

    @Test
    fun testOrchestratorExecutesFlashlightFastPath() = runBlocking {
        val result = orchestrator.execute("Turn on flashlight")

        assertEquals(RunStatus.COMPLETED, result.status)
        assertEquals("system_utility", result.workflowId)
        val executed = fakeUtilityHandler.executedActions.first()
        assertTrue(executed is SystemUtilityAction.ToggleFlashlight)
        assertTrue((executed as SystemUtilityAction.ToggleFlashlight).enable)
    }

    @Test
    fun testTaughtWorkflowOverridesUtilityWhenExactMatchExists() = runBlocking {
        // If a user specifically taught a workflow called "Set an alarm for 7:00 AM"
        val customWf = Workflow(
            id = "custom_alarm_flow",
            intentTag = "custom_alarm",
            originalUtterance = "Set an alarm for 7:00 AM",
            generalizedIntent = "Set an alarm for 7:00 AM",
            supportedPackages = listOf("com.google.android.deskclock"),
            status = WorkflowStatus.ACTIVE,
            steps = listOf(
                WorkflowStep(
                    id = "step_1",
                    workflowId = "custom_alarm_flow",
                    stepOrder = 0,
                    target = StepTarget(text = "Add Alarm", semanticRole = "button"),
                    actionType = ActionType.CLICK
                )
            )
        )
        repository.save(customWf)

        val dummySnapshot = UiSnapshot(
            packageName = "com.google.android.deskclock",
            activityName = "MainActivity",
            allNodes = listOf(
                UiNode(text = "Add Alarm", semanticRole = "button", clickable = true)
            )
        )

        val result = orchestrator.execute("Set an alarm for 7:00 AM") { dummySnapshot }

        // Since exact taught workflow exists, it prioritized the taught workflow!
        assertEquals("custom_alarm_flow", result.workflowId)
        assertEquals(0, fakeUtilityHandler.executedActions.size)
    }

    @Test
    fun testUnknownAppIntentDoesNotTriggerUtility() = runBlocking {
        val result = orchestrator.execute("Fly a rocket to the moon")
        assertEquals(RunStatus.FAILED, result.status)
        assertEquals(0, fakeUtilityHandler.executedActions.size)
    }
}
