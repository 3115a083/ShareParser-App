package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.*
import kotlin.test.*
import org.junit.Test

class ProfilePreviewTest {
    private fun profile()=Profile("p","Test",matchers=listOf(MatcherRule("ID:")),
        extractors=listOf(ExtractorRule("id","ID: ([A-Z]+)",required=true)),
        actions=listOf(ProcessingAction.Url("a","Öffnen",urlTemplate="https://example.org/?id={{id|url}}")))

    @Test fun previewsResolvedTargetsWithoutExecution() {
        val result=ProfilePreviewEngine.inspect(profile(),SharedPayload("ID: ABC"))
        assertTrue(result.matches)
        assertEquals("ABC",result.values["id"])
        assertEquals("https://example.org/?id=ABC",result.actions.single().fields["URL"])
        assertTrue(result.actions.single().confirm)
    }
    @Test fun missingRequiredValueIsVisible() {
        val result=ProfilePreviewEngine.inspect(profile(),SharedPayload("ID: 123"))
        assertTrue(result.error.contains("Pflichtfeld"))
    }
    @Test fun localExampleDetectsExtractionAndActionChanges() {
        val p=profile();val input=SharedPayload("ID: ABC")
        val result=ProfilePreviewEngine.inspect(p,input)
        val test=ProfileExample(label="ABC",payload=input,expectedMatch=true,
            expectedValues=result.values,expectedActions=result.actionDigest())
        assertTrue(test.differences(p).isEmpty())
        val changed=p.copy(actions=listOf(ProcessingAction.Url("a","Öffnen",urlTemplate="https://other.example/{{id}}")))
        assertTrue(test.differences(changed).contains("Aktionsplan geändert"))
        val broken=p.copy(extractors=listOf(ExtractorRule("id","ID: (.)")))
        assertTrue(test.differences(broken).any {it.contains("Variable")})
    }
    @Test fun negativeExampleDetectsRecognitionRegression() {
        val p=profile();val input=SharedPayload("unrelated")
        val result=ProfilePreviewEngine.inspect(p,input)
        assertFalse(result.matches)
        val test=ProfileExample(label="negativ",payload=input,expectedMatch=false,
            expectedValues=result.values,expectedActions=result.actionDigest())
        assertTrue(test.differences(p).isEmpty())
        assertTrue(test.differences(p.copy(matchers=emptyList(),extractors=emptyList()))
            .any {it.contains("Profilzuordnung")})
    }
    @Test fun skippedActionHasNoResolvedSideEffectsAndWebhookIsMarked() {
        val action=ProcessingAction.Webhook("w","Webhook",urlTemplate="https://127.0.0.1:1/",
            mode=WebhookMode.ALWAYS,condition=ActionCondition(listOf(ActionConditionClause("id",ActionConditionMode.EMPTY))))
        val result=ProfilePreviewEngine.inspect(profile().copy(actions=listOf(action)),SharedPayload("ID: ABC"))
        assertFalse(result.actions.single().available)
        assertTrue(result.actions.single().fields.isEmpty())
        assertTrue(result.actions.single().automaticWebhook)
    }
}
