package cc.stkmn.shareparser.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import cc.stkmn.shareparser.data.CaseMode
import cc.stkmn.shareparser.data.ExtractorRule
import cc.stkmn.shareparser.data.InputSource
import cc.stkmn.shareparser.data.MatcherRule
import cc.stkmn.shareparser.data.ProcessingAction
import cc.stkmn.shareparser.data.Profile
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.SharedPayload
import cc.stkmn.shareparser.data.UrlOpenMode
import cc.stkmn.shareparser.data.ValueTransform
import cc.stkmn.shareparser.engine.GuidedRuleFactory
import cc.stkmn.shareparser.engine.ParserEngine
import java.util.UUID

private val reservedVariables = setOf("input", "text", "subject")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditorScreen(
    existing: Profile?,
    sample: SharedPayload?,
    highlightField: String?,
    repository: ProfileRepository,
    onSaved: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val parser = remember { ParserEngine() }

    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var enabled by remember(existing?.id) { mutableStateOf(existing?.enabled ?: true) }
    val matchers = remember(existing?.id) { mutableStateListOf<MatcherRule>().apply { addAll(existing?.matchers.orEmpty()) } }
    val extractors = remember(existing?.id) { mutableStateListOf<ExtractorRule>().apply { addAll(existing?.extractors.orEmpty()) } }
    val actions = remember(existing?.id) {
        mutableStateListOf<ProcessingAction>().apply { addAll(existing?.actions ?: listOf(defaultCalendarAction())) }
    }

    var advanced by remember { mutableStateOf(false) }
    var advancedJson by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf("") }
    var addActionMenu by remember { mutableStateOf(false) }
    var customMatcher by remember { mutableStateOf("") }

    var subjectSelection by remember(sample?.subject) { mutableStateOf(TextFieldValue(sample?.subject.orEmpty())) }
    var bodySelection by remember(sample?.text) { mutableStateOf(TextFieldValue(sample?.text.orEmpty())) }
    var pendingSelection by remember { mutableStateOf<SelectionDraft?>(null) }
    var pendingCandidate by remember { mutableStateOf<GuidedRuleFactory.Candidate?>(null) }
    var variableName by remember { mutableStateOf("") }
    var variableRequired by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && pendingExport.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExport) }
                    ?: error("Datei konnte nicht geöffnet werden")
            }.onFailure { validationMessage = "Export fehlgeschlagen: ${it.message}" }
        }
    }

    fun buildProfile(): Profile = Profile(
        id = existing?.id ?: UUID.randomUUID().toString(),
        name = name.trim(),
        enabled = enabled,
        matchers = matchers.toList(),
        extractors = extractors.toList(),
        actions = actions.toList()
    )

    fun validate(profile: Profile): String? {
        if (profile.name.isBlank()) return "Bitte einen Profilnamen eingeben."
        profile.matchers.forEach { matcher ->
            runCatching { Regex(matcher.regex) }.getOrElse { return "Ein Erkennungsmerkmal ist ungültig: ${it.message}" }
        }
        val keys = mutableSetOf<String>()
        for (rule in profile.extractors) {
            if (rule.key.isBlank()) return "Jede Variable braucht einen Namen."
            if (rule.key in reservedVariables) return "'${rule.key}' ist bereits eine eingebaute Variable. Bitte einen anderen Namen wählen."
            if (!keys.add(rule.key)) return "Variablenname '${rule.key}' wird mehrfach verwendet."
            runCatching { Regex(rule.regex) }.getOrElse { return "Erkennungsregel für '${rule.key}' ist ungültig: ${it.message}" }
            if (rule.group < 0) return "Capture Group für '${rule.key}' darf nicht negativ sein."
        }
        if (profile.actions.isEmpty()) return "Mindestens eine Weiterverarbeitung hinzufügen."
        return null
    }

    fun addCandidate(candidate: GuidedRuleFactory.Candidate) {
        val key = variableName.ifBlank {
            if (candidate.suggestedKey in reservedVariables) "${candidate.suggestedKey}_part" else candidate.suggestedKey
        }
        extractors += GuidedRuleFactory.extractor(candidate, key, variableRequired)
        pendingCandidate = null
        variableName = ""
        variableRequired = false
    }

    fun addSelection(draft: SelectionDraft) {
        extractors += GuidedRuleFactory.extractorFromSelection(
            sourceText = draft.sourceText,
            selectionStart = draft.start,
            selectionEnd = draft.end,
            key = variableName.ifBlank { "field${extractors.size + 1}" },
            source = draft.source,
            required = variableRequired
        )
        pendingSelection = null
        variableName = ""
        variableRequired = false
    }

    LaunchedEffect(advanced) {
        if (advanced) advancedJson = repository.export(buildProfile())
    }

    pendingCandidate?.let { candidate ->
        VariableDialog(
            title = "„${candidate.value.take(48)}“ als Variable",
            suggestedName = if (candidate.suggestedKey in reservedVariables) "${candidate.suggestedKey}_part" else candidate.suggestedKey,
            currentName = variableName,
            required = variableRequired,
            onNameChange = { variableName = it },
            onRequiredChange = { variableRequired = it },
            onConfirm = { addCandidate(candidate) },
            onDismiss = { pendingCandidate = null; variableName = ""; variableRequired = false }
        )
    }
    pendingSelection?.let { draft ->
        VariableDialog(
            title = "Markierten Text als Variable verwenden",
            suggestedName = "field${extractors.size + 1}",
            currentName = variableName,
            required = variableRequired,
            onNameChange = { variableName = it },
            onRequiredChange = { variableRequired = it },
            onConfirm = { addSelection(draft) },
            onDismiss = { pendingSelection = null; variableName = ""; variableRequired = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (highlightField != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Fehlerstelle hervorgehoben", fontWeight = FontWeight.SemiBold)
                            Text(highlightField, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item { SectionTitle("Profil") }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profilname") },
                placeholder = { Text("z. B. FairEmail Termin") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(enabled, { enabled = it })
                Spacer(Modifier.width(10.dp))
                Text(if (enabled) "Profil aktiv" else "Profil deaktiviert")
            }
        }

        item { HorizontalDivider() }
        item { SectionTitle("Wann soll dieses Profil angeboten werden?") }
        item {
            Text("Erkennungsmerkmale sind feste Textteile, die in ähnlichen Nachrichten wieder vorkommen. Nur wenn alle ausgewählten Merkmale gefunden werden, bietet ShareParser dieses Profil an. Wähle zum Beispiel einen typischen Betreff oder Bezeichnungen wie „Datum:“ und „Buchungsnummer:“.")
        }
        if (sample != null) {
            item {
                Text("Vorschläge aus dem Beispiel", style = MaterialTheme.typography.labelLarge)
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GuidedRuleFactory.suggestedMatchers(sample)) { suggestion ->
                        val selected = matchers.any { it.friendlyText == suggestion || it.regex == Regex.escape(suggestion) }
                        item {
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (selected) matchers.removeAll { it.friendlyText == suggestion || it.regex == Regex.escape(suggestion) }
                                    else matchers += GuidedRuleFactory.matcherFromText(suggestion)
                                },
                                label = { Text(suggestion.take(36)) }
                            )
                        }
                    }
                }
            }
        }
        if (matchers.isNotEmpty()) {
            item { Text("Aktive Merkmale", style = MaterialTheme.typography.labelLarge) }
            itemsIndexed(matchers, key = { index, matcher -> "${matcher.regex}-$index" }) { _, matcher ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(matcher.friendlyText.ifBlank { matcher.regex }, modifier = Modifier.weight(1f))
                        IconButton(onClick = { matchers.remove(matcher) }) { Icon(Icons.Outlined.Delete, "Merkmal entfernen") }
                    }
                }
            }
        } else {
            item { Text("Keine Merkmale gewählt. Das Profil kann bei jedem geteilten Text angeboten werden.", style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customMatcher,
                    onValueChange = { customMatcher = it },
                    label = { Text("Eigenes festes Textmerkmal") },
                    placeholder = { Text("z. B. Terminbestätigung") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (customMatcher.isNotBlank()) {
                            matchers += GuidedRuleFactory.matcherFromText(customMatcher)
                            customMatcher = ""
                        }
                    }
                ) { Text("Hinzufügen") }
            }
        }

        if (sample != null) {
            item { HorizontalDivider() }
            item { SectionTitle("Informationen aus dem Beispiel auswählen") }
            item {
                Text("Markiere im Betreff oder Nachrichtentext den Teil, der sich von Mail zu Mail ändern darf, und erstelle daraus eine Variable. ShareParser erzeugt die technische Erkennungsregel automatisch aus dem umgebenden Text.")
            }
            if (sample.subject.isNotBlank()) {
                item {
                    SelectionSourceCard(
                        title = "Betreff",
                        fixedText = sample.subject,
                        value = subjectSelection,
                        onValueChange = { subjectSelection = it.copy(text = sample.subject) },
                        onCreate = {
                            val selection = subjectSelection.selection
                            if (!selection.collapsed) {
                                variableName = ""
                                pendingSelection = SelectionDraft(sample.subject, selection.start, selection.end, InputSource.SUBJECT)
                            }
                        }
                    )
                }
            }
            item {
                SelectionSourceCard(
                    title = "Nachrichtentext",
                    fixedText = sample.text,
                    value = bodySelection,
                    onValueChange = { bodySelection = it.copy(text = sample.text) },
                    onCreate = {
                        val selection = bodySelection.selection
                        if (!selection.collapsed) {
                            variableName = ""
                            pendingSelection = SelectionDraft(sample.text, selection.start, selection.end, InputSource.TEXT)
                        }
                    }
                )
            }
            item {
                Text("Schnellauswahl für typische Zeilen", style = MaterialTheme.typography.labelLarge)
            }
            items(GuidedRuleFactory.candidates(sample).filter { it.source == InputSource.TEXT }.take(20)) { candidate ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(candidate.label, fontWeight = FontWeight.SemiBold)
                            Text(candidate.value, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            variableName = if (candidate.suggestedKey in reservedVariables) "${candidate.suggestedKey}_part" else candidate.suggestedKey
                            pendingCandidate = candidate
                        }) { Text("Variable") }
                    }
                }
            }
        }

        item { HorizontalDivider() }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Variablen", Modifier.weight(1f))
                TextButton(onClick = {
                    extractors += ExtractorRule(
                        key = "field${extractors.size + 1}",
                        regex = "(.+)",
                        required = false
                    )
                }) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Manuell")
                }
            }
        }
        item {
            Text("Jede Variable kann später per Klick in Titel, URL, Nachricht, Ort, Datum oder andere Zielfelder eingesetzt werden.")
        }
        if (extractors.isEmpty()) {
            item { Text("Noch keine eigenen Variablen. Betreff und gesamter Text sind bereits als eingebaute Variablen verfügbar.", style = MaterialTheme.typography.bodySmall) }
        }
        items(extractors, key = { it.id }) { rule ->
            val index = extractors.indexOfFirst { it.id == rule.id }
            ExtractorCard(
                rule = rule,
                sample = sample,
                parser = parser,
                highlighted = highlightField == rule.key,
                advanced = advanced,
                onChange = { changed -> if (index >= 0) extractors[index] = changed },
                onDelete = { if (index >= 0) extractors.removeAt(index) }
            )
        }

        item { HorizontalDivider() }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Weiterverarbeitung", Modifier.weight(1f))
                Column {
                    TextButton(onClick = { addActionMenu = true }) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Aktion")
                    }
                    DropdownMenu(expanded = addActionMenu, onDismissRequest = { addActionMenu = false }) {
                        DropdownMenuItem(text = { Text("Kalendereintrag") }, onClick = { actions += defaultCalendarAction(); addActionMenu = false })
                        DropdownMenuItem(text = { Text("URL öffnen") }, onClick = { actions += defaultUrlAction(); addActionMenu = false })
                        DropdownMenuItem(text = { Text("Text weiterleiten") }, onClick = { actions += defaultShareAction(); addActionMenu = false })
                    }
                }
            }
        }
        val variables = listOf("subject", "text", "input") + extractors.map { it.key }.filter { it.isNotBlank() }
        items(actions, key = { it.id }) { action ->
            val index = actions.indexOfFirst { it.id == action.id }
            val highlighted = highlightField?.startsWith(actionHighlightPrefix(action)) == true || highlightField == action.id
            ActionEditorCard(
                action = action,
                variables = variables,
                highlighted = highlighted,
                onChange = { changed -> if (index >= 0) actions[index] = changed },
                onDelete = { if (index >= 0) actions.removeAt(index) }
            )
        }

        item { HorizontalDivider() }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(advanced, { advanced = it })
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Erweiterter Modus")
                    Text("Zeigt Regex-Details und erlaubt direkte Bearbeitung des Profil-JSON.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (advanced) {
            item {
                OutlinedTextField(
                    value = advancedJson,
                    onValueChange = { advancedJson = it },
                    label = { Text("Profil-JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 10
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            val decoded = repository.decodeBundle(advancedJson).copy(id = existing?.id ?: buildProfile().id)
                            val error = validate(decoded)
                            if (error != null) kotlin.error(error)
                            repository.save(decoded)
                        }.onSuccess { onSaved() }
                            .onFailure { validationMessage = "JSON konnte nicht angewendet werden: ${it.message}" }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("JSON anwenden") }
            }
        }

        validationMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }

        item {
            Button(
                onClick = {
                    val profile = buildProfile()
                    val error = validate(profile)
                    if (error == null) {
                        repository.save(profile)
                        onSaved()
                    } else validationMessage = error
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Profil speichern") }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AssistChip(
                        onClick = { clipboard.setText(AnnotatedString(repository.export(buildProfile()))) },
                        label = { Text("JSON kopieren") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            val json = repository.export(buildProfile())
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_TEXT, json)
                                        putExtra(Intent.EXTRA_SUBJECT, "ShareParser Profil: ${name.ifBlank { "Profil" }}")
                                    },
                                    "Profil teilen"
                                )
                            )
                        },
                        label = { Text("Teilen") },
                        leadingIcon = { Icon(Icons.Outlined.Share, null) }
                    )
                }
                item {
                    AssistChip(
                        onClick = {
                            pendingExport = repository.export(buildProfile())
                            exportLauncher.launch("${safeFileName(name.ifBlank { "shareparser-profile" })}.json")
                        },
                        label = { Text("Speichern") },
                        leadingIcon = { Icon(Icons.Outlined.Download, null) }
                    )
                }
            }
        }
        if (existing != null) {
            item {
                TextButton(
                    onClick = { repository.delete(existing.id); onDeleted() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Delete, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Profil löschen")
                }
            }
        }
        item { Spacer(Modifier.height(36.dp)) }
    }
}

