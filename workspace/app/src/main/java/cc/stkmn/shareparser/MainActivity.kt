package cc.stkmn.shareparser

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.stkmn.shareparser.data.*
import cc.stkmn.shareparser.engine.ActionExecutor
import cc.stkmn.shareparser.engine.ParserEngine
import cc.stkmn.shareparser.engine.ProcessingException
import cc.stkmn.shareparser.notify.FailureNotifier
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShareParserApp(intent) }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Editor(val profile: Profile?) : Screen
    data class Shared(val text: String) : Screen
    data object Failure : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareParserApp(startIntent: Intent) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { ProfileRepository(context) }
    var profiles by remember { mutableStateOf(repository.profiles()) }
    val sharedText = remember(startIntent) { startIntent.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT) }
    val deepFailure = remember(startIntent) { startIntent.data?.scheme == "shareparser" && startIntent.data?.host == "failure" }
    var screen by remember { mutableStateOf<Screen>(when { deepFailure -> Screen.Failure; !sharedText.isNullOrBlank() -> Screen.Shared(sharedText); else -> Screen.Home }) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    MaterialTheme(colorScheme = dynamicOrDefaultScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(when (screen) {
                        Screen.Home -> "ShareParser"
                        is Screen.Editor -> if ((screen as Screen.Editor).profile == null) "Profil erstellen" else "Profil bearbeiten"
                        is Screen.Shared -> "Geteilter Inhalt"
                        Screen.Failure -> "Fehlerbericht"
                    }) },
                    navigationIcon = {
                        if (screen !is Screen.Home) IconButton(onClick = { screen = Screen.Home }) { Icon(Icons.Outlined.ArrowBack, null) }
                    }
                )
            },
            floatingActionButton = {
                if (screen is Screen.Home) FloatingActionButton(onClick = { screen = Screen.Editor(null) }) { Icon(Icons.Outlined.Add, "Profil erstellen") }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (val current = screen) {
                    Screen.Home -> HomeScreen(profiles, onEdit = { screen = Screen.Editor(it) }, onCreate = { screen = Screen.Editor(null) })
                    is Screen.Editor -> ProfileEditor(current.profile, repository, onSaved = {
                        profiles = repository.profiles(); screen = Screen.Home
                    })
                    is Screen.Shared -> SharedScreen(current.text, profiles, repository, onEditProfile = { screen = Screen.Editor(it) })
                    Screen.Failure -> FailureScreen(repository.lastFailure(), profiles, onEdit = { id -> profiles.firstOrNull { it.id == id }?.let { screen = Screen.Editor(it) } })
                }
            }
        }
    }
}

