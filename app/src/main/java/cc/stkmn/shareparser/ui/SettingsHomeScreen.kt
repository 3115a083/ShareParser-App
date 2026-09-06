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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cc.stkmn.shareparser.R
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.ShareSelectionMode
import cc.stkmn.shareparser.notify.ShareSelectionNotifier
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsHomeScreen(
    repository: ProfileRepository,
    onRegionalSettings: () -> Unit,
    onLanguageSettings: () -> Unit,
    onAppearanceSettings: () -> Unit,
    onAdditionalShareSettings: () -> Unit,
    onSettingsChanged: (cc.stkmn.shareparser.data.AppSettings) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(repository.settings()) }
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val versionName = packageInfo?.versionName.orEmpty().ifBlank { "?" }
    val versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo?.longVersionCode ?: 0L
        else packageInfo?.versionCode?.toLong() ?: 0L
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var overlayPermissionRequested by remember { mutableStateOf(false) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    fun saveMode(mode: ShareSelectionMode) {
        settings = settings.copy(shareSelectionMode = mode)
        repository.saveSettings(settings)
        onSettingsChanged(settings)
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
            onSettingsChanged(settings)
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
                            onSettingsChanged(settings)
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
                    icon = { Icon(Icons.Outlined.Apps, null) },
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
            SettingsTopicCard("Teilen") {
                SettingsLinkRow(
                    icon = { Icon(Icons.Outlined.Share, null) },
                    title = "Zusätzliche Teiloptionen",
                    description = "Karten, Links, Telefon, E-Mail, Dateien und eigene Web-Ziele",
                    onClick = onAdditionalShareSettings
                )
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Version ${versionName} · Build ${versionCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        enabled = !updateChecking,
                        onClick = {
                            updateChecking = true
                            updateStatus = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val connection = URL("https://api.github.com/repos/3115a083/ShareParser-App/releases/latest")
                                            .openConnection() as HttpURLConnection
                                        connection.connectTimeout = 6_000
                                        connection.readTimeout = 6_000
                                        connection.setRequestProperty("Accept", "application/vnd.github+json")
                                        connection.setRequestProperty("User-Agent", "ShareParser/$versionName")
                                        connection.inputStream.bufferedReader().use { it.readText() }
                                    }.mapCatching { body ->
                                        Regex("\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                                            .find(body)?.groupValues?.get(1)
                                            ?: error("Keine Release-Version gefunden")
                                    }
                                }
                                updateChecking = false
                                updateStatus = result.fold(
                                    onSuccess = { latest ->
                                        if (isNewerVersion(latest, versionName)) "Update verfügbar: $latest"
                                        else "ShareParser ist aktuell."
                                    },
                                    onFailure = { "Update-Prüfung fehlgeschlagen." }
                                )
                            }
                        }
                    ) {
                        Text(if (updateChecking) "Prüfe…" else "Updates prüfen")
                    }
                }
                updateStatus?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
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
                Text(
                    "Vibecoded with ❤️",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun AdditionalShareSettingsScreen(
    repository: ProfileRepository,
    onSettingsChanged: (cc.stkmn.shareparser.data.AppSettings) -> Unit = {}
) {
    var settings by remember { mutableStateOf(repository.settings()) }

    fun save(changed: cc.stkmn.shareparser.data.AppSettings) {
        settings = changed
        repository.saveSettings(changed)
        onSettingsChanged(changed)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Diese Optionen sind standardmäßig aus. Sie erscheinen nur, wenn der geteilte Inhalt zum jeweiligen Ziel passt.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            SettingsTopicCard(
                title = "Erkannte Ziele",
                description = "ShareParser stellt passende Inhalte zusätzlich als eingebaute Variablen bereit: shared_address, shared_web, shared_phone und shared_email."
            ) {
                ExtraShareToggle(settings.extraShareMap, "Adresse in Karten-App öffnen") {
                    save(settings.copy(extraShareMap = it))
                }
                ExtraShareToggle(settings.extraShareWebLink, "Erkannten Web-Link öffnen") {
                    save(settings.copy(extraShareWebLink = it))
                }
                ExtraShareToggle(settings.extraSharePhone, "Erkannte Telefonnummer öffnen") {
                    save(settings.copy(extraSharePhone = it))
                }
                ExtraShareToggle(settings.extraShareEmail, "Erkannte E-Mail-Adresse öffnen") {
                    save(settings.copy(extraShareEmail = it))
                }
                ExtraShareToggle(settings.extraShareFileOpen, "Geteilten Text-Dateityp direkt öffnen") {
                    save(settings.copy(extraShareFileOpen = it))
                }
            }
        }
        item {
            SettingsTopicCard(
                title = "Eigenes Web-Ziel",
                description = "Fügt einen festen Link als zusätzliche Teiloption hinzu, zum Beispiel eine Such- oder Web-App."
            ) {
                ExtraShareToggle(settings.extraShareCustomWeb, "Eigenes Web-Ziel anzeigen") {
                    save(settings.copy(extraShareCustomWeb = it))
                }
                if (settings.extraShareCustomWeb) {
                    OutlinedTextField(
                        value = settings.extraShareCustomWebName,
                        onValueChange = { save(settings.copy(extraShareCustomWebName = it.take(60))) },
                        label = { Text("Anzeigename") },
                        placeholder = { Text("z. B. In interner Suche öffnen") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = settings.extraShareCustomWebUrl,
                        onValueChange = { save(settings.copy(extraShareCustomWebUrl = it.take(1000))) },
                        label = { Text("Web-Adresse") },
                        placeholder = { Text("https://example.com/search?q={{input|url}}") },
                        supportingText = { Text("Variablen wie {{input|url}} oder {{shared_web|url}} sind erlaubt.") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }
        }
        item {
            SettingsTopicCard(
                title = "Beim Öffnen aus anderen Apps",
                description = "ShareParser kann als Ziel für Web-Links, Karten-Adressen, Telefonnummern und E-Mail-Adressen erscheinen. Der empfangene Wert steht als target und der Typ als target_type zur Verfügung."
            ) {}
        }
    }
}

@Composable
private fun ExtraShareToggle(
    checked: Boolean,
    title: String,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
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


private fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
    fun parts(value: String): List<Int> = value
        .trim()
        .removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }

    val latest = parts(latestTag)
    val current = parts(currentVersion)
    val count = maxOf(latest.size, current.size)
    for (index in 0 until count) {
        val left = latest.getOrElse(index) { 0 }
        val right = current.getOrElse(index) { 0 }
        if (left != right) return left > right
    }
    return false
}
