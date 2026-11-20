package io.github.smicca.lector.feature.voices.data

import android.content.Context
import android.net.Uri
import io.github.smicca.lector.core.model.CatalogEntry
import io.github.smicca.lector.core.model.DownloadState
import io.github.smicca.lector.core.model.InstalledModel
import io.github.smicca.lector.core.model.VoiceModelCatalog
import io.github.smicca.lector.core.service.ModelDownloadService
import io.github.smicca.lector.core.storage.ModelStorage
import kotlinx.coroutines.flow.StateFlow

class VoiceRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("lector_prefs", Context.MODE_PRIVATE)

    val downloadState: StateFlow<DownloadState> = ModelDownloadService.downloadState

    fun getCatalog(): List<CatalogEntry> = VoiceModelCatalog.loadCatalog(context)

    fun getInstalledModels(): List<InstalledModel> {
        val selectedId = getSelectedModelId()
        return ModelStorage.getInstalledModels(context, selectedId)
    }

    fun getSelectedModelId(): String? = prefs.getString("selected_model_id", null)

    fun getSelectedVoiceId(): String = prefs.getString("selected_voice_id", "en-us-michael") ?: "en-us-michael"

    fun setSelected(modelId: String, voiceId: String) {
        prefs.edit()
            .putString("selected_model_id", modelId)
            .putString("selected_voice_id", voiceId)
            .apply()
    }

    fun startDownload(modelId: String) {
        ModelDownloadService.startDownload(context, modelId)
    }

    fun cancelDownload() {
        ModelDownloadService.cancelDownload(context)
    }

    fun deleteModel(folderName: String): Boolean {
        val success = ModelStorage.deleteModel(context, folderName)
        if (success) {
            val installed = getInstalledModels()
            val newSelected = installed.firstOrNull()
            if (newSelected != null) {
                setSelected(newSelected.id, newSelected.voices.firstOrNull()?.id ?: "default")
            } else {
                prefs.edit().remove("selected_model_id").remove("selected_voice_id").apply()
            }
        }
        return success
    }

    fun importFromUri(uri: Uri): Result<InstalledModel> {
        val res = ModelStorage.importFromUri(context, uri)
        res.onSuccess { model ->
            setSelected(model.id, model.voices.firstOrNull()?.id ?: "default")
        }
        return res
    }

    fun getAvailableStorageBytes(): Long = ModelStorage.getAvailableStorageBytes(context)
}
