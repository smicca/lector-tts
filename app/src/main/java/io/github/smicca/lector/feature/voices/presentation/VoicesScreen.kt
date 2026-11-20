package io.github.smicca.lector.feature.voices.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.smicca.lector.core.model.CatalogEntry
import io.github.smicca.lector.core.model.DownloadState
import io.github.smicca.lector.core.model.InstalledModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicesScreen(
    viewModel: VoicesViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var deleteCandidate by remember { mutableStateOf<InstalledModel?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importModel(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voices") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Main"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StorageAndImportHeader(
                    storageBytes = uiState.availableStorageBytes,
                    onImportClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "application/x-bzip2",
                                "application/x-tar",
                                "application/zip",
                                "application/octet-stream"
                            )
                        )
                    }
                )
            }

            item {
                Text(
                    text = "AVAILABLE VOICES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            items(uiState.catalog, key = { it.id }) { catalogEntry ->
                val installedMatch = uiState.installedModels.firstOrNull { it.id == catalogEntry.id }
                val isDownloadingThis = uiState.downloadState is DownloadState.Downloading &&
                        catalogEntry.archiveName.isNotBlank()

                VoiceCatalogCard(
                    entry = catalogEntry,
                    installed = installedMatch,
                    isDownloading = isDownloadingThis,
                    downloadState = uiState.downloadState,
                    selectedVoiceId = uiState.selectedVoiceId,
                    onDownload = { viewModel.downloadModel(catalogEntry.id) },
                    onSelectVoice = { voiceId -> viewModel.selectModel(catalogEntry.id, voiceId) },
                    onDelete = { installedMatch?.let { deleteCandidate = it } }
                )
            }

            // Display any imported models not in official catalog
            val customModels = uiState.installedModels.filter { inst ->
                uiState.catalog.none { it.id == inst.id }
            }
            if (customModels.isNotEmpty()) {
                item {
                    Text(
                        text = "IMPORTED VOICES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(customModels, key = { it.id }) { custom ->
                    CustomModelCard(
                        model = custom,
                        selectedVoiceId = uiState.selectedVoiceId,
                        onSelectVoice = { voiceId -> viewModel.selectModel(custom.id, voiceId) },
                        onDelete = { deleteCandidate = custom }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    deleteCandidate?.let { modelToDelete ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete Voice") },
            text = { Text("Are you sure you want to delete ${modelToDelete.name}? You can re-download it anytime.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteModel(modelToDelete.folderName)
                        deleteCandidate = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StorageAndImportHeader(
    storageBytes: Long,
    onImportClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val freeGb = "%.1f".format(storageBytes.toDouble() / (1024 * 1024 * 1024))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Device Storage",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$freeGb GB free",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedButton(
                    onClick = onImportClick,
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import from file")
                }
            }
        }
    }
}

@Composable
fun VoiceCatalogCard(
    entry: CatalogEntry,
    installed: InstalledModel?,
    isDownloading: Boolean,
    downloadState: DownloadState,
    selectedVoiceId: String,
    onDownload: () -> Unit,
    onSelectVoice: (String) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (installed != null) {
                MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (entry.recommended) {
                            Spacer(modifier = Modifier.width(8.dp))
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Recommended") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(entry.qualityBadge) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(entry.speedBadge) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val mbDownload = entry.archiveSizeBytes / (1024 * 1024)
            val mbInstalled = entry.installedSizeBytes / (1024 * 1024)
            Text(
                text = "$mbDownload MB download • ~$mbInstalled MB installed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (installed == null) {
                if (isDownloading && downloadState is DownloadState.Downloading) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { downloadState.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Downloading... ${downloadState.progressPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (isDownloading && downloadState is DownloadState.Extracting) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { downloadState.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Extracting files... ${downloadState.progressPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download")
                    }
                }
            } else {
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Speakers / Voices",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Voice selection chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (voice in entry.voices) {
                        val isChosen = voice.id == selectedVoiceId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${voice.name} (${voice.locale})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isChosen) {
                                FilterChip(
                                    selected = true,
                                    onClick = {},
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Selected") }
                                )
                            } else {
                                OutlinedButton(
                                    onClick = { onSelectVoice(voice.id) },
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Use")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomModelCard(
    model: InstalledModel,
    selectedVoiceId: String,
    onSelectVoice: (String) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                text = "Type: ${model.modelType} • ${model.sizeBytes / (1024 * 1024)} MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
            for (voice in model.voices) {
                val isChosen = voice.id == selectedVoiceId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = voice.name, style = MaterialTheme.typography.bodyMedium)
                    if (isChosen) {
                        Text("Selected", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    } else {
                        OutlinedButton(onClick = { onSelectVoice(voice.id) }) {
                            Text("Use")
                        }
                    }
                }
            }
        }
    }
}
