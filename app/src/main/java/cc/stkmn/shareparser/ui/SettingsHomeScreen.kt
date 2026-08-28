package cc.stkmn.shareparser.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.stkmn.shareparser.LauncherIconManager
import cc.stkmn.shareparser.R
import cc.stkmn.shareparser.data.LauncherIcon
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.ShareSelectionMode
import cc.stkmn.shareparser.notify.ShareSelectionNotifier

@Composable
internal fun SettingsHomeScreen(
    repository: ProfileRepository,
    onRegionalSettings: () -> Unit
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(repository.settings()) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    fun saveMode(mode: ShareSelectionMode) {
        settings = settings.copy(shareSelectionMode = mode)
        repository.saveSettings(settings)
    }

    fun saveIcon(icon: LauncherIcon) {
        settings = settings.copy(launcherIcon = icon)
        repository.saveSettings(settings)
        LauncherIconManager.apply(context, icon)
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Darstellung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        item {
            Text("App-Symbol", fontWeight = FontWeight.SemiBold)
        }
        item {
            Text("Logo 5 ist der Standard. Beim Wechsel kann der Launcher einige Sekunden brauchen, bis das neue Symbol sichtbar ist.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(LauncherIcon.entries) { icon ->
                    val selected = settings.launcherIcon == icon
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { saveIcon(icon) }
                            .padding(8.dp)
                    ) {
                        Image(
                            painter = painterResource(launcherIconResource(icon)),
                            contentDescription = "App-Symbol ${icon.ordinal + 1}",
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Fit
                        )
                        Text("${icon.ordinal + 1}", style = MaterialTheme.typography.labelMedium)
                    }
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
                description = if (overlayGranted) "Overlay-Berechtigung erteilt. Die Auswahl schließt sich spätestens nach einer Minute."
                else "Benötigt die optionale Android-Berechtigung „Über anderen Apps anzeigen“.",
                icon = { Icon(Icons.Outlined.PictureInPictureAlt, null) },
                onClick = {
                    overlayGranted = Settings.canDrawOverlays(context)
                    if (overlayGranted) {
                        saveMode(ShareSelectionMode.OVERLAY)
                    } else {
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
    }
}

private fun launcherIconResource(icon: LauncherIcon): Int = when (icon) {
    LauncherIcon.LOGO_1 -> R.mipmap.app_logo_1
    LauncherIcon.LOGO_2 -> R.mipmap.app_logo_2
    LauncherIcon.LOGO_3 -> R.mipmap.app_logo_3
    LauncherIcon.LOGO_4 -> R.mipmap.app_logo_4
    LauncherIcon.LOGO_5 -> R.mipmap.app_logo_5
    LauncherIcon.LOGO_6 -> R.mipmap.app_logo_6
}

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
