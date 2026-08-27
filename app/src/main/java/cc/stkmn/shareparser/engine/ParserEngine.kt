package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.ExtractorRule
import cc.stkmn.shareparser.data.Profile

class ParserEngine {
    fun matchingProfiles(input: String, profiles: List<Profile>): List<Profile> = profiles.filter { profile ->
        profile.enabled && (profile.matchers.isEmpty() || profile.matchers.all { matcher ->
            Regex(matcher.regex, if (matcher.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()).containsMatchIn(input)
        })
    }

    fun extract(input: String, profile: Profile): Map<String, String> {
        val values = linkedMapOf("input" to input)
        for (rule in profile.extractors) {
            val value = extractOne(input, rule)
            if (value == null && rule.required) throw ProcessingException(
                userMessage = "Pflichtfeld '${rule.key}' konnte nicht erkannt werden.",
                failingField = rule.key,
                technicalDetails = "Regex did not match: ${rule.regex}"
            )
            if (value != null) values[rule.key] = value
        }
        return values
    }

    private fun extractOne(input: String, rule: ExtractorRule): String? {
        val match = try { Regex(rule.regex, setOf(RegexOption.MULTILINE)).find(input) }
        catch (e: Exception) { throw ProcessingException("Ungültiger regulärer Ausdruck für '${rule.key}'.", rule.key, e.message ?: e.toString()) }
        val raw = match?.groups?.getOrNull(rule.group)?.value ?: return null
        return if (rule.trim) raw.trim() else raw
    }
}

class ProcessingException(
    val userMessage: String,
    val failingField: String? = null,
    val technicalDetails: String
) : RuntimeException(userMessage)
