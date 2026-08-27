package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.ExtractorRule
import cc.stkmn.shareparser.data.MatcherRule
import cc.stkmn.shareparser.data.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParserEngineTest {
    private val engine = ParserEngine()

    @Test fun extractsRegexGroup() {
        val profile = Profile(
            id = "1", name = "Mail",
            extractors = listOf(ExtractorRule("subject", "(?m)^Subject: (.+)$", required = true))
        )
        assertEquals("Train 42", engine.extract("Subject: Train 42\nBody", profile)["subject"])
    }

    @Test fun matchesSimilarTexts() {
        val profile = Profile("1", "Rail", matchers = listOf(MatcherRule("Booking (ICE|IC)")))
        assertTrue(engine.matchingProfiles("Booking ICE 612", listOf(profile)).isNotEmpty())
    }
}
