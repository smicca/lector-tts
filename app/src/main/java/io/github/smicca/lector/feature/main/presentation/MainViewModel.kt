package io.github.smicca.lector.feature.main.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.smicca.lector.TestPassage
import io.github.smicca.lector.core.engine.LectorTtsEngine
import io.github.smicca.lector.core.model.InstalledModel
import io.github.smicca.lector.core.model.VoiceOption
import io.github.smicca.lector.feature.voices.data.VoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val text: String = TestPassage.RAW_TEXT,
    val isSpeaking: Boolean = false,
    val isPreparing: Boolean = false,
    val selectedModel: InstalledModel? = null,
    val selectedVoice: VoiceOption? = null,
    val availableVoices: List<VoiceOption> = emptyList(),
    val speed: Float = 1.0f,
    val hasInstalledModels: Boolean = false,
    val simulateFirstRun: Boolean = false,
    val statusMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository(application)
    private val engine = LectorTtsEngine.getInstance(application)
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = repository.getInstalledModels()
            val selectedModelId = repository.getSelectedModelId()
            val selectedVoiceId = repository.getSelectedVoiceId()

            val activeModel = installed.firstOrNull { it.id == selectedModelId }
                ?: installed.firstOrNull { it.modelType == "KOKORO" }
                ?: installed.firstOrNull()

            val voices = activeModel?.voices.orEmpty()
            val activeVoice = voices.firstOrNull { it.id == selectedVoiceId }
                ?: voices.firstOrNull { it.isDefault }
                ?: voices.firstOrNull()

            if (activeModel != null && activeVoice != null) {
                repository.setSelected(activeModel.id, activeVoice.id)
                engine.ensureModelLoaded(activeModel, numThreads = 4)
                engine.setSpeaker(activeVoice.speakerId)
            }

            _uiState.update {
                it.copy(
                    selectedModel = activeModel,
                    selectedVoice = activeVoice,
                    availableVoices = voices,
                    hasInstalledModels = installed.isNotEmpty()
                )
            }
        }
    }

    fun setText(newText: String) {
        _uiState.update { it.copy(text = newText) }
    }

    fun setSpeed(newSpeed: Float) {
        val clamped = newSpeed.coerceIn(0.5f, 2.0f)
        engine.setSpeed(clamped)
        _uiState.update { it.copy(speed = clamped) }
    }

    fun setVoice(voice: VoiceOption) {
        val currentModel = _uiState.value.selectedModel ?: return
        repository.setSelected(currentModel.id, voice.id)
        engine.setSpeaker(voice.speakerId)
        _uiState.update { it.copy(selectedVoice = voice) }
    }

    fun speak() {
        val state = _uiState.value
        val model = state.selectedModel
        if (model == null) {
            _uiState.update { it.copy(statusMessage = "Please download a voice first.") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isPreparing = true, statusMessage = "Preparing voice...") }
            engine.ensureModelLoaded(model, numThreads = 4)
            val speakerId = state.selectedVoice?.speakerId ?: 16

            engine.playText(
                text = state.text,
                speakerId = speakerId,
                speed = state.speed,
                onStart = {
                    _uiState.update { it.copy(isPreparing = false, isSpeaking = true, statusMessage = null) }
                },
                onFinished = {
                    _uiState.update { it.copy(isPreparing = false, isSpeaking = false) }
                },
                onError = { err ->
                    _uiState.update { it.copy(isPreparing = false, isSpeaking = false, statusMessage = "Error: ${err.message}") }
                }
            )
        }
    }

    fun stop() {
        engine.stop()
        _uiState.update { it.copy(isPreparing = false, isSpeaking = false) }
    }

    fun setSimulateFirstRun(simulate: Boolean) {
        _uiState.update { it.copy(simulateFirstRun = simulate) }
    }

    fun resetSampleText() {
        setText(TestPassage.RAW_TEXT)
    }

    fun downloadFirstVoice() {
        val catalog = repository.getCatalog()
        val kokoro = catalog.firstOrNull { it.recommended } ?: catalog.firstOrNull()
        if (kokoro != null) {
            repository.startDownload(kokoro.id)
        }
    }
}
