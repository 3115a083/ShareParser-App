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
    fun missingValueIsReported() {
        assertFailsWith<ProcessingException> {
            TemplateEngine.render("{{missing}}", emptyMap())
        }
    }
}
