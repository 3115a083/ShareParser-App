package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.CaseMode
import cc.stkmn.shareparser.data.ExtractorRule
import cc.stkmn.shareparser.data.InputSource
import cc.stkmn.shareparser.data.Profile
import cc.stkmn.shareparser.data.SharedPayload
import cc.stkmn.shareparser.data.ValueTransform

class ParserEngine {
    fun matchingProfiles(payload: SharedPayload, profiles: List<Profile>): List<Profile> = profiles.filter { profile ->
        profile.enabled && profile.matchers.all { matcher ->
            runCatching {
                val options = buildSet {
                    add(RegexOption.MULTILINE)
                    if (matcher.ignoreCase) add(RegexOption.IGNORE_CASE)
                }
                Regex(matcher.regex, options).containsMatchIn(payload.combined)
            }.getOrDefault(false)
        }
    }

    fun matchingProfiles(input: String, profiles: List<Profile>): List<Profile> =
        matchingProfiles(SharedPayload(text = input), profiles)

    fun extract(payload: SharedPayload, profile: Profile): Map<String, String> {
        val values = linkedMapOf(
            "input" to payload.combined,
            "text" to payload.text,
            "subject" to payload.subject
        )
        for (rule in profile.extractors) {
            val value = extractOne(sourceFor(payload, rule.source), rule)
            if (value == null && rule.required) {
                throw ProcessingException(
                    userMessage = "Pflichtfeld '${rule.key}' konnte nicht erkannt werden.",
                    failingField = rule.key,
                    technicalDetails = "Regex did not match: ${rule.regex}"
                )
            }
            if (value != null) values[rule.key] = value
        }
        return values
    }

    fun extract(input: String, profile: Profile): Map<String, String> =
        extract(SharedPayload(text = input), profile)

    private fun sourceFor(payload: SharedPayload, source: InputSource): String = when (source) {
        InputSource.COMBINED -> payload.combined
        InputSource.TEXT -> payload.text
        InputSource.SUBJECT -> payload.subject
    }

    private fun extractOne(input: String, rule: ExtractorRule): String? {
        val match = try {
            Regex(rule.regex, setOf(RegexOption.MULTILINE)).find(input)
        } catch (e: Exception) {
            throw ProcessingException(
                "Ungültiger regulärer Ausdruck für '${rule.key}'.",
                rule.key,
                e.message ?: e.toString()
            )
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

    private fun applyTransform(value: String, transform: ValueTransform, key: String): String = when (transform) {
        ValueTransform.Trim -> value.trim()
        is ValueTransform.Prefix -> transform.value + value
        is ValueTransform.Suffix -> value + transform.value
        is ValueTransform.ChangeCase -> when (transform.mode) {
            CaseMode.LOWER -> value.lowercase()
            CaseMode.UPPER -> value.uppercase()
        }
        is ValueTransform.RegexReplace -> {
            val options = if (transform.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            try {
                Regex(transform.regex, options).replace(value, transform.replacement)
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

class ProcessingException(
    val userMessage: String,
    val failingField: String? = null,
    val technicalDetails: String
) : RuntimeException(userMessage)
