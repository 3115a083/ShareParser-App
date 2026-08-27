package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.InputSource
import cc.stkmn.shareparser.data.Profile
import cc.stkmn.shareparser.data.SharedPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuidedRuleFactoryTest {
    private val parser = ParserEngine()

    @Test
    fun createsReusableRuleFromLabelValueLine() {
        val sample = SharedPayload(text = "Datum: 14.12.2026\nOrt: Berlin")
        val candidate = GuidedRuleFactory.candidates(sample).first { it.label == "Datum" }
        val rule = GuidedRuleFactory.extractor(candidate, "datum", required = true)
        val profile = Profile("1", "Termin", extractors = listOf(rule))

        assertEquals("15.12.2026", parser.extract("Datum: 15.12.2026\nOrt: Hamburg", profile)["datum"])
    }

    @Test
    fun createsRuleFromSelectedPartWithSurroundingContext() {
        val text = "Buchungsnummer: ABC-123 / Status: bestätigt"
        val start = text.indexOf("ABC-123")
        val rule = GuidedRuleFactory.extractorFromSelection(
            sourceText = text,
            selectionStart = start,
            selectionEnd = start + "ABC-123".length,
            key = "booking",
            source = InputSource.TEXT,
            required = true
        )
        val profile = Profile("1", "Booking", extractors = listOf(rule))

        assertEquals("XYZ-999", parser.extract("Buchungsnummer: XYZ-999 / Status: bestätigt", profile)["booking"])
    }

    @Test
    fun matcherUsesLiteralTextInsteadOfRegexSyntax() {
        val matcher = GuidedRuleFactory.matcherFromText("Termin (Arbeit)")
        val profile = Profile("1", "Termin", matchers = listOf(matcher))
        assertTrue(parser.matchingProfiles("Ihre Termin (Arbeit) Bestätigung", listOf(profile)).isNotEmpty())
    }
}
