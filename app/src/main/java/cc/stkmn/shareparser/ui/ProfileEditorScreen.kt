package cc.stkmn.shareparser.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Splitscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import cc.stkmn.shareparser.ShareSourceAppCatalog
import cc.stkmn.shareparser.calendar.CalendarCatalog
import cc.stkmn.shareparser.data.CalendarTargetMode
import cc.stkmn.shareparser.data.CaseMode
import cc.stkmn.shareparser.data.EditorModeStore
import cc.stkmn.shareparser.data.EmptyValuePolicy
import cc.stkmn.shareparser.data.ExtractorRule
import cc.stkmn.shareparser.data.InputSource
import cc.stkmn.shareparser.data.MatcherJoin
import cc.stkmn.shareparser.data.MatcherRule
import cc.stkmn.shareparser.data.MatcherValueMode
import cc.stkmn.shareparser.data.ParseDirection
import cc.stkmn.shareparser.data.ProcessingAction
import cc.stkmn.shareparser.data.Profile
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.SharedPayload
import cc.stkmn.shareparser.data.TextFileMode
import cc.stkmn.shareparser.data.UrlOpenMode
import cc.stkmn.shareparser.data.ValueTransform
import cc.stkmn.shareparser.data.WebhookMode
import cc.stkmn.shareparser.engine.GuidedRuleFactory
import cc.stkmn.shareparser.engine.ParserEngine
import cc.stkmn.shareparser.engine.TemplateEngine
import java.util.UUID
import kotlinx.coroutines.launch

private val reservedVariables = setOf(
    "input",
    "text",
    "subject",
    "source_app",
    "source_package",
    "file_name",
    "mime_type"
)

