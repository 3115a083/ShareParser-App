package cc.stkmn.shareparser.engine

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import cc.stkmn.shareparser.SaveTextFileActivity
import cc.stkmn.shareparser.WebViewActivity
import cc.stkmn.shareparser.data.AppSettings
import cc.stkmn.shareparser.data.CalendarTargetMode
import cc.stkmn.shareparser.data.DateTimeLocale
import cc.stkmn.shareparser.data.EmptyValuePolicy
import cc.stkmn.shareparser.data.ProcessingAction
import cc.stkmn.shareparser.data.TextFileMode
import cc.stkmn.shareparser.data.UrlOpenMode
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class ActionExecutor(context: Context, private val settings: AppSettings = AppSettings(), private val backgroundMode: Boolean = false) {
    private val appContext = context.applicationContext

    data class ExecutionResult(val warnings: List<String> = emptyList())

    fun execute(action: ProcessingAction, values: Map<String, String>): ExecutionResult = when (action) {
        is ProcessingAction.Calendar -> openCalendar(action, values)
        is ProcessingAction.Url -> openUrl(action, values)
        is ProcessingAction.Share -> shareText(action, values)
        is ProcessingAction.Webhook -> sendWebhook(action, values)
    }

    private fun openCalendar(action: ProcessingAction.Calendar, values: Map<String, String>): ExecutionResult {
        val missing = linkedSetOf<String>()
        val warnings = mutableListOf<String>()
        fun render(template: String): String = if (template.isBlank()) "" else TemplateEngine.renderLenient(template, values) { missing += it }.trim()

        val title = render(action.titleTemplate)
        val description = render(action.descriptionTemplate)
        val location = render(action.locationTemplate)
        val startText = render(action.startTemplate)
        val endText = render(action.endTemplate)
        val durationText = render(action.durationTemplate)
        val calendarName = render(action.calendarNameTemplate)

        val intent = Intent(Intent.ACTION_INSERT)
            .setDataAndType(CalendarContract.Events.CONTENT_URI, "vnd.android.cursor.dir/event")
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, action.allDay)

        if (action.startPattern.isNotBlank()) {
            if (startText.isBlank()) {
                warnings += "Beginn konnte nicht vorausgefüllt werden. Bitte im Kalender manuell ergänzen."
            } else {
                runCatching { parseExplicit(startText, action.startPattern) }
                    .onSuccess { intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                    .onFailure { warnings += "Beginn '$startText' passt nicht zum eingestellten Format und muss manuell geprüft werden." }
            }
            if (endText.isNotBlank()) {
                val endPattern = action.endPattern.ifBlank { action.startPattern }
                runCatching { parseExplicit(endText, endPattern) }
                    .onSuccess { intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
                    .onFailure { warnings += "Ende '$endText' konnte nicht gelesen werden und muss manuell geprüft werden." }
            }
        } else {
            val parsed = FlexibleDateTimeParser.parse(
                startValue = startText,
                endValue = endText,
                durationValue = durationText,
                allDay = action.allDay,
                settings = settings
            )
            parsed.startEpochMs?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }

            if (parsed.recurrenceStartEpochMs.isNotEmpty() && parsed.startEpochMs != null) {
                val occurrenceDuration = parsed.durationMillis
                    ?: parsed.endEpochMs?.let { it - parsed.startEpochMs }.takeIf { (it ?: 0L) > 0L }
                    ?: if (action.allDay) 24L * 60L * 60L * 1000L else null
                if (occurrenceDuration != null) {
                    intent.putExtra(CalendarContract.Events.DURATION, toRfc2445Duration(occurrenceDuration))
                    intent.putExtra(
                        CalendarContract.Events.RDATE,
                        parsed.recurrenceStartEpochMs.joinToString(",") { toRfc2445DateTime(it) }
                    )
                } else {
                    warnings += "Mehrere Termine wurden erkannt, aber ohne Dauer kann Android die Wiederholung nicht vollständig vorausfüllen."
                    parsed.endEpochMs?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
                }
            } else {
                parsed.endEpochMs?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            }
            warnings += parsed.warnings
        }

        val selectedCalendarId = when {
            action.calendarId != null -> validateCalendarId(action.calendarId, calendarName, warnings)
            calendarName.isNotBlank() -> resolveCalendarId(calendarName, warnings)
            else -> null
        }
        selectedCalendarId?.let { intent.putExtra(CalendarContract.Events.CALENDAR_ID, it) }

        if (missing.isNotEmpty()) {
            warnings += "Nicht erkannte Variablen: ${missing.joinToString(", ")}. Bitte diese Angaben im Kalender manuell ergänzen."
        }
        if (title.isBlank()) warnings += "Titel ist leer. Bitte den Termin-Titel im Kalender prüfen."

        if (action.targetMode == CalendarTargetMode.DIRECT_SAVE) {
            return saveToSelectedCalendar(
                action = action,
                intent = intent,
                selectedCalendarId = selectedCalendarId,
                calendarName = calendarName,
                warnings = warnings
            )
        }

        if (selectedCalendarId != null) {
            warnings += "Android erlaubt Kalender-Apps, die Zielkalender-Vorgabe beim Öffnen zu ignorieren. Nutze 'Zielkalender verbindlich', wenn der Kalender garantiert stimmen muss."
        }

        try {
            launch(intent, "calendar", "Kalender konnte nicht geöffnet werden")
        } catch (e: ProcessingException) {
            if (!e.technicalDetails.contains("ActivityNotFoundException")) throw e

            val fallback = Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI)
            try {
                launch(fallback, "calendar", "Kalender konnte nicht geöffnet werden")
                warnings += "Deine Kalender-App unterstützt das Vorausfüllen über Android nicht. Der Kalender wurde geöffnet, der Termin muss manuell angelegt werden."
            } catch (_: ProcessingException) {
                throw e
            }
        }
        return ExecutionResult(warnings.distinct())
    }

    private fun saveToSelectedCalendar(
        action: ProcessingAction.Calendar,
        intent: Intent,
        selectedCalendarId: Long?,
        calendarName: String,
        warnings: MutableList<String>
    ): ExecutionResult {
        val calendarId = selectedCalendarId ?: throw ProcessingException(
            "Für den verbindlichen Modus muss ein verfügbarer Zielkalender ausgewählt sein.",
            "calendar",
            "DIRECT_SAVE requested without a valid calendar id"
        )
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            throw ProcessingException(
                "Für den verbindlichen Zielkalender fehlt die Kalender-Schreibberechtigung. Öffne das Profil und erlaube sie dort.",
                "calendar",
                "WRITE_CALENDAR permission missing for DIRECT_SAVE"
            )
        }

        val begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, Long.MIN_VALUE)
        if (begin == Long.MIN_VALUE) {
            throw ProcessingException(
                "Der Termin hat keinen sicher erkannten Startzeitpunkt und kann deshalb nicht direkt gespeichert werden.",
                "calendar.start",
                "DIRECT_SAVE requires EXTRA_EVENT_BEGIN_TIME"
            )
        }

        val end = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, Long.MIN_VALUE)
        val duration = intent.getStringExtra(CalendarContract.Events.DURATION)
        val rdate = intent.getStringExtra(CalendarContract.Events.RDATE)
        val defaultEnd = begin + if (action.allDay) 24L * 60L * 60L * 1000L else 60L * 60L * 1000L

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, intent.getStringExtra(CalendarContract.Events.TITLE).orEmpty())
            put(CalendarContract.Events.DESCRIPTION, intent.getStringExtra(CalendarContract.Events.DESCRIPTION).orEmpty())
            put(CalendarContract.Events.EVENT_LOCATION, intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION).orEmpty())
            put(CalendarContract.Events.DTSTART, begin)
            put(CalendarContract.Events.ALL_DAY, if (action.allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (action.allDay) "UTC" else ZoneId.systemDefault().id)
            if (!rdate.isNullOrBlank()) {
                put(CalendarContract.Events.RDATE, rdate)
                put(CalendarContract.Events.DURATION, duration ?: toRfc2445Duration(defaultEnd - begin))
            } else {
                val effectiveEnd = if (end != Long.MIN_VALUE && end > begin) end else defaultEnd
                put(CalendarContract.Events.DTEND, effectiveEnd)
                if (end == Long.MIN_VALUE) warnings += "Keine Endzeit erkannt. Für den gespeicherten Termin wurde ${if (action.allDay) "ein Tag" else "eine Stunde"} verwendet."
            }
        }

        val eventUri = try {
            appContext.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (e: Exception) {
            throw ProcessingException(
                "Der Termin konnte nicht in den ausgewählten Kalender geschrieben werden.",
                "calendar",
                "${e::class.java.name}: ${e.message ?: e.toString()}"
            )
        } ?: throw ProcessingException(
            "Der Termin konnte nicht in den ausgewählten Kalender geschrieben werden.",
            "calendar",
            "Calendar provider returned null from insert"
        )

        warnings += "Termin wurde verbindlich in '${calendarName.ifBlank { "den ausgewählten Kalender" }}' angelegt und zum Bearbeiten geöffnet."
        launch(Intent(Intent.ACTION_EDIT, eventUri), "calendar", "Der gespeicherte Termin konnte nicht zum Bearbeiten geöffnet werden")
        return ExecutionResult(warnings.distinct())
    }

    private fun validateCalendarId(id: Long, fallbackName: String, warnings: MutableList<String>): Long? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            warnings += "Der ausgewählte Zielkalender konnte ohne Kalender-Leseberechtigung nicht geprüft werden."
            return null
        }
        return try {
            val found = appContext.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars._ID}=? AND ${CalendarContract.Calendars.VISIBLE}=1",
                arrayOf(id.toString()),
                null
            )?.use { it.moveToFirst() } == true
            if (found) id
            else if (fallbackName.isNotBlank()) resolveCalendarId(fallbackName, warnings)
            else {
                warnings += "Der gespeicherte Zielkalender ist auf diesem Gerät nicht mehr verfügbar."
                null
            }
        } catch (_: Exception) {
            if (fallbackName.isNotBlank()) resolveCalendarId(fallbackName, warnings) else null
        }
    }

    private fun resolveCalendarId(name: String, warnings: MutableList<String>): Long? {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            warnings += "Zielkalender '$name' konnte ohne Kalender-Leseberechtigung nicht automatisch ausgewählt werden."
            return null
        }
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        val candidates = mutableListOf<Pair<Long, List<String>>>()
        try {
            appContext.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE}=1",
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val labels = listOfNotNull(cursor.getString(1), cursor.getString(2), cursor.getString(3))
                    candidates += id to labels
                }
            }
        } catch (_: Exception) {
            warnings += "Zielkalender '$name' konnte nicht abgefragt werden."
            return null
        }

        val exact = candidates.firstOrNull { (_, labels) -> labels.any { it.equals(name, ignoreCase = true) } }
        if (exact != null) return exact.first
        val partial = candidates.firstOrNull { (_, labels) -> labels.any { it.contains(name, ignoreCase = true) } }
        if (partial != null) return partial.first
        warnings += "Kalender '$name' wurde nicht gefunden. Bitte im Kalender manuell auswählen."
        return null
    }

    private fun openUrl(action: ProcessingAction.Url, values: Map<String, String>): ExecutionResult {
        val url = TemplateEngine.render(action.urlTemplate, values).trim()
        val uri = runCatching { Uri.parse(url) }.getOrElse {
            throw ProcessingException("Die erzeugte URL ist ungültig.", "url", it.message ?: it.toString())
        }
        val allowed = setOf("http", "https", "geo", "mailto", "tel")
        val scheme = uri.scheme?.lowercase()
        if (scheme !in allowed) {
            throw ProcessingException(
                "Das URL-Schema '${uri.scheme ?: "(fehlt)"}' ist nicht erlaubt.",
                "url",
                "Rejected URI scheme: ${uri.scheme}"
            )
        }
        if ((scheme == "http" || scheme == "https") && uri.host.isNullOrBlank()) {
            throw ProcessingException("Die erzeugte Web-Adresse hat keinen Host.", "url", url)
        }

        when (action.openMode) {
            UrlOpenMode.BROWSER -> {
                val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    if (scheme == "http" || scheme == "https") addCategory(Intent.CATEGORY_BROWSABLE)
                }
                launch(viewIntent, "url", "Link konnte nicht geöffnet werden")
            }
            UrlOpenMode.WEBVIEW -> {
                if (scheme !in setOf("http", "https")) {
                    throw ProcessingException(
                        "Die In-App-Ansicht unterstützt nur http- und https-Links.",
                        "url",
                        "WebView mode rejected scheme $scheme"
                    )
                }
                launch(
                    Intent(appContext, WebViewActivity::class.java).putExtra(WebViewActivity.EXTRA_URL, url),
                    "url",
                    "Link konnte nicht in ShareParser geöffnet werden"
                )
            }
        }
        return ExecutionResult()
    }

    private fun shareText(action: ProcessingAction.Share, values: Map<String, String>): ExecutionResult {
        val text = TemplateEngine.render(action.textTemplate, values)
        val subject = if (action.subjectTemplate.isBlank()) "" else TemplateEngine.render(action.subjectTemplate, values)
        val mimeType = "text/plain"
        if (!action.asFile) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_TEXT, text)
                if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            }
            launch(Intent.createChooser(intent, action.friendlyName), "share", "Teilen-Dialog konnte nicht geöffnet werden")
            return ExecutionResult()
        }

        val renderedName = renderFileTemplate(
            template = action.fileNameTemplate,
            values = values,
            policy = action.emptyValuePolicy,
            fallback = action.fallbackFileName.ifBlank { "ShareParser.txt" },
            field = "share.fileName",
            label = "Dateiname"
        )
        val baseFileName = resolveFileName(action, renderedName)
        val extension = if (action.fileExtension.isBlank()) {
            inferLegacyExtension(baseFileName, action.mimeType)
        } else {
            normalizeExtension(action.fileExtension)
        }
        val fileName = if (action.fileExtension.isBlank()) {
            ensureExtension(baseFileName, extension)
        } else {
            replaceExtension(baseFileName, extension)
        }
        val fileType = textMimeForExtension(extension)
        val fileWarnings = if (fileType.supported) emptyList() else listOf(
            "Die Dateiendung '.$extension' ist kein bekanntes Textformat. ShareParser speichert den Inhalt trotzdem als Textdatei mit dieser Endung."
        )
        val renderedPath = if (action.relativePathTemplate.isBlank()) {
            ""
        } else {
            renderFileTemplate(
                template = action.relativePathTemplate,
                values = values,
                policy = action.emptyValuePolicy,
                fallback = action.fallbackPath,
                field = "share.relativePath",
                label = "Unterordner"
            )
        }
        val relativePath = resolveRelativePath(action, renderedPath)
        return when (action.fileMode) {
            TextFileMode.SHARE -> {
                val file = writeTemporaryFile(fileName, text)
                val uri = shareUri(file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = fileType.mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
                    clipData = ClipData.newUri(appContext.contentResolver, fileName, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                launch(Intent.createChooser(intent, action.friendlyName), "share.file", "Datei konnte nicht geteilt werden")
                ExecutionResult(fileWarnings)
            }
            TextFileMode.OPEN -> {
                val file = writeTemporaryFile(fileName, text)
                val uri = shareUri(file)
                launch(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, fileType.mimeType)
                        clipData = ClipData.newUri(appContext.contentResolver, fileName, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "share.file",
                    "Datei konnte nicht geöffnet werden"
                )
                ExecutionResult(fileWarnings)
            }
            TextFileMode.SAVE -> {
                val result = saveTextFile(fileName, relativePath, fileType.mimeType, text)
                result.copy(warnings = (fileWarnings + result.warnings).distinct())
            }
        }
    }

    private data class TextFileType(val mimeType: String, val supported: Boolean)

    private fun inferLegacyExtension(fileName: String, mimeType: String): String {
        val fromName = fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let(::normalizeExtension)
        if (fromName != null) return fromName
        return when (mimeType.lowercase(Locale.ROOT)) {
            "text/markdown" -> "md"
            "text/html" -> "html"
            "text/csv" -> "csv"
            "application/json" -> "json"
            "application/xml", "text/xml" -> "xml"
            "application/yaml", "text/yaml" -> "yaml"
            else -> "txt"
        }
    }

    private fun normalizeExtension(value: String): String = value
        .trim()
        .removePrefix(".")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "")
        .ifBlank { "txt" }
        .take(12)

    private fun ensureExtension(fileName: String, extension: String): String {
        val suffix = "." + extension
        return if (fileName.endsWith(suffix, ignoreCase = true)) fileName else fileName.trimEnd('.') + suffix
    }

    private fun replaceExtension(fileName: String, extension: String): String {
        val clean = fileName.trimEnd('.')
        val dot = clean.lastIndexOf('.')
        val base = if (dot > 0) clean.substring(0, dot) else clean
        return base + "." + extension
    }

    private fun textMimeForExtension(extension: String): TextFileType = when (extension) {
        "txt", "log", "ini", "conf", "cfg" -> TextFileType("text/plain", true)
        "md", "markdown" -> TextFileType("text/markdown", true)
        "html", "htm" -> TextFileType("text/html", true)
        "csv" -> TextFileType("text/csv", true)
        "tsv" -> TextFileType("text/tab-separated-values", true)
        "json" -> TextFileType("application/json", true)
        "xml" -> TextFileType("application/xml", true)
        "yaml", "yml" -> TextFileType("application/yaml", true)
        "css" -> TextFileType("text/css", true)
        "js", "mjs" -> TextFileType("text/javascript", true)
        "ics" -> TextFileType("text/calendar", true)
        "sql", "kt", "java", "py", "sh" -> TextFileType("text/plain", true)
        else -> TextFileType("text/plain", false)
    }

    private fun sendWebhook(action: ProcessingAction.Webhook, values: Map<String, String>): ExecutionResult {
        val renderedUrl = TemplateEngine.render(action.urlTemplate, values).trim()
        if (renderedUrl.isBlank()) throw ProcessingException("Die Webhook-URL ist leer.", "webhook.url", "Rendered webhook URL was blank")
        val uri = runCatching { Uri.parse(renderedUrl) }.getOrElse {
            throw ProcessingException("Die Webhook-URL ist ungültig.", "webhook.url", it.message ?: it.toString())
        }
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw ProcessingException("Webhooks unterstützen nur vollständige http- oder https-URLs.", "webhook.url", "Rejected webhook URL: $renderedUrl")
        }

        val renderedBody = TemplateEngine.render(action.bodyTemplate, values)
        val body = if (renderedBody.isNotBlank()) renderedBody else when (action.emptyValuePolicy) {
            EmptyValuePolicy.FALLBACK -> action.fallbackBody.ifBlank { "{}" }
            EmptyValuePolicy.ERROR -> throw ProcessingException("Der Webhook-Inhalt ist leer.", "webhook.body", "Rendered webhook body was blank")
        }

        val connection = try {
            (URL(renderedUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = if (backgroundMode) 20_000 else 10_000
                readTimeout = if (backgroundMode) 25_000 else 10_000
                doOutput = true
                val safeContentType = action.contentType
                    .replace("\r", "")
                    .replace("\n", "")
                    .trim()
                    .take(120)
                    .ifBlank { "application/json; charset=utf-8" }
                setRequestProperty("Content-Type", safeContentType)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("User-Agent", "ShareParser")
            }
        } catch (e: Exception) {
            throw ProcessingException("Die Webhook-Verbindung konnte nicht erstellt werden.", "webhook.url", "${e::class.java.name}: ${e.message ?: e.toString()}")
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val response = runCatching { (connection.errorStream ?: connection.inputStream)?.bufferedReader()?.use { it.readText().take(1000) } }.getOrNull().orEmpty()
                throw ProcessingException("Der Webhook wurde vom Server abgelehnt (HTTP $code).", "webhook", "HTTP $code ${connection.responseMessage.orEmpty()}\n$response")
            }
        } catch (e: ProcessingException) {
            throw e
        } catch (e: Exception) {
            throw ProcessingException("Der Webhook konnte nicht gesendet werden.", "webhook", "${e::class.java.name}: ${e.message ?: e.toString()}")
        } finally {
            connection.disconnect()
        }
        return ExecutionResult()
    }
    private fun saveTextFile(fileName: String, relativePath: String, mimeType: String, text: String): ExecutionResult {
        if (settings.defaultSaveTreeUri.isNotBlank()) {
            val saved = runCatching {
                val root = DocumentFile.fromTreeUri(appContext, Uri.parse(settings.defaultSaveTreeUri))
                    ?: error("Default tree URI is unavailable")
                var directory = root
                safePathSegments(relativePath).forEach { segment ->
                    val next = directory.findFile(segment)?.takeIf { it.isDirectory }
                        ?: directory.createDirectory(segment)
                        ?: error("Could not create directory '$segment'")
                    directory = next
                }
                val target = directory.findFile(fileName)?.takeIf { it.isFile }
                    ?: directory.createFile(mimeType, fileName)
                    ?: error("Could not create file '$fileName'")
                appContext.contentResolver.openOutputStream(target.uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                    ?: error("Could not open output stream")
                target.uri
            }.getOrNull()
            if (saved != null) {
                val location = safePathSegments(relativePath).joinToString("/")
                val display = if (location.isBlank()) fileName else "$location/$fileName"
                return ExecutionResult(listOf("Datei '$display' wurde im voreingestellten Ordner gespeichert."))
            }
        }

        val file = writeTemporaryFile(fileName, text)
        launch(
            Intent(appContext, SaveTextFileActivity::class.java).apply {
                putExtra(SaveTextFileActivity.EXTRA_SOURCE_PATH, file.absolutePath)
                putExtra(SaveTextFileActivity.EXTRA_MIME_TYPE, mimeType)
                putExtra(SaveTextFileActivity.EXTRA_FILE_NAME, fileName)
            },
            "share.file",
            "Speicherort konnte nicht geöffnet werden"
        )
        return ExecutionResult(
            if (settings.defaultSaveTreeUri.isBlank()) emptyList()
            else listOf("Der voreingestellte Speicherordner war nicht erreichbar. Android zeigt deshalb den Dateidialog an.")
        )
    }

    private fun writeTemporaryFile(fileName: String, text: String): File {
        val directory = File(appContext.cacheDir, "shared").apply { mkdirs() }
        val file = File(directory, fileName)
        runCatching { file.writeText(text) }.getOrElse {
            throw ProcessingException(
                "Die Textdatei konnte nicht erstellt werden.",
                "share.file",
                "${it::class.java.name}: ${it.message ?: it.toString()}"
            )
        }
        return file
    }

    private fun shareUri(file: File): Uri = try {
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
    } catch (e: Exception) {
        throw ProcessingException(
            "Die Textdatei konnte nicht für andere Apps freigegeben werden.",
            "share.file",
            "${e::class.java.name}: ${e.message ?: e.toString()}"
        )
    }

    private fun renderFileTemplate(
        template: String,
        values: Map<String, String>,
        policy: EmptyValuePolicy,
        fallback: String,
        field: String,
        label: String
    ): String {
        val emptyVariables = TemplateEngine.variables(template).filter { values[it].isNullOrBlank() }
        if (template.isBlank() || emptyVariables.isNotEmpty()) {
            return when (policy) {
                EmptyValuePolicy.FALLBACK -> fallback
                EmptyValuePolicy.ERROR -> throw ProcessingException(
                    "$label ist leer oder enthält eine leere Variable.",
                    field,
                    if (emptyVariables.isEmpty()) "Template was blank"
                    else "Blank variables: ${emptyVariables.joinToString()}"
                )
            }
        }
        return TemplateEngine.render(template, values)
    }

    private val unsafeFileChars = Regex("[\\u0000-\\u001F<>:\"/\\\\|?*]+")

    private fun resolveFileName(action: ProcessingAction.Share, rendered: String): String {
        val trimmed = rendered.trim()
        if (trimmed.isBlank()) {
            return when (action.emptyValuePolicy) {
                EmptyValuePolicy.FALLBACK -> safeFileName(action.fallbackFileName.ifBlank { "ShareParser.txt" })
                EmptyValuePolicy.ERROR -> throw ProcessingException(
                    "Der erzeugte Dateiname ist leer.",
                    "share.fileName",
                    "Rendered filename was blank"
                )
            }
        }
        if (trimmed == "." || trimmed == "..") {
            return when (action.emptyValuePolicy) {
                EmptyValuePolicy.FALLBACK -> safeFileName(action.fallbackFileName.ifBlank { "ShareParser.txt" })
                EmptyValuePolicy.ERROR -> throw ProcessingException(
                    "Der erzeugte Dateiname ist kein gültiger Dateiname.",
                    "share.fileName",
                    "Rejected path traversal filename: '$rendered'"
                )
            }
        }
        return safeFileName(trimmed)
    }

    private fun resolveRelativePath(action: ProcessingAction.Share, rendered: String): String {
        if (rendered.isBlank()) return ""
        val rawSegments = rendered.split('/', '\\')
        val traversal = rawSegments.any { segment ->
            val trimmed = segment.trim()
            trimmed == "." || trimmed == ".."
        }
        if (!traversal) return safePathSegments(rendered).joinToString("/")
        return when (action.emptyValuePolicy) {
            EmptyValuePolicy.FALLBACK -> safePathSegments(action.fallbackPath).joinToString("/")
            EmptyValuePolicy.ERROR -> throw ProcessingException(
                "Der erzeugte Unterordner enthält einen unzulässigen Pfadteil.",
                "share.relativePath",
                "Rejected traversal path: '$rendered'"
            )
        }
    }

    private fun safeFileName(value: String): String = value
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()
        .replace(unsafeFileChars, "_")
        .replace(Regex("[. ]+$"), "")
        .take(180)
        .ifBlank { "ShareParser.txt" }

    private fun safePathSegments(value: String): List<String> = value
        .split('/', '\\')
        .map { segment ->
            segment.trim().replace(unsafeFileChars, "_").take(80)
        }
        .filter { it.isNotBlank() && it != "." && it != ".." }
    private fun launch(intent: Intent, failingField: String, message: String) {
        val safeIntent = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            appContext.startActivity(safeIntent)
        } catch (e: ActivityNotFoundException) {
            throw ProcessingException(
                "Keine passende App für diese Aktion gefunden.",
                failingField,
                diagnostic(e, safeIntent)
            )
        } catch (e: SecurityException) {
            throw ProcessingException(
                "Android hat das Öffnen dieser Aktion aus Sicherheitsgründen blockiert.",
                failingField,
                diagnostic(e, safeIntent)
            )
        } catch (e: Exception) {
            throw ProcessingException(message, failingField, diagnostic(e, safeIntent))
        }
    }

    private fun diagnostic(error: Throwable, intent: Intent): String =
        "${error::class.java.name}: ${error.message ?: error.toString()}\n" +
            "Android API: ${Build.VERSION.SDK_INT}\n" +
            "Manufacturer: ${Build.MANUFACTURER}\n" +
            "Model: ${Build.MODEL}\n" +
            "Intent: ${runCatching { intent.toUri(0) }.getOrDefault("unavailable")}"

    private fun toRfc2445Duration(durationMillis: Long): String = "PT${durationMillis / 1000L}S"

    private fun toRfc2445DateTime(epochMs: Long): String =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochMilli(epochMs))

    private fun parseExplicit(value: String, pattern: String): Long {
        val formatter = DateTimeFormatter.ofPattern(pattern, localeForSettings())
        return runCatching {
            LocalDateTime.parse(value.trim(), formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.recoverCatching {
            LocalDate.parse(value.trim(), formatter).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrThrow()
    }

    private fun localeForSettings(): Locale = when (settings.dateTimeLocale) {
        DateTimeLocale.DE_DE -> Locale.GERMANY
        DateTimeLocale.EN_US -> Locale.US
        DateTimeLocale.EN_GB -> Locale.UK
        DateTimeLocale.ISO -> Locale.ROOT
        DateTimeLocale.SYSTEM -> Locale.getDefault()
    }
}
