package cc.stkmn.shareparser.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateEngineTest {
    @Test fun rendersAndUrlEncodes() {
        val result = TemplateEngine.render("https://example.test?q={{query|url}}", mapOf("query" to "ICE 612"))
        assertEquals("https://example.test?q=ICE+612", result)
    }
}
