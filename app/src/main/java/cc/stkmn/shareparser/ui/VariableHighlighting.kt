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
    val relevant = extractors.filter { it.source == source && it.key.isNotBlank() }
    val highlights = relevant.map { rule ->
        VariableHighlight(rule.key, palette[(rule.id.hashCode() and Int.MAX_VALUE) % palette.size])
    }
    val byKey = highlights.associateBy { it.key }

    val transformation = VisualTransformation { text ->
        val builder = AnnotatedString.Builder(text)
        val occupied = mutableListOf<IntRange>()
        relevant.forEach { rule ->
            val color = byKey[rule.key]?.color ?: return@forEach
            val matches = runCatching {
                Regex(rule.regex, setOf(RegexOption.MULTILINE))
                    .findAll(text.text)
                    .take(200)
                    .toList()
            }.getOrDefault(emptyList())

            matches.forEach { match ->
                val group = if (rule.group in 0 until match.groups.size) match.groups[rule.group] else null
                if (group == null) return@forEach
                val range = group.range
                if (range.first < 0 || range.last < range.first || range.last >= text.length) return@forEach
                val length = range.last - range.first + 1
                val sample = rule.sampleLabel.trim()
                val expected = sample.length
                val suspiciouslyLarge = length > 1000 ||
                    (expected > 0 && length > maxOf(expected * 4, expected + 80))
                val highlightRange = if (suspiciouslyLarge && sample.isNotBlank()) {
                    val groupText = text.text.substring(range.first, range.last + 1)
                    val local = groupText.indexOf(sample)
                    if (local >= 0) {
                        val start = range.first + local
                        start until (start + sample.length)
                    } else {
                        null
                    }
                } else {
                    range
                } ?: return@forEach
                if (highlightRange.first < 0 || highlightRange.last >= text.length) return@forEach
                if (occupied.any { it.first <= highlightRange.last && highlightRange.first <= it.last }) return@forEach
                occupied += highlightRange
                builder.addStyle(
                    SpanStyle(background = color, fontWeight = FontWeight.SemiBold),
                    highlightRange.first,
                    highlightRange.last + 1
                )
            }
        }
        TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
    return transformation to highlights
}
