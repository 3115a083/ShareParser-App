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
        val rule = GuidedRuleFactory.extractorFromSelection(text, start, start + 7, "booking", InputSource.TEXT, true)
        val profile = Profile("1", "Booking", extractors = listOf(rule))
        assertEquals("XYZ-999", parser.extract("Buchungsnummer: XYZ-999 / Status: bestätigt", profile)["booking"])
    }

    @Test
    fun suggestsStreetAddressWithoutColonSeparator() {
        val sample = SharedPayload(text = "Straße Hausnummer  Teststraße 151\n12345 Berlin")
        val address = GuidedRuleFactory.candidates(sample).first { it.suggestedKey == "adresse" }
        assertEquals("Teststraße 151", address.value)
        val rule = GuidedRuleFactory.extractor(address, "adresse")
        val profile = Profile("1", "Adresse", extractors = listOf(rule))
        assertEquals("Beispielstraße 7", parser.extract("Straße Hausnummer  Beispielstraße 7", profile)["adresse"])
    }

    @Test
    fun sanitizeKeyAllowsTemporarilyBlankEditorValue() {
        assertEquals("", GuidedRuleFactory.sanitizeKey(""))
        assertEquals("mein_feld", GuidedRuleFactory.sanitizeKey("mein feld"))
    }

    @Test
    fun matcherUsesLiteralTextInsteadOfRegexSyntax() {
        val matcher = GuidedRuleFactory.matcherFromText("Termin (Arbeit)")
        val profile = Profile("1", "Termin", matchers = listOf(matcher))
        assertTrue(parser.matchingProfiles("Ihre Termin (Arbeit) Bestätigung", listOf(profile)).isNotEmpty())
    }

    @Test
    fun matcherCanBeCreatedFromSelectedText() {
        val text = "Ihre feste Profilkennung ABC erscheint hier"
        val start = text.indexOf("Profilkennung ABC")
        val matcher = GuidedRuleFactory.matcherFromSelection(text, start, start + "Profilkennung ABC".length)
        val profile = Profile("1", "Selected", matchers = listOf(matcher))
        assertTrue(parser.matchingProfiles("Neue Mail mit Profilkennung ABC und anderen Werten", listOf(profile)).isNotEmpty())
    }

    @Test
    fun selectedVariableCapturesOnlySelectedPart() {
        val text = "Ort: Berlin / Raum: 12"
        val start = text.indexOf("Berlin")
        val rule = GuidedRuleFactory.extractorFromSelection(
            text,
            start,
            start + "Berlin".length,
            "ort",
            InputSource.TEXT,
            true
        )
        val profile = Profile("1", "Ort", extractors = listOf(rule))
        assertEquals("Hamburg", parser.extract("Ort: Hamburg / Raum: 12", profile)["ort"])
    }

    @Test
    fun suggestsLinksEmailAndPhoneTargets() {
        val sample = SharedPayload(
            text = "Web https://example.com/test\nMail mailto:test@example.com\nTelefon tel:+491701234567"
        )
        val candidates = GuidedRuleFactory.candidates(sample)
        assertTrue(candidates.any { it.suggestedKey == "link" && it.value.startsWith("https://") })
        assertTrue(candidates.any { it.suggestedKey == "email" && it.value.startsWith("mailto:") })
        assertTrue(candidates.any { it.suggestedKey == "telefon" && it.value.startsWith("tel:") })
    }

}
