package cc.stkmn.shareparser.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ProfileRepository(context: Context) {
    private val appContext = context.applicationContext

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }
    private val file get() = File(appContext.filesDir, "profiles.json")
    private val failureFile get() = File(appContext.filesDir, "failure.json")
    private val settingsFile get() = File(appContext.filesDir, "settings.json")

    fun profiles(): List<Profile> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString<List<Profile>>(file.readText())
    }.getOrDefault(emptyList())

    fun save(profile: Profile) {
        val updated = profiles().filterNot { it.id == profile.id } + profile
        writeAtomic(file, json.encodeToString(updated))
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val profile = profiles().firstOrNull { it.id == id } ?: return
        save(profile.copy(enabled = enabled))
    }

    fun delete(id: String) {
        writeAtomic(file, json.encodeToString(profiles().filterNot { it.id == id }))
    }

    fun create(name: String): Profile = Profile(id = UUID.randomUUID().toString(), name = name)

    fun export(profile: Profile): String = json.encodeToString(ProfileBundle(profile = profile))

    fun decodeBundle(text: String, assignNewId: Boolean = false): Profile {
        val bundle = json.decodeFromString<ProfileBundle>(text)
        require(bundle.schemaVersion in 1..3) { "Unsupported profile schema ${bundle.schemaVersion}" }
        return if (assignNewId) bundle.profile.copy(id = UUID.randomUUID().toString()) else bundle.profile
    }

    fun import(text: String): Profile {
        val imported = decodeBundle(text, assignNewId = true)
        save(imported)
        return imported
    }

    fun settings(): AppSettings = runCatching {
        if (!settingsFile.exists()) AppSettings() else json.decodeFromString<AppSettings>(settingsFile.readText())
    }.getOrDefault(AppSettings())

    fun saveSettings(settings: AppSettings) = writeAtomic(settingsFile, json.encodeToString(settings))

    fun saveFailure(report: FailureReport) = writeAtomic(failureFile, json.encodeToString(report))

    fun lastFailure(): FailureReport? = runCatching {
        if (!failureFile.exists()) null else json.decodeFromString<FailureReport>(failureFile.readText())
    }.getOrNull()

    private fun writeAtomic(target: File, content: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(content)
        if (!temp.renameTo(target)) {
            target.writeText(content)
            temp.delete()
        }
    }
}
