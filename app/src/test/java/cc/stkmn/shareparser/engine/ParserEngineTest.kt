package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.CaseMode
import cc.stkmn.shareparser.data.ExtractorRule
import cc.stkmn.shareparser.data.InputSource
import cc.stkmn.shareparser.data.MatcherJoin
import cc.stkmn.shareparser.data.MatcherRule
import cc.stkmn.shareparser.data.MatcherValueMode
import cc.stkmn.shareparser.data.ParseDirection
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
        val profile = Profile("1", "Mail", extractors = listOf(ExtractorRule("mailSubject", "(?m)^Subject: (.+)$", required = true)))
        assertEquals("Train 42", engine.extract("Subject: Train 42\nBody", profile)["mailSubject"])
    }

    @Test
    fun bottomUpParsingUsesLastMatchingValue() {
        val input = "Datum: 01.01.2026\nAntworttext\n--- ursprüngliche Mail ---\nDatum: 14.12.2026"
        val rule = ExtractorRule("datum", "(?m)^Datum: (.+)$", required = true)
        val top = Profile("top", "Top", extractors = listOf(rule), parseDirection = ParseDirection.TOP_DOWN)
        val bottom = Profile("bottom", "Bottom", extractors = listOf(rule), parseDirection = ParseDirection.BOTTOM_UP)
        assertEquals("01.01.2026", engine.extract(input, top)["datum"])
        assertEquals("14.12.2026", engine.extract(input, bottom)["datum"])
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
            extractors = listOf(ExtractorRule("booking", "Buchung: ([A-Z0-9-]+)", required = false)),
            matchers = listOf(MatcherRule(regex = ".+", friendlyText = "booking erkannt", variableKey = "booking"))
        )
        assertTrue(engine.matchingProfiles("Buchung: ICE-612", listOf(profile)).isNotEmpty())
        assertTrue(engine.matchingProfiles("Keine Buchung enthalten", listOf(profile)).isEmpty())
    }

    @Test
    fun sourcePackageCanTriggerProfile() {
        val fairEmail = Profile(
            "1",
            "FairEmail",
            matchers = listOf(MatcherRule(Regex.escape("eu.faircode.email"), variableKey = "source_package"))
        )
        val payload = SharedPayload(text = "Termin", sourcePackage = "eu.faircode.email", sourceApp = "FairEmail")
        assertEquals(listOf("FairEmail"), engine.matchingProfiles(payload, listOf(fairEmail)).map { it.name })
    }

    @Test
    fun specificProfileWinsOverEmptyFallbackProfile() {
        val fallback = Profile("fallback", "Fallback")
        val specific = Profile("specific", "Termin", matchers = listOf(MatcherRule(Regex.escape("Terminbestätigung"))))
        val matched = engine.matchingProfiles("Ihre Terminbestätigung", listOf(fallback, specific))
        assertEquals(listOf("Termin"), matched.map { it.name })
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
            extractors = listOf(ExtractorRule("booking", "Buchung ([A-Z0-9-]+)", source = InputSource.SUBJECT, required = true))
        )
        val values = engine.extract(SharedPayload(text = "Vielen Dank.", subject = "Buchung ICE-612 bestätigt"), profile)
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
                        ValueTransform.RegexReplace("\\s+", "-", literal = false),
                        ValueTransform.Prefix("ID-"),
                        ValueTransform.ChangeCase(CaseMode.UPPER)
                    )
                )
            )
        )
        assertEquals("ID-AB-42", engine.extract("Code:  ab 42  ", profile)["code"])
    }

    @Test
    fun literalRemovalHandlesParenthesesWithoutRegexEscaping() {
        val profile = Profile(
            "1",
            "Literal",
            extractors = listOf(
                ExtractorRule(
                    key = "text_part",
                    regex = "Wert: (.+)",
                    transforms = listOf(ValueTransform.RegexReplace("(intern)", "", literal = true), ValueTransform.Trim)
                )
            )
        )
        assertEquals("Termin", engine.extract("Wert: Termin (intern)", profile)["text_part"])
    }

    @Test
    fun derivedVariablesCanSplitPreviouslyExtractedValue() {
        val splitRegex = "^\\s*(\\S+)\\s+(.+?)\\s*$"
        val profile = Profile(
            "1",
            "Adresse",
            extractors = listOf(
                ExtractorRule("PLZ_ort", "PLZ_ort: (.+)", required = true),
                ExtractorRule(
                    key = "PLZ",
                    regex = splitRegex,
                    group = 1,
                    required = true,
                    sourceVariableKey = "PLZ_ort"
                ),
                ExtractorRule(
                    key = "Ort",
                    regex = splitRegex,
                    group = 2,
                    required = true,
                    sourceVariableKey = "PLZ_ort"
                )
            )
        )

        val values = engine.extract("PLZ_ort: 59000 Lünen", profile)
        assertEquals("59000", values["PLZ"])
        assertEquals("Lünen", values["Ort"])
    }

    @Test
    fun fileMetadataIsAvailableForRecognition() {
        val profile = Profile(
            "1",
            "Markdown",
            matchers = listOf(
                MatcherRule(regex = ".+\\.md", variableKey = "file_name"),
                MatcherRule(regex = "text/markdown", variableKey = "mime_type")
            )
        )
        val payload = SharedPayload(
            text = "# Termin",
            mimeType = "text/markdown",
            fileName = "termin.md"
        )
        assertEquals(listOf("Markdown"), engine.matchingProfiles(payload, listOf(profile)).map { it.name })
    }

    @Test
    fun matcherCanUseOrFromSecondCriterion() {
        val profile = Profile(
            "1",
            "Either",
            matchers = listOf(
                MatcherRule(regex = "Alpha"),
                MatcherRule(regex = "Beta", join = MatcherJoin.OR)
            )
        )
        assertTrue(engine.matchingProfiles("Beta", listOf(profile)).isNotEmpty())
    }

    @Test
    fun variableMatcherCanCheckEmptyAndNotEmpty() {
        val extractor = ExtractorRule("postal", "PLZ: ([0-9]+)")
        val emptyProfile = Profile(
            "empty",
            "Empty",
            extractors = listOf(extractor),
            matchers = listOf(
                MatcherRule(
                    regex = "",
                    variableKey = "postal",
                    valueMode = MatcherValueMode.EMPTY
                )
            )
        )
        val presentProfile = Profile(
            "present",
            "Present",
            extractors = listOf(extractor),
            matchers = listOf(
                MatcherRule(
                    regex = "",
                    variableKey = "postal",
                    valueMode = MatcherValueMode.NOT_EMPTY
                )
            )
        )

        assertEquals(listOf("Empty"), engine.matchingProfiles("Keine PLZ", listOf(emptyProfile)).map { it.name })
        assertEquals(listOf("Present"), engine.matchingProfiles("PLZ: 59000", listOf(presentProfile)).map { it.name })
    }


    @Test
    fun sourceAppCriterionCanBeNegated() {
        val profile = Profile(
            "negated",
            "Not FairEmail",
            matchers = listOf(
                MatcherRule(
                    regex = Regex.escape("eu.faircode.email"),
                    variableKey = "source_package",
                    negate = true
                )
            )
        )
        assertTrue(
            engine.matchingProfiles(
                SharedPayload(text = "Termin", sourcePackage = "com.example.other"),
                listOf(profile)
            ).isNotEmpty()
        )
        assertTrue(
            engine.matchingProfiles(
                SharedPayload(text = "Termin", sourcePackage = "eu.faircode.email"),
                listOf(profile)
            ).isEmpty()
        )
    }

}
