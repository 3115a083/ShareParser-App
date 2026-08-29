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
    val navigationScope = rememberCoroutineScope()
    val recognitionAnchor = remember { BringIntoViewRequester() }
    val exampleAnchor = remember { BringIntoViewRequester() }
    val variablesAnchor = remember { BringIntoViewRequester() }
    val actionsAnchor = remember { BringIntoViewRequester() }
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
    var showDeleteDialog by remember(existing?.id) { mutableStateOf(false) }

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
    var sourceAppMenu by remember { mutableStateOf(false) }
    var sourceAppNegated by remember { mutableStateOf(false) }

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

    fun renameVariableReferences(oldKey: String, newKey: String, sourceIndex: Int) {
        if (oldKey.isBlank() || oldKey == newKey) return
        for (i in matchers.indices) {
            if (matchers[i].variableKey == oldKey) {
                val matcher = matchers[i]
                matchers[i] = matcher.copy(
                    variableKey = newKey,
                    friendlyText = when (matcher.valueMode) {
                        MatcherValueMode.EMPTY -> newKey + " ist leer"
                        MatcherValueMode.NOT_EMPTY -> newKey + " ist nicht leer"
                        MatcherValueMode.REGEX -> newKey + " erfüllt die Inhaltsprüfung"
                    }
                )
            }
        }
        for (i in (sourceIndex + 1) until extractors.size) {
            if (extractors[i].sourceVariableKey == oldKey) {
                extractors[i] = extractors[i].copy(sourceVariableKey = newKey)
            }
        }
    }

    fun applyExtractor(proposed: ExtractorRule, index: Int? = null) {
        val conflictIndex = extractors.indexOfFirst { it.key == proposed.key }
            .takeIf { it >= 0 && it != index }
        if (proposed.key.isNotBlank() && conflictIndex != null) {
            variableConflict = VariableConflict(proposed, index)
            return
        }
        if (index == null) {
            extractors += proposed
        } else if (index in extractors.indices) {
            val oldKey = extractors[index].key
            extractors[index] = proposed
            renameVariableReferences(oldKey, proposed.key, index)
        }
    }

    fun overwriteConflict(conflict: VariableConflict) {
        val target = extractors.indexOfFirst { it.key == conflict.proposed.key }
        val source = conflict.index
        val oldKey = source?.takeIf { it in extractors.indices }?.let { extractors[it].key }.orEmpty()
        var finalIndex = source ?: target
        when {
            target < 0 && source == null -> {
                extractors += conflict.proposed
                finalIndex = extractors.lastIndex
            }
            target < 0 && source != null && source in extractors.indices -> extractors[source] = conflict.proposed
            source == null -> extractors[target] = conflict.proposed
            source == target -> extractors[source] = conflict.proposed
            source < target -> {
                extractors[source] = conflict.proposed
                extractors.removeAt(target)
                finalIndex = source
            }
            else -> {
                extractors.removeAt(target)
                val adjusted = source - 1
                if (adjusted in extractors.indices) {
                    extractors[adjusted] = conflict.proposed
                    finalIndex = adjusted
                }
            }
        }
        if (source != null) renameVariableReferences(oldKey, conflict.proposed.key, finalIndex.coerceAtLeast(0))
        variableConflict = null
    }

    fun incrementConflict(conflict: VariableConflict) {
        val changed = conflict.proposed.copy(key = uniqueVariableKey(conflict.proposed.key, conflict.index))
        if (conflict.index == null) {
            extractors += changed
        } else if (conflict.index in extractors.indices) {
            val oldKey = extractors[conflict.index].key
            extractors[conflict.index] = changed
            renameVariableReferences(oldKey, changed.key, conflict.index)
        }
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

    if (showDeleteDialog && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Profil löschen?") },
            text = { Text("Das Profil '${existing.name}' wird dauerhaft gelöscht.") },
            confirmButton = {
                Button(onClick = {
                    repository.delete(existing.id)
                    showDeleteDialog = false
                    onDeleted()
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") }
            }
        )
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
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(repository.export(buildProfile()))) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.ContentCopy, null)
                    Spacer(Modifier.width(4.dp))
                    Text("JSON kopieren")
                }
                OutlinedButton(
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
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Share, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Teilen")
                }
                OutlinedButton(
                    onClick = {
                        pendingExport = repository.export(buildProfile())
                        exportLauncher.launch("${safeFileName(name.ifBlank { "shareparser-profile" })}.json")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Download, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Speichern")
                }
            }
        }
        if (existing != null) {
            item {
                TextButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
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
    onConfirm: (List<String>, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sourceLower = sourceKey.lowercase()
    val initialKeys = if (sourceLower.contains("plz") && sourceLower.contains("ort")) {
        listOf("PLZ", "Ort")
    } else {
        listOf("${sourceKey}_1", "${sourceKey}_2")
    }
    val keys = remember(sourceKey) {
        mutableStateListOf<String>().apply {
            addAll(initialKeys.map(GuidedRuleFactory::sanitizeKey))
        }
    }
    var separator by remember(sourceKey) { mutableStateOf(" ") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${variableLabel(sourceKey)} aufteilen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (preview.isNotBlank()) Text("Beispiel: $preview", style = MaterialTheme.typography.bodySmall)
                Text("Lege ein Trennzeichen fest und füge so viele Untervariablen hinzu wie benötigt. Bei leerem Trennzeichen wird nach Leerraum getrennt.")
                OutlinedTextField(
                    value = separator,
                    onValueChange = { separator = it },
                    label = { Text("Trennzeichen") },
                    placeholder = { Text("Leerraum") },
                    singleLine = true
                )
                keys.forEachIndexed { index, key ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = key,
                            onValueChange = { keys[index] = GuidedRuleFactory.sanitizeKey(it) },
                            label = { Text("Untervariable ${index + 1}") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (keys.size > 2) {
                            IconButton(onClick = { keys.removeAt(index) }) {
                                Icon(Icons.Outlined.Delete, "Untervariable entfernen")
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { keys += GuidedRuleFactory.sanitizeKey("${sourceKey}_${keys.size + 1}") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Text("Untervariable hinzufügen")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(keys.toList(), separator); onDismiss() },
                enabled = keys.size >= 2 &&
                    keys.all { it.isNotBlank() } &&
                    keys.distinct().size == keys.size
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
    onSplit: (List<String>, String) -> Unit,
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Outlined.ArrowUpward, "Nach oben")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Outlined.ArrowDownward, "Nach unten")
                }
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
        val inferredExtension = inferEditorExtension(action.fileNameTemplate, action.mimeType)
        var extensionField by remember(action.id) { mutableStateOf(action.fileExtension) }
        LaunchedEffect(action.fileExtension) {
            if (action.fileExtension != extensionField && action.fileExtension.isNotBlank()) {
                extensionField = action.fileExtension
            }
        }
        OutlinedTextField(
            value = extensionField,
            onValueChange = {
                extensionField = it.removePrefix(".")
                onChange(action.copy(fileExtension = extensionField))
            },
            label = { Text("Dateiendung") },
            placeholder = { Text(inferredExtension.ifBlank { "txt" }) },
            supportingText = {
                when {
                    extensionField.isBlank() -> Text("Leer lassen übernimmt die bisherige oder Standard-Endung.")
                    !isKnownTextExtension(extensionField) -> Text(
                        "Unbekannte Endung. Die Datei wird trotzdem als Textdatei mit dieser Endung erzeugt.",
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Text("ShareParser wählt den passenden Text-Inhaltstyp automatisch.")
                }
            },
            isError = extensionField.isNotBlank() && !isKnownTextExtension(extensionField),
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
private fun EditorSectionHeader(text: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
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
