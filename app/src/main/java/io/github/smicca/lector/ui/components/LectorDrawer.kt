package io.github.smicca.lector.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.smicca.lector.core.model.VoiceOption

enum class DrawerSection {
    ROOT,
    ADVANCED
}

enum class DrawerRowKind {
    Submenu,
    Screen,
    LeavesApp,
    Picker,
    Command,
    Toggle
}

@Composable
fun LectorDrawerSheet(
    activeSection: DrawerSection,
    onSectionChange: (DrawerSection) -> Unit,
    onClose: () -> Unit,
    onNavigateToVoices: () -> Unit,
    selectedVoice: VoiceOption?,
    availableVoices: List<VoiceOption>,
    onSelectVoice: (VoiceOption) -> Unit,
    simulateFirstRun: Boolean,
    onToggleSimulateFirstRun: (Boolean) -> Unit,
    onResetSampleText: () -> Unit,
    versionLabel: String = "v0.1.0 • Offline Neural TTS"
) {
    val context = LocalContext.current
    var isVoicePickerOpen by remember { mutableStateOf(false) }
    var isLicenseDialogOpen by remember { mutableStateOf(false) }

    // Check if Lector is currently the system-wide default speech engine
    val isLectorDefaultTts = remember(context) {
        try {
            val defaultSynth = Settings.Secure.getString(context.contentResolver, "tts_default_synth")
            defaultSynth != null && (defaultSynth == context.packageName || defaultSynth.contains("io.github.smicca.lector"))
        } catch (e: Exception) {
            false
        }
    }

    ModalDrawerSheet(
        modifier = Modifier
            .widthIn(max = 340.dp)
            .fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            when (activeSection) {
                DrawerSection.ROOT -> {
                    // Pinned Identity Header
                    DrawerIdentityHeader(
                        versionLabel = versionLabel,
                        onClose = onClose
                    )

                    // Scrolling Body
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Purpose Group: LISTEN
                        DrawerGroupHeader(title = "LISTEN")

                        DrawerItemRow(
                            icon = Icons.Outlined.Headphones,
                            title = "Listen",
                            summary = "Offline neural text-to-speech reader",
                            kind = DrawerRowKind.Command,
                            onClick = onClose
                        )

                        DrawerDivider()

                        // Purpose Group: VOICES
                        DrawerGroupHeader(title = "VOICES")

                        DrawerItemRow(
                            icon = Icons.Outlined.RecordVoiceOver,
                            title = "Manage voices",
                            summary = "Installed voices and neural catalog",
                            kind = DrawerRowKind.Screen,
                            onClick = {
                                onClose()
                                onNavigateToVoices()
                            }
                        )

                        DrawerItemRow(
                            icon = Icons.Outlined.Tune,
                            title = "Default voice",
                            summary = selectedVoice?.let { "${it.name} (${it.locale})" } ?: "Select voice",
                            kind = DrawerRowKind.Picker,
                            onClick = { isVoicePickerOpen = true }
                        )

                        DrawerDivider()

                        // Purpose Group: SYSTEM
                        DrawerGroupHeader(title = "SYSTEM")

                        DrawerItemRow(
                            icon = Icons.Outlined.Settings,
                            title = "System speech engine",
                            summary = if (isLectorDefaultTts) {
                                "Lector is active as system default"
                            } else {
                                "Open Android Text-to-Speech settings"
                            },
                            kind = DrawerRowKind.LeavesApp,
                            onClick = {
                                onClose()
                                openTtsSettings(context)
                            }
                        )

                        DrawerDivider()

                        // Purpose Group: ABOUT
                        DrawerGroupHeader(title = "ABOUT")

                        DrawerItemRow(
                            icon = Icons.Outlined.Code,
                            title = "GitHub repository",
                            summary = "Source code, models, and licenses",
                            kind = DrawerRowKind.LeavesApp,
                            onClick = {
                                onClose()
                                openUrl(context, "https://github.com/smicca/lector")
                            }
                        )

                        DrawerItemRow(
                            icon = Icons.Outlined.Description,
                            title = "License",
                            summary = "GPL-3.0-or-later (open source)",
                            kind = DrawerRowKind.Command,
                            onClick = { isLicenseDialogOpen = true }
                        )

                        DrawerDivider()

                        // Advanced Tier (last, after divider, unlabeled single row per family standard)
                        DrawerItemRow(
                            icon = Icons.Outlined.Build,
                            title = "Advanced settings",
                            summary = "Preview and developer tools",
                            kind = DrawerRowKind.Submenu,
                            onClick = { onSectionChange(DrawerSection.ADVANCED) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                DrawerSection.ADVANCED -> {
                    // Pinned Nested Header
                    DrawerNestedHeader(
                        title = "Advanced settings",
                        onBack = { onSectionChange(DrawerSection.ROOT) },
                        onClose = onClose
                    )

                    // Scrolling Body for Advanced
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DrawerGroupHeader(title = "PREVIEW & DEVELOPER")

                        DrawerToggleRow(
                            icon = Icons.Outlined.Visibility,
                            title = "Simulate first run",
                            summary = "Preview first-run card without clearing data",
                            checked = simulateFirstRun,
                            onCheckedChange = onToggleSimulateFirstRun
                        )

                        DrawerItemRow(
                            icon = Icons.Outlined.Refresh,
                            title = "Reset sample passage",
                            summary = "Reload Scripture & theology test passage",
                            kind = DrawerRowKind.Command,
                            onClick = {
                                onResetSampleText()
                                onClose()
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Voice Picker Dialog
    if (isVoicePickerOpen) {
        AlertDialog(
            onDismissRequest = { isVoicePickerOpen = false },
            title = {
                Text(
                    text = "Select Default Voice",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (availableVoices.isEmpty()) {
                        Text(
                            text = "No voices installed yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        availableVoices.forEach { voice ->
                            val isSelected = voice.id == selectedVoice?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        onSelectVoice(voice)
                                        isVoicePickerOpen = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = voice.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = voice.locale,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isVoicePickerOpen = false }) {
                    Text("Close")
                }
            }
        )
    }

    // License Dialog
    if (isLicenseDialogOpen) {
        AlertDialog(
            onDismissRequest = { isLicenseDialogOpen = false },
            title = {
                Text(
                    text = "Open Source License",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Lector is licensed under GNU General Public License v3.0 or later (GPL-3.0-or-later).\n\n" +
                                "Bundles sherpa-onnx (Apache-2.0) and espeak-ng (GPL-3.0-or-later) offline inference libraries.\n\n" +
                                "All neural synthesis models run 100% locally on your device with no telemetry, cloud dependencies, or tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { isLicenseDialogOpen = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun DrawerIdentityHeader(
    versionLabel: String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Lector",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = versionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close menu",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DrawerNestedHeader(
    title: String,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to menu",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close menu",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DrawerGroupHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .semantics { heading() }
            .padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun DrawerDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun DrawerItemRow(
    icon: ImageVector,
    title: String,
    summary: String,
    kind: DrawerRowKind,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.38f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DrawerTrailingCue(kind = kind, tint = MaterialTheme.colorScheme.outline.copy(alpha = alpha))
    }
}

@Composable
private fun DrawerToggleRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.38f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = null, // handled by toggleable row
            enabled = enabled
        )
    }
}

@Composable
private fun DrawerTrailingCue(kind: DrawerRowKind, tint: Color) {
    val icon = when (kind) {
        DrawerRowKind.Submenu -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        DrawerRowKind.Screen -> Icons.AutoMirrored.Filled.ArrowForward
        DrawerRowKind.LeavesApp -> Icons.AutoMirrored.Outlined.OpenInNew
        DrawerRowKind.Picker -> Icons.Filled.ArrowDropDown
        DrawerRowKind.Command -> return
        DrawerRowKind.Toggle -> return
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(20.dp)
    )
}

private fun openTtsSettings(context: Context) {
    try {
        val intent = Intent("com.android.settings.TTS_SETTINGS")
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallback = Intent(Settings.ACTION_SETTINGS)
            context.startActivity(fallback)
        } catch (_: Exception) {}
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {}
}
