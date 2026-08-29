package cc.stkmn.shareparser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import cc.stkmn.shareparser.data.ExtractorRule
import cc.stkmn.shareparser.data.InputSource

internal data class VariableHighlight(
    val key: String,
    val color: Color
)

@Composable
internal fun rememberVariableHighlighting(
    source: InputSource,
    extractors: List<ExtractorRule>
): Pair<VisualTransformation, List<VariableHighlight>> {
    val palette = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant
    )
    // Only rules that directly read this source can be mapped back to positions in the
    // displayed text. Derived-variable rules previously ran against the original text
    // as well and could accidentally highlight very large ranges.
    val relevant = extractors.filter {
        it.source == source &&
            it.sourceVariableKey.isBlank() &&
            it.key.isNotBlank() &&
            it.regex.isNotBlank()
    }
    val highlights = relevant.map { rule ->
        VariableHighlight(rule.key, palette[(rule.id.hashCode() and Int.MAX_VALUE) % palette.size])
    }
    val byKey = highlights.associateBy { it.key }

    val transformation = VisualTransformation { text ->
        val builder = AnnotatedString.Builder(text)
        relevant.forEach { rule ->
            val color = byKey[rule.key]?.color ?: return@forEach
            runCatching { Regex(rule.regex, setOf(RegexOption.MULTILINE)).findAll(text.text).toList() }
                .getOrDefault(emptyList())
                .asSequence()
                .take(250)
                .forEach { match ->
                    val group = if (rule.group in match.groupValues.indices) match.groups[rule.group] else null
                    val start = group?.range?.first ?: return@forEach
                    val endExclusive = (group.range.last + 1).coerceAtMost(text.length)
                    // Empty capture groups use an empty range. Never style them. This also
                    // protects AnnotatedString from malformed/oversized regex group ranges.
                    if (group.value.isNotEmpty() && start in 0 until text.length && endExclusive > start) {
                        builder.addStyle(
                            SpanStyle(background = color, fontWeight = FontWeight.SemiBold),
                            start,
                            endExclusive
                        )
                    }
                }
        }
        TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
    return transformation to highlights
}
