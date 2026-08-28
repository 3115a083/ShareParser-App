package cc.stkmn.shareparser.engine

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import cc.stkmn.shareparser.WebViewActivity
import cc.stkmn.shareparser.data.AppSettings
import cc.stkmn.shareparser.data.CalendarTargetMode
import cc.stkmn.shareparser.data.DateTimeLocale
import cc.stkmn.shareparser.data.ProcessingAction
import cc.stkmn.shareparser.data.UrlOpenMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class ActionExecutor(context: Context, private val settings: AppSettings = AppSettings()) {
    private val appContext = context.applicationContext

    data class ExecutionResult(val warnings: List<String> = emptyList())

    fun execute(action: ProcessingAction, values: Map<String, String>): ExecutionResult = when (action) {
        is ProcessingAction.Calendar -> openCalendar(action, values)
        is ProcessingAction.Url -> openUrl(action, values)
        is ProcessingAction.Share -> shareText(action, values)
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
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
                launch(browserIntent, "url", "Link konnte nicht im Browser geöffnet werden")
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
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = action.mimeType.ifBlank { "text/plain" }
            putExtra(Intent.EXTRA_TEXT, text)
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        launch(Intent.createChooser(intent, action.friendlyName), "share", "Teilen-Dialog konnte nicht geöffnet werden")
        return ExecutionResult()
    }

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