@Composable
internal fun ProfileEditorScreen(
    existing: Profile?,
    sample: SharedPayload?,
    highlightField: String?,
    repository: ProfileRepository,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    exitRequest: Int = 0,
    undoRequest: Int = 0,
    redoRequest: Int = 0,
    onDirtyChanged: (Boolean) -> Unit = {},
    onHistoryChanged: (Boolean, Boolean) -> Unit = { _, _ -> },
    onExitRequestHandled: () -> Unit = {},
    onHistoryRequestHandled: () -> Unit = {},
    onDiscarded: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val parser = remember { ParserEngine() }
    val profileId = remember(existing?.id) { existing?.id ?: UUID.randomUUID().toString() }
    val editorModeStore = remember { EditorModeStore(context) }

    DisposableEffect(profileId) {
        editorModeStore.activate(profileId)
        onDispose { editorModeStore.clear(profileId) }
    }

    val initialActions = remember(existing?.id) {
        existing?.actions?.takeIf { it.isNotEmpty() } ?: listOf(defaultCalendarAction())
    }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var enabled by remember(existing?.id) { mutableStateOf(existing?.enabled ?: true) }
    var parseDirection by remember(existing?.id) { mutableStateOf(existing?.parseDirection ?: ParseDirection.TOP_DOWN) }
    val matchers = remember(existing?.id) { mutableStateListOf<MatcherRule>().apply { addAll(existing?.matchers.orEmpty()) } }
    val extractors = remember(existing?.id) { mutableStateListOf<ExtractorRule>().apply { addAll(existing?.extractors.orEmpty()) } }
    val actions = remember(existing?.id) { mutableStateListOf<ProcessingAction>().apply { addAll(initialActions) } }
    val initialProfile = remember(existing?.id, profileId) {
        Profile(
            id = profileId,
            name = existing?.name.orEmpty().trim(),
            enabled = existing?.enabled ?: true,
            matchers = existing?.matchers.orEmpty(),
            extractors = existing?.extractors.orEmpty(),
            actions = initialActions,
            parseDirection = existing?.parseDirection ?: ParseDirection.TOP_DOWN
        )
    }
    val undoStack = remember(existing?.id) { mutableStateListOf<Profile>() }
    val redoStack = remember(existing?.id) { mutableStateListOf<Profile>() }
    var lastObserved by remember(existing?.id) { mutableStateOf(initialProfile) }
    var restoringHistory by remember(existing?.id) { mutableStateOf(false) }
    var showExitDialog by remember(existing?.id) { mutableStateOf(false) }

    var advanced by remember { mutableStateOf(false) }
    var advancedJson by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf("") }
    var addActionMenu by remember { mutableStateOf(false) }
    var customMatcher by remember { mutableStateOf("") }
    var variableMatcherExpanded by remember { mutableStateOf(false) }
    val selectedMatcherVariables = remember { mutableStateListOf<String>() }
    var variableMatcherMode by remember { mutableStateOf(MatcherValueMode.NOT_EMPTY) }
    var matcherPatternKind by remember { mutableStateOf("contains") }
    var matcherPatternValue by remember { mutableStateOf("") }

    var subjectSelection by remember(sample?.subject) { mutableStateOf(TextFieldValue(sample?.subject.orEmpty())) }
    var bodySelection by remember(sample?.text) { mutableStateOf(TextFieldValue(sample?.text.orEmpty())) }
    var pendingSelection by remember { mutableStateOf<SelectionDraft?>(null) }
    var pendingCandidate by remember { mutableStateOf<GuidedRuleFactory.Candidate?>(null) }
    var variableName by remember { mutableStateOf("") }
    var variableRequired by remember { mutableStateOf(false) }
    var variableConflict by remember { mutableStateOf<VariableConflict?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && pendingExport.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExport) }
                    ?: error("Datei konnte nicht geöffnet werden")
            }.onFailure { validationMessage = "Export fehlgeschlagen: ${it.message}" }
        }
    }

    fun buildProfile() = Profile(
        id = profileId,
        name = name.trim(),
        enabled = enabled,
        matchers = matchers.toList(),
        extractors = extractors.toList(),
        actions = actions.toList(),
        parseDirection = parseDirection
    )

    fun restoreProfile(profile: Profile) {
        restoringHistory = true
        name = profile.name
        enabled = profile.enabled
        parseDirection = profile.parseDirection
        matchers.clear(); matchers.addAll(profile.matchers)
        extractors.clear(); extractors.addAll(profile.extractors)
        actions.clear(); actions.addAll(profile.actions)
    }

    fun validate(profile: Profile): String? {
        if (profile.name.isBlank()) return "Bitte einen Profilnamen eingeben."
        val keys = profile.extractors.map { it.key }
        if (keys.any { it.isBlank() }) return "Jede Variable braucht einen Namen."
        if (keys.any { it in reservedVariables }) return "Ein Variablenname ist bereits für eine eingebaute Variable reserviert."
        if (keys.size != keys.distinct().size) return "Jeder Variablenname darf nur einmal vorkommen."

        val variablesAvailableAtStep = reservedVariables.toMutableSet()
        profile.extractors.forEach { rule ->
            runCatching { Regex(rule.regex) }.getOrElse { return "Erkennungsregel für '${rule.key}' ist ungültig: ${it.message}" }
            if (rule.sourceVariableKey.isNotBlank() && rule.sourceVariableKey !in variablesAvailableAtStep) {
                return "Variable '${rule.key}' nutzt '${rule.sourceVariableKey}' als Quelle. Die Quellvariable muss vorher definiert sein."
            }
            variablesAvailableAtStep += rule.key
        }

        val available = reservedVariables + keys
        profile.matchers.forEach { matcher ->
            if (matcher.valueMode == MatcherValueMode.REGEX) {
                runCatching { Regex(matcher.regex) }.getOrElse { return "Ein Profilmerkmal ist ungültig: ${it.message}" }
            }
            if (matcher.variableKey.isNotBlank() && matcher.variableKey !in available) {
                return "Profilmerkmal verweist auf unbekannte Variable '${matcher.variableKey}'."
            }
        }
        profile.actions.forEach { action ->
            actionTemplates(action).forEach { (field, template) ->
                val unknown = TemplateEngine.variables(template) - available
                if (unknown.isNotEmpty()) return "$field verwendet unbekannte Variable: ${unknown.joinToString()}"
            }
        }
        if (profile.actions.isEmpty()) return "Mindestens eine Weiterverarbeitung hinzufügen."
        return null
    }

    fun uniqueVariableKey(base: String, skipIndex: Int? = null): String {
        val used = extractors.mapIndexedNotNull { index, rule ->
            rule.key.takeIf { index != skipIndex && it.isNotBlank() }
        }.toSet()
        if (base !in used) return base
        var number = 2
        while ("${base}_${number}" in used) number += 1
        return "${base}_${number}"
    }

    fun applyExtractor(proposed: ExtractorRule, index: Int? = null) {
        val conflictIndex = extractors.indexOfFirst { it.key == proposed.key }
            .takeIf { it >= 0 && it != index }
        if (proposed.key.isNotBlank() && conflictIndex != null) {
            variableConflict = VariableConflict(proposed, index)
            return
        }
        if (index == null) extractors += proposed
        else if (index in extractors.indices) extractors[index] = proposed
    }

    fun overwriteConflict(conflict: VariableConflict) {
        val target = extractors.indexOfFirst { it.key == conflict.proposed.key }
        val source = conflict.index
        when {
            target < 0 && source == null -> extractors += conflict.proposed
            target < 0 && source != null && source in extractors.indices -> extractors[source] = conflict.proposed
            source == null -> extractors[target] = conflict.proposed
            source == target -> extractors[source] = conflict.proposed
            source < target -> {
                extractors[source] = conflict.proposed
                extractors.removeAt(target)
            }
            else -> {
                extractors.removeAt(target)
                val adjusted = source - 1
                if (adjusted in extractors.indices) extractors[adjusted] = conflict.proposed
            }
        }
        variableConflict = null
    }

    fun incrementConflict(conflict: VariableConflict) {
        val changed = conflict.proposed.copy(key = uniqueVariableKey(conflict.proposed.key, conflict.index))
        if (conflict.index == null) extractors += changed
        else if (conflict.index in extractors.indices) extractors[conflict.index] = changed
        variableConflict = null
    }

    fun addCandidate(candidate: GuidedRuleFactory.Candidate) {
        if (variableName.isBlank()) return
        applyExtractor(GuidedRuleFactory.extractor(candidate, variableName, variableRequired))
        pendingCandidate = null
        variableName = ""
        variableRequired = false
    }

    fun addSelection(draft: SelectionDraft) {
        if (variableName.isBlank()) return
        applyExtractor(
            GuidedRuleFactory.extractorFromSelection(
                sourceText = draft.sourceText,
                selectionStart = draft.start,
                selectionEnd = draft.end,
                key = variableName,
                source = draft.source,
                required = variableRequired
            )
        )
        pendingSelection = null
        variableName = ""
        variableRequired = false
    }

    fun addMatcherSelection(sourceText: String, selection: TextRange) {
        if (selection.collapsed) return
        val matcher = GuidedRuleFactory.matcherFromSelection(sourceText, selection.start, selection.end)
        if (matcher.friendlyText.isNotBlank() && matchers.none { it.variableKey.isBlank() && it.regex == matcher.regex }) {
            matchers += matcher
        }
    }

    LaunchedEffect(advanced) {
        if (advanced) advancedJson = repository.export(buildProfile())
    }

    val currentDraft = buildProfile()
    LaunchedEffect(currentDraft) {
        onDirtyChanged(currentDraft != initialProfile)
        if (restoringHistory) {
            restoringHistory = false
            lastObserved = currentDraft
        } else if (currentDraft != lastObserved) {
            undoStack += lastObserved
            while (undoStack.size > 60) undoStack.removeAt(0)
            redoStack.clear()
            lastObserved = currentDraft
        }
        onHistoryChanged(undoStack.isNotEmpty(), redoStack.isNotEmpty())
    }

    LaunchedEffect(undoRequest, redoRequest) {
        when {
            undoRequest > 0 && undoStack.isNotEmpty() -> {
                redoStack += buildProfile()
                restoreProfile(undoStack.removeAt(undoStack.lastIndex))
                onHistoryChanged(undoStack.isNotEmpty(), redoStack.isNotEmpty())
            }
            redoRequest > 0 && redoStack.isNotEmpty() -> {
                undoStack += buildProfile()
                restoreProfile(redoStack.removeAt(redoStack.lastIndex))
                onHistoryChanged(undoStack.isNotEmpty(), redoStack.isNotEmpty())
            }
        }
        if (undoRequest > 0 || redoRequest > 0) onHistoryRequestHandled()
    }

    LaunchedEffect(exitRequest) {
        if (exitRequest > 0) {
            if (buildProfile() == initialProfile) onDiscarded() else showExitDialog = true
            onExitRequestHandled()
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Ungespeicherte Änderungen") },
            text = { Text("Möchtest du die Änderungen am Profil anwenden oder verwerfen?") },
            confirmButton = {
                Button(onClick = {
                    val profile = buildProfile()
                    val error = validate(profile)
                    if (error == null) {
                        repository.save(profile)
                        showExitDialog = false
                        onSaved()
                    } else {
                        validationMessage = error
                        showExitDialog = false
                    }
                }) { Text("Anwenden") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showExitDialog = false }) { Text("Abbrechen") }
                    TextButton(onClick = {
                        showExitDialog = false
                        onDiscarded()
                    }) { Text("Verwerfen") }
                }
            }
        )
    }


    variableConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { variableConflict = null },
            title = { Text("Variable bereits vorhanden") },
            text = {
                Text("Die Variable '${conflict.proposed.key}' existiert bereits. Wähle, wie ShareParser fortfahren soll.")
            },
            confirmButton = {
                TextButton(onClick = { overwriteConflict(conflict) }) { Text("Überschreiben") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { incrementConflict(conflict) }) { Text("Mit Nummer speichern") }
                    TextButton(onClick = { variableConflict = null }) { Text("Verwerfen") }
                }
            }
        )
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Share, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bearbeitungsmodus aktiv", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Neue Nachrichten oder Textdateien, die du jetzt an ShareParser teilst, werden direkt als Beispiel in dieses Profil geladen.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) restoreProfile(undoStack.removeAt(undoStack.lastIndex))
                        },
                        enabled = undoStack.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.Undo, "Letzte Änderung rückgängig")
                    }
                }
            }
        }

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
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Parsing-Reihenfolge", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(parseDirection == ParseDirection.TOP_DOWN, { parseDirection = ParseDirection.TOP_DOWN })
                    Text("Von oben nach unten")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(parseDirection == ParseDirection.BOTTOM_UP, { parseDirection = ParseDirection.BOTTOM_UP })
                    Text("Von unten nach oben")
                }
                Text(
                    if (parseDirection == ParseDirection.BOTTOM_UP)
                        "Nimmt bei mehrfach vorkommenden Feldern den letzten Treffer. Sinnvoll, wenn die ursprüngliche Mail unter einer Antwort oder Weiterleitung steht."
                    else "Nimmt bei mehrfach vorkommenden Feldern den ersten Treffer.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item { HorizontalDivider() }
        item { SectionTitle("Profil automatisch erkennen") }
        item {
            Text("Kombiniere feste Textteile, teilende App und Variablen. Ab dem zweiten Merkmal kannst du UND oder ODER wählen.")
        }

        if (sample != null) {
            if (sample.sourcePackage.isNotBlank()) {
                item {
                    val active = matchers.any { it.variableKey == "source_package" && it.regex == Regex.escape(sample.sourcePackage) }
                    FilterChip(
                        selected = active,
                        onClick = {
                            if (active) matchers.removeAll { it.variableKey == "source_package" && it.regex == Regex.escape(sample.sourcePackage) }
                            else matchers += MatcherRule(
                                regex = Regex.escape(sample.sourcePackage),
                                friendlyText = "Geteilt aus ${sample.sourceApp.ifBlank { sample.sourcePackage }}",
                                variableKey = "source_package"
                            )
                        },
                        label = { Text("Nur aus ${sample.sourceApp.ifBlank { sample.sourcePackage }}") }
                    )
                }
            }
            if (sample.fileName.isNotBlank()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val fileActive = matchers.any { it.variableKey == "file_name" && it.regex == Regex.escape(sample.fileName) }
                        FilterChip(
                            selected = fileActive,
                            onClick = {
                                if (fileActive) matchers.removeAll { it.variableKey == "file_name" && it.regex == Regex.escape(sample.fileName) }
                                else matchers += MatcherRule(
                                    regex = Regex.escape(sample.fileName),
                                    friendlyText = "Dateiname ${sample.fileName}",
                                    variableKey = "file_name"
                                )
                            },
                            label = { Text("Datei: ${sample.fileName.take(32)}") }
                        )
                        val mimeActive = matchers.any { it.variableKey == "mime_type" && it.regex == Regex.escape(sample.mimeType) }
                        FilterChip(
                            selected = mimeActive,
                            onClick = {
                                if (mimeActive) matchers.removeAll { it.variableKey == "mime_type" && it.regex == Regex.escape(sample.mimeType) }
                                else matchers += MatcherRule(
                                    regex = Regex.escape(sample.mimeType),
                                    friendlyText = "Dateityp ${sample.mimeType}",
                                    variableKey = "mime_type"
                                )
                            },
                            label = { Text(sample.mimeType) }
                        )
                    }
                }
            }
            if (sample.subject.isNotBlank()) {
                item {
                    SelectionSourceCard(
                        title = "Betreff",
                        fixedText = sample.subject,
                        value = subjectSelection,
                        source = InputSource.SUBJECT,
                        extractors = extractors,
                        onValueChange = { subjectSelection = it.copy(text = sample.subject) },
                        onVariable = {
                            val s = subjectSelection.selection
                            if (!s.collapsed) {
                                variableName = "field${extractors.size + 1}"
                                pendingSelection = SelectionDraft(sample.subject, s.start, s.end, InputSource.SUBJECT)
                            }
                        },
                        onMatcher = { addMatcherSelection(sample.subject, subjectSelection.selection) }
                    )
                }
            }
            item {
                SelectionSourceCard(
                    title = if (sample.fileName.isBlank()) "Nachrichtentext" else "Dateiinhalt",
                    fixedText = sample.text,
                    value = bodySelection,
                    source = InputSource.TEXT,
                    extractors = extractors,
                    onValueChange = { bodySelection = it.copy(text = sample.text) },
                    onVariable = {
                        val s = bodySelection.selection
                        if (!s.collapsed) {
                            variableName = "field${extractors.size + 1}"
                            pendingSelection = SelectionDraft(sample.text, s.start, s.end, InputSource.TEXT)
                        }
                    },
                    onMatcher = { addMatcherSelection(sample.text, bodySelection.selection) }
                )
            }
            item { Text("Automatische Vorschläge", style = MaterialTheme.typography.labelLarge) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GuidedRuleFactory.suggestedMatchers(sample)) { suggestion ->
                        val regex = Regex.escape(suggestion)
                        val active = matchers.any { it.variableKey.isBlank() && it.regex == regex }
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (active) matchers.removeAll { it.variableKey.isBlank() && it.regex == regex }
                                else matchers += GuidedRuleFactory.matcherFromText(suggestion)
                            },
                            label = { Text(suggestion.take(36)) }
                        )
                    }
                }
            }
        }

        if (extractors.any { it.key.isNotBlank() }) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { variableMatcherExpanded = !variableMatcherExpanded }
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Variablen als Profilmerkmal", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Eingeklappt. Wähle bei Bedarf eine oder mehrere Variablen und lege fest, was geprüft werden soll.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Icon(
                                if (variableMatcherExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                null
                            )
                        }
                        if (variableMatcherExpanded) {
                            Text("Variablen auswählen", fontWeight = FontWeight.SemiBold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(extractors.filter { it.key.isNotBlank() }, key = { it.id }) { rule ->
                                    val selected = rule.key in selectedMatcherVariables
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            if (selected) selectedMatcherVariables.remove(rule.key)
                                            else selectedMatcherVariables.add(rule.key)
                                        },
                                        label = { Text(variableLabel(rule.key)) }
                                    )
                                }
                            }

                            Text("Prüfung", fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(variableMatcherMode == MatcherValueMode.EMPTY, { variableMatcherMode = MatcherValueMode.EMPTY })
                                Text("Ist leer")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(variableMatcherMode == MatcherValueMode.NOT_EMPTY, { variableMatcherMode = MatcherValueMode.NOT_EMPTY })
                                Text("Ist nicht leer")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(variableMatcherMode == MatcherValueMode.REGEX, { variableMatcherMode = MatcherValueMode.REGEX })
                                Text("Inhalt prüfen")
                            }

                            if (variableMatcherMode == MatcherValueMode.REGEX) {
                                Text(
                                    "Du musst keinen Regex schreiben. Wähle zuerst die Art der Prüfung und gib anschließend nur den Vergleichswert ein.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item { FilterChip(matcherPatternKind == "contains", { matcherPatternKind = "contains" }, label = { Text("Enthält") }) }
                                    item { FilterChip(matcherPatternKind == "starts", { matcherPatternKind = "starts" }, label = { Text("Beginnt mit") }) }
                                    item { FilterChip(matcherPatternKind == "ends", { matcherPatternKind = "ends" }, label = { Text("Endet mit") }) }
                                    item { FilterChip(matcherPatternKind == "exact", { matcherPatternKind = "exact" }, label = { Text("Exakt") }) }
                                    item { FilterChip(matcherPatternKind == "digits", { matcherPatternKind = "digits" }, label = { Text("Nur Ziffern") }) }
                                    item { FilterChip(matcherPatternKind == "digit_count", { matcherPatternKind = "digit_count" }, label = { Text("Anzahl Ziffern") }) }
                                    item { FilterChip(matcherPatternKind == "custom", { matcherPatternKind = "custom" }, label = { Text("Eigener Regex") }) }
                                }
                                if (matcherPatternKind != "digits") {
                                    OutlinedTextField(
                                        value = matcherPatternValue,
                                        onValueChange = { matcherPatternValue = it },
                                        label = {
                                            Text(
                                                if (matcherPatternKind == "digit_count") "Anzahl der Ziffern"
                                                else if (matcherPatternKind == "custom") "Regex"
                                                else "Vergleichswert"
                                            )
                                        },
                                        supportingText = {
                                            Text(
                                                when (matcherPatternKind) {
                                                    "contains" -> "Beispiel: ABC findet jeden Inhalt, der ABC enthält."
                                                    "starts" -> "Der Inhalt muss mit diesem Text beginnen."
                                                    "ends" -> "Der Inhalt muss mit diesem Text enden."
                                                    "exact" -> "Der komplette Inhalt muss exakt übereinstimmen."
                                                    "digit_count" -> "Beispiel: 5 für eine fünfstellige PLZ."
                                                    "custom" -> "Für Sonderfälle. Regex wird vor dem Speichern geprüft."
                                                    else -> ""
                                                }
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = matcherPatternKind != "custom"
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val regex = when (matcherPatternKind) {
                                        "contains" -> Regex.escape(matcherPatternValue)
                                        "starts" -> "^" + Regex.escape(matcherPatternValue)
                                        "ends" -> Regex.escape(matcherPatternValue) + "$"
                                        "exact" -> "^" + Regex.escape(matcherPatternValue) + "$"
                                        "digits" -> "^\\d+$"
                                        "digit_count" -> matcherPatternValue.toIntOrNull()?.takeIf { it in 1..50 }?.let { "^\\d{" + it + "}$" }.orEmpty()
                                        "custom" -> matcherPatternValue
                                        else -> ""
                                    }
                                    val validRegex = variableMatcherMode != MatcherValueMode.REGEX ||
                                        (regex.isNotBlank() && runCatching { Regex(regex) }.isSuccess)
                                    if (selectedMatcherVariables.isNotEmpty() && validRegex) {
                                        selectedMatcherVariables.toList().forEach { key ->
                                            val oldJoin = matchers.firstOrNull { it.variableKey == key }?.join ?: MatcherJoin.AND
                                            matchers.removeAll { it.variableKey == key }
                                            val text = when (variableMatcherMode) {
                                                MatcherValueMode.EMPTY -> key + " ist leer"
                                                MatcherValueMode.NOT_EMPTY -> key + " ist nicht leer"
                                                MatcherValueMode.REGEX -> key + " erfüllt die Inhaltsprüfung"
                                            }
                                            matchers += MatcherRule(
                                                regex = if (variableMatcherMode == MatcherValueMode.REGEX) regex else "",
                                                ignoreCase = true,
                                                friendlyText = text,
                                                variableKey = key,
                                                join = oldJoin,
                                                valueMode = variableMatcherMode
                                            )
                                        }
                                        selectedMatcherVariables.clear()
                                    } else if (!validRegex) {
                                        validationMessage = "Die gewählte Inhaltsprüfung ist noch unvollständig oder der Regex ist ungültig."
                                    }
                                },
                                enabled = selectedMatcherVariables.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Prüfung übernehmen") }
                        }
                    }
                }
            }
        }

        if (matchers.isNotEmpty()) {
            item { Text("Aktive Merkmale", style = MaterialTheme.typography.labelLarge) }
            itemsIndexed(matchers, key = { index, matcher -> matcher.variableKey + "-" + matcher.regex + "-" + matcher.valueMode + "-" + index }) { index, matcher ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (index > 0) {
                            OutlinedButton(
                                onClick = {
                                    val currentIndex = matchers.indexOf(matcher)
                                    if (currentIndex >= 0) {
                                        matchers[currentIndex] = matcher.copy(
                                            join = if (matcher.join == MatcherJoin.AND) MatcherJoin.OR else MatcherJoin.AND
                                        )
                                    }
                                }
                            ) { Text(if (matcher.join == MatcherJoin.AND) "UND" else "ODER") }
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(matcher.friendlyText.ifBlank { matcher.regex }, modifier = Modifier.weight(1f))
                        IconButton(onClick = { matchers.remove(matcher) }) {
                            Icon(Icons.Outlined.Delete, "Merkmal entfernen")
                        }
                    }
                }
            }
        } else {
            item { Text("Noch kein Merkmal. Ohne Merkmale dient dieses Profil nur als Fallback.", style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customMatcher,
                    onValueChange = { customMatcher = it },
                    label = { Text("Fester Text als Merkmal") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (customMatcher.isNotBlank()) {
                        matchers += GuidedRuleFactory.matcherFromText(customMatcher)
                        customMatcher = ""
                    }
                }) { Text("Hinzufügen") }
            }
        }

        if (sample != null) {
            item { HorizontalDivider() }
            item { SectionTitle("Variablen aus dem Beispiel") }
            item { Text("Markiere einen veränderlichen Wert oben und tippe auf „Als Variable“. Bereits definierte Variablen werden im Beispiel farbig markiert. Beispielwerte lassen sich kopieren und anschließend in Umwandlungen verwenden.") }
            items(GuidedRuleFactory.candidates(sample).filter { it.source == InputSource.TEXT }.take(30)) { candidate ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(candidate.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                candidate.value,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.clickable { clipboard.setText(AnnotatedString(candidate.value)) }
                            )
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(candidate.value)) }) {
                            Icon(Icons.Outlined.ContentCopy, "Beispielwert kopieren")
                        }
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
                    extractors += ExtractorRule(key = "", regex = "(.+)", required = false)
                }) {
                    Icon(Icons.Outlined.Add, null)
                    Text("Manuell")
                }
            }
        }
        items(extractors, key = { it.id }) { rule ->
            val index = extractors.indexOfFirst { it.id == rule.id }
            val previewRules = if (index >= 0) extractors.take(index + 1) else listOf(rule)
            val availableSourceVariables = buildList {
                addAll(reservedVariables)
                if (index > 0) addAll(extractors.take(index).map { it.key }.filter { it.isNotBlank() })
            }.distinct()
            ExtractorCard(
                rule = rule,
                sample = sample,
                parser = parser,
                parseDirection = parseDirection,
                previewRules = previewRules,
                availableSourceVariables = availableSourceVariables,
                highlighted = highlightField == rule.key,
                advanced = advanced,
                onChange = { changed ->
                    if (index >= 0) {
                        val oldKey = extractors[index].key
                        extractors[index] = changed
                        if (oldKey != changed.key) {
                            for (i in matchers.indices) {
                                if (matchers[i].variableKey == oldKey) {
                                    val matcher = matchers[i]
                                    matchers[i] = matcher.copy(
                                        variableKey = changed.key,
                                        friendlyText = when (matcher.valueMode) {
                                            MatcherValueMode.EMPTY -> changed.key + " ist leer"
                                            MatcherValueMode.NOT_EMPTY -> changed.key + " ist nicht leer"
                                            MatcherValueMode.REGEX -> changed.key + " erfüllt die Inhaltsprüfung"
                                        }
                                    )
                                }
                            }
                            for (i in (index + 1) until extractors.size) {
                                if (extractors[i].sourceVariableKey == oldKey) {
                                    extractors[i] = extractors[i].copy(sourceVariableKey = changed.key)
                                }
                            }
                        }
                    }
                },
                onSplit = { firstKey, secondKey, separator ->
                    if (index >= 0 && rule.key.isNotBlank()) {
                        val splitRegex = if (separator.isBlank()) {
                            "^\\s*(\\S+)\\s+(.+?)\\s*$"
                        } else {
                            "^\\s*(.*?)\\s*${Regex.escape(separator)}\\s*(.+?)\\s*$"
                        }
                        val first = ExtractorRule(
                            key = firstKey,
                            regex = splitRegex,
                            group = 1,
                            required = rule.required,
                            sourceVariableKey = rule.key,
                            transforms = listOf(ValueTransform.Trim)
                        )
                        val second = ExtractorRule(
                            key = secondKey,
                            regex = splitRegex,
                            group = 2,
                            required = rule.required,
                            sourceVariableKey = rule.key,
                            transforms = listOf(ValueTransform.Trim)
                        )
                        extractors.add(index + 1, first)
                        extractors.add(index + 2, second)
                    }
                },
                onDelete = {
                    if (index >= 0) {
                        val key = extractors[index].key
                        extractors.removeAt(index)
                        matchers.removeAll { it.variableKey == key }
                        for (i in extractors.indices) {
                            if (extractors[i].sourceVariableKey == key) {
                                extractors[i] = extractors[i].copy(sourceVariableKey = "")
                            }
                        }
                    }
                }
            )
        }

        item { HorizontalDivider() }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Weiterverarbeitung", Modifier.weight(1f))
                Column {
                    TextButton(onClick = { addActionMenu = true }) {
                        Icon(Icons.Outlined.Add, null)
                        Text("Aktion")
                    }
                    DropdownMenu(expanded = addActionMenu, onDismissRequest = { addActionMenu = false }) {
                        DropdownMenuItem(text = { Text("Kalendereintrag") }, onClick = { actions += defaultCalendarAction(); addActionMenu = false })
                        DropdownMenuItem(text = { Text("URL öffnen") }, onClick = { actions += defaultUrlAction(); addActionMenu = false })
                        DropdownMenuItem(text = { Text("Text oder Textdatei") }, onClick = { actions += defaultShareAction(); addActionMenu = false })
                        DropdownMenuItem(text = { Text("Webhook") }, onClick = { actions += defaultWebhookAction(); addActionMenu = false })
                    }
                }
            }
        }
        val variables = listOf("subject", "text", "input", "source_app", "source_package", "file_name", "mime_type") + extractors.map { it.key }.filter { it.isNotBlank() }
        items(actions, key = { it.id }) { action ->
            val index = actions.indexOfFirst { it.id == action.id }
            ActionEditorCard(
                action = action,
                variables = variables,
                highlighted = highlightField?.startsWith(actionHighlightPrefix(action)) == true || highlightField == action.id,
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
                            val decoded = repository.decodeBundle(advancedJson).copy(id = profileId)
                            validate(decoded)?.let { error(it) }
                            restoreProfile(decoded)
                        }.onSuccess {
                            validationMessage = "JSON übernommen. Speichere das Profil, um die Änderungen anzuwenden."
                        }.onFailure { validationMessage = "JSON konnte nicht angewendet werden: ${it.message}" }
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
                            runCatching {
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
                            }.onFailure { validationMessage = "Teilen fehlgeschlagen: ${it.message}" }
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
                TextButton(onClick = { repository.delete(existing.id); onDeleted() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Delete, null)
                    Text("Profil löschen")
                }
            }
        }
        item { Spacer(Modifier.height(36.dp)) }
    }
}

