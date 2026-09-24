package com.samsung.prism.teachable.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samsung.prism.teachable.generalization.WorkflowGeneralizer
import com.samsung.prism.teachable.model.Workflow
import com.samsung.prism.teachable.replay.Orchestrator
import com.samsung.prism.teachable.replay.ReplayState
import com.samsung.prism.teachable.service.AutomationAccessibilityService
import com.samsung.prism.teachable.storage.IWorkflowRepository
import com.samsung.prism.teachable.storage.RunResult
import com.samsung.prism.teachable.storage.WorkflowRepository
import com.samsung.prism.teachable.stuck.ClarificationOption
import com.samsung.prism.teachable.stuck.ClarificationQuestion
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

class MainViewModel(
    application: Application,
    val repository: IWorkflowRepository = WorkflowRepository.create(application),
    val orchestrator: Orchestrator = Orchestrator(
        repository = repository,
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
    val recognizedText = speechToText.recognizedText
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    private val _currentTeachingSession = MutableStateFlow<TeachingSession?>(null)
    val currentTeachingSession: StateFlow<TeachingSession?> = _currentTeachingSession.asStateFlow()

    val isTeaching: Boolean get() = TeachingRecorder.instance.isRecording

    init {
        loadData()
        observeSpeechInput()
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
                if (text.isNotBlank()) {
                    runCommand(text)
                }
            }
        }
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
    }

    fun stopTeachingAndSave() {
        val session = TeachingRecorder.instance.stopSession() ?: return
        if (session.retainedActions.isNotEmpty()) {
            val workflow = generalizer.generalize(session)
            viewModelScope.launch {
                repository.save(workflow)
                loadData()
                orchestrator.ttsManager?.speak("Learned: ${workflow.originalUtterance}")
            }
        }
        _currentTeachingSession.value = null
    }

    fun resolveClarification(option: ClarificationOption) {
        val result = orchestrator.clarificationHandler.handleOptionSelection(option)
        // Handled through orchestrator / UI update
        orchestrator.stuckDetector.reset()
    }

    fun dismissBoundaryAlert() {
        orchestrator.stuckDetector.reset()
    }
}
