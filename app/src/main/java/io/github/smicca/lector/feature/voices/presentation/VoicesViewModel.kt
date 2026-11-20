package io.github.smicca.lector.feature.voices.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.smicca.lector.core.model.CatalogEntry
import io.github.smicca.lector.core.model.DownloadState
import io.github.smicca.lector.core.model.InstalledModel
import io.github.smicca.lector.feature.voices.data.VoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoicesUiState(
    val catalog: List<CatalogEntry> = emptyList(),
    val installedModels: List<InstalledModel> = emptyList(),
    val selectedModelId: String? = null,
    val selectedVoiceId: String = "en-us-michael",
    val availableStorageBytes: Long = 0L,
    val downloadState: DownloadState = DownloadState.Idle,
    val message: String? = null
)

class VoicesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository(application)
    private val _uiState = MutableStateFlow(VoicesUiState())
    val uiState: StateFlow<VoicesUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            repository.downloadState.collect { dState ->
                _uiState.update { it.copy(downloadState = dState) }
                if (dState is DownloadState.Completed) {
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val catalog = repository.getCatalog()
            val installed = repository.getInstalledModels()
            val selectedModelId = repository.getSelectedModelId()
                ?: installed.firstOrNull { it.modelType == "KOKORO" }?.id
                ?: installed.firstOrNull()?.id
            val selectedVoiceId = repository.getSelectedVoiceId()
            val storage = repository.getAvailableStorageBytes()

            _uiState.update {
                it.copy(
                    catalog = catalog,
                    installedModels = installed,
                    selectedModelId = selectedModelId,
                    selectedVoiceId = selectedVoiceId,
                    availableStorageBytes = storage
                )
            }
        }
    }

    fun selectModel(modelId: String, voiceId: String) {
        repository.setSelected(modelId, voiceId)
        _uiState.update {
            it.copy(selectedModelId = modelId, selectedVoiceId = voiceId)
        }
        refresh()
    }

    fun downloadModel(modelId: String) {
        repository.startDownload(modelId)
    }

    fun deleteModel(folderName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteModel(folderName)
            refresh()
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.importFromUri(uri)
            result.onSuccess { model ->
                _uiState.update { it.copy(message = "Imported ${model.name}") }
                refresh()
            }.onFailure { err ->
                _uiState.update { it.copy(message = "Import failed: ${err.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