private data class VariableConflict(
    val proposed: ExtractorRule,
    val index: Int?
)

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gib der Information einen Namen. Das Feld darf beim Bearbeiten vollständig geleert werden.")
                OutlinedTextField(
                    value = currentName,
                    onValueChange = { onNameChange(GuidedRuleFactory.sanitizeKey(it)) },
                    label = { Text("Variablenname") },
                    placeholder = { Text(suggestedName) },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(required, onRequiredChange)
                    Text("Pflichtfeld")
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = currentName.isNotBlank()) { Text("Variable erstellen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun SplitVariableDialog(
    sourceKey: String,
    preview: String,
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sourceLower = sourceKey.lowercase()
    val firstSuggested = if (sourceLower.contains("plz") && sourceLower.contains("ort")) "PLZ" else "${sourceKey}_1"
    val secondSuggested = if (sourceLower.contains("plz") && sourceLower.contains("ort")) "Ort" else "${sourceKey}_2"
    var firstKey by remember(sourceKey) { mutableStateOf(GuidedRuleFactory.sanitizeKey(firstSuggested)) }
    var secondKey by remember(sourceKey) { mutableStateOf(GuidedRuleFactory.sanitizeKey(secondSuggested)) }
    var separator by remember(sourceKey) { mutableStateOf(" ") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${variableLabel(sourceKey)} aufteilen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (preview.isNotBlank()) Text("Beispiel: $preview", style = MaterialTheme.typography.bodySmall)
                Text("Ein leeres Trennzeichen oder ein Leerzeichen teilt nach dem ersten Wort. Für andere Werte kannst du z. B. „,“, „-“ oder „/“ verwenden.")
                OutlinedTextField(
                    value = separator,
                    onValueChange = { separator = it },
                    label = { Text("Trennzeichen") },
                    placeholder = { Text("Leerzeichen") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = firstKey,
                    onValueChange = { firstKey = GuidedRuleFactory.sanitizeKey(it) },
                    label = { Text("Erste neue Variable") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = secondKey,
                    onValueChange = { secondKey = GuidedRuleFactory.sanitizeKey(it) },
                    label = { Text("Zweite neue Variable") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(firstKey, secondKey, separator); onDismiss() },
                enabled = firstKey.isNotBlank() && secondKey.isNotBlank() && firstKey != secondKey
            ) { Text("Aufteilen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun SelectionSourceCard(
    title: String,
    fixedText: String,
    value: TextFieldValue,
    source: InputSource,
    extractors: List<ExtractorRule>,
    onValueChange: (TextFieldValue) -> Unit,
    onVariable: () -> Unit,
    onMatcher: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val selected = selectedText(fixedText, value.selection)
    var expanded by remember(title) { mutableStateOf(false) }
    val (visualTransformation, highlights) = rememberVariableHighlighting(source, extractors)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, if (expanded) "Textfeld verkleinern" else "Textfeld maximieren")
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(fixedText)) }) {
                    Icon(Icons.Outlined.ContentCopy, "Text kopieren")
                }
            }
            Text("Text gedrückt halten und einen Bereich markieren. Der Text ist kopierbar. Bereits erkannte Variablen sind farbig hinterlegt.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = true,
                visualTransformation = visualTransformation,
                modifier = Modifier.fillMaxWidth(),
                minLines = if (expanded) 18 else if (title == "Nachrichtentext" || title == "Dateiinhalt") 5 else 1,
                maxLines = if (expanded) 32 else if (title == "Nachrichtentext" || title == "Dateiinhalt") 14 else 3
            )
            if (highlights.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(highlights, key = { it.key }) { highlight ->
                        AssistChip(
                            onClick = { },
                            label = { Text(variableLabel(highlight.key)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = highlight.color)
                        )
                    }
                }
            }
            if (selected.isNotBlank()) Text("Markiert: $selected", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onVariable, enabled = selected.isNotBlank()) { Text("Als Variable") }
                OutlinedButton(onClick = onMatcher, enabled = selected.isNotBlank()) { Text("Als Profilmerkmal") }
            }
        }
    }
}

