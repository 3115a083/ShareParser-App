package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class PreviewAction(
    val id: String,
    val name: String,
    val kind: String,
    val available: Boolean,
    val confirm: Boolean,
    val automaticWebhook: Boolean = false,
    val fields: Map<String,String> = emptyMap(),
    val children: List<String> = emptyList(),
    val error: String = ""
)
data class ProfilePreview(
    val matches: Boolean,
    val values: Map<String,String>,
    val actions: List<PreviewAction>,
    val error: String = ""
) {
    fun actionDigest(): String {
        val encoded = Json.encodeToString(actions.map { it.copy(name = "") })
        return MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

/** Pure, bounded planning. It never receives an Android context or an action executor. */
object ProfilePreviewEngine {
    fun inspect(profile: Profile, payload: SharedPayload): ProfilePreview {
        return try {
            val parser=ParserEngine()
            val matches=parser.matchingProfiles(payload,listOf(profile.copy(enabled=true))).isNotEmpty()
            if(!matches) return ProfilePreview(false,emptyMap(),emptyList())
            val allValues=parser.extract(payload,profile)
            val userKeys=profile.extractors.map { it.key.lowercase() }.toSet()
            val actions=profile.actions.map { action ->
                val available=ActionConditionEvaluator.isAvailable(action,profile.actions,allValues)
                val fields=linkedMapOf<String,String>()
                var issue=""
                if(available) {
                    for((key,value) in templates(action)) {
                        try { fields[key]=if(value.isBlank()) "" else TemplateEngine.render(value,allValues) }
                        catch(error: ProcessingException) {
                            fields[key]="[Wert fehlt]"
                            issue=listOf(issue,error.userMessage).filter { it.isNotBlank() }.joinToString("; ")
                        }
                    }
                    if(action is ProcessingAction.Chain) {
                        try { ActionChainRunner.validate(action,profile.actions) }
                        catch(error: Exception) { issue=error.message.orEmpty() }
                    }
                }
                PreviewAction(
                    action.id,action.friendlyName,action.javaClass.simpleName,available,action.confirmBeforeAutoRun,
                    action is ProcessingAction.Webhook && action.mode==WebhookMode.ALWAYS,
                    fields,
                    if(action is ProcessingAction.Chain) action.actionIds else emptyList(),
                    issue
                )
            }
            ProfilePreview(true,allValues.filterKeys { it in userKeys },actions)
        } catch(cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch(error: Exception) {
            ProfilePreview(true,emptyMap(),emptyList(),
                if(error is ProcessingException) error.userMessage else error.message ?: "Vorschau fehlgeschlagen.")
        }
    }

    private fun templates(action: ProcessingAction): List<Pair<String,String>> = when(action) {
        is ProcessingAction.Calendar -> listOf("Titel" to action.titleTemplate,"Beschreibung" to action.descriptionTemplate,
            "Ort" to action.locationTemplate,"Beginn" to action.startTemplate,"Ende" to action.endTemplate,
            "Dauer" to action.durationTemplate,"Kalender" to action.calendarNameTemplate)
        is ProcessingAction.Url -> listOf("URL" to action.urlTemplate)
        is ProcessingAction.Share -> listOf("Betreff" to action.subjectTemplate,"Text" to action.textTemplate,
            "Dateiname" to action.fileNameTemplate,"Unterordner" to action.relativePathTemplate)
        is ProcessingAction.Target -> listOf("Ziel" to action.targetTemplate)
        is ProcessingAction.Webhook -> listOf("URL" to action.urlTemplate,"Inhalt" to action.bodyTemplate)
        is ProcessingAction.Chain -> listOf("Modus" to action.mode.name,"Pause (ms)" to action.delayMs.toString())
    }
}
