package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.CaseMode
import cc.stkmn.shareparser.data.ExtractorRule
import cc.stkmn.shareparser.data.InputSource
import cc.stkmn.shareparser.data.MatcherJoin
import cc.stkmn.shareparser.data.MatcherValueMode
import cc.stkmn.shareparser.data.ParseDirection
import cc.stkmn.shareparser.data.Profile
import cc.stkmn.shareparser.data.SharedPayload
import cc.stkmn.shareparser.data.ValueTransform

class ParserEngine {
    fun matchingProfiles(payload: SharedPayload, profiles: List<Profile>): List<Profile> {
        val matched = profiles.filter { profile ->
            if (!profile.enabled) return@filter false
            val triggerValues = availableValues(payload, profile)
            if (profile.matchers.isEmpty()) {
                true
            } else {
                profile.matchers.map { matcher ->
                    runCatching {
                        val source = if (matcher.variableKey.isBlank()) {
                            payload.combined
                        } else {
                            triggerValues[matcher.variableKey].orEmpty()
                        }
                        val matchedValue = when (matcher.valueMode) {
                            MatcherValueMode.EMPTY -> source.isBlank()
                            MatcherValueMode.NOT_EMPTY -> source.isNotBlank()
                            MatcherValueMode.REGEX -> {
                                val options = buildSet {
                                    add(RegexOption.MULTILINE)
                                    if (matcher.ignoreCase) add(RegexOption.IGNORE_CASE)
                                }
                                Regex(matcher.regex, options).containsMatchIn(source)
                            }
                        }
                        if (matcher.negate) !matchedValue else matchedValue
                    }.getOrDefault(false)
                }.let { results ->
                    var matchedResult = results.first()
                    for (index in 1 until results.size) {
                        matchedResult = when (profile.matchers[index].join) {
                            MatcherJoin.AND -> matchedResult && results[index]
                            MatcherJoin.OR -> matchedResult || results[index]
                        }
                    }
                    matchedResult
                }
            }
        }
        val specific = matched.filter { it.matchers.isNotEmpty() }
        return if (specific.isNotEmpty()) specific else matched
    }

    fun matchingProfiles(input: String, profiles: List<Profile>): List<Profile> =
        matchingProfiles(SharedPayload(text = input), profiles)

    fun extract(payload: SharedPayload, profile: Profile): Map<String, String> {
        val values = builtInValues(payload)
        for (rule in profile.extractors) {
            val source = extractionSource(payload, rule, values)
            val value = if (source == null) null else extractOne(source, rule, profile.parseDirection)
            if (value == null && rule.required) {
                val details = if (rule.sourceVariableKey.isBlank()) {
                    "Regex did not match: ${rule.regex}"
                } else {
                    "Source variable '${rule.sourceVariableKey}' was unavailable or regex did not match: ${rule.regex}"
                }
                throw ProcessingException(
                    userMessage = "Pflichtfeld '${rule.key}' konnte nicht erkannt werden.",
                    failingField = rule.key,
                    technicalDetails = details
                )
            }
            if (value != null) values[rule.key] = value
        }
        return values
    }

    fun extract(input: String, profile: Profile): Map<String, String> =
        extract(SharedPayload(text = input), profile)

    private fun availableValues(payload: SharedPayload, profile: Profile): Map<String, String> {
        val values = builtInValues(payload)
        profile.extractors.forEach { rule ->
            val source = extractionSource(payload, rule, values) ?: return@forEach
            runCatching { extractOne(source, rule, profile.parseDirection) }
                .getOrNull()
                ?.let { values[rule.key] = it }
        }
        return values
    }

    private fun extractionSource(
        payload: SharedPayload,
        rule: ExtractorRule,
        values: Map<String, String>
    ): String? = if (rule.sourceVariableKey.isBlank()) {
        sourceFor(payload, rule.source)
    } else {
        values[rule.sourceVariableKey]
    }

    private fun builtInValues(payload: SharedPayload) = linkedMapOf(
        "input" to payload.combined,
        "text" to payload.text,
        "subject" to payload.subject,
        "source_app" to payload.sourceApp,
        "source_package" to payload.sourcePackage,
        "file_name" to payload.fileName,
        "mime_type" to payload.mimeType
    )

    private fun sourceFor(payload: SharedPayload, source: InputSource): String = when (source) {
        InputSource.COMBINED -> payload.combined
        InputSource.TEXT -> payload.text
        InputSource.SUBJECT -> payload.subject
        InputSource.LINKS -> payload.linkTargets.joinToString("\n")
    }

    private fun extractOne(input: String, rule: ExtractorRule, direction: ParseDirection): String? {
        val regex = try {
            Regex(rule.regex, setOf(RegexOption.MULTILINE))
        } catch (e: Exception) {
            throw ProcessingException(
                "Ungültiger regulärer Ausdruck für '${rule.key}'.",
                rule.key,
                e.message ?: e.toString()
            )
        }

        val match = when (direction) {
            ParseDirection.TOP_DOWN -> regex.find(input)
            ParseDirection.BOTTOM_UP -> regex.findAll(input).lastOrNull()
        } ?: return null

        if (rule.group !in match.groupValues.indices) {
            throw ProcessingException(
                "Capture Group ${rule.group} existiert für '${rule.key}' nicht.",
                rule.key,
                "Regex '${rule.regex}' produced ${match.groupValues.size} groups including group 0"
            )
        }

        var value = match.groups[rule.group]?.value ?: return null
        for (transform in rule.transforms) {
            value = applyTransform(value, transform, rule.key)
        }
        return value
    }

    private fun applyTransform(value: String, transform: ValueTransform, key: String): String {
        return when (transform) {
            ValueTransform.Trim -> value.trim()
            is ValueTransform.Prefix -> transform.value + value
            is ValueTransform.Suffix -> value + transform.value
            is ValueTransform.ChangeCase -> when (transform.mode) {
                CaseMode.LOWER -> value.lowercase()
                CaseMode.UPPER -> value.uppercase()
            }
            is ValueTransform.RegexReplace -> {
                if (transform.regex.isEmpty()) {
                    value
                } else {
                    val options = if (transform.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                    try {
                        val pattern = if (transform.literal) Regex.escape(transform.regex) else transform.regex
                        Regex(pattern, options).replace(value, transform.replacement)
                    } catch (e: Exception) {
                        throw ProcessingException(
                            "Ersetzen-Baustein für '$key' ist ungültig.",
                            key,
                            e.message ?: e.toString()
                        )
                    }
                }
            }
        }
    }
}

class ProcessingException(
    val userMessage: String,
    val failingField: String? = null,
    val technicalDetails: String
) : RuntimeException(userMessage)
