package io.github.smicca.lector.core.model

import kotlinx.serialization.Serializable

@Serializable
data class VoiceOption(
    val id: String,
    val name: String,
    val speakerId: Int,
    val locale: String = "en-US",
    val isDefault: Boolean = false
)

@Serializable
data class CatalogEntry(
    val id: String,
    val name: String,
    val description: String,
    val recommended: Boolean = false,
    val archiveName: String,
    val folderName: String,
    val downloadUrl: String,
    val sha256: String,
    val archiveSizeBytes: Long,
    val installedSizeBytes: Long,
    val modelType: String,
    val qualityBadge: String,
    val speedBadge: String,
    val defaultVoiceId: String,
    val voices: List<VoiceOption>
)

data class InstalledModel(
    val id: String,
    val name: String,
    val folderName: String,
    val modelType: String,
    val sizeBytes: Long,
    val voices: List<VoiceOption>,
    val isSelected: Boolean = false
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progressPercent: Int, val bytesRead: Long, val totalBytes: Long) : DownloadState
    data class Extracting(val progressPercent: Int) : DownloadState
    data class Error(val message: String) : DownloadState
    data object Completed : DownloadState
}
