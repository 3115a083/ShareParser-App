package cc.stkmn.shareparser.ui

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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import cc.stkmn.shareparser.data.FailureReport
import cc.stkmn.shareparser.data.ProcessingAction
import cc.stkmn.shareparser.data.Profile
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.SharedPayload
import cc.stkmn.shareparser.engine.ActionExecutor
import cc.stkmn.shareparser.engine.ParserEngine
import cc.stkmn.shareparser.engine.ProcessingException
import cc.stkmn.shareparser.notify.FailureNotifier
import java.util.UUID

@Composable
internal fun HomeScreen(
    profiles: List<Profile>,
    onEdit: (Profile) -> Unit,
    onCreate: () -> Unit,
    onImport: () -> Unit
) {
    if (profiles.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.Share, null, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(20.dp))
            Text("Erstelle dein erstes Profil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Ein Profil erkennt ähnliche Texte, extrahiert Werte und bietet passende Aktionen an.")
            Spacer(Modifier.height(24.dp))
            Button(onClick = onCreate) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Profil erstellen")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onImport) {
                Icon(Icons.Outlined.FileOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("Profil importieren")
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onImport) {
                    Icon(Icons.Outlined.FileOpen, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
            }
        }
        items(profiles, key = { it.id }) { profile ->
            Card(onClick = { onEdit(profile) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(if (profile.enabled) "Aktiv" else "Aus", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${profile.extractors.size} Felder · ${profile.actions.size} Aktionen", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
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

    val extraction = remember(payload, selected) {
        selected?.let { profile -> runCatching { parser.extract(payload, profile) } }
    }

    fun runAction(profile: Profile, action: ProcessingAction) {
        try {
            val extracted = parser.extract(payload, profile)
            ActionExecutor(context).execute(action, extracted)
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

    LaunchedEffect(selected?.id) {
        showActionPicker = selected?.actions?.size?.let { it > 1 } == true
    }

    if (showActionPicker && selected != null) {
        ModalBottomSheet(onDismissRequest = { showActionPicker = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wie weiterverarbeiten?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(selected!!.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                selected!!.actions.forEach { action ->
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
                    SelectionContainer { Text(payload.text, maxLines = 16) }
                    Text("Typ: ${payload.mimeType}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (matches.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Kein Profil passt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Erstelle direkt aus diesem Beispiel ein neues Profil. Der geteilte Text bleibt dabei nur lokal auf dem Gerät.")
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
                        supportingContent = { Text("${profile.extractors.size} Felder · ${profile.actions.size} Aktionen") }
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
                    if (matches.size > 1) {
                        TextButton(onClick = { selected = null }) { Text("Wechseln") }
                    }
                }
            }

            val result = extraction
            if (result != null && result.isSuccess) {
                val values = result.getOrThrow()
                val custom = values.filterKeys { it !in setOf("input", "text", "subject") }
                if (custom.isEmpty()) {
                    item { Text("Dieses Profil verwendet nur die eingebauten Werte {{subject}}, {{text}} und {{input}}.") }
                } else {
                    item { Text("Extrahierte Werte", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
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
                            Text(error?.userMessage ?: result.exceptionOrNull()?.message.orEmpty())
                        }
                    }
                }
            }

            item { HorizontalDivider() }
            item { Text("Weiterverarbeitung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            when (selected!!.actions.size) {
                0 -> item { Text("Dieses Profil hat noch keine Aktion.") }
                1 -> {
                    val action = selected!!.actions.first()
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
