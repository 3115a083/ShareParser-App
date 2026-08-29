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

    private val streetAddress = Regex(
        "(?i)(?:\\b(?:am|an der|auf der|unter den|zum|zur)\\s+)?[\\p{L}][\\p{L}.'’/-]*(?:straße|strasse|str\\.?|weg|allee|platz|gasse|ring|ufer|chaussee|damm|steig|stieg|pfad|promenade)\\s+\\d{1,5}[a-zA-Z]?(?:\\s*[-/]\\s*\\d{1,5}[a-zA-Z]?)?"
    )
    private val postalCity = Regex("(?<!\\d)\\d{5}\\s+[\\p{L}][\\p{L} .'-]{1,50}(?!\\d)")
    private val webUrl = Regex("(?i)\\bhttps?://[^\\s<>\"']+")
    private val mailTo = Regex("(?i)\\bmailto:[^\\s<>\"']+")
    private val telLink = Regex("(?i)\\btel:[+0-9()./ -]{5,}")
    private val plainEmail = Regex("(?i)(?<![\\w.+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![\\w.-])")
    private val hrefTarget = Regex("(?i)href\\s*=\\s*[\"'](https?://[^\"']+|mailto:[^\"']+|tel:[^\"']+)[\"']")

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

                streetAddress.findAll(line).forEach { match ->
                    val address = match.value.trim()
                    if (address.length >= 5 && address != value) {
                        add(
                            Candidate(
                                label = "Adresse",
                                value = address,
                                source = InputSource.TEXT,
                                sourceLine = line,
                                suggestedKey = "adresse"
                            )
                        )
                    }
                }
                postalCity.findAll(line).forEach { match ->
                    val place = match.value.trim()
                    if (place.length >= 7 && place != value) {
                        add(
                            Candidate(
                                label = "PLZ und Ort",
                                value = place,
                                source = InputSource.TEXT,
                                sourceLine = line,
                                suggestedKey = "plz_ort"
                            )
                        )
                    }
                }

                webUrl.findAll(line).forEach { match ->
                    add(Candidate("Web-Link", match.value.trimEnd('.', ',', ';'), InputSource.TEXT, line, "link"))
                }
                mailTo.findAll(line).forEach { match ->
                    add(Candidate("E-Mail-Link", match.value, InputSource.TEXT, line, "email"))
                }
                telLink.findAll(line).forEach { match ->
                    add(Candidate("Telefon-Link", match.value.trim(), InputSource.TEXT, line, "telefon"))
                }
                plainEmail.findAll(line).forEach { match ->
                    add(Candidate("E-Mail-Adresse", match.value, InputSource.TEXT, line, "email"))
                }
                hrefTarget.findAll(line).forEach { match ->
                    val target = match.groups[1]?.value.orEmpty()
                    if (target.isNotBlank()) {
                        val label = when {
                            target.startsWith("mailto:", true) -> "E-Mail-Link"
                            target.startsWith("tel:", true) -> "Telefon-Link"
                            else -> "Web-Link"
                        }
                        val key = when {
                            target.startsWith("mailto:", true) -> "email"
                            target.startsWith("tel:", true) -> "telefon"
                            else -> "link"
                        }
                        add(Candidate(label, target, InputSource.TEXT, line, key))
                    }
                }
            }
    }.distinctBy { Triple(it.source, it.sourceLine, it.value) }

    fun extractor(candidate: Candidate, key: String, required: Boolean = false): ExtractorRule {
        val normalizedKey = sanitizeKey(key.ifBlank { candidate.suggestedKey }).ifBlank { candidate.suggestedKey }
        if (candidate.source == InputSource.SUBJECT) {
            return ExtractorRule(
                key = normalizedKey,
                regex = "(?s)^\\s*(.+?)\\s*$",
                required = required,
                source = InputSource.SUBJECT,
                sampleLabel = candidate.label
            )
        }

        val selectedIndex = candidate.sourceLine.indexOf(candidate.value)
        if (selectedIndex >= 0 && candidate.value != candidate.sourceLine) {
            return extractorFromSelection(
                sourceText = candidate.sourceLine,
                selectionStart = selectedIndex,
                selectionEnd = selectedIndex + candidate.value.length,
                key = normalizedKey,
                source = candidate.source,
                required = required
            ).copy(sampleLabel = candidate.label)
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

    fun extractorFromSelection(
        sourceText: String,
        selectionStart: Int,
        selectionEnd: Int,
        key: String,
        source: InputSource,
        required: Boolean = false
    ): ExtractorRule {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, sourceText.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, sourceText.length)
        require(end > start) { "Bitte zuerst einen Textbereich markieren." }

        val lineStart = sourceText.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = sourceText.indexOf('\n', end).let { if (it < 0) sourceText.length else it }
        val prefix = sourceText.substring(lineStart, start)
        val suffix = sourceText.substring(end, lineEnd)
        val selected = sourceText.substring(start, end)

        val horizontalSpace = "[\\t\\p{Zs}]*"
        val regex = if (prefix.isBlank() && suffix.isBlank()) {
            "(?m)^${horizontalSpace}(.+?)${horizontalSpace}$"
        } else {
            "(?m)^${flexibleLiteral(prefix)}(.+?)${flexibleLiteral(suffix)}$"
        }
        return ExtractorRule(
            key = sanitizeKey(key),
            regex = regex,
            required = required,
            source = source,
            sampleLabel = selected.take(80)
        )
    }

    fun matcherFromText(text: String): MatcherRule = MatcherRule(
        regex = Regex.escape(text.trim()),
        ignoreCase = true,
        friendlyText = text.trim()
    )

    fun matcherFromSelection(sourceText: String, selectionStart: Int, selectionEnd: Int): MatcherRule {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, sourceText.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, sourceText.length)
        require(end > start) { "Bitte zuerst einen Textbereich markieren." }
        return matcherFromText(sourceText.substring(start, end))
    }

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

    private fun flexibleLiteral(value: String): String {
        if (value.isEmpty()) return ""
        // Mail and HTML content often contains Unicode separator characters such
        // as EN SPACE (U+2002) or non-breaking spaces. Treat all separators as
        // interchangeable whitespace so rules learned from one mail remain reusable.
        return value.split(Regex("[\\t\\p{Zs}]+"))
            .filter { it.isNotEmpty() }
            .joinToString("[\\t\\p{Zs}]+") { Regex.escape(it) }
    }

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
}
