package cc.stkmn.shareparser.engine

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object TemplateEngine {
    private val token = Regex("\\{\\{([a-zA-Z0-9_.-]+)(?:\\|([a-zA-Z]+))?}}")

    fun render(template: String, values: Map<String, String>): String = token.replace(template) { m ->
        val key = m.groupValues[1]
        val modifier = m.groupValues[2]
        val value = values[key] ?: throw ProcessingException(
            "Wert '$key' fehlt für die Verarbeitung.", key, "Template variable '$key' is not available"
        )
        when (modifier) {
            "url" -> URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
            "lower" -> value.lowercase()
            "upper" -> value.uppercase()
            "trim" -> value.trim()
            "" -> value
            else -> throw ProcessingException("Unbekannter Baustein '$modifier'.", key, "Unknown template modifier: $modifier")
        }
    }
}