@Composable
private fun dynamicOrDefaultScheme(): ColorScheme {
    val context = androidx.compose.ui.platform.LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (androidx.compose.foundation.isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
}

@Composable
private fun HomeScreen(profiles: List<Profile>, onEdit: (Profile) -> Unit, onCreate: () -> Unit) {
    if (profiles.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Share, null, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(20.dp))
            Text("Erstelle dein erstes Profil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Ein Profil erkennt ähnliche Texte, extrahiert Werte und bietet passende Aktionen an.")
            Spacer(Modifier.height(24.dp))
            Button(onClick = onCreate) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Profil erstellen") }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        items(profiles, key = { it.id }) { profile ->
            Card(onClick = { onEdit(profile) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${profile.extractors.size} Felder · ${profile.actions.size} Aktionen", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ProfileEditor(existing: Profile?, repository: ProfileRepository, onSaved: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var matcher by remember { mutableStateOf(existing?.matchers?.firstOrNull()?.regex ?: "") }
    var key by remember { mutableStateOf(existing?.extractors?.firstOrNull()?.key ?: "title") }
    var regex by remember { mutableStateOf(existing?.extractors?.firstOrNull()?.regex ?: "(?m)^Subject:\\s*(.+)$") }
    var actionName by remember { mutableStateOf(existing?.actions?.firstOrNull()?.friendlyName ?: "Kalender öffnen") }
    var titleTemplate by remember { mutableStateOf((existing?.actions?.firstOrNull() as? ProcessingAction.Calendar)?.titleTemplate ?: "{{$key}}") }
    var advanced by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Grundlagen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item { OutlinedTextField(name, { name = it }, label = { Text("Profilname") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(matcher, { matcher = it }, label = { Text("Optionaler Erkennungs-Regex") }, supportingText = { Text("Leer bedeutet: Profil kann für jeden geteilten Text gewählt werden.") }, modifier = Modifier.fillMaxWidth()) }
        item { HorizontalDivider() }
        item { Text("1. Information extrahieren", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item { OutlinedTextField(key, { key = it }, label = { Text("Variablenname") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(regex, { regex = it }, label = { Text("Regex") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
        item { HorizontalDivider() }
        item { Text("2. Verarbeitung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item { OutlinedTextField(actionName, { actionName = it }, label = { Text("Friendly Name") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(titleTemplate, { titleTemplate = it }, label = { Text("Kalendertitel") }, supportingText = { Text("Variablen als {{name}} einsetzen.") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(advanced, { advanced = it }); Spacer(Modifier.width(10.dp)); Text("Erweiterter Modus")
            }
        }
        if (advanced && existing != null) {
            item { Text("Profil-JSON kann exportiert, geteilt und später wieder importiert werden.") }
            item { Button(onClick = { clipboard.setText(AnnotatedString(repository.export(existing))) }) { Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("JSON kopieren") } }
            item { OutlinedTextField(importText, { importText = it }, label = { Text("Profil-JSON importieren") }, modifier = Modifier.fillMaxWidth(), minLines = 5) }
            item { OutlinedButton(onClick = { if (importText.isNotBlank()) { runCatching { repository.import(importText) }; onSaved() } }) { Text("Importieren") } }
        }
        item {
            Button(
                enabled = name.isNotBlank() && key.isNotBlank() && regex.isNotBlank(),
                onClick = {
                    val id = existing?.id ?: UUID.randomUUID().toString()
                    val profile = Profile(
                        id = id,
                        name = name.trim(),
                        matchers = matcher.takeIf { it.isNotBlank() }?.let { listOf(MatcherRule(it)) } ?: emptyList(),
                        extractors = listOf(ExtractorRule(key.trim(), regex, required = true)),
                        actions = listOf(ProcessingAction.Calendar(UUID.randomUUID().toString(), actionName.ifBlank { "Kalender öffnen" }, titleTemplate = titleTemplate))
                    )
                    repository.save(profile); onSaved()
                }, modifier = Modifier.fillMaxWidth()
            ) { Text("Profil speichern") }
        }
    }
}

@Composable
private fun SharedScreen(input: String, profiles: List<Profile>, repository: ProfileRepository, onEditProfile: (Profile) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val parser = remember { ParserEngine() }
    val matches = remember(input, profiles) { runCatching { parser.matchingProfiles(input, profiles) }.getOrDefault(emptyList()) }
    var selected by remember { mutableStateOf<Profile?>(matches.singleOrNull()) }
    val values = remember(input, selected) { selected?.let { runCatching { parser.extract(input, it) }.getOrNull() } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Original", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp)); Text(input, maxLines = 10) } }
        }
        if (matches.isEmpty()) {
            item { Text("Kein Profil passt zu diesem Text. Öffne ShareParser normal und erstelle ein Profil mit einem passenden Erkennungs-Regex.") }
        } else if (selected == null) {
            item { Text("Profil auswählen", style = MaterialTheme.typography.titleMedium) }
            items(matches, key = { it.id }) { profile -> AssistChip(onClick = { selected = profile }, label = { Text(profile.name) }, leadingIcon = { Icon(Icons.Outlined.Tune, null) }) }
        } else {
            item { Text("Profil: ${selected!!.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            values?.forEach { (k, v) -> if (k != "input") item { ListItem(headlineContent = { Text(k) }, supportingContent = { Text(v) }) } }
            item { Text("Weiterverarbeitung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(selected!!.actions, key = { it.id }) { action ->
                ElevatedButton(onClick = {
                    try {
                        val extracted = parser.extract(input, selected!!)
                        ActionExecutor(context).execute(action, extracted)
                    } catch (e: Exception) {
                        val p = e as? ProcessingException
                        val report = FailureReport(
                            id = UUID.randomUUID().toString(), profileId = selected!!.id, profileName = selected!!.name,
                            actionId = action.id, message = p?.userMessage ?: "Verarbeitung fehlgeschlagen.",
                            technicalDetails = p?.technicalDetails ?: (e.message ?: e.toString()), failingField = p?.failingField,
                            inputPreview = input.take(1000), createdAtEpochMs = System.currentTimeMillis()
                        )
                        repository.saveFailure(report); FailureNotifier.show(context, report)
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(actionIcon(action.icon), null); Spacer(Modifier.width(8.dp)); Text(action.friendlyName)
                }
            }
            item { TextButton(onClick = { onEditProfile(selected!!) }) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(6.dp)); Text("Profil bearbeiten") } }
        }
    }
}

@Composable
private fun FailureScreen(report: FailureReport?, profiles: List<Profile>, onEdit: (String) -> Unit) {
    if (report == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Kein Fehlerbericht vorhanden.") }; return }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(report.message, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
        report.failingField?.let { item { Card { Column(Modifier.padding(16.dp)) { Text("Betroffener Bereich", fontWeight = FontWeight.SemiBold); Text(it) } } } }
        item { Text("Technische Details", fontWeight = FontWeight.SemiBold) }
        item { Text(report.technicalDetails) }
        item { Text("Eingabe", fontWeight = FontWeight.SemiBold) }
        item { Text(report.inputPreview) }
        if (report.profileId != null && profiles.any { it.id == report.profileId }) item {
            Button(onClick = { onEdit(report.profileId) }) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(8.dp)); Text("Profil bearbeiten") }
        }
    }
}

private fun actionIcon(name: String) = when (name) {
    "event" -> Icons.Outlined.Event
    "link" -> Icons.Outlined.Link
    "send" -> Icons.Outlined.Send
    else -> Icons.Outlined.Share
}
