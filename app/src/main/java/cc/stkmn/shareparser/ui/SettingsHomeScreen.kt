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
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.ShareSelectionMode
import cc.stkmn.shareparser.BuildConfig
import cc.stkmn.shareparser.notify.ShareSelectionNotifier

@Composable
internal fun SettingsHomeScreen(
    repository: ProfileRepository,
    onRegionalSettings: () -> Unit,
    onLanguageSettings: () -> Unit
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(repository.settings()) }
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
            Text("Sprache und Format", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLanguageSettings)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Language, null)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("App-Sprache", fontWeight = FontWeight.SemiBold)
                        Text("Systemstandard, Deutsch oder English", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        }

        item {
            Text("Format und Erkennung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRegionalSettings)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Language, null)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Datum und Uhrzeit", fontWeight = FontWeight.SemiBold)
                        Text("Regionale Schreibweisen und 12/24-Stunden-Format", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        }

        item {
            Text("Textdateien", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Folder, null)
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Voreingestellter Speicherordner", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (settings.defaultSaveTreeUri.isBlank())
                                "Nicht gesetzt. Bei einer Datei-Aktion mit „Speichern“ zeigt Android den Dateidialog an."
                            else "Aktiv: ${folderLabel(settings.defaultSaveTreeUri)}. Profile können darunter einen variablen Unterordner angeben.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = { saveFolderLauncher.launch(settings.defaultSaveTreeUri.takeIf { it.isNotBlank() }?.let(Uri::parse)) }) {
                            Text(if (settings.defaultSaveTreeUri.isBlank()) "Ordner auswählen" else "Ordner ändern")
                        }
                        if (settings.defaultSaveTreeUri.isNotBlank()) {
                            OutlinedButton(onClick = {
                                settings = settings.copy(defaultSaveTreeUri = "")
                                repository.saveSettings(settings)
                            }) { Text("Voreinstellung entfernen") }
                        }
                    }
                }
            }
        }

        item {
            Text("Auswahl beim Teilen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Text("Wenn genau ein Profil mit genau einer Aktion passt, führt ShareParser sie direkt aus. Diese Einstellung gilt nur, wenn mehrere Möglichkeiten zur Auswahl stehen.")
        }
        item {
            ChoiceCard(
                selected = settings.shareSelectionMode == ShareSelectionMode.APP,
                title = "In ShareParser auswählen",
                description = "Öffnet ShareParser als eigene App im App-Wechsler.",
                onClick = { saveMode(ShareSelectionMode.APP) }
            )
        }
        item {
            ChoiceCard(
                selected = settings.shareSelectionMode == ShareSelectionMode.OVERLAY,
                title = "Overlay über der teilenden App",
                description = if (overlayGranted) "Overlay-Berechtigung erteilt. Die Auswahl erscheint mittig und schließt sich spätestens nach einer Minute."
                else "Benötigt die optionale Android-Berechtigung „Über anderen Apps anzeigen“.",
                icon = { Icon(Icons.Outlined.PictureInPictureAlt, null) },
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
        }
        item {
            if (!overlayGranted) {
                OutlinedButton(
                    onClick = {
                        overlayGranted = Settings.canDrawOverlays(context)
                        if (overlayGranted) saveMode(ShareSelectionMode.OVERLAY)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Overlay-Berechtigung erneut prüfen") }
            }
        }
        item {
            ChoiceCard(
                selected = settings.shareSelectionMode == ShareSelectionMode.NOTIFICATION,
                title = "Als Benachrichtigung auswählen",
                description = "Eigener Android-Kanal. Ton, Vibration oder stumm stellst du in den Systemeinstellungen ein. Die Nachricht verschwindet spätestens nach einer Minute.",
                icon = { Icon(Icons.Outlined.Notifications, null) },
                onClick = {
                    saveMode(ShareSelectionMode.NOTIFICATION)
                    ShareSelectionNotifier.ensureChannel(context)
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }
        item {
            OutlinedButton(
                onClick = {
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
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Benachrichtigungskanal konfigurieren") }
        }

        item { HorizontalDivider() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Über ShareParser", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
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
                    Icon(Icons.Outlined.OpenInNew, null)
                    Text("GitHub")
                }
                Text(
                    "Dieses Projekt ist vibecoded. Es wurde vor allem für den eigenen Bedarf erstellt und mit der Community geteilt, falls es auch anderen hilfreich ist.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun folderLabel(uri: String): String = runCatching {
    Uri.parse(uri).lastPathSegment?.substringAfterLast(':')?.ifBlank { "Ausgewählter Ordner" }
}.getOrNull().orEmpty().ifBlank { "Ausgewählter Ordner" }

@Composable
private fun ChoiceCard(
    selected: Boolean,
    title: String,
    description: String,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = onClick)
            if (icon != null) {
                Column(Modifier.padding(top = 10.dp, end = 8.dp)) { icon() }
            }
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
