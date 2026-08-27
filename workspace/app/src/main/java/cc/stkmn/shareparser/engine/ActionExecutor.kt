package cc.stkmn.shareparser.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import cc.stkmn.shareparser.data.ProcessingAction
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ActionExecutor(private val context: Context) {
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
            .putExtra(CalendarContract.Events.DESCRIPTION, TemplateEngine.render(action.descriptionTemplate, values))
            .putExtra(CalendarContract.Events.EVENT_LOCATION, TemplateEngine.render(action.locationTemplate, values))
        renderOptional(action.startTemplate, values)?.takeIf { it.isNotBlank() }?.let {
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, parseDateTime(it))
        }
        renderOptional(action.endTemplate, values)?.takeIf { it.isNotBlank() }?.let {
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, parseDateTime(it))
        }
        launch(intent)
    }

    private fun openUrl(action: ProcessingAction.Url, values: Map<String, String>) {
        val url = TemplateEngine.render(action.urlTemplate, values)
        val uri = runCatching { Uri.parse(url) }.getOrElse {
            throw ProcessingException("Die erzeugte URL ist ungültig.", "url", it.message ?: it.toString())
        }
        if (uri.scheme !in setOf("http", "https", "geo", "mailto", "tel")) {
            throw ProcessingException("Das URL-Schema '${uri.scheme}' ist nicht erlaubt.", "url", "Rejected URI scheme: ${uri.scheme}")
        }
        launch(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun shareText(action: ProcessingAction.Share, values: Map<String, String>) {
        val text = TemplateEngine.render(action.textTemplate, values)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = action.mimeType
            putExtra(Intent.EXTRA_TEXT, text)
        }
        launch(Intent.createChooser(intent, action.friendlyName))
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            throw ProcessingException("Keine passende App für diese Aktion gefunden.", "action", intent.toUri(0))
        }
        context.startActivity(intent)
    }

    private fun renderOptional(template: String, values: Map<String, String>): String? =
        if (template.isBlank()) null else TemplateEngine.render(template, values)

    private fun parseDateTime(value: String): Long {
        val trimmed = value.trim()
        return runCatching { OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            .getOrElse { throw ProcessingException("Datum/Zeit '$trimmed' konnte nicht gelesen werden.", "datetime", it.message ?: it.toString()) }
    }
}
