package cc.stkmn.shareparser.engine

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import cc.stkmn.shareparser.WebViewActivity
import cc.stkmn.shareparser.data.AppSettings
import cc.stkmn.shareparser.data.DateTimeLocale
import cc.stkmn.shareparser.data.ProcessingAction
import cc.stkmn.shareparser.data.UrlOpenMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ActionExecutor(context: Context, private val settings: AppSettings = AppSettings()) {
    private val appContext = context.applicationContext
    private val launchContext = context

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
        val calendarName = render(action.calendarNameTemplate)

        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            .putExtra(CalendarContract.Events.ALL_DAY, action.allDay)

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
                allDay = action.allDay,
                settings = settings
            )
            parsed.startEpochMs?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            parsed.endEpochMs?.let { intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            warnings += parsed.warnings
        }

        if (calendarName.isNotBlank()) {
            val calendarId = resolveCalendarId(calendarName, warnings)
            calendarId?.let { intent.putExtra(CalendarContract.Events.CALENDAR_ID, it) }
        }

        if (missing.isNotEmpty()) {
            warnings += "Nicht erkannte Variablen: ${missing.joinToString(", ")}. Bitte diese Angaben im Kalender manuell ergänzen."
        }
        if (title.isBlank()) warnings += "Titel ist leer. Bitte den Termin-Titel im Kalender prüfen."

        launch(intent, "calendar", "Kalender konnte nicht geöffnet werden")
        return ExecutionResult(warnings.distinct())
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
        } catch (e: Exception) {
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
            UrlOpenMode.BROWSER -> launch(Intent(Intent.ACTION_VIEW, uri), "url", "Link konnte nicht im Browser geöffnet werden")
            UrlOpenMode.WEBVIEW -> {
                if (scheme !in setOf("http", "https")) {
                    throw ProcessingException(
                        "Die In-App-Ansicht unterstützt nur http- und https-Links.",
                        "url",
                        "WebView mode rejected scheme $scheme"
                    )
                }
                launch(
                    Intent(launchContext, WebViewActivity::class.java).putExtra(WebViewActivity.EXTRA_URL, url),
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
        val safeIntent = Intent(intent)
        if (launchContext !is Activity) safeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (safeIntent.resolveActivity(appContext.packageManager) == null) {
                throw ProcessingException(
                    "Keine passende App für diese Aktion gefunden.",
                    failingField,
                    safeIntent.toUri(0)
                )
            }
            launchContext.startActivity(safeIntent)
        } catch (e: ProcessingException) {
            throw e
        } catch (e: Exception) {
            throw ProcessingException(
                message,
                failingField,
                "${e::class.java.name}: ${e.message ?: e.toString()}\nIntent: ${runCatching { safeIntent.toUri(0) }.getOrDefault("unavailable")}" 
            )
        }
    }

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
