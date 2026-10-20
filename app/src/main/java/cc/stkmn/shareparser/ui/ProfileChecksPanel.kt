package cc.stkmn.shareparser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material3.Text as CheckText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cc.stkmn.shareparser.AppLocale
import cc.stkmn.shareparser.data.*
import cc.stkmn.shareparser.engine.*
import kotlinx.coroutines.*

private fun ct(de:String,en:String)=AppLocale.text(de,en)

@Composable
internal fun ProfileChecksPanel(profile:Profile,sample:SharedPayload?,canPersist:Boolean) {
    val context=LocalContext.current
    val store=remember { ProfileExampleStore(context) }
    val scope=rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var examples by remember(profile.id) { mutableStateOf<List<ProfileExample>>(emptyList()) }
    var loaded by remember(profile.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ProfilePreview?>(null) }
    var results by remember { mutableStateOf<Map<String,List<String>>>(emptyMap()) }
    var resetConfirm by remember { mutableStateOf(false) }

    fun persist(next:List<ProfileExample>) {
        busy=true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.save(profile.id,next) }
                examples=next
                message=null
                loaded=true
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) { message=error.message }
            finally { busy=false }
        }
    }

    LaunchedEffect(open,profile.id) {
        if(!open) return@LaunchedEffect
        try {
            examples=withContext(Dispatchers.IO) { store.read(profile.id) }
            loaded=true
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(error:Exception) { loaded=false;message=error.message }
    }
    LaunchedEffect(open,profile,sample,examples) {
        if(!open) return@LaunchedEffect
        preview=null
        results=emptyMap()
        delay(250)
        val next=withContext(Dispatchers.Default) {
            val current=sample?.let { ProfilePreviewEngine.inspect(profile,it) }
            val checks=examples.associate { example ->
                currentCoroutineContext().ensureActive()
                example.id to example.differences(profile)
            }
            current to checks
        }
        preview=next.first
        results=next.second
    }

    OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()) {
        CheckText(ct("Vorschau & Profiltests","Preview & profile tests"))
    }
    if(!open)return

    Dialog(onDismissRequest={open=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(modifier=Modifier.widthIn(max=720.dp).fillMaxWidth().fillMaxHeight(.9f),
            shape=MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                CheckText(ct("Vorschau & Profiltests","Preview & profile tests"),
                    style=MaterialTheme.typography.titleLarge,modifier=Modifier.semantics { heading() })
                CheckText(ct("Nur Vorschau. Es werden keine Apps geöffnet und keine Webhooks gesendet.",
                    "Preview only. No apps are opened and no webhooks are sent."),
                    style=MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),
                    contentPadding=PaddingValues(vertical=12.dp)) {
                    item {
                        if(!profile.enabled) CheckText(ct("Dieses Profil ist deaktiviert.","This profile is disabled."),
                            color=MaterialTheme.colorScheme.error)
                        if(sample==null) CheckText(ct("Teile zuerst ein Beispiel in den Editor.","First share an example into the editor."))
                        else if(preview==null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else {
                            val current=preview!!
                            CheckText(if(current.matches)ct("Erkennung: Profil passt","Recognition: profile matches")
                                else ct("Erkennung: Profil passt nicht","Recognition: profile does not match"),
                                style=MaterialTheme.typography.titleMedium)
                            if(current.error.isNotBlank()) CheckText(current.error,color=MaterialTheme.colorScheme.error)
                            if(current.values.isNotEmpty()) CheckText(ct("Erkannte Variablen","Detected variables"),
                                style=MaterialTheme.typography.titleSmall)
                            current.values.forEach { (key,value) ->
                                SelectionContainer { CheckText(key+" = "+value.take(2000)) }
                            }
                            if(current.matches) CheckText(ct("Mögliche Aktionen","Available action choices"),
                                style=MaterialTheme.typography.titleSmall)
                        }
                    }
                    items(preview?.actions.orEmpty(),key={it.id}) { action ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                CheckText(action.name.ifBlank { action.kind },style=MaterialTheme.typography.titleSmall)
                                CheckText(if(action.available)ct("Bedingung erfüllt","Condition met")
                                    else ct("Bedingung nicht erfüllt","Condition not met"))
                                if(action.automaticWebhook) CheckText(ct("Automatischer Webhook","Automatic webhook"))
                                CheckText(if(action.confirm)ct("Auto-Ausführung: Bestätigung erforderlich","Automatic execution: confirmation required")
                                    else ct("Auto-Ausführung ohne Rückfrage erlaubt","Automatic execution without a prompt allowed"),
                                    style=MaterialTheme.typography.bodySmall)
                                action.fields.forEach { (name,value) ->
                                    SelectionContainer { CheckText(name+": "+value.take(2000)+
                                        if(value.length>2000)ct(" [gekürzt]"," [truncated]") else "") }
                                }
                                if(action.children.isNotEmpty()) CheckText(ct("Kettenschritte: ","Chain steps: ")+
                                    action.children.joinToString(" → ") { id ->
                                        profile.actions.firstOrNull { it.id==id }?.friendlyName ?: id
                                    })
                                if(action.error.isNotBlank()) CheckText(action.error,color=MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    item {
                        HorizontalDivider()
                        CheckText(ct("Lokale Beispieltests","Local example tests"),style=MaterialTheme.typography.titleMedium)
                        CheckText(ct("Beispielinhalte bleiben lokal und werden nicht mit dem Profil exportiert.",
                            "Example contents stay local and are not included in profile exports."),
                            style=MaterialTheme.typography.bodySmall)
                        if(!canPersist) CheckText(ct("Speichere zuerst das Profil, um Beispiele zu sichern.",
                            "Save the profile before storing examples."))
                        OutlinedTextField(value=label,onValueChange={label=it.take(80)},
                            label={CheckText(ct("Beispielname","Example name"))},
                            modifier=Modifier.fillMaxWidth(),singleLine=true)
                        Button(enabled=canPersist&&loaded&&!busy&&sample!=null&&preview!=null&&
                            preview!!.error.isBlank()&&examples.size<ProfileExampleStore.MAX_EXAMPLES,
                            onClick={
                                try {
                                    val example=store.capture(label,sample!!,preview!!)
                                    persist(examples+example)
                                    label=""
                                } catch(error:Exception) { message=error.message }
                            },modifier=Modifier.fillMaxWidth()) {
                            CheckText(ct("Beispiel mit aktuellen Sollwerten speichern","Save example with current expectations"))
                        }
                        if(!loaded&&message!=null) TextButton(onClick={resetConfirm=true}) {
                            CheckText(ct("Lokale Testsammlung zurücksetzen","Reset local test collection"))
                        }
                        message?.let { CheckText(it,color=MaterialTheme.colorScheme.error) }
                        CheckText(examples.size.toString()+" / "+ProfileExampleStore.MAX_EXAMPLES)
                    }
                    items(examples,key={it.id}) { example ->
                        var showInput by remember(example.id) { mutableStateOf(false) }
                        val differences=results[example.id]
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                CheckText(example.label,style=MaterialTheme.typography.titleSmall)
                                CheckText(when {
                                    differences==null -> ct("Wird geprüft…","Checking…")
                                    differences.isEmpty() -> ct("Bestanden","Passed")
                                    else -> ct("Abweichung: ","Changed: ")+differences.joinToString("; ")
                                })
                                TextButton(onClick={showInput=!showInput}) {
                                    CheckText(ct("Beispielinhalt anzeigen / ausblenden","Show / hide example input"))
                                }
                                if(showInput) SelectionContainer {
                                    CheckText((example.payload.subject+"\n"+example.payload.text).take(4000))
                                }
                                TextButton(enabled=!busy,onClick={persist(examples.filterNot {it.id==example.id})}) {
                                    CheckText(ct("Dieses Beispiel löschen","Delete this example"))
                                }
                            }
                        }
                    }
                }
                TextButton(onClick={open=false},modifier=Modifier.fillMaxWidth()) {
                    CheckText(ct("Schließen","Close"))
                }
            }
        }
    }
    if(resetConfirm) AlertDialog(onDismissRequest={resetConfirm=false},
        title={CheckText(ct("Lokale Beispiele löschen?","Delete local examples?"))},
        text={CheckText(ct("Nur die Beispiele dieses Profils werden entfernt.","Only this profile's examples will be removed."))},
        confirmButton={TextButton(onClick={persist(emptyList());resetConfirm=false}) { CheckText(ct("Löschen","Delete")) }},
        dismissButton={TextButton(onClick={resetConfirm=false}) { CheckText(ct("Abbrechen","Cancel")) }})
}
