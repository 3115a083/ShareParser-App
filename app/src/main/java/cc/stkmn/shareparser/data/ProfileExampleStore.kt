package cc.stkmn.shareparser.data

import android.content.Context
import android.util.AtomicFile
import cc.stkmn.shareparser.engine.ProfilePreview
import cc.stkmn.shareparser.engine.ProfilePreviewEngine
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ProfileExample(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val payload: SharedPayload,
    val expectedMatch: Boolean,
    val expectedValues: Map<String,String>,
    val expectedActions: String
) {
    fun differences(profile: Profile): List<String> {
        val actual=ProfilePreviewEngine.inspect(profile,payload)
        return buildList {
            if(actual.error.isNotBlank()) add(actual.error)
            if(actual.matches!=expectedMatch) add("Profilzuordnung geändert")
            (expectedValues.keys+actual.values.keys).sorted().forEach { key ->
                if(expectedValues[key]!=actual.values[key]) add("Variable geändert: "+key)
            }
            if(actual.actionDigest()!=expectedActions) add("Aktionsplan geändert")
        }.take(20)
    }
}

/** Explicit examples stay local; Profile/ProfileBundle never include this file. */
class ProfileExampleStore(context: Context) {
    private val directory=File(context.applicationContext.filesDir,"profile-examples")
    private val json=Json { ignoreUnknownKeys=true }
    companion object {
        private val lock=Any()
        const val MAX_EXAMPLES=10
        private const val MAX_FILE_BYTES=2*1024*1024
    }
    private fun file(profileId: String): AtomicFile {
        val id=java.security.MessageDigest.getInstance("SHA-256").digest(profileId.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        check(directory.exists() || directory.mkdirs()) { "Beispielordner konnte nicht angelegt werden." }
        return AtomicFile(File(directory,id+".json"))
    }
    fun read(profileId: String): List<ProfileExample> = synchronized(lock) {
        val file=file(profileId)
        if(!file.baseFile.exists()&&!File(file.baseFile.path+".bak").exists()) return@synchronized emptyList()
        file.openRead().use { input ->
            val output=java.io.ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            while(true) {
                val count=input.read(buffer)
                if(count<0)break
                require(count<=MAX_FILE_BYTES-output.size()) { "Testsammlung ist zu groß." }
                output.write(buffer,0,count)
            }
            val bytes=output.toByteArray()
            json.decodeFromString<List<ProfileExample>>(bytes.toString(Charsets.UTF_8))
                .also { require(it.size<=MAX_EXAMPLES) }
        }
    }
    fun save(profileId: String, examples: List<ProfileExample>) = synchronized(lock) {
        require(examples.size<=MAX_EXAMPLES) { "Höchstens 10 Beispiele pro Profil." }
        val bytes=json.encodeToString(examples).toByteArray()
        require(bytes.size<=MAX_FILE_BYTES) { "Testsammlung ist größer als 2 MiB." }
        val target=file(profileId)
        val output=target.startWrite()
        try { output.write(bytes);target.finishWrite(output) }
        catch(error: Exception) { target.failWrite(output);throw error }
    }
    fun capture(label: String, payload: SharedPayload, preview: ProfilePreview): ProfileExample {
        require(json.encodeToString(payload).toByteArray().size<=64*1024) { "Beispiel ist größer als 64 KiB." }
        require(preview.error.isBlank()) { "Bitte zuerst die Vorschaufehler beheben." }
        return ProfileExample(label=label.trim().take(80).ifBlank { "Beispiel" },payload=payload,
            expectedMatch=preview.matches,expectedValues=preview.values,expectedActions=preview.actionDigest())
    }
    fun delete(profileId: String) = synchronized(lock) { file(profileId).delete() }
}
