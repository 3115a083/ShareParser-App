package cc.stkmn.shareparser.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VariableAdd
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cc.stkmn.shareparser.data.AppSettings
import cc.stkmn.shareparser.data.DateTimeLocale
import cc.stkmn.shareparser.data.FailureReport
import cc.stkmn.shareparser.data.ProcessingAction
import cc.stkmn.shareparser.data.Profile
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.SharedPayload
import cc.stkmn.shareparser.data.WebhookMode
import cc.stkmn.shareparser.engine.ActionExecutor
import cc.stkmn.shareparser.engine.ParserEngine
import cc.stkmn.shareparser.engine.ProcessingException
import cc.stkmn.shareparser.notify.FailureNotifier
import cc.stkmn.shareparser.notify.WarningNotifier
import cc.stkmn.shareparser.share.ShareCoordinator
import java.util.UUID

@Composable
internal fun HomeScreen(
    profiles: List<Profile>,
    importError: String?,
    onEdit: (Profile) -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit,
    onToggle: (Profile, Boolean) -> Unit,
    onDelete: (Profile) -> Unit,
    onSettings: () -> Unit
) {
    var deleteCandidate by remember { mutableStateOf<Profile?>(null) }
    deleteCandidate?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Profil löschen?") },
            text = { Text("„${profile.name}“ wird dauerhaft entfernt. Diese Aktion kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(profile)
                    deleteCandidate = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Abbrechen") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeading(
                "Profile",
                "Erkennung und Weiterverarbeitung geteilter Inhalte."
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport) {
                    Icon(Icons.Outlined.FileOpen, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Importieren", maxLines = 1)
                }
                OutlinedButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Einstellungen", maxLines = 1)
                }
            }
        }
        if (importError != null) {
            item {
                ErrorNotice(
                    title = "Import fehlgeschlagen",
                    description = "Die Profildatei konnte nicht gelesen werden. Prüfe, ob sie aus ShareParser exportiert wurde.",
                    action = { TextButton(onClick = onImport) { Text("Erneut versuchen") } }
                )
            }
        }

        if (profiles.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Noch kein Profil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Erstelle ein Profil oder teile zuerst einen Beispieltext mit ShareParser.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onCreate) {
                            Icon(Icons.Outlined.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Profil erstellen")
                        }
                    }
                }
            }
        } else {
            items(profiles, key = { it.id }) { profile ->
                Card(Modifier.fillMaxWidth().clickable { onEdit(profile) }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (profile.enabled) "Aktiv" else "Deaktiviert",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = profile.enabled,
                                onCheckedChange = { onToggle(profile, it) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { onEdit(profile) },
                                label = { Text("${profile.extractors.size}") },
                                leadingIcon = { Icon(Icons.Outlined.VariableAdd, null) }
                            )
                            AssistChip(
                                onClick = { onEdit(profile) },
                                label = { Text("${profile.actions.size}") },
                                leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onEdit(profile) }) {
                                Icon(Icons.Outlined.Edit, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Bearbeiten")
                            }
                            TextButton(onClick = { deleteCandidate = profile }) {
                                Icon(Icons.Outlined.Delete, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Löschen")
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
internal fun SettingsScreen(repository: ProfileRepository) {
    var settings by remember { mutableStateOf(repository.settings()) }

    fun select(locale: DateTimeLocale) {
        settings = AppSettings(dateTimeLocale = locale)
        repository.saveSettings(settings)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Datum und Uhrzeit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        item {
            Text("Diese Einstellung steuert, wie freie Datums- und Zeitangaben für Kalenderaktionen interpretiert werden.")
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = settings.dateTimeLocale == DateTimeLocale.DE_DE,
                            onClick = { select(DateTimeLocale.DE_DE) }
                        )
                        Column {
                            Text("Deutsch (Deutschland)", fontWeight = FontWeight.SemiBold)
                            Text("Empfohlen für deutsche E-Mails und Nachrichten.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "Beispiele: 14.12.2026, 14/12/26, 14.12., morgen, nächsten Montag, 12-14, 12 Uhr bis 14 Uhr, 12:00 Uhr bis 14:00 Uhr.",
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.dateTimeLocale == DateTimeLocale.SYSTEM,
                        onClick = { select(DateTimeLocale.SYSTEM) }
                    )
                    Column {
                        Text("Geräteeinstellung", fontWeight = FontWeight.SemiBold)
                        Text("Verwendet die Gerätesprache für Textvergleiche. Die flexiblen deutschen Zahlenformate bleiben weiterhin verfügbar.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedScreen(
    payload: SharedPayload,
    profiles: List<Profile>,
    repository: ProfileRepository,
    onEditProfile: (Profile) -> Unit,
    onCreateFromSample: () -> Unit
) {
    val context = LocalContext.current
    val parser = remember { ParserEngine() }
    val matches = remember(payload, profiles) { parser.matchingProfiles(payload, profiles) }
    var selected by remember(payload, profiles) { mutableStateOf<Profile?>(matches.singleOrNull()) }
    var showActionPicker by remember { mutableStateOf(false) }
    var pendingCalendarExecution by remember { mutableStateOf<Pair<Profile, ProcessingAction.Calendar>?>(null) }

    val extraction = remember(payload, selected) {
        selected?.let { profile -> runCatching { parser.extract(payload, profile) } }
    }

    fun executeNow(profile: Profile, action: ProcessingAction) {
        if (action is ProcessingAction.Webhook) {
            ShareCoordinator(context).execute(payload, profile, action)
            return
        }
        try {
            val extracted = parser.extract(payload, profile)
            val result = ActionExecutor(context, repository.settings()).execute(action, extracted)
            WarningNotifier.show(context, result.warnings)
        } catch (e: Exception) {
            val processing = e as? ProcessingException
            val report = FailureReport(
                id = UUID.randomUUID().toString(),
                profileId = profile.id,
                profileName = profile.name,
                actionId = action.id,
                message = processing?.userMessage ?: "Verarbeitung fehlgeschlagen.",
                technicalDetails = processing?.technicalDetails ?: (e.message ?: e.toString()),
                failingField = processing?.failingField,
                inputPreview = payload.combined.take(2000),
                createdAtEpochMs = System.currentTimeMillis()
            )
            repository.saveFailure(report)
            FailureNotifier.show(context, report)
        }
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val pending = pendingCalendarExecution
        pendingCalendarExecution = null
        if (pending != null) executeNow(pending.first, pending.second)
    }

    fun runAction(profile: Profile, action: ProcessingAction) {
        if (action is ProcessingAction.Calendar &&
            action.calendarNameTemplate.isNotBlank() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCalendarExecution = profile to action
            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        } else {
            executeNow(profile, action)
        }
    }

    val selectableActions = selected?.actions
        ?.filterNot { it is ProcessingAction.Webhook && it.mode == WebhookMode.ALWAYS }
        .orEmpty()

    LaunchedEffect(selected?.id, selectableActions.size) {
        showActionPicker = selectableActions.size > 1
    }

    if (showActionPicker && selected != null) {
        ModalBottomSheet(onDismissRequest = { showActionPicker = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wie weiterverarbeiten?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(selected!!.name)
                selectableActions.forEach { action ->
                    ElevatedButton(
                        onClick = {
                            showActionPicker = false
                            runAction(selected!!, action)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(actionIcon(action.icon), null)
                        Spacer(Modifier.width(8.dp))
                        Text(action.friendlyName)
                    }
                }
            }
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Geteilte Informationen", fontWeight = FontWeight.SemiBold)
                    if (payload.subject.isNotBlank()) {
                        Text("Betreff", style = MaterialTheme.typography.labelMedium)
                        SelectionContainer { Text(payload.subject) }
                    }
                    Text("Text", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer { Text(payload.text, maxLines = 18) }
                    Text("Typ: ${payload.mimeType}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (matches.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Kein Profil passt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Erstelle aus diesem Beispiel ein Profil. Du kannst anschließend einzelne Zeilen als Variablen markieren, ShareParser erzeugt die Regeln automatisch.")
                        Button(onClick = onCreateFromSample, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Profil aus diesem Text erstellen")
                        }
                    }
                }
            }
        } else if (selected == null) {
            item { Text("Passendes Profil auswählen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(matches, key = { it.id }) { profile ->
                Card(onClick = { selected = profile }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.Tune, null) },
                        headlineContent = { Text(profile.name) },
                        supportingContent = { Text("${profile.extractors.size} Variablen · ${profile.actions.size} Aktionen") }
                    )
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Profil", style = MaterialTheme.typography.labelMedium)
                        Text(selected!!.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (matches.size > 1) TextButton(onClick = { selected = null }) { Text("Wechseln") }
                }
            }

            val result = extraction
            if (result != null && result.isSuccess) {
                val values = result.getOrThrow()
                val custom = values.filterKeys { it !in setOf("input", "text", "subject") }
                if (custom.isNotEmpty()) {
                    item { Text("Erkannte Variablen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    custom.forEach { (key, value) ->
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                ListItem(
                                    headlineContent = { Text(key) },
                                    supportingContent = { SelectionContainer { Text(value) } }
                                )
                            }
                        }
                    }
                }
            } else if (result != null) {
                val error = result.exceptionOrNull() as? ProcessingException
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Extraktion nicht vollständig", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                            Text(
                                error?.userMessage ?: "Die Variablen konnten mit diesem Profil nicht vollständig erkannt werden. Öffne das Profil und prüfe die markierten Regeln.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider() }
            item { Text("Weiterverarbeitung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            when (selectableActions.size) {
                0 -> item { Text("Dieses Profil hat noch keine auswählbare Aktion.") }
                1 -> {
                    val action = selectableActions.first()
                    item {
                        ElevatedButton(onClick = { runAction(selected!!, action) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(actionIcon(action.icon), null)
                            Spacer(Modifier.width(8.dp))
                            Text(action.friendlyName)
                        }
                    }
                }
                else -> item {
                    Button(onClick = { showActionPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Tune, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Aktion auswählen")
                    }
                }
            }
            item {
                TextButton(onClick = { onEditProfile(selected!!) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Edit, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Profil bearbeiten")
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
internal fun FailureScreen(
    report: FailureReport?,
    profile: Profile?,
    onEdit: (Profile, String?) -> Unit
) {
    if (report == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Kein Fehlerbericht vorhanden.") }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(report.message, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        report.profileName?.let { item { Text("Profil: $it") } }
        report.failingField?.let { field ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Betroffener Bereich", fontWeight = FontWeight.SemiBold)
                        Text(field)
                    }
                }
            }
        }
        item { Text("Technische Details", fontWeight = FontWeight.SemiBold) }
        item { SelectionContainer { Text(report.technicalDetails) } }
        item { Text("Eingabe", fontWeight = FontWeight.SemiBold) }
        item { SelectionContainer { Text(report.inputPreview) } }
        if (profile != null) {
            item {
                Button(onClick = { onEdit(profile, report.failingField) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Profil an Fehlerstelle bearbeiten")
                }
            }
        }
    }
}
