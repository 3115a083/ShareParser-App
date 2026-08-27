package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.ExtractorRule
import cc.stkmn.shareparser.data.InputSource
import cc.stkmn.shareparser.data.MatcherRule
import cc.stkmn.shareparser.data.SharedPayload

object GuidedRuleFactory {
    data class Candidate(
        val label: String,
        val value: String,
        val source: InputSource,
        val sourceLine: String,
        val suggestedKey: String
    )

    fun candidates(payload: SharedPayload): List<Candidate> = buildList {
        if (payload.subject.isNotBlank()) {
            add(
                Candidate(
                    label = "Betreff",
                    value = payload.subject.trim(),
                    source = InputSource.SUBJECT,
                    sourceLine = payload.subject.trim(),
                    suggestedKey = "subject"
                )
            )
        }
        payload.text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(80)
            .forEachIndexed { index, line ->
                val split = splitLabelAndValue(line)
                val label = split?.first ?: "Textzeile ${index + 1}"
                val value = split?.second ?: line
                add(
                    Candidate(
                        label = label,
                        value = value,
                        source = InputSource.TEXT,
                        sourceLine = line,
                        suggestedKey = suggestedKey(label, index)
                    )
                )
            }
    }

    fun extractor(candidate: Candidate, key: String, required: Boolean = false): ExtractorRule {
        val normalizedKey = sanitizeKey(key.ifBlank { candidate.suggestedKey })
        if (candidate.source == InputSource.SUBJECT) {
            return ExtractorRule(
                key = normalizedKey,
                regex = "(?s)^\\s*(.+?)\\s*$",
                required = required,
                source = InputSource.SUBJECT,
                sampleLabel = candidate.label
            )
        }

        val split = splitLabelAndValue(candidate.sourceLine)
        return if (split != null) {
            val label = Regex.escape(split.first)
            val separator = Regex.escape(separatorOf(candidate.sourceLine))
            ExtractorRule(
                key = normalizedKey,
                regex = "(?m)^\\s*$label\\s*$separator\\s*(.+?)\\s*$",
                required = required,
                source = InputSource.TEXT,
                sampleLabel = split.first
            )
        } else {
            val literal = Regex.escape(candidate.sourceLine)
            ExtractorRule(
                key = normalizedKey,
                regex = "(?m)^\\s*($literal)\\s*$",
                required = required,
                source = InputSource.TEXT,
                sampleLabel = candidate.label
            )
        }
    }

    fun matcherFromText(text: String): MatcherRule = MatcherRule(
        regex = Regex.escape(text.trim()),
        ignoreCase = true,
        friendlyText = text.trim()
    )

    fun suggestedMatchers(payload: SharedPayload): List<String> = buildList {
        payload.subject.trim().takeIf { it.length in 4..120 }?.let(::add)
        payload.text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { splitLabelAndValue(it)?.first }
            .filter { it.length >= 3 }
            .distinct()
            .take(8)
            .forEach(::add)
    }.distinct()

    private fun splitLabelAndValue(line: String): Pair<String, String>? {
        val separators = listOf(":", "=", "–", "—")
        for (separator in separators) {
            val index = line.indexOf(separator)
            if (index in 1 until line.lastIndex) {
                val left = line.substring(0, index).trim()
                val right = line.substring(index + separator.length).trim()
                if (left.length <= 50 && right.isNotBlank()) return left to right
            }
        }
        return null
    }

    private fun separatorOf(line: String): String = listOf(":", "=", "–", "—")
        .firstOrNull { it in line } ?: ":"

    private fun suggestedKey(label: String, index: Int): String {
        val normalized = sanitizeKey(
            label.lowercase()
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("ß", "ss")
        )
        return normalized.ifBlank { "field${index + 1}" }
    }

    fun sanitizeKey(value: String): String = value
        .trim()
        .replace(Regex("[^a-zA-Z0-9_.-]+"), "_")
        .trim('_')
        .ifBlank { "field" }
}