private data class SelectionDraft(
    val sourceText: String,
    val start: Int,
    val end: Int,
    val source: InputSource
)

@Composable
private fun VariableDialog(
    title: String,
    suggestedName: String,
    currentName: String,
    required: Boolean,
    onNameChange: (String) -> Unit,
    onRequiredChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (currentName.isBlank()) onNameChange(suggestedName)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gib der Information einen kurzen Namen. Diesen Namen kannst du danach in den Ziel-Feldern auswählen.")
                OutlinedTextField(
                    value = currentName,
                    onValueChange = { onNameChange(GuidedRuleFactory.sanitizeKey(it)) },
                    label = { Text("Variablenname") },
                    placeholder = { Text(suggestedName) },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(required, onRequiredChange)
                    Text("Pflichtfeld. Fehlt der Wert, Verarbeitung als Fehler melden.")
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = currentName.isNotBlank()) { Text("Variable erstellen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun SelectionSourceCard(
    title: String,
    fixedText: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onCreate: () -> Unit
) {
    val selectedText = if (!value.selection.collapsed) {
        fixedText.substring(
            minOf(value.selection.start, value.selection.end).coerceIn(0, fixedText.length),
            maxOf(value.selection.start, value.selection.end).coerceIn(0, fixedText.length)
        )
    } else ""
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text("Text gedrückt halten und den gewünschten Wert markieren.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                minLines = if (title == "Nachrichtentext") 5 else 1,
                maxLines = if (title == "Nachrichtentext") 12 else 3
            )
            if (selectedText.isNotBlank()) Text("Markiert: $selectedText", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onCreate, enabled = selectedText.isNotBlank()) { Text("Markierung als Variable") }
        }
    }
}