@Composable
private fun ExtractorCard(
    rule: ExtractorRule,
    sample: SharedPayload?,
    parser: ParserEngine,
    parseDirection: ParseDirection,
    previewRules: List<ExtractorRule>,
    availableSourceVariables: List<String>,
    highlighted: Boolean,
    advanced: Boolean,
    onChange: (ExtractorRule) -> Unit,
    onSplit: (String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var details by remember(rule.id) { mutableStateOf(false) }
    var transformMenu by remember { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf(false) }
    var splitDialog by remember { mutableStateOf(false) }
    val preview = remember(rule, sample, parseDirection, previewRules) {
        sample?.let {
            val safePreviewRules = previewRules.map { previewRule ->
                if (previewRule.id == rule.id) rule.copy(required = false) else previewRule.copy(required = false)
            }
            runCatching {
                parser.extract(
                    it,
                    Profile("preview", "preview", extractors = safePreviewRules, parseDirection = parseDirection)
                )[rule.key]
            }.getOrNull()
        }
    }
    val border = if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp)) else Modifier

    if (splitDialog) {
        SplitVariableDialog(
            sourceKey = rule.key,
            preview = preview.orEmpty(),
            onConfirm = onSplit,
            onDismiss = { splitDialog = false }
        )
    }

    Card(border.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.key.ifBlank { "Unbenannte Variable" }, fontWeight = FontWeight.SemiBold)
                    if (preview != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Beispielwert: $preview",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { clipboard.setText(AnnotatedString(preview)) }
                            )
                            IconButton(onClick = { clipboard.setText(AnnotatedString(preview)) }) {
                                Icon(Icons.Outlined.ContentCopy, "Beispielwert kopieren")
                            }
                        }
                    } else if (sample != null) {
                        Text("Im Beispiel nicht erkannt", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
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
                Checkbox(rule.required, { onChange(rule.copy(required = it)) })
                Text("Pflichtfeld")
                Spacer(Modifier.weight(1f))
                if (rule.key.isNotBlank()) {
                    TextButton(onClick = { splitDialog = true }) {
                        Icon(Icons.Outlined.Splitscreen, null)
                        Text("Aufteilen")
                    }
                }
                TextButton(onClick = { details = !details }) {
                    Text(if (details) "Details ausblenden" else "Bausteine")
                    Icon(Icons.Outlined.ExpandMore, null)
                }
            }
            if (details || advanced) {
                OutlinedButton(onClick = { sourceMenu = true }) {
                    Text(
                        if (rule.sourceVariableKey.isBlank()) "Quelle: ${sourceLabel(rule.source)}"
                        else "Quelle: ${variableLabel(rule.sourceVariableKey)}"
                    )
                }
                DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                    InputSource.entries.forEach { sourceChoice ->
                        DropdownMenuItem(
                            text = { Text(sourceLabel(sourceChoice)) },
                            onClick = {
                                onChange(rule.copy(source = sourceChoice, sourceVariableKey = ""))
                                sourceMenu = false
                            }
                        )
                    }
                    availableSourceVariables.filter { it.isNotBlank() }.forEach { variable ->
                        DropdownMenuItem(
                            text = { Text("Variable: ${variableLabel(variable)}") },
                            onClick = {
                                onChange(rule.copy(sourceVariableKey = variable))
                                sourceMenu = false
                            }
                        )
                    }
                }
                if (rule.sourceVariableKey.isNotBlank()) {
                    Text(
                        "Diese Variable wird aus '${variableLabel(rule.sourceVariableKey)}' abgeleitet. So kannst du z. B. PLZ und Ort aus einem gemeinsamen Wert erzeugen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                rule.transforms.forEachIndexed { index, transform ->
                    TransformEditor(
                        transform = transform,
                        advanced = advanced,
                        onChange = { changed -> onChange(rule.copy(transforms = rule.transforms.toMutableList().apply { this[index] = changed })) },
                        onDelete = { onChange(rule.copy(transforms = rule.transforms.toMutableList().apply { removeAt(index) })) }
                    )
                }
                TextButton(onClick = { transformMenu = true }) { Icon(Icons.Outlined.Add, null); Text("Umwandlung hinzufügen") }
                DropdownMenu(expanded = transformMenu, onDismissRequest = { transformMenu = false }) {
                    DropdownMenuItem(text = { Text("Leerzeichen am Rand entfernen") }, onClick = { onChange(rule.copy(transforms = rule.transforms + ValueTransform.Trim)); transformMenu = false })
                    DropdownMenuItem(text = { Text("Textteil entfernen oder ersetzen") }, onClick = { onChange(rule.copy(transforms = rule.transforms + ValueTransform.RegexReplace("", "", literal = true))); transformMenu = false })
                    DropdownMenuItem(text = { Text("Text davor setzen") }, onClick = { onChange(rule.copy(transforms = rule.transforms + ValueTransform.Prefix(""))); transformMenu = false })
                    DropdownMenuItem(text = { Text("Text danach setzen") }, onClick = { onChange(rule.copy(transforms = rule.transforms + ValueTransform.Suffix(""))); transformMenu = false })
                    DropdownMenuItem(text = { Text("Kleinschreibung") }, onClick = { onChange(rule.copy(transforms = rule.transforms + ValueTransform.ChangeCase(CaseMode.LOWER))); transformMenu = false })
                    DropdownMenuItem(text = { Text("Großschreibung") }, onClick = { onChange(rule.copy(transforms = rule.transforms + ValueTransform.ChangeCase(CaseMode.UPPER))); transformMenu = false })
                }
            }
            if (advanced) {
                OutlinedTextField(rule.regex, { onChange(rule.copy(regex = it)) }, label = { Text("Regex, erweitert") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(rule.group.toString(), { text -> text.toIntOrNull()?.let { onChange(rule.copy(group = it)) } }, label = { Text("Capture Group") }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TransformEditor(
    transform: ValueTransform,
    advanced: Boolean,
    onChange: (ValueTransform) -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(transformLabel(transform), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Baustein entfernen") }
            }
            when (transform) {
                ValueTransform.Trim -> Text("Entfernt Leerzeichen am Anfang und Ende.", style = MaterialTheme.typography.bodySmall)
                is ValueTransform.Prefix -> OutlinedTextField(transform.value, { onChange(transform.copy(value = it)) }, label = { Text("Text davor") }, modifier = Modifier.fillMaxWidth())
                is ValueTransform.Suffix -> OutlinedTextField(transform.value, { onChange(transform.copy(value = it)) }, label = { Text("Text danach") }, modifier = Modifier.fillMaxWidth())
                is ValueTransform.ChangeCase -> Text(if (transform.mode == CaseMode.LOWER) "In Kleinschreibung umwandeln" else "In Großschreibung umwandeln")
                is ValueTransform.RegexReplace -> {
                    OutlinedTextField(
                        transform.regex,
                        { onChange(transform.copy(regex = it)) },
                        label = { Text(if (transform.literal) "Text, der entfernt/ersetzt wird" else "Regulärer Ausdruck") },
                        supportingText = { Text(if (transform.literal) "Sonderzeichen wie ( ) [ ] . * werden wörtlich behandelt." else "Erweiterte Regex-Syntax ist aktiv.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(transform.replacement, { onChange(transform.copy(replacement = it)) }, label = { Text("Ersetzen durch, leer = entfernen") }, modifier = Modifier.fillMaxWidth())
                    if (advanced) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(transform.literal, { onChange(transform.copy(literal = it)) })
                            Text("Eingabe wörtlich behandeln")
                        }
                    }
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
    var expanded by remember(action.id) { mutableStateOf(highlighted) }
    val border = if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp)) else Modifier
    Card(border.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
            ) {
                Icon(actionIcon(action.icon), null)
                Spacer(Modifier.width(8.dp))
                Text(action.friendlyName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Aktion entfernen") }
            }
            if (expanded) {
                OutlinedTextField(
                    action.friendlyName,
                    { onChange(withFriendlyName(action, it)) },
                    label = { Text("Anzeigename") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(onClick = { iconMenu = true }) {
                    Icon(actionIcon(action.icon), "Icon auswählen")
                }
                DropdownMenu(expanded = iconMenu, onDismissRequest = { iconMenu = false }) {
                    actionIcons.chunked(6).forEach { choices ->
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                            choices.forEach { choice ->
                                IconButton(onClick = {
                                    onChange(withIcon(action, choice.id))
                                    iconMenu = false
                                }) {
                                    Icon(choice.vector, choice.label)
                                }
                            }
                        }
                    }
                }
                when (action) {
                    is ProcessingAction.Calendar -> CalendarActionFields(action, variables, onChange)
                    is ProcessingAction.Url -> UrlActionFields(action, variables, onChange)
                    is ProcessingAction.Share -> ShareActionFields(action, variables, onChange)
                    is ProcessingAction.Webhook -> WebhookActionFields(action, variables, onChange)
                }
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
    val context = LocalContext.current
    val writePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onChange(action.copy(targetMode = CalendarTargetMode.DIRECT_SAVE))
    }

    Text("Kalenderfelder", fontWeight = FontWeight.SemiBold)
    TemplateField("Titel", action.titleTemplate, variables) { onChange(action.copy(titleTemplate = it)) }
    TemplateField("Beschreibung", action.descriptionTemplate, variables, minLines = 3) { onChange(action.copy(descriptionTemplate = it)) }
    TemplateField("Ort", action.locationTemplate, variables) { onChange(action.copy(locationTemplate = it)) }
    TemplateField("Beginn oder Zeitraum", action.startTemplate, variables, placeholder = "z. B. {{datum}} {{zeit}} oder morgen 12-14") { onChange(action.copy(startTemplate = it, startPattern = "")) }
    TemplateField("Ende, optional", action.endTemplate, variables, placeholder = "Nur nötig, wenn der Beginn keinen Zeitraum enthält") { onChange(action.copy(endTemplate = it, endPattern = "")) }
    TemplateField("Dauer, optional", action.durationTemplate, variables, placeholder = "z. B. 1,5h, 2h, 90 Minuten, eine Stunde") { onChange(action.copy(durationTemplate = it)) }
    CalendarPickerField(action = action, onChange = { onChange(it) })

    Text("Zielkalender-Verhalten", fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(action.targetMode == CalendarTargetMode.APP_EDITOR, { onChange(action.copy(targetMode = CalendarTargetMode.APP_EDITOR)) })
        Column {
            Text("Kalender-App vorausfüllen")
            Text("Ohne Schreibzugriff. Die Kalender-App kann die Zielkalender-Vorgabe ignorieren.", style = MaterialTheme.typography.bodySmall)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            action.targetMode == CalendarTargetMode.DIRECT_SAVE,
            {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                    onChange(action.copy(targetMode = CalendarTargetMode.DIRECT_SAVE))
                } else {
                    writePermissionLauncher.launch(Manifest.permission.WRITE_CALENDAR)
                }
            }
        )
        Column {
            Text("Zielkalender verbindlich")
            Text("Speichert den Termin direkt im ausgewählten Kalender und öffnet ihn danach zum Bearbeiten. Benötigt Kalender-Schreibzugriff.", style = MaterialTheme.typography.bodySmall)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(action.allDay, { onChange(action.copy(allDay = it)) })
        Text("Ganztägig")
    }
}

@Composable
private fun CalendarPickerField(
    action: ProcessingAction.Calendar,
    onChange: (ProcessingAction.Calendar) -> Unit
) {
    val context = LocalContext.current
    var choices by remember { mutableStateOf(CalendarCatalog.list(context)) }
    var menu by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            choices = CalendarCatalog.list(context)
            menu = true
        }
    }

    Text("Zielkalender, optional", fontWeight = FontWeight.SemiBold)
    OutlinedButton(
        onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                choices = CalendarCatalog.list(context)
                menu = true
            } else launcher.launch(Manifest.permission.READ_CALENDAR)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(action.calendarNameTemplate.ifBlank { "Kalender auswählen" })
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(text = { Text("Kein fester Zielkalender") }, onClick = {
            onChange(action.copy(calendarId = null, calendarNameTemplate = "", targetMode = CalendarTargetMode.APP_EDITOR)); menu = false
        })
        choices.forEach { choice ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(choice.displayName)
                        if (choice.accountName.isNotBlank()) Text(choice.accountName, style = MaterialTheme.typography.bodySmall)
                    }
                },
                onClick = {
                    onChange(action.copy(calendarId = choice.id, calendarNameTemplate = choice.displayName))
                    menu = false
                }
            )
        }
    }
    Text(
        if (action.targetMode == CalendarTargetMode.DIRECT_SAVE)
            "Im verbindlichen Modus nutzt ShareParser diese lokale Kalender-ID direkt."
        else "Beim normalen Android-Kalender-Intent ist die Kalender-ID nur eine Empfehlung an die Kalender-App.",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun UrlActionFields(action: ProcessingAction.Url, variables: List<String>, onChange: (ProcessingAction) -> Unit) {
    TemplateField("Link", action.urlTemplate, variables, placeholder = "https://example.com/?id={{id|url}}", minLines = 2, urlEncodeVariables = true) {
        onChange(action.copy(urlTemplate = it))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(action.openMode == UrlOpenMode.BROWSER, { onChange(action.copy(openMode = UrlOpenMode.BROWSER)) })
        Text("Standard-Browser")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(action.openMode == UrlOpenMode.WEBVIEW, { onChange(action.copy(openMode = UrlOpenMode.WEBVIEW)) })
        Text("In ShareParser öffnen")
    }
}

@Composable
private fun ShareActionFields(action: ProcessingAction.Share, variables: List<String>, onChange: (ProcessingAction) -> Unit) {
    TemplateField("Betreff", action.subjectTemplate, variables) { onChange(action.copy(subjectTemplate = it)) }
    TemplateField("Nachricht", action.textTemplate, variables, minLines = 4) { onChange(action.copy(textTemplate = it)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(action.asFile, { onChange(action.copy(asFile = it)) })
        Column {
            Text("Als Textdatei ausgeben")
            Text("Erzeugt aus dem transformierten Text eine Datei statt nur normalen Android-Text.", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (action.asFile) {
        val shownExtension = action.fileExtension.ifBlank { inferEditorExtension(action.fileNameTemplate, action.mimeType) }
        OutlinedTextField(
            value = shownExtension,
            onValueChange = { onChange(action.copy(fileExtension = it.removePrefix("."))) },
            label = { Text("Dateiendung") },
            placeholder = { Text("z. B. txt, md, html, json") },
            supportingText = {
                if (!isKnownTextExtension(shownExtension)) {
                    Text("Unbekannte Endung. Die Datei wird trotzdem als Textdatei mit dieser Endung erzeugt.")
                } else {
                    Text("ShareParser wählt den passenden Text-Inhaltstyp automatisch.")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        TemplateField(
            "Dateiname",
            action.fileNameTemplate,
            variables,
            placeholder = "z. B. {{datum}}-{{ort}}"
        ) { onChange(action.copy(fileNameTemplate = it)) }
        TemplateField(
            "Unterordner, optional",
            action.relativePathTemplate,
            variables,
            placeholder = "z. B. Termine/{{jahr}}"
        ) { onChange(action.copy(relativePathTemplate = it)) }
        Text("Leere oder ungültige Dateifelder", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(action.emptyValuePolicy == EmptyValuePolicy.FALLBACK, { onChange(action.copy(emptyValuePolicy = EmptyValuePolicy.FALLBACK)) })
            Text("Fallback verwenden")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(action.emptyValuePolicy == EmptyValuePolicy.ERROR, { onChange(action.copy(emptyValuePolicy = EmptyValuePolicy.ERROR)) })
            Text("Fehler melden und Aktion abbrechen")
        }
        if (action.emptyValuePolicy == EmptyValuePolicy.FALLBACK) {
            OutlinedTextField(
                value = action.fallbackFileName,
                onValueChange = { onChange(action.copy(fallbackFileName = it)) },
                label = { Text("Fallback-Dateiname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = action.fallbackPath,
                onValueChange = { onChange(action.copy(fallbackPath = it)) },
                label = { Text("Fallback-Unterordner, optional") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        Text("Datei verwenden", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(action.fileMode == TextFileMode.SHARE, { onChange(action.copy(fileMode = TextFileMode.SHARE)) })
            Column {
                Text("Teilen")
                Text("Öffnet den Android-Teilen-Dialog mit der erzeugten Datei.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(action.fileMode == TextFileMode.OPEN, { onChange(action.copy(fileMode = TextFileMode.OPEN)) })
            Column {
                Text("Direkt öffnen")
                Text("Öffnet die Datei in einer passenden App, z. B. Markdown- oder HTML-Viewer.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(action.fileMode == TextFileMode.SAVE, { onChange(action.copy(fileMode = TextFileMode.SAVE)) })
            Column {
                Text("Im Dateisystem speichern")
                Text("Nutzt den voreingestellten Ordner aus den Einstellungen. Ohne Voreinstellung erscheint der Android-Dateidialog. Der Unterordner kann Variablen enthalten.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WebhookActionFields(action: ProcessingAction.Webhook, variables: List<String>, onChange: (ProcessingAction) -> Unit) {
    Text("Webhook", fontWeight = FontWeight.SemiBold)
    TemplateField("URL", action.urlTemplate, variables, placeholder = "https://example.com/webhook", minLines = 2, urlEncodeVariables = true) { onChange(action.copy(urlTemplate = it)) }
    TemplateField("POST-Inhalt", action.bodyTemplate, variables, placeholder = "{\"text\":\"{{text}}\"}", minLines = 5) { onChange(action.copy(bodyTemplate = it)) }
    OutlinedTextField(action.contentType, { onChange(action.copy(contentType = it)) }, label = { Text("Content-Type") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Text("Ausführung", fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(action.mode == WebhookMode.ON_SELECTION, { onChange(action.copy(mode = WebhookMode.ON_SELECTION)) })
        Column { Text("Nur bei Auswahl dieser Aktion"); Text("Der Webhook erscheint wie jede andere Aktion in der Auswahl.", style = MaterialTheme.typography.bodySmall) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(action.mode == WebhookMode.ALWAYS, { onChange(action.copy(mode = WebhookMode.ALWAYS)) })
        Column { Text("Immer senden"); Text("Feuert automatisch, sobald dieses Profil zu einem geteilten Inhalt passt.", style = MaterialTheme.typography.bodySmall) }
    }
    Text("Leerer POST-Inhalt", fontWeight = FontWeight.SemiBold)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(action.emptyValuePolicy == EmptyValuePolicy.FALLBACK, { onChange(action.copy(emptyValuePolicy = EmptyValuePolicy.FALLBACK)) })
        Text("Fallback verwenden")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(action.emptyValuePolicy == EmptyValuePolicy.ERROR, { onChange(action.copy(emptyValuePolicy = EmptyValuePolicy.ERROR)) })
        Text("Fehler melden")
    }
    if (action.emptyValuePolicy == EmptyValuePolicy.FALLBACK) {
        OutlinedTextField(action.fallbackBody, { onChange(action.copy(fallbackBody = it)) }, label = { Text("Fallback-Inhalt") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    }
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
    var field by remember { mutableStateOf(TextFieldValue(value, selection = TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != field.text) {
            val pos = field.selection.start.coerceAtMost(value.length)
            field = TextFieldValue(value, selection = TextRange(pos))
        }
    }
    val known = variables.toSet()
    val detected = remember(field.text) { TemplateEngine.variables(field.text) }
    val unknown = detected - known

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                onChange(it.text)
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            supportingText = {
                when {
                    unknown.isNotEmpty() -> Text("Unbekannte Variable: ${unknown.joinToString()}")
                    detected.isNotEmpty() -> Text("Erkannte Variablen: ${detected.joinToString { variableLabel(it) }}")
                    else -> Text("Variablen können per Baustein oder direkt als {{name}} geschrieben werden.")
                }
            },
            isError = unknown.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(variables.distinct()) { variable ->
                AssistChip(
                    onClick = {
                        val token = if (urlEncodeVariables) "{{${variable}|url}}" else "{{${variable}}}"
                        val start = minOf(field.selection.start, field.selection.end).coerceIn(0, field.text.length)
                        val end = maxOf(field.selection.start, field.selection.end).coerceIn(0, field.text.length)
                        val changed = field.text.replaceRange(start, end, token)
                        val cursor = start + token.length
                        field = TextFieldValue(changed, selection = TextRange(cursor))
                        onChange(changed)
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

private fun selectedText(text: String, selection: TextRange): String {
    if (selection.collapsed) return ""
    val start = minOf(selection.start, selection.end).coerceIn(0, text.length)
    val end = maxOf(selection.start, selection.end).coerceIn(0, text.length)
    return text.substring(start, end)
}

private fun actionTemplates(action: ProcessingAction): List<Pair<String, String>> = when (action) {
    is ProcessingAction.Calendar -> listOf(
        "Kalendertitel" to action.titleTemplate,
        "Beschreibung" to action.descriptionTemplate,
        "Ort" to action.locationTemplate,
        "Beginn" to action.startTemplate,
        "Ende" to action.endTemplate,
        "Dauer" to action.durationTemplate
    )
    is ProcessingAction.Url -> listOf("URL" to action.urlTemplate)
    is ProcessingAction.Share -> listOf(
        "Betreff" to action.subjectTemplate,
        "Nachricht" to action.textTemplate,
        "Dateiname" to action.fileNameTemplate,
        "Unterordner" to action.relativePathTemplate
    )
    is ProcessingAction.Webhook -> listOf(
        "Webhook-URL" to action.urlTemplate,
        "Webhook-Inhalt" to action.bodyTemplate
    )
}

private fun defaultCalendarAction() = ProcessingAction.Calendar(UUID.randomUUID().toString(), "Kalender öffnen")
private fun defaultUrlAction() = ProcessingAction.Url(UUID.randomUUID().toString(), "Link öffnen")
private fun defaultShareAction() = ProcessingAction.Share(UUID.randomUUID().toString(), "Text weiterleiten", fileExtension = "txt")
private fun defaultWebhookAction() = ProcessingAction.Webhook(UUID.randomUUID().toString(), "Webhook senden")

private fun withFriendlyName(action: ProcessingAction, name: String): ProcessingAction = when (action) {
    is ProcessingAction.Calendar -> action.copy(friendlyName = name)
    is ProcessingAction.Url -> action.copy(friendlyName = name)
    is ProcessingAction.Share -> action.copy(friendlyName = name)
    is ProcessingAction.Webhook -> action.copy(friendlyName = name)
}

private fun withIcon(action: ProcessingAction, icon: String): ProcessingAction = when (action) {
    is ProcessingAction.Calendar -> action.copy(icon = icon)
    is ProcessingAction.Url -> action.copy(icon = icon)
    is ProcessingAction.Share -> action.copy(icon = icon)
    is ProcessingAction.Webhook -> action.copy(icon = icon)
}

private fun actionHighlightPrefix(action: ProcessingAction): String = when (action) {
    is ProcessingAction.Calendar -> "calendar"
    is ProcessingAction.Url -> "url"
    is ProcessingAction.Share -> "share"
    is ProcessingAction.Webhook -> "webhook"
}

private fun sourceLabel(source: InputSource): String = when (source) {
    InputSource.COMBINED -> "Betreff + Text"
    InputSource.TEXT -> "Nachrichtentext / Dateiinhalt"
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
    "text" -> "Gesamte Nachricht / Dateiinhalt"
    "input" -> "Betreff + Nachricht"
    "source_app" -> "Teilende App"
    "source_package" -> "Paketname der teilenden App"
    "file_name" -> "Dateiname"
    "mime_type" -> "Inhaltstyp"
    else -> key
}

private fun inferEditorExtension(fileNameTemplate: String, mimeType: String): String {
    val fromName = fileNameTemplate.substringAfterLast('.', "").takeIf { it.isNotBlank() }
    if (fromName != null) return fromName
    return when (mimeType.lowercase()) {
        "text/markdown" -> "md"
        "text/html" -> "html"
        "text/csv" -> "csv"
        "application/json" -> "json"
        "application/xml", "text/xml" -> "xml"
        "application/yaml", "text/yaml" -> "yaml"
        else -> "txt"
    }
}

private fun isKnownTextExtension(value: String): Boolean =
    value.trim().removePrefix(".").lowercase() in setOf(
        "txt", "log", "ini", "conf", "cfg", "md", "markdown", "html", "htm",
        "csv", "tsv", "json", "xml", "yaml", "yml", "css", "js", "mjs", "ics",
        "sql", "kt", "java", "py", "sh"
    )

private fun safeFileName(name: String): String = name
    .trim()
    .replace(Regex("[^a-zA-Z0-9._-]+"), "-")
    .trim('-')
    .ifBlank { "shareparser-profile" }
