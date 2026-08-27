package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.CaseMode
import cc.stkmn.shareparser.data.ExtractorRule
import cc.stkmn.shareparser.data.InputSource
import cc.stkmn.shareparser.data.MatcherRule
import cc.stkmn.shareparser.data.Profile
import cc.stkmn.shareparser.data.SharedPayload
import cc.stkmn.shareparser.data.ValueTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParserEngineTest {
    private val engine = ParserEngine()

    @Test
    fun extractsRegexGroup() {
        val profile = Profile(
            id = "1",
            name = "Mail",
            extractors = listOf(ExtractorRule("mailSubject", "(?m)^Subject: (.+)$", required = true))
        )
        assertEquals("Train 42", engine.extract("Subject: Train 42\nBody", profile)["mailSubject"])
    }

    @Test
    fun matchesSimilarTexts() {
        val profile = Profile("1", "Rail", matchers = listOf(MatcherRule("Booking (ICE|IC)")))
        assertTrue(engine.matchingProfiles("Booking ICE 612", listOf(profile)).isNotEmpty())
    }

    @Test
    fun extractedVariableCanTriggerProfile() {
        val profile = Profile(
            id = "1",
            name = "Variable trigger",
            extractors = listOf(
                ExtractorRule("booking", "Buchung: ([A-Z0-9-]+)", required = false)
            ),
            matchers = listOf(
                MatcherRule(regex = ".+", friendlyText = "booking erkannt", variableKey = "booking")
            )
        )
        assertTrue(engine.matchingProfiles("Buchung: ICE-612", listOf(profile)).isNotEmpty())
        assertTrue(engine.matchingProfiles("Keine Buchung enthalten", listOf(profile)).isEmpty())
    }

    @Test
    fun variableTriggerCanRestrictExtractedValue() {
        val profile = Profile(
            id = "1",
            name = "Restricted variable trigger",
            extractors = listOf(ExtractorRule("kind", "Typ: (.+)")),
            matchers = listOf(MatcherRule(regex = "Termin", ignoreCase = true, variableKey = "kind"))
        )
        assertTrue(engine.matchingProfiles("Typ: Terminbestätigung", listOf(profile)).isNotEmpty())
        assertTrue(engine.matchingProfiles("Typ: Rechnung", listOf(profile)).isEmpty())
    }

    @Test
    fun canExtractFromMailSubjectOnly() {
        val profile = Profile(
            "1",
            "Mail",
            extractors = listOf(
                ExtractorRule("booking", "Buchung ([A-Z0-9-]+)", source = InputSource.SUBJECT, required = true)
            )
        )
        val values = engine.extract(
            SharedPayload(text = "Vielen Dank.", subject = "Buchung ICE-612 bestätigt"),
            profile
        )
        assertEquals("ICE-612", values["booking"])
    }

    @Test
    fun appliesTransformationBlocksInOrder() {
        val profile = Profile(
            "1",
            "Transform",
            extractors = listOf(
                ExtractorRule(
                    key = "code",
                    regex = "Code: (.+)",
                    transforms = listOf(
                        ValueTransform.Trim,
                        ValueTransform.RegexReplace("\\s+", "-"),
                        ValueTransform.Prefix("ID-"),
                        ValueTransform.ChangeCase(CaseMode.UPPER)
                    )
                )
            )
        )
        assertEquals("ID-AB-42", engine.extract("Code:  ab 42  ", profile)["code"])
    }
}
