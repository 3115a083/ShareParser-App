package cc.stkmn.shareparser.engine

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object TemplateEngine {
    private data class Token(
        val start: Int,
        val endExclusive: Int,
        val key: String,
        val modifier: String
    )

    fun render(template: String, values: Map<String, String>): String = renderInternal(template, values) { key ->
        throw ProcessingException(
            "Wert '$key' fehlt für die Verarbeitung.",
            key,
            "Template variable '$key' is not available"
        )
    }

    fun renderLenient(
        template: String,
        values: Map<String, String>,
        onMissing: (String) -> Unit
    ): String = renderInternal(template, values) { key ->
        onMissing(key)
        ""
    }

    fun variables(template: String): Set<String> = findTokens(template)
        .map { it.key }
        .toSet()

    private fun renderInternal(
        template: String,
        values: Map<String, String>,
        missing: (String) -> String
    ): String {
        val tokens = findTokens(template)
        if (tokens.isEmpty()) return template

        val result = StringBuilder(template.length)
        var cursor = 0
        for (token in tokens) {
            result.append(template, cursor, token.start)
            val value = values[token.key] ?: missing(token.key)
            result.append(applyModifier(value, token.key, token.modifier))
            cursor = token.endExclusive
        }
        result.append(template, cursor, template.length)
        return result.toString()
    }

    private fun applyModifier(value: String, key: String, modifier: String): String = when (modifier) {
        "url" -> URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
        "lower" -> value.lowercase()
        "upper" -> value.uppercase()
        "trim" -> value.trim()
        "json" -> jsonEscape(value)
        "" -> value
        else -> throw ProcessingException(
            "Unbekannte Umwandlung '$modifier'.",
            key,
            "Unknown template modifier: $modifier"
        )
    }

    private fun jsonEscape(value: String): String = buildString(value.length + 8) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else append(ch)
            }
        }
    }

    /**
     * Parses {{name}} and {{name|modifier}} without java.util.regex.
     * Android's ICU regex engine and the desktop JVM differ in how they accept
     * unescaped closing braces, so this core path deliberately avoids regex.
     */
    private fun findTokens(template: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var searchFrom = 0

        while (searchFrom < template.length) {
            val start = template.indexOf("{{", searchFrom)
            if (start < 0) break
            val close = template.indexOf("}}", start + 2)
            if (close < 0) break

            val body = template.substring(start + 2, close)
            val firstPipe = body.indexOf('|')
            val secondPipe = if (firstPipe >= 0) body.indexOf('|', firstPipe + 1) else -1
            val key = (if (firstPipe >= 0) body.substring(0, firstPipe) else body).lowercase()
            val modifier = if (firstPipe >= 0) body.substring(firstPipe + 1) else ""

            val validKey = key.isNotEmpty() && key.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' }
            val validModifier = secondPipe < 0 && (firstPipe < 0 || (modifier.isNotEmpty() && modifier.all { it.isLetter() }))

            if (validKey && validModifier) {
                tokens += Token(
                    start = start,
                    endExclusive = close + 2,
                    key = key,
                    modifier = modifier
                )
                searchFrom = close + 2
            } else {
                // Preserve malformed template text as-is and continue looking for
                // a later valid token rather than failing during class initialization.
                searchFrom = start + 2
            }
        }

        return tokens
    }
}
