package com.samsung.prism.teachable.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samsung.prism.teachable.ai.GeminiConfigStore
import com.samsung.prism.teachable.ai.GenAiManager
import com.samsung.prism.teachable.generalization.ExtractedParameters
import com.samsung.prism.teachable.generalization.UniversalDomainExtractor
import com.samsung.prism.teachable.generalization.WorkflowGeneralizer
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.replay.Orchestrator
import com.samsung.prism.teachable.replay.ReplayState
import com.samsung.prism.teachable.retrieval.WorkflowRetriever
import com.samsung.prism.teachable.service.AutomationAccessibilityService
import com.samsung.prism.teachable.storage.IWorkflowRepository
import com.samsung.prism.teachable.storage.RunResult
import com.samsung.prism.teachable.storage.WorkflowRepository
import com.samsung.prism.teachable.stuck.ClarificationGenerator
import com.samsung.prism.teachable.stuck.ClarificationOption
import com.samsung.prism.teachable.stuck.ClarificationQuestion
import com.samsung.prism.teachable.stuck.ClarificationResult
import com.samsung.prism.teachable.teaching.TeachingNotificationManager
import com.samsung.prism.teachable.teaching.TeachingRecorder
import com.samsung.prism.teachable.teaching.TeachingSession
import com.samsung.prism.teachable.voice.SpeechToText
import com.samsung.prism.teachable.voice.TTSManager
import com.samsung.prism.teachable.voice.VoiceInputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel @JvmOverloads constructor(
    application: Application,
    val repository: IWorkflowRepository = WorkflowRepository.create(application),
    val geminiConfigStore: GeminiConfigStore = GeminiConfigStore(application),
    val genAiManager: GenAiManager = GenAiManager.getInstance(application),
    val orchestrator: Orchestrator = Orchestrator(
        repository = repository,
        retriever = WorkflowRetriever(repository, genAiManager = genAiManager),
        clarificationGenerator = ClarificationGenerator(genAiManager),
        ttsManager = TTSManager(application)
    ),
    val speechToText: SpeechToText = SpeechToText(application)
) : AndroidViewModel(application) {

    private val generalizer = WorkflowGeneralizer()

    val isA11yConnected: StateFlow<Boolean> = AutomationAccessibilityService.isConnected

    private val _workflows = MutableStateFlow<List<Workflow>>(emptyList())
    val workflows: StateFlow<List<Workflow>> = _workflows.asStateFlow()

    private val _recentRuns = MutableStateFlow<List<RunResult>>(emptyList())
    val recentRuns: StateFlow<List<RunResult>> = _recentRuns.asStateFlow()

    val replayState: StateFlow<ReplayState> = orchestrator.state
    val statusMessage: StateFlow<String> = orchestrator.statusMessage
    val stuckClarification: StateFlow<ClarificationQuestion?> = orchestrator.stuckClarification
    val boundaryNotification: StateFlow<String?> = orchestrator.boundaryNotification
    val currentStepIndex: StateFlow<Int> = orchestrator.currentStepIndex
    val totalSteps: StateFlow<Int> = orchestrator.totalSteps

    val voiceInputState: StateFlow<VoiceInputState> = speechToText.state
    val speechErrorMessage: StateFlow<String?> = speechToText.errorMessage
    val recognizedText = speechToText.recognizedText
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    private val _currentTeachingSession = MutableStateFlow<TeachingSession?>(null)
    val currentTeachingSession: StateFlow<TeachingSession?> = _currentTeachingSession.asStateFlow()

    private val _lastLearnedWorkflow = MutableStateFlow<Workflow?>(null)
    val lastLearnedWorkflow: StateFlow<Workflow?> = _lastLearnedWorkflow.asStateFlow()

    fun clearLastLearnedWorkflow() {
        _lastLearnedWorkflow.value = null
    }

    val isTeaching: Boolean get() = TeachingRecorder.instance.isRecording

    // Tracks whether the user is in the interactive "Teach a new flow" setup dialog
    private val _isTeachingSetupActive = MutableStateFlow(false)
    val isTeachingSetupActive: StateFlow<Boolean> = _isTeachingSetupActive.asStateFlow()

    fun setTeachingSetupActive(active: Boolean) {
        _isTeachingSetupActive.value = active
    }

    // Gemini API Key & GenAI Configuration State
    private val _geminiApiKey = MutableStateFlow(geminiConfigStore.apiKey ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(geminiConfigStore.selectedModel)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _isGenAiEnabled = MutableStateFlow(geminiConfigStore.isGenAiEnabled)
    val isGenAiEnabled: StateFlow<Boolean> = _isGenAiEnabled.asStateFlow()

    private val _validationStatus = MutableStateFlow(geminiConfigStore.lastValidationStatus)
    val validationStatus: StateFlow<String> = _validationStatus.asStateFlow()

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    init {
        loadData()
        observeSpeechInput()
        observeTeachingActions()
    }

    private fun observeTeachingActions() {
        viewModelScope.launch {
            TeachingRecorder.instance.actionStream.collect {
                val session = _currentTeachingSession.value
                if (session != null) {
                    TeachingNotificationManager.updateActionCount(
                        context = getApplication(),
                        utterance = session.originalUtterance,
                        actionCount = session.retainedActions.size
                    )
                }
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _workflows.value = repository.findActive()
            _recentRuns.value = repository.getRecentRuns(20)
        }
    }

    private fun observeSpeechInput() {
        viewModelScope.launch {
            speechToText.recognizedText.collect { text ->
                if (text.isNotBlank() && !_isTeachingSetupActive.value && _currentTeachingSession.value == null) {
                    val activeClarification = orchestrator.stuckClarification.value
                    if (activeClarification != null) {
                        val result = orchestrator.clarificationHandler.handleVoiceResponse(text, activeClarification)
                        orchestrator.dismissClarification()
                        when (result) {
                            is ClarificationResult.ResumeWithNode -> {
                                viewModelScope.launch {
                                    orchestrator.actionExecutor.executeClick(result.node)
                                }
                            }
                            is ClarificationResult.SkipStep -> { /* dismissed */ }
                            is ClarificationResult.AbortWorkflow -> { /* dismissed */ }
                        }
                        loadData()
                    } else {
                        runCommand(text)
                    }
                }
            }
        }
    }

    suspend fun analyzeGoalWithGemini(utterance: String): ExtractedParameters {
        return genAiManager.extractParametersFromGoal(utterance)
    }

    fun analyzeGoalLocally(utterance: String, targetPackageHint: String? = null): ExtractedParameters {
        return UniversalDomainExtractor.extract(utterance, targetPackageHint)
    }

    fun startListening() {
        speechToText.startListening()
    }

    fun stopListening() {
        speechToText.stopListening()
    }

    fun runCommand(utterance: String) {
        viewModelScope.launch {
            orchestrator.execute(utterance)
            loadData()
        }
    }

    fun deleteWorkflow(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            loadData()
        }
    }

    fun startTeaching(utterance: String, targetPackageHint: String? = null) {
        val session = TeachingRecorder.instance.startSession(utterance, targetPackageHint)
        _currentTeachingSession.value = session
        orchestrator.ttsManager?.speak("Okay. I'll watch your actions.")
        TeachingNotificationManager.showTeachingNotification(
            context = getApplication(),
            utterance = utterance,
            actionCount = 0
        )
    }

    fun stopTeachingAndSave() {
        TeachingNotificationManager.dismissTeachingNotification(getApplication())
        val session = TeachingRecorder.instance.stopSession() ?: return
        if (session.retainedActions.isNotEmpty()) {
            viewModelScope.launch {
                val workflow = genAiManager.generalizeWorkflow(session)
                repository.save(workflow)
                loadData()
                _lastLearnedWorkflow.value = workflow
                orchestrator.ttsManager?.speak("Learned: ${workflow.originalUtterance}")
            }
        }
        _currentTeachingSession.value = null
    }

    fun cancelTeaching() {
        TeachingNotificationManager.dismissTeachingNotification(getApplication())
        TeachingRecorder.instance.cancelSession()
        _currentTeachingSession.value = null
    }

    fun resolveClarification(option: ClarificationOption) {
        val result = orchestrator.clarificationHandler.handleOptionSelection(option)
        orchestrator.dismissClarification()
        when (result) {
            is ClarificationResult.ResumeWithNode -> {
                viewModelScope.launch {
                    orchestrator.actionExecutor.executeClick(result.node)
                }
            }
            is ClarificationResult.SkipStep -> { /* dismissed */ }
            is ClarificationResult.AbortWorkflow -> { /* dismissed */ }
        }
        loadData()
    }

    fun dismissClarification() {
        orchestrator.dismissClarification()
        loadData()
    }

    fun dismissBoundaryAlert() {
        orchestrator.dismissClarification()
        loadData()
    }

    // Gemini API Key Management
    fun saveGeminiApiKey(key: String) {
        geminiConfigStore.saveKey(key)
        _geminiApiKey.value = key.trim()
        _validationStatus.value = "Key Saved"
    }

    fun clearGeminiApiKey() {
        geminiConfigStore.clearKey()
        _geminiApiKey.value = ""
        _validationStatus.value = "Key Cleared"
    }

    fun setModel(model: String) {
        geminiConfigStore.selectedModel = model
        _selectedModel.value = model
    }

    fun toggleGenAi(enabled: Boolean) {
        geminiConfigStore.isGenAiEnabled = enabled
        _isGenAiEnabled.value = enabled
    }

    // Available Gemini Models for UI Chips
    private val _availableModels = MutableStateFlow(geminiConfigStore.cachedAvailableModels)
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    fun testGeminiApiKey(key: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isValidating.value = true
            val trimmed = key.trim()
            val result = genAiManager.testKey(trimmed, _selectedModel.value)
            _isValidating.value = false
            if (result.isSuccess) {
                val validation = result.getOrThrow()
                _selectedModel.value = validation.activeModel
                if (validation.availableModels.isNotEmpty()) {
                    _availableModels.value = validation.availableModels
                }
                _validationStatus.value = "Connected (${validation.activeModel})"
                onResult(true, validation.message)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Validation failed"
                _validationStatus.value = "Error: $err"
                onResult(false, err)
            }
        }
    }
}
