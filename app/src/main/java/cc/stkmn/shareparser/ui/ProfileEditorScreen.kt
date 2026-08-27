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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import cc.stkmn.shareparser.data.CaseMode
import cc.stkmn.shareparser.data.ExtractorRule
import cc.stkmn.shareparser.data.InputSource
import cc.stkmn.shareparser.data.MatcherRule
import cc.stkmn.shareparser.data.ProcessingAction
import cc.stkmn.shareparser.data.Profile
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.SharedPayload
import cc.stkmn.shareparser.data.ValueTransform
import cc.stkmn.shareparser.engine.ParserEngine
import java.util.UUID

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
    var matcher by remember(existing?.id) { mutableStateOf(existing?.matchers?.firstOrNull()?.regex.orEmpty()) }
    val extractors = remember(existing?.id) {
        mutableStateListOf<ExtractorRule>().apply { addAll(existing?.extractors.orEmpty()) }
    }
    val actions = remember(existing?.id) {
        mutableStateListOf<ProcessingAction>().apply {
            addAll(existing?.actions ?: listOf(defaultCalendarAction()))
        }
    }
    var advanced by remember { mutableStateOf(false) }
    var advancedJson by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf("") }
    var addActionMenu by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
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
        matchers = matcher.trim().takeIf { it.isNotBlank() }?.let { listOf(MatcherRule(it)) }.orEmpty(),
        extractors = extractors.toList(),
        actions = actions.toList()
    )

    fun validate(profile: Profile): String? {
        if (profile.name.isBlank()) return "Bitte einen Profilnamen eingeben."
        profile.matchers.forEach {
            runCatching { Regex(it.regex) }.getOrElse { error ->
                return "Erkennungs-Regex ist ungültig: ${error.message}"
            }
        }
        val keys = mutableSetOf<String>()
        for (rule in profile.extractors) {
            if (rule.key.isBlank()) return "Jede Extraktion braucht einen Variablennamen."
            if (!keys.add(rule.key)) return "Variablenname '${rule.key}' wird mehrfach verwendet."
            runCatching { Regex(rule.regex) }.getOrElse { error ->
                return "Regex für '${rule.key}' ist ungültig: ${error.message}"
            }
            if (rule.group < 0) return "Gruppe für '${rule.key}' darf nicht negativ sein."
        }
        if (profile.actions.isEmpty()) return "Mindestens eine Weiterverarbeitung hinzufügen."
        return null
    }

    LaunchedEffect(advanced) {
        if (advanced) advancedJson = repository.export(buildProfile())
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

        if (sample != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Beispiel aus der Teilen-Funktion", fontWeight = FontWeight.SemiBold)
                        if (sample.subject.isNotBlank()) {
                            Text("Betreff", style = MaterialTheme.typography.labelMedium)
                            SelectionContainer { Text(sample.subject) }
                        }
                        Text("Text", style = MaterialTheme.typography.labelMedium)
                        SelectionContainer { Text(sample.text, maxLines = 12) }
                        Text(
                            "Nutze den Beispieltext, um Regex-Regeln zu definieren. Die Vorschau unter jedem Feld zeigt sofort das Ergebnis.",
                            style = MaterialTheme.typography.bodySmall
                        )
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
            OutlinedTextField(
                value = matcher,
                onValueChange = { matcher = it },
                label = { Text("Erkennung, Regex optional") },
                supportingText = { Text("Nur passende Texte bieten dieses Profil an. Leer bedeutet: immer verfügbar.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }

        item { HorizontalDivider() }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Extraktion", Modifier.weight(1f))
                TextButton(onClick = {
                    extractors.add(
                        ExtractorRule(
                            key = "field${extractors.size + 1}",
                            regex = "(.+)",
                            required = false
                        )
                    )
                }) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Feld")
                }
            }
        }
        if (extractors.isEmpty()) {
            item {
                Text(
                    "Noch keine Extraktion. Die eingebauten Variablen {{subject}}, {{text}} und {{input}} können trotzdem verwendet werden.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        itemsIndexed(extractors, key = { index, rule -> "${rule.key}-$index" }) { index, rule ->
            ExtractorCard(
                rule = rule,
                sample = sample,
                parser = parser,
                highlighted = highlightField == rule.key,
                onChange = { extractors[index] = it },
                onDelete = { extractors.removeAt(index) }
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
                        DropdownMenuItem(
                            text = { Text("Kalendereintrag") },
                            onClick = { actions.add(defaultCalendarAction()); addActionMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("URL öffnen") },
                            onClick = { actions.add(defaultUrlAction()); addActionMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Text weiterleiten") },
                            onClick = { actions.add(defaultShareAction()); addActionMenu = false }
                        )
                    }
                }
            }
        }
        itemsIndexed(actions, key = { _, action -> action.id }) { index, action ->
            val highlighted = highlightField?.startsWith(actionHighlightPrefix(action)) == true || highlightField == action.id
            ActionEditorCard(
                action = action,
                highlighted = highlighted,
                onChange = { actions[index] = it },
                onDelete = { actions.removeAt(index) }
            )
        }

        item { HorizontalDivider() }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(advanced, { advanced = it })
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Erweiterter Modus")
                    Text("Direkte Bearbeitung des versionierten Profil-JSON", style = MaterialTheme.typography.bodySmall)
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
                    minLines = 12
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            val decoded = repository.decodeBundle(advancedJson).copy(id = existing?.id ?: buildProfile().id)
                            val validationError = validate(decoded)
                            if (validationError != null) kotlin.error(validationError)
                            repository.save(decoded)
                        }.onSuccess { onSaved() }
                            .onFailure { validationMessage = "JSON konnte nicht angewendet werden: ${it.message}" }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("JSON anwenden") }
            }
        }

        validationMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }

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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {
                        val json = repository.export(buildProfile())
                        clipboard.setText(AnnotatedString(json))
                    },
                    label = { Text("JSON kopieren") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }
                )
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

        if (existing != null) {
            item {
                TextButton(
                    onClick = {
                        repository.delete(existing.id)
                        onDeleted()
                    },
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

@Composable
private fun ExtractorCard(
    rule: ExtractorRule,
    sample: SharedPayload?,
    parser: ParserEngine,
    highlighted: Boolean,
    onChange: (ExtractorRule) -> Unit,
    onDelete: () -> Unit
) {
    var transformMenu by remember { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf(false) }
    val borderModifier = if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp)) else Modifier
    val preview = remember(rule, sample) {
        sample?.let {
            runCatching {
                parser.extract(it, Profile("preview", "preview", extractors = listOf(rule.copy(required = false))))[rule.key]
            }.getOrNull()
        }
    }

    Card(borderModifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rule.key.ifBlank { "Extraktion" }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Extraktion entfernen") }
            }
            OutlinedTextField(
                value = rule.key,
                onValueChange = { onChange(rule.copy(key = it.replace(" ", "_"))) },
                label = { Text("Variablenname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Column {
                OutlinedButton(onClick = { sourceMenu = true }) {
                    Text("Quelle: ${sourceLabel(rule.source)}")
                }
                DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                    InputSource.entries.forEach { source ->
                        DropdownMenuItem(
                            text = { Text(sourceLabel(source)) },
                            onClick = { onChange(rule.copy(source = source)); sourceMenu = false }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = rule.regex,
                onValueChange = { onChange(rule.copy(regex = it)) },
                label = { Text("Regex") },
                supportingText = { Text("Der Inhalt der gewählten Capture Group wird übernommen.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            OutlinedTextField(
                value = rule.group.toString(),
                onValueChange = { value -> value.toIntOrNull()?.let { onChange(rule.copy(group = it)) } },
                label = { Text("Capture Group") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = rule.required, onCheckedChange = { onChange(rule.copy(required = it)) })
                Text("Pflichtfeld")
            }
            if (rule.transforms.isNotEmpty()) {
                Text("Bausteine", style = MaterialTheme.typography.labelLarge)
                rule.transforms.forEachIndexed { transformIndex, transform ->
                    TransformEditor(
                        transform = transform,
                        onChange = { changed ->
                            onChange(rule.copy(transforms = rule.transforms.toMutableList().apply { this[transformIndex] = changed }))
                        },
                        onDelete = {
                            onChange(rule.copy(transforms = rule.transforms.toMutableList().apply { removeAt(transformIndex) }))
                        }
                    )
                }
            }
            Column {
                TextButton(onClick = { transformMenu = true }) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Baustein hinzufügen")
                }
                DropdownMenu(expanded = transformMenu, onDismissRequest = { transformMenu = false }) {
                    DropdownMenuItem(text = { Text("Leerzeichen entfernen") }, onClick = {
                        onChange(rule.copy(transforms = rule.transforms + ValueTransform.Trim)); transformMenu = false
                    })
                    DropdownMenuItem(text = { Text("Regex ersetzen/entfernen") }, onClick = {
                        onChange(rule.copy(transforms = rule.transforms + ValueTransform.RegexReplace("", ""))); transformMenu = false
                    })
                    DropdownMenuItem(text = { Text("Text davor") }, onClick = {
                        onChange(rule.copy(transforms = rule.transforms + ValueTransform.Prefix(""))); transformMenu = false
                    })
                    DropdownMenuItem(text = { Text("Text danach") }, onClick = {
                        onChange(rule.copy(transforms = rule.transforms + ValueTransform.Suffix(""))); transformMenu = false
                    })
                    DropdownMenuItem(text = { Text("Kleinschreibung") }, onClick = {
                        onChange(rule.copy(transforms = rule.transforms + ValueTransform.ChangeCase(CaseMode.LOWER))); transformMenu = false
                    })
                    DropdownMenuItem(text = { Text("Großschreibung") }, onClick = {
                        onChange(rule.copy(transforms = rule.transforms + ValueTransform.ChangeCase(CaseMode.UPPER))); transformMenu = false
                    })
                }
            }
            if (sample != null) {
                HorizontalDivider()
                Text("Vorschau", style = MaterialTheme.typography.labelLarge)
                Text(preview ?: "Kein Treffer", color = if (preview == null && rule.required) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
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
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Baustein entfernen") }
            }
            when (transform) {
                ValueTransform.Trim -> Unit
                is ValueTransform.Prefix -> OutlinedTextField(
                    transform.value,
                    { onChange(transform.copy(value = it)) },
                    label = { Text("Text davor") },
                    modifier = Modifier.fillMaxWidth()
                )
                is ValueTransform.Suffix -> OutlinedTextField(
                    transform.value,
                    { onChange(transform.copy(value = it)) },
                    label = { Text("Text danach") },
                    modifier = Modifier.fillMaxWidth()
                )
                is ValueTransform.ChangeCase -> Text(if (transform.mode == CaseMode.LOWER) "In Kleinschreibung umwandeln" else "In Großschreibung umwandeln")
                is ValueTransform.RegexReplace -> {
                    OutlinedTextField(
                        transform.regex,
                        { onChange(transform.copy(regex = it)) },
                        label = { Text("Regex zum Ersetzen") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        transform.replacement,
                        { onChange(transform.copy(replacement = it)) },
                        label = { Text("Ersetzen durch, leer = entfernen") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionEditorCard(
    action: ProcessingAction,
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
                action.friendlyName,
                { actionName -> onChange(withFriendlyName(action, actionName)) },
                label = { Text("Friendly Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Column {
                OutlinedButton(onClick = { iconMenu = true }) {
                    Icon(actionIcon(action.icon), null)
                    Spacer(Modifier.width(6.dp))
                    Text("Icon")
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
                is ProcessingAction.Calendar -> CalendarActionFields(action, onChange)
                is ProcessingAction.Url -> UrlActionFields(action, onChange)
                is ProcessingAction.Share -> ShareActionFields(action, onChange)
            }
        }
    }
}

@Composable
private fun CalendarActionFields(action: ProcessingAction.Calendar, onChange: (ProcessingAction) -> Unit) {
    TemplateField("Titel", action.titleTemplate) { onChange(action.copy(titleTemplate = it)) }
    TemplateField("Beschreibung", action.descriptionTemplate, minLines = 3) { onChange(action.copy(descriptionTemplate = it)) }
    TemplateField("Ort", action.locationTemplate) { onChange(action.copy(locationTemplate = it)) }
    TemplateField("Beginn", action.startTemplate, "z. B. {{date}} {{time}}") { onChange(action.copy(startTemplate = it)) }
    TemplateField("Format Beginn, optional", action.startPattern, "z. B. dd.MM.yyyy HH:mm") { onChange(action.copy(startPattern = it)) }
    TemplateField("Ende", action.endTemplate) { onChange(action.copy(endTemplate = it)) }
    TemplateField("Format Ende, optional", action.endPattern) { onChange(action.copy(endPattern = it)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(action.allDay, { onChange(action.copy(allDay = it)) })
        Text("Ganztägig")
    }
}

@Composable
private fun UrlActionFields(action: ProcessingAction.Url, onChange: (ProcessingAction) -> Unit) {
    TemplateField(
        label = "URL-Template",
        value = action.urlTemplate,
        placeholder = "https://example.com/?id={{booking|url}}",
        minLines = 2
    ) { onChange(action.copy(urlTemplate = it)) }
}

@Composable
private fun ShareActionFields(action: ProcessingAction.Share, onChange: (ProcessingAction) -> Unit) {
    TemplateField("Betreff", action.subjectTemplate) { onChange(action.copy(subjectTemplate = it)) }
    TemplateField("Text", action.textTemplate, minLines = 4) { onChange(action.copy(textTemplate = it)) }
    TemplateField("MIME-Type", action.mimeType, "text/plain") { onChange(action.copy(mimeType = it)) }
}

@Composable
private fun TemplateField(
    label: String,
    value: String,
    placeholder: String = "Variablen als {{name}} einsetzen",
    minLines: Int = 1,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        supportingText = { Text("Unterstützt {{name}}, {{name|url}}, {{name|trim}}, {{name|lower}}, {{name|upper}}") },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines
    )
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
    friendlyName = "URL öffnen"
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
    InputSource.TEXT -> "Text"
    InputSource.SUBJECT -> "Betreff"
}

private fun transformLabel(transform: ValueTransform): String = when (transform) {
    ValueTransform.Trim -> "Leerzeichen entfernen"
    is ValueTransform.RegexReplace -> "Regex ersetzen / entfernen"
    is ValueTransform.Prefix -> "Text davor"
    is ValueTransform.Suffix -> "Text danach"
    is ValueTransform.ChangeCase -> if (transform.mode == CaseMode.LOWER) "Kleinschreibung" else "Großschreibung"
}

private fun safeFileName(name: String): String = name
    .trim()
    .replace(Regex("[^a-zA-Z0-9._-]+"), "-")
    .trim('-')
    .ifBlank { "shareparser-profile" }
