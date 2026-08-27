package cc.stkmn.shareparser.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import cc.stkmn.shareparser.data.ProcessingAction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class ActionExecutor(context: Context) {
    private val appContext = context.applicationContext

    fun execute(action: ProcessingAction, values: Map<String, String>) {
        when (action) {
            is ProcessingAction.Calendar -> openCalendar(action, values)
            is ProcessingAction.Url -> openUrl(action, values)
            is ProcessingAction.Share -> shareText(action, values)
        }
    }

    private fun openCalendar(action: ProcessingAction.Calendar, values: Map<String, String>) {
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, TemplateEngine.render(action.titleTemplate, values))
            .putExtra(CalendarContract.Events.DESCRIPTION, renderOptional(action.descriptionTemplate, values).orEmpty())
            .putExtra(CalendarContract.Events.EVENT_LOCATION, renderOptional(action.locationTemplate, values).orEmpty())
            .putExtra(CalendarContract.Events.ALL_DAY, action.allDay)

        renderOptional(action.startTemplate, values)?.takeIf { it.isNotBlank() }?.let {
            intent.putExtra(
                CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                parseDateTime(it, action.startPattern, "calendar.start")
            )
        }
        renderOptional(action.endTemplate, values)?.takeIf { it.isNotBlank() }?.let {
            intent.putExtra(
                CalendarContract.EXTRA_EVENT_END_TIME,
                parseDateTime(it, action.endPattern, "calendar.end")
            )
        }
        launch(intent, "calendar")
    }

    private fun openUrl(action: ProcessingAction.Url, values: Map<String, String>) {
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
        launch(Intent(Intent.ACTION_VIEW, uri), "url")
    }

    private fun shareText(action: ProcessingAction.Share, values: Map<String, String>) {
        val text = TemplateEngine.render(action.textTemplate, values)
        val subject = renderOptional(action.subjectTemplate, values).orEmpty()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = action.mimeType.ifBlank { "text/plain" }
            putExtra(Intent.EXTRA_TEXT, text)
            if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        launch(Intent.createChooser(intent, action.friendlyName), "share")
    }

    private fun launch(intent: Intent, failingField: String) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(appContext.packageManager) == null) {
            throw ProcessingException(
                "Keine passende App für diese Aktion gefunden.",
                failingField,
                intent.toUri(0)
            )
        }
        appContext.startActivity(intent)
    }

    private fun renderOptional(template: String, values: Map<String, String>): String? =
        if (template.isBlank()) null else TemplateEngine.render(template, values)

    private fun parseDateTime(value: String, explicitPattern: String, failingField: String): Long {
        val trimmed = value.trim()
        if (explicitPattern.isNotBlank()) {
            return runCatching {
                LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern(explicitPattern, Locale.getDefault()))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.recoverCatching {
                LocalDate.parse(trimmed, DateTimeFormatter.ofPattern(explicitPattern, Locale.getDefault()))
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrElse {
                throw ProcessingException(
                    "Datum/Zeit '$trimmed' passt nicht zum Muster '$explicitPattern'.",
                    failingField,
                    it.message ?: it.toString()
                )
            }
        }

        runCatching { return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }
        runCatching {
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        val dateTimePatterns = listOf(
            "dd.MM.yyyy HH:mm",
            "dd.MM.yyyy, HH:mm",
            "dd.MM.yy HH:mm",
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm"
        )
        for (pattern in dateTimePatterns) {
            try {
                return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // Try the next common representation.
            }
        }

        val datePatterns = listOf("dd.MM.yyyy", "dd.MM.yy", "yyyy-MM-dd", "dd/MM/yyyy")
        for (pattern in datePatterns) {
            try {
                return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                // Try the next common representation.
            }
        }

        throw ProcessingException(
            "Datum/Zeit '$trimmed' konnte nicht gelesen werden.",
            failingField,
            "No supported date/time pattern matched '$trimmed'"
        )
    }
}
