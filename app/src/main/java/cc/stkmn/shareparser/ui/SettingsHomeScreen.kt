package cc.stkmn.shareparser.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cc.stkmn.shareparser.R
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.ShareSelectionMode
import cc.stkmn.shareparser.notify.ShareSelectionNotifier

@Composable
internal fun SettingsHomeScreen(
    repository: ProfileRepository,
    onRegionalSettings: () -> Unit,
    onLanguageSettings: () -> Unit,
    onAppearanceSettings: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var settings by remember { mutableStateOf(repository.settings()) }
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName.orEmpty().ifBlank { "?" }
    val versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo?.longVersionCode ?: 0L
        else packageInfo?.versionCode?.toLong() ?: 0L
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var overlayPermissionRequested by remember { mutableStateOf(false) }

    fun saveMode(mode: ShareSelectionMode) {
        settings = settings.copy(shareSelectionMode = mode)
        repository.saveSettings(settings)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayGranted = Settings.canDrawOverlays(context)
        if (overlayPermissionRequested && overlayGranted) {
            saveMode(ShareSelectionMode.OVERLAY)
            overlayPermissionRequested = false
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val saveFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            settings = settings.copy(defaultSaveTreeUri = uri.toString())
            repository.saveSettings(settings)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Darstellung, Erkennung, Dateiausgabe und Auswahlverhalten.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            SettingsTopicCard("Darstellung") {
                SettingsLinkRow(
                    icon = { Icon(Icons.Outlined.Palette, null) },
                    title = "Design",
                    description = "System, Hell, Dunkel und Farbpalette",
                    onClick = onAppearanceSettings
                )
                SettingsLinkRow(
                    icon = { Icon(Icons.Outlined.Language, null) },
                    title = "App-Sprache",
                    description = "Systemstandard, Deutsch oder English",
                    onClick = onLanguageSettings
                )
            }
        }

        item {
            SettingsTopicCard("Format und Erkennung") {
                SettingsLinkRow(
                    icon = { Icon(Icons.Outlined.Language, null) },
                    title = "Datum und Uhrzeit",
                    description = "Regionale Schreibweisen und 12/24-Stunden-Format",
                    onClick = onRegionalSettings
                )
            }
        }

        item {
            SettingsTopicCard(
                title = "Textdateien",
                description = if (settings.defaultSaveTreeUri.isBlank())
                    "Ohne Voreinstellung öffnet Android beim Speichern den Dateidialog."
                else "Dateien werden bevorzugt im gewählten Basisordner gespeichert."
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, null)
                    Text(
                        if (settings.defaultSaveTreeUri.isBlank()) "Kein Basisordner" else folderLabel(settings.defaultSaveTreeUri),
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (settings.defaultSaveTreeUri.isNotBlank()) {
                    TechnicalValue(
                        value = settings.defaultSaveTreeUri,
                        onCopy = { clipboard.setText(AnnotatedString(settings.defaultSaveTreeUri)) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            saveFolderLauncher.launch(
                                settings.defaultSaveTreeUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
                            )
                        }
                    ) { Text(if (settings.defaultSaveTreeUri.isBlank()) "Ordner wählen" else "Ändern") }
                    if (settings.defaultSaveTreeUri.isNotBlank()) {
                        OutlinedButton(onClick = {
                            settings = settings.copy(defaultSaveTreeUri = "")
                            repository.saveSettings(settings)
                        }) { Text("Entfernen") }
                    }
                }
            }
        }

        item {
            SettingsTopicCard(
                title = "Auswahl beim Teilen",
                description = "Nur relevant, wenn mehrere Aktionen verfügbar sind."
            ) {
                ShareModeRow(
                    selected = settings.shareSelectionMode == ShareSelectionMode.APP,
                    icon = null,
                    title = "In ShareParser",
                    description = "Volle Liste in der App.",
                    onClick = { saveMode(ShareSelectionMode.APP) }
                )
                ShareModeRow(
                    selected = settings.shareSelectionMode == ShareSelectionMode.OVERLAY,
                    icon = { Icon(Icons.Outlined.PictureInPictureAlt, null) },
                    title = "Overlay",
                    description = if (overlayGranted) "Berechtigung erteilt. Maximal vier Direktaktionen."
                    else "Benötigt die Android-Overlay-Berechtigung.",
                    onClick = {
                        overlayGranted = Settings.canDrawOverlays(context)
                        if (overlayGranted) {
                            saveMode(ShareSelectionMode.OVERLAY)
                        } else {
                            overlayPermissionRequested = true
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        }
                    }
                )
                ShareModeRow(
                    selected = settings.shareSelectionMode == ShareSelectionMode.NOTIFICATION,
                    icon = { Icon(Icons.Outlined.Notifications, null) },
                    title = "Benachrichtigung",
                    description = "Maximal drei Direktaktionen. Weitere öffnen die App.",
                    onClick = {
                        saveMode(ShareSelectionMode.NOTIFICATION)
                        ShareSelectionNotifier.ensureChannel(context)
                        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
                if (!overlayGranted) {
                    OutlinedButton(onClick = {
                        overlayGranted = Settings.canDrawOverlays(context)
                        if (overlayGranted) saveMode(ShareSelectionMode.OVERLAY)
                    }) { Text("Overlay-Berechtigung prüfen") }
                }
                OutlinedButton(onClick = {
                    ShareSelectionNotifier.ensureChannel(context)
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                putExtra(Settings.EXTRA_CHANNEL_ID, ShareSelectionNotifier.CHANNEL)
                            }
                        )
                    }.recoverCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        )
                    }
                }) { Text("Benachrichtigungskanal") }
            }
        }

        item {
            SettingsTopicCard(
                title = "Über ShareParser",
                description = "Vibecoded für den eigenen Bedarf und mit der Community geteilt."
            ) {
                Text(
                    "Version ${versionName} · Build ${versionCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/3115a083/ShareParser-App")
                            )
                        )
                    }
                ) {
                    Icon(ImageVector.vectorResource(R.drawable.ic_github_mark), null)
                    Text("GitHub", modifier = Modifier.padding(start = 8.dp))
                }
                TechnicalValue(
                    value = "https://github.com/3115a083/ShareParser-App",
                    onCopy = { clipboard.setText(AnnotatedString("https://github.com/3115a083/ShareParser-App")) },
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SettingsLinkRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Outlined.ChevronRight, null)
    }
}

@Composable
private fun ShareModeRow(
    selected: Boolean,
    icon: (@Composable () -> Unit)?,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = onClick)
        if (icon != null) {
            Column(Modifier.padding(top = 12.dp, end = 8.dp)) { icon() }
        }
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun folderLabel(uri: String): String = runCatching {
    Uri.parse(uri).lastPathSegment?.substringAfterLast(':')?.ifBlank { "Ausgewählter Ordner" }
}.getOrNull().orEmpty().ifBlank { "Ausgewählter Ordner" }
