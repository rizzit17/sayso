package com.samsung.prism.teachable.replay

import android.util.Log
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.model.WorkflowStep
import com.samsung.prism.teachable.observation.UiSnapshot
import com.samsung.prism.teachable.observation.UiTreeCapture
import com.samsung.prism.teachable.retrieval.RetrievalResult
import com.samsung.prism.teachable.retrieval.WorkflowRetriever
import com.samsung.prism.teachable.security.CredentialBoundaryDetector
import com.samsung.prism.teachable.service.AutomationAccessibilityService
import com.samsung.prism.teachable.storage.IWorkflowRepository
import com.samsung.prism.teachable.storage.RunResult
import com.samsung.prism.teachable.storage.RunStatus
import com.samsung.prism.teachable.storage.StepRunResult
import com.samsung.prism.teachable.stuck.ClarificationGenerator
import com.samsung.prism.teachable.stuck.ClarificationHandler
import com.samsung.prism.teachable.stuck.ClarificationQuestion
import com.samsung.prism.teachable.stuck.ClarificationResult
import com.samsung.prism.teachable.generalization.UniversalDomainExtractor
import com.samsung.prism.teachable.stuck.StuckDetector
import com.samsung.prism.teachable.utility.ISystemUtilityHandler
import com.samsung.prism.teachable.utility.SystemUtilityResult
import com.samsung.prism.teachable.voice.SlotExtractor
import com.samsung.prism.teachable.voice.TTSManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class Orchestrator(
    val repository: IWorkflowRepository,
    val retriever: WorkflowRetriever = WorkflowRetriever(repository),
    val slotExtractor: SlotExtractor = SlotExtractor(),
    val parameterBinder: ParameterBinder = ParameterBinder(),
    val uiMatcher: SemanticUiMatcher = SemanticUiMatcher(),
    val actionExecutor: IActionExecutor = ActionExecutor(),
    val stateVerifier: StateVerifier = StateVerifier(),
    val recoveryManager: RecoveryManager = RecoveryManager(),
    val stuckDetector: StuckDetector = StuckDetector(),
    val clarificationGenerator: ClarificationGenerator = ClarificationGenerator(),
    val clarificationHandler: ClarificationHandler = ClarificationHandler(),
    val boundaryDetector: CredentialBoundaryDetector = CredentialBoundaryDetector(),
    val systemUtilityHandler: ISystemUtilityHandler? = null,
    var ttsManager: TTSManager? = null,
    val appLauncher: IAppLauncher? = null
) {
    private val tag = "Orchestrator"

    private val _state = MutableStateFlow(ReplayState.IDLE)
    val state: StateFlow<ReplayState> = _state.asStateFlow()

    private val _activeWorkflow = MutableStateFlow<Workflow?>(null)
    val activeWorkflow: StateFlow<Workflow?> = _activeWorkflow.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _totalSteps = MutableStateFlow(0)
    val totalSteps: StateFlow<Int> = _totalSteps.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _stuckClarification = MutableStateFlow<ClarificationQuestion?>(null)
    val stuckClarification: StateFlow<ClarificationQuestion?> = _stuckClarification.asStateFlow()

    private val _boundaryNotification = MutableStateFlow<String?>(null)
    val boundaryNotification: StateFlow<String?> = _boundaryNotification.asStateFlow()

    fun dismissClarification() {
        _stuckClarification.value = null
        _boundaryNotification.value = null
        _state.value = ReplayState.IDLE
        _statusMessage.value = "Ready"
        stuckDetector.reset()
        ttsManager?.stop()
    }

    suspend fun execute(
        utterance: String,
        snapshotProvider: (() -> UiSnapshot?)? = null
    ): RunResult {
        val runId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        stuckDetector.reset()
        _boundaryNotification.value = null
        _stuckClarification.value = null

        // 0. FAST-PATH: System Utilities (Alarms, Timers, Calendar, Flashlight, Settings)
        if (systemUtilityHandler != null && systemUtilityHandler.canHandle(utterance)) {
            val existingExactWorkflow = repository.findActive().firstOrNull {
                it.originalUtterance.equals(utterance, ignoreCase = true)
            }
            if (existingExactWorkflow == null) {
                _state.value = ReplayState.EXECUTING_STEP
                _statusMessage.value = "Executing system utility..."
                val utilityResult = systemUtilityHandler.execute(utterance)
                if (utilityResult is SystemUtilityResult.Success) {
                    _state.value = ReplayState.COMPLETED
                    _statusMessage.value = utilityResult.message
                    ttsManager?.speak(utilityResult.message)
                    val res = RunResult(
                        runId = runId,
                        workflowId = "system_utility",
                        workflowTitle = utterance,
                        startedAt = startTime,
                        endedAt = System.currentTimeMillis(),
                        status = RunStatus.COMPLETED
                    )
                    repository.recordRun(res)
                    return res
                } else if (utilityResult is SystemUtilityResult.Failed) {
                    _state.value = ReplayState.FAILED
                    _statusMessage.value = utilityResult.reason
                    ttsManager?.speak(utilityResult.reason)
                    val res = RunResult(
                        runId = runId,
                        workflowId = "system_utility",
                        workflowTitle = utterance,
                        startedAt = startTime,
                        endedAt = System.currentTimeMillis(),
                        status = RunStatus.FAILED,
                        failureReason = utilityResult.reason
                    )
                    repository.recordRun(res)
                    return res
                }
            }
        }

        // 1. RETRIEVING
        _state.value = ReplayState.RETRIEVING
        _statusMessage.value = "Got it — finding your flow..."
        val retrieval = retriever.retrieve(utterance)

        val workflow = when (retrieval) {
            is RetrievalResult.Selected -> retrieval.workflow
            is RetrievalResult.NoActiveWorkflows -> {
                _state.value = ReplayState.FAILED
                _statusMessage.value = "I haven't learned this yet. Want to teach me?"
                ttsManager?.speak("I haven't learned this yet. Want to teach me?")
                val res = RunResult(runId, "unknown", utterance, startTime, System.currentTimeMillis(), RunStatus.FAILED, failureReason = "No active workflows")
                repository.recordRun(res)
                return res
            }
            is RetrievalResult.Ambiguous -> {
                _state.value = ReplayState.ASKING_USER
                _statusMessage.value = "I know a few things like that — which one did you mean?"
                ttsManager?.speak("I know a few things like that — which one did you mean?")
                val res = RunResult(runId, "unknown", utterance, startTime, System.currentTimeMillis(), RunStatus.ASKED_USER, failureReason = "Ambiguous intent")
                repository.recordRun(res)
                return res
            }
            is RetrievalResult.Unknown -> {
                _state.value = ReplayState.FAILED
                _statusMessage.value = "I haven't learned this yet. Want to teach me?"
                ttsManager?.speak("I haven't learned this yet. Want to teach me?")
                val res = RunResult(runId, "unknown", utterance, startTime, System.currentTimeMillis(), RunStatus.FAILED, failureReason = "Unknown intent")
                repository.recordRun(res)
                return res
            }
        }

        return executeWorkflow(
            workflow = workflow,
            utterance = utterance,
            snapshotProvider = snapshotProvider,
            existingRunId = runId,
            existingStartTime = startTime
        )
    }

    suspend fun executeWorkflow(
        workflow: Workflow,
        utterance: String = workflow.originalUtterance,
        snapshotProvider: (() -> UiSnapshot?)? = null,
        existingRunId: String? = null,
        existingStartTime: Long? = null
    ): RunResult {
        val runId = existingRunId ?: UUID.randomUUID().toString()
        val startTime = existingStartTime ?: System.currentTimeMillis()
        val stepResults = mutableListOf<StepRunResult>()
        stuckDetector.reset()
        _boundaryNotification.value = null
        _stuckClarification.value = null

        _activeWorkflow.value = workflow
        _totalSteps.value = workflow.steps.size

        // 2. EXTRACTING_SLOTS
        _state.value = ReplayState.EXTRACTING_SLOTS
        _statusMessage.value = "Extracting slot parameters..."
        val extraction = slotExtractor.extractSlots(workflow, utterance)

        if (!extraction.isComplete) {
            _state.value = ReplayState.ASKING_USER
            _statusMessage.value = "Missing info: ${extraction.missingRequiredSlots.joinToString()}"
            ttsManager?.speak("Could you specify ${extraction.missingRequiredSlots.firstOrNull()}?")
            val res = RunResult(runId, workflow.id, workflow.originalUtterance, startTime, System.currentTimeMillis(), RunStatus.ASKED_USER, failureReason = "Missing required slots")
            repository.recordRun(res)
            return res
        }

        // 3. BINDING
        _state.value = ReplayState.BINDING
        _statusMessage.value = "Binding parameters: ${extraction.boundSlots}..."
        val boundSteps = workflow.steps.map { parameterBinder.bindStep(it, extraction.boundSlots) }
        ttsManager?.speak("Starting workflow: ${workflow.originalUtterance}")

        // 3b. LAUNCH ASSOCIATED TARGET APP
        launchAssociatedAppIfNeeded(workflow, boundSteps, snapshotProvider)

        // 4. STEP EXECUTION LOOP
        for ((index, step) in boundSteps.withIndex()) {
            _currentStepIndex.value = index + 1
            recoveryManager.resetForNewStep()

            var stepExecuted = false
            var attempts = 0

            while (!stepExecuted && attempts < 3) {
                attempts++
                val snapshotBefore = captureSnapshot(snapshotProvider) ?: UiSnapshot.EMPTY

                // 4a. SAFETY CHECK: Check Credential / Payment Boundary
                val boundaryCheck = boundaryDetector.checkBoundary(snapshotBefore)
                if (boundaryCheck.isBoundary || step.isBoundary) {
                    val reason = if (boundaryCheck.isBoundary) boundaryCheck.reason else "Pre-defined workflow boundary reached"
                    Log.i(tag, "CREDENTIAL BOUNDARY HALT: $reason")
                    _state.value = ReplayState.STOPPED_AT_BOUNDARY
                    _boundaryNotification.value = "Your turn — I've reached the payment screen. Payment or Credential screen reached ($reason)."
                    ttsManager?.speak("Your turn — I've reached the payment screen.")
                    _statusMessage.value = "Your turn — I've reached the payment screen."
                    appLauncher?.bringSaysoToFront()

                    val res = RunResult(
                        runId = runId,
                        workflowId = workflow.id,
                        workflowTitle = workflow.originalUtterance,
                        startedAt = startTime,
                        endedAt = System.currentTimeMillis(),
                        status = RunStatus.COMPLETED_TO_BOUNDARY,
                        stoppedAtStepId = step.id,
                        failureReason = null,
                        boundParams = extraction.boundSlots,
                        stepResults = stepResults
                    )
                    repository.recordRun(res)
                    return res
                }

                // 4b. Find matching target element
                _state.value = ReplayState.EXECUTING_STEP
                _statusMessage.value = "On it."

                var match = uiMatcher.findBestMatch(step.target, snapshotBefore)

                if (match == null) {
                    // Trigger Recovery Manager
                    _state.value = ReplayState.RECOVERING
                    val recAction = recoveryManager.nextRecoveryAction(snapshotBefore)
                    _statusMessage.value = "Working around a change..."
                    Log.w(tag, "Recovery action triggered: ${recAction.stage}")

                    if (recAction.waitDelayMs > 0) {
                        delay(recAction.waitDelayMs)
                        continue
                    }
                    if (recAction.relaxedThreshold != null) {
                        match = uiMatcher.findBestMatch(step.target, snapshotBefore, threshold = recAction.relaxedThreshold)
                    }
                    if (recAction.stage == RecoveryStage.STAGE_5_ESCALATE) {
                        val stuckContext = stuckDetector.buildStuckContext(
                            workflow.id, step, snapshotBefore, recAction.stage, "Element not found"
                        )
                        if (stuckContext != null) {
                            val question = clarificationGenerator.generateQuestionSuspend(stuckContext)
                            _stuckClarification.value = question
                            _state.value = ReplayState.ASKING_USER
                            ttsManager?.speak(question.ttsPrompt)
                            appLauncher?.bringSaysoToFront()
                            val res = RunResult(
                                runId = runId,
                                workflowId = workflow.id,
                                workflowTitle = workflow.originalUtterance,
                                startedAt = startTime,
                                endedAt = System.currentTimeMillis(),
                                status = RunStatus.ASKED_USER,
                                stoppedAtStepId = step.id,
                                failureReason = "Stuck: ${question.questionText}",
                                boundParams = extraction.boundSlots,
                                stepResults = stepResults
                            )
                            repository.recordRun(res)
                            return res
                        }
                    }
                }

                val targetNode = match?.node
                if (targetNode == null) {
                    continue
                }

                // 4c. Execute Action
                _statusMessage.value = "On it."
                val dispatched = when (step.actionType) {
                    com.samsung.prism.teachable.teaching.ActionType.CLICK -> actionExecutor.executeClick(targetNode)
                    com.samsung.prism.teachable.teaching.ActionType.SET_TEXT -> actionExecutor.executeSetText(targetNode, step.inputText ?: "")
                    com.samsung.prism.teachable.teaching.ActionType.SCROLL_FORWARD -> actionExecutor.executeScroll(true, targetNode)
                    com.samsung.prism.teachable.teaching.ActionType.SCROLL_BACKWARD -> actionExecutor.executeScroll(false, targetNode)
                    com.samsung.prism.teachable.teaching.ActionType.LONG_CLICK -> actionExecutor.executeClick(targetNode)
                }

                // 4d. VERIFYING_STATE
                _state.value = ReplayState.VERIFYING_STATE
                delay(300)
                val snapshotAfter = captureSnapshot(snapshotProvider) ?: snapshotBefore

                val verification = stateVerifier.verify(step.expectedStateTransition, snapshotBefore, snapshotAfter)
                if (verification.verified || dispatched) {
                    stepExecuted = true
                    stepResults.add(
                        StepRunResult(
                            stepId = step.id,
                            outcome = "SUCCESS",
                            confidence = match.score,
                            recoveryInvoked = attempts > 1
                        )
                    )
                }
            }

            if (!stepExecuted) {
                _state.value = ReplayState.FAILED
                _statusMessage.value = "No — it stopped at step ${index + 1}."
                val res = RunResult(
                    runId = runId,
                    workflowId = workflow.id,
                    workflowTitle = workflow.originalUtterance,
                    startedAt = startTime,
                    endedAt = System.currentTimeMillis(),
                    status = RunStatus.FAILED,
                    stoppedAtStepId = step.id,
                    failureReason = "Failed to execute step ${index + 1}",
                    boundParams = extraction.boundSlots,
                    stepResults = stepResults
                )
                repository.recordRun(res)
                return res
            }
        }

        // 5. COMPLETED
        _state.value = ReplayState.COMPLETED
        _statusMessage.value = "Yes — completed successfully."
        ttsManager?.speak("Yes — completed successfully.")

        val finalRun = RunResult(
            runId = runId,
            workflowId = workflow.id,
            workflowTitle = workflow.originalUtterance,
            startedAt = startTime,
            endedAt = System.currentTimeMillis(),
            status = RunStatus.COMPLETED,
            boundParams = extraction.boundSlots,
            confidence = 1.0,
            stepResults = stepResults
        )
        repository.recordRun(finalRun)
        return finalRun
    }

    private suspend fun launchAssociatedAppIfNeeded(
        workflow: Workflow,
        steps: List<WorkflowStep>,
        snapshotProvider: (() -> UiSnapshot?)?
    ) {
        val targetPkg = resolveTargetPackage(workflow, steps)
        Log.i(tag, "Resolved target package for workflow: $targetPkg")

        if (!targetPkg.isNullOrBlank() && targetPkg != "com.samsung.prism.teachable") {
            _statusMessage.value = "Opening app..."
            val launched = appLauncher?.launchApp(targetPkg) ?: false
            Log.i(tag, "Target app launch ($targetPkg) result: $launched")

            if (launched) {
                val waitStart = System.currentTimeMillis()
                while (System.currentTimeMillis() - waitStart < 3500) {
                    delay(300)
                    val snap = captureSnapshot(snapshotProvider)
                    if (snap != null && snap.packageName.isNotBlank() && snap.packageName != "com.samsung.prism.teachable") {
                        Log.i(tag, "Foreground window is now: ${snap.packageName}")
                        break
                    }
                }
                delay(600)
            }
        } else {
            val snap = captureSnapshot(snapshotProvider)
            if (snap != null && snap.packageName == "com.samsung.prism.teachable") {
                Log.i(tag, "SaySo is in foreground without explicit target package; minimizing to home")
                appLauncher?.minimizeToHome()
                delay(800)
            }
        }
    }

    private fun resolveTargetPackage(workflow: Workflow, steps: List<WorkflowStep>): String? {
        val primary = workflow.primaryPackage
        if (!primary.isNullOrBlank() && primary != "com.samsung.prism.teachable") {
            return primary
        }

        val supported = workflow.supportedPackages.firstOrNull {
            it.isNotBlank() && it != "com.samsung.prism.teachable" && it != "com.android.systemui"
        }
        if (supported != null) return supported

        val fromTransition = steps.mapNotNull { it.expectedStateTransition.expectedPackageName }
            .firstOrNull { it.isNotBlank() && it != "com.samsung.prism.teachable" && it != "com.android.systemui" }
        if (fromTransition != null) return fromTransition

        val fromResId = steps.mapNotNull { it.target.resourceId?.substringBefore(':') }
            .firstOrNull { it.isNotBlank() && it != "com.samsung.prism.teachable" && it.contains(".") && it != "android" }
        if (fromResId != null) return fromResId

        val extracted = UniversalDomainExtractor.extract(workflow.originalUtterance)
        if (!extracted.targetPackage.isNullOrBlank() && extracted.targetPackage != "com.samsung.prism.teachable") {
            return extracted.targetPackage
        }

        val lower = workflow.originalUtterance.lowercase()
        val allStepText = steps.joinToString(" ") { "${it.target.text} ${it.target.contentDescription}" }.lowercase()
        if (lower.contains("setting") || lower.contains("wifi") || lower.contains("wi-fi") ||
            lower.contains("network") || lower.contains("bluetooth") || lower.contains("hotspot") ||
            lower.contains("airplane") || allStepText.contains("network & internet") ||
            allStepText.contains("connected devices") || allStepText.contains("hotspot")
        ) {
            return "com.android.settings"
        }

        return null
    }

    private fun captureSnapshot(provider: (() -> UiSnapshot?)?): UiSnapshot? {
        if (provider != null) {
            return provider()
        }
        return UiTreeCapture.captureCurrentScreen()
    }
}