@Composable
private fun ExtractorCard(
    rule: ExtractorRule,
    sample: SharedPayload?,
    parser: ParserEngine,
    highlighted: Boolean,
    advanced: Boolean,
    onChange: (ExtractorRule) -> Unit,
    onDelete: () -> Unit
) {
    var showDetails by remember(rule.id) { mutableStateOf(false) }
    var transformMenu by remember { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf(false) }
    val borderModifier = if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp)) else Modifier
    val preview = remember(rule, sample) {
        sample?.let {
            runCatching { parser.extract(it, Profile("preview", "preview", extractors = listOf(rule.copy(required = false))))[rule.key] }.getOrNull()
        }
    }

    Card(borderModifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.key.ifBlank { "Variable" }, fontWeight = FontWeight.SemiBold)
                    if (preview != null) Text("Beispielwert: $preview", style = MaterialTheme.typography.bodySmall)
                    else if (sample != null) Text("Im Beispiel nicht erkannt", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Variable entfernen") }
            }
            OutlinedTextField(
                value = rule.key,
                onValueChange = { onChange(rule.copy(key = GuidedRuleFactory.sanitizeKey(it))) },
                label = { Text("Variablenname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = rule.required, onCheckedChange = { onChange(rule.copy(required = it)) })
                Text("Pflichtfeld")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Details ausblenden" else "Bausteine")
                    Icon(Icons.Outlined.ExpandMore, null)
                }
            }

            if (showDetails || advanced) {
                Column {
                    OutlinedButton(onClick = { sourceMenu = true }) { Text("Quelle: ${sourceLabel(rule.source)}") }
                    DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                        InputSource.entries.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(sourceLabel(source)) },
                                onClick = { onChange(rule.copy(source = source)); sourceMenu = false }
                            )
                        }
                    }
                }
                if (rule.transforms.isNotEmpty()) {
                    Text("Umwandlungen", style = MaterialTheme.typography.labelLarge)
                    rule.transforms.forEachIndexed { transformIndex, transform ->
                        TransformEditor(
                            transform = transform,
                            onChange = { changed -> onChange(rule.copy(transforms = rule.transforms.toMutableList().apply { this[transformIndex] = changed })) },
                            onDelete = { onChange(rule.copy(transforms = rule.transforms.toMutableList().apply { removeAt(transformIndex) })) }
                        )
                    }
                }
                Column {
                    TextButton(onClick = { transformMenu = true }) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Umwandlung hinzufügen")
                    }
                    DropdownMenu(expanded = transformMenu, onDismissRequest = { transformMenu = false }) {
                        DropdownMenuItem(text = { Text("Leerzeichen am Rand entfernen") }, onClick = { onChange(rule.copy(transforms = rule.transforms + ValueTransform.Trim)); transformMenu = false })
                        DropdownMenuItem(text = { Text("Textteil ersetzen/entfernen") }, onClick = { onChange(rule.copy(transforms = rule.transforms + ValueTransform.RegexReplace("", ""))); transformMenu = false })
                        DropdownMenuItem(text = { Text("Text davor setzen") }, onClick = { onChange(rule.copy(transforms = rule.transforms + ValueTransform.Prefix(""))); transformMenu = false })
                        DropdownMenuItem(text = { Text("Text danach setzen") }, onClick = { onChange(rule.copy(transforms = rule.transforms + ValueTransform.Suffix(""))); transformMenu = false })
                        DropdownMenuItem(text = { Text("Kleinschreibung") }, onClick = { onChange(rule.copy(transforms = rule.transforms + ValueTransform.ChangeCase(CaseMode.LOWER))); transformMenu = false })
                        DropdownMenuItem(text = { Text("Großschreibung") }, onClick = { onChange(rule.copy(transforms = rule.transforms + ValueTransform.ChangeCase(CaseMode.UPPER))); transformMenu = false })
                    }
                }
            }
            if (advanced) {
                OutlinedTextField(
                    value = rule.regex,
                    onValueChange = { onChange(rule.copy(regex = it)) },
                    label = { Text("Regex, erweitert") },
                    supportingText = { Text("Nur für erfahrene Nutzer. Normalerweise erzeugt ShareParser diese Regel aus dem Beispiel.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = rule.group.toString(),
                    onValueChange = { it.toIntOrNull()?.let { group -> onChange(rule.copy(group = group)) } },
                    label = { Text("Capture Group") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
private fun TransformEditor(
    transform: ValueTransform,
    onChange: (ValueTransform) -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(transformLabel(transform), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Umwandlung entfernen") }
            }
            when (transform) {
                ValueTransform.Trim -> Text("Entfernt Leerzeichen am Anfang und Ende.", style = MaterialTheme.typography.bodySmall)
                is ValueTransform.Prefix -> OutlinedTextField(transform.value, { onChange(transform.copy(value = it)) }, label = { Text("Text davor") }, modifier = Modifier.fillMaxWidth())
                is ValueTransform.Suffix -> OutlinedTextField(transform.value, { onChange(transform.copy(value = it)) }, label = { Text("Text danach") }, modifier = Modifier.fillMaxWidth())
                is ValueTransform.ChangeCase -> Text(if (transform.mode == CaseMode.LOWER) "In Kleinschreibung umwandeln" else "In Großschreibung umwandeln")
                is ValueTransform.RegexReplace -> {
                    OutlinedTextField(transform.regex, { onChange(transform.copy(regex = it)) }, label = { Text("Zu ersetzender Text oder Regex") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(transform.replacement, { onChange(transform.copy(replacement = it)) }, label = { Text("Ersetzen durch, leer = entfernen") }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ActionEditorCard(
    action: ProcessingAction,
    variables: List<String>,
    highlighted: Boolean,
    onChange: (ProcessingAction) -> Unit,
    onDelete: () -> Unit
) {
    var iconMenu by remember { mutableStateOf(false) }
    val borderModifier = if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp)) else Modifier
    Card(borderModifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(actionIcon(action.icon), null)
                Spacer(Modifier.width(8.dp))
                Text(action.friendlyName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Aktion entfernen") }
            }
            OutlinedTextField(
                value = action.friendlyName,
                onValueChange = { onChange(withFriendlyName(action, it)) },
                label = { Text("Anzeigename") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Column {
                OutlinedButton(onClick = { iconMenu = true }) {
                    Icon(actionIcon(action.icon), null)
                    Spacer(Modifier.width(6.dp))
                    Text("Icon auswählen")
                }
                DropdownMenu(expanded = iconMenu, onDismissRequest = { iconMenu = false }) {
                    actionIcons.forEach { choice ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(choice.vector, null) },
                            text = { Text(choice.label) },
                            onClick = { onChange(withIcon(action, choice.id)); iconMenu = false }
                        )
                    }
                }
            }
            when (action) {
                is ProcessingAction.Calendar -> CalendarActionFields(action, variables, onChange)
                is ProcessingAction.Url -> UrlActionFields(action, variables, onChange)
                is ProcessingAction.Share -> ShareActionFields(action, variables, onChange)
            }
        }
    }
}

@Composable
private fun CalendarActionFields(
    action: ProcessingAction.Calendar,
    variables: List<String>,
    onChange: (ProcessingAction) -> Unit
) {
    Text("Kalenderfelder", style = MaterialTheme.typography.labelLarge)
    TemplateField("Titel", action.titleTemplate, variables) { onChange(action.copy(titleTemplate = it)) }
    TemplateField("Beschreibung", action.descriptionTemplate, variables, minLines = 3) { onChange(action.copy(descriptionTemplate = it)) }
    TemplateField("Ort", action.locationTemplate, variables) { onChange(action.copy(locationTemplate = it)) }
    TemplateField(
        "Beginn oder Zeitraum",
        action.startTemplate,
        variables,
        placeholder = "Füge z. B. Datum und Uhrzeit ein. Auch 'morgen 12-14' wird erkannt."
    ) { onChange(action.copy(startTemplate = it, startPattern = "")) }
    TemplateField(
        "Ende, optional",
        action.endTemplate,
        variables,
        placeholder = "Nur nötig, wenn der Beginn keinen Zeitraum enthält."
    ) { onChange(action.copy(endTemplate = it, endPattern = "")) }
    TemplateField(
        "Zielkalender, optional",
        action.calendarNameTemplate,
        variables,
        placeholder = "z. B. Arbeit"
    ) { onChange(action.copy(calendarNameTemplate = it)) }
    Text("Bei einem Zielkalender fragt ShareParser beim ersten Ausführen nach Kalender-Lesezugriff und versucht einen Kalender mit diesem Namen auszuwählen. Falls die Kalender-App das nicht übernimmt, erscheint ein Hinweis zur manuellen Auswahl.", style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(action.allDay, { onChange(action.copy(allDay = it)) })
        Text("Ganztägig")
    }
}

@Composable
private fun UrlActionFields(
    action: ProcessingAction.Url,
    variables: List<String>,
    onChange: (ProcessingAction) -> Unit
) {
    TemplateField(
        label = "Link",
        value = action.urlTemplate,
        variables = variables,
        placeholder = "https://example.com/?id=…",
        minLines = 2,
        urlEncodeVariables = true
    ) { onChange(action.copy(urlTemplate = it)) }
    Text("Öffnen mit", style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = action.openMode == UrlOpenMode.BROWSER, onClick = { onChange(action.copy(openMode = UrlOpenMode.BROWSER)) })
        Text("Standard-Browser")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = action.openMode == UrlOpenMode.WEBVIEW, onClick = { onChange(action.copy(openMode = UrlOpenMode.WEBVIEW)) })
        Column {
            Text("In ShareParser öffnen")
            Text("Abgesicherte WebView ohne JavaScript und ohne Drittanbieter-Cookies.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ShareActionFields(
    action: ProcessingAction.Share,
    variables: List<String>,
    onChange: (ProcessingAction) -> Unit
) {
    TemplateField("Betreff", action.subjectTemplate, variables) { onChange(action.copy(subjectTemplate = it)) }
    TemplateField("Nachricht", action.textTemplate, variables, minLines = 4) { onChange(action.copy(textTemplate = it)) }
    OutlinedTextField(
        value = action.mimeType,
        onValueChange = { onChange(action.copy(mimeType = it)) },
        label = { Text("Inhaltstyp") },
        placeholder = { Text("text/plain") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun TemplateField(
    label: String,
    value: String,
    variables: List<String>,
    placeholder: String = "Fester Text oder Variable",
    minLines: Int = 1,
    urlEncodeVariables: Boolean = false,
    onChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            supportingText = { Text("Du kannst festen Text schreiben und erkannte Variablen darunter einfügen.") },
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines
        )
        Text("Variable einfügen", style = MaterialTheme.typography.labelSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(variables.distinct()) { variable ->
                AssistChip(
                    onClick = {
                        val token = if (urlEncodeVariables) "{{${variable}|url}}" else "{{${variable}}}"
                        val separator = if (value.isNotBlank() && !value.endsWith(" ") && !urlEncodeVariables) " " else ""
                        onChange(value + separator + token)
                    },
                    label = { Text(variableLabel(variable)) }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

private fun defaultCalendarAction() = ProcessingAction.Calendar(
    id = UUID.randomUUID().toString(),
    friendlyName = "Kalender öffnen"
)

private fun defaultUrlAction() = ProcessingAction.Url(
    id = UUID.randomUUID().toString(),
    friendlyName = "Link öffnen"
)

private fun defaultShareAction() = ProcessingAction.Share(
    id = UUID.randomUUID().toString(),
    friendlyName = "Text weiterleiten"
)

private fun withFriendlyName(action: ProcessingAction, name: String): ProcessingAction = when (action) {
    is ProcessingAction.Calendar -> action.copy(friendlyName = name)
    is ProcessingAction.Url -> action.copy(friendlyName = name)
    is ProcessingAction.Share -> action.copy(friendlyName = name)
}

private fun withIcon(action: ProcessingAction, icon: String): ProcessingAction = when (action) {
    is ProcessingAction.Calendar -> action.copy(icon = icon)
    is ProcessingAction.Url -> action.copy(icon = icon)
    is ProcessingAction.Share -> action.copy(icon = icon)
}

private fun actionHighlightPrefix(action: ProcessingAction): String = when (action) {
    is ProcessingAction.Calendar -> "calendar"
    is ProcessingAction.Url -> "url"
    is ProcessingAction.Share -> "share"
}

private fun sourceLabel(source: InputSource): String = when (source) {
    InputSource.COMBINED -> "Betreff + Text"
    InputSource.TEXT -> "Nachrichtentext"
    InputSource.SUBJECT -> "Betreff"
}

private fun transformLabel(transform: ValueTransform): String = when (transform) {
    ValueTransform.Trim -> "Leerzeichen entfernen"
    is ValueTransform.RegexReplace -> "Textteil ersetzen / entfernen"
    is ValueTransform.Prefix -> "Text davor"
    is ValueTransform.Suffix -> "Text danach"
    is ValueTransform.ChangeCase -> if (transform.mode == CaseMode.LOWER) "Kleinschreibung" else "Großschreibung"
}

private fun variableLabel(key: String): String = when (key) {
    "subject" -> "Betreff"
    "text" -> "Gesamte Nachricht"
    "input" -> "Betreff + Nachricht"
    else -> key
}

private fun safeFileName(name: String): String = name
    .trim()
    .replace(Regex("[^a-zA-Z0-9._-]+"), "-")
    .trim('-')
    .ifBlank { "shareparser-profile" }
