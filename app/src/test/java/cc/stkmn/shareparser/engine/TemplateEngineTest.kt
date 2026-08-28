package cc.stkmn.shareparser.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TemplateEngineTest {
    @Test
    fun rendersAndUrlEncodes() {
        val result = TemplateEngine.render("https://example.test?q={{query|url}}", mapOf("query" to "ICE 612"))
        assertEquals("https://example.test?q=ICE+612", result)
    }

    @Test
    fun builtInModifiersCanBeUsed() {
        val result = TemplateEngine.render("{{name|trim}}/{{name|upper}}", mapOf("name" to "  Ab c  "))
        assertEquals("Ab c/  AB C  ", result)
    }

    @Test
    fun rendersMultipleTokensWithoutRegexInitialization() {
        val result = TemplateEngine.render(
            "https://example.test/{{category}}?id={{id|url}}&title={{title|trim}}",
            mapOf("category" to "ticket", "id" to "ICE 612", "title" to " Berlin ")
        )
        assertEquals("https://example.test/ticket?id=ICE+612&title=Berlin", result)
    }

    @Test
    fun variablesFindsPlainAndModifiedTokens() {
        assertEquals(
            setOf("date", "location", "booking-id"),
            TemplateEngine.variables("{{date}} {{location|trim}} {{booking-id|url}}")
        )
    }

    @Test
    fun malformedTokenIsPreservedInsteadOfCrashingEngine() {
        assertEquals(
            "before {{bad|url|upper}} after",
            TemplateEngine.render("before {{bad|url|upper}} after", mapOf("bad" to "value"))
        )
    }

    @Test
    fun missingValueIsReported() {
        assertFailsWith<ProcessingException> {
            TemplateEngine.render("{{missing}}", emptyMap())
        }
    }
}
