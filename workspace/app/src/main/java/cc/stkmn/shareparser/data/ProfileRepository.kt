package cc.stkmn.shareparser.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ProfileRepository(private val context: Context) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }
    private val file get() = File(context.filesDir, "profiles.json")
    private val failureFile get() = File(context.filesDir, "failure.json")

    fun profiles(): List<Profile> = runCatching {
        if (!file.exists()) emptyList() else json.decodeFromString<List<Profile>>(file.readText())
    }.getOrDefault(emptyList())

    fun save(profile: Profile) {
        val updated = profiles().filterNot { it.id == profile.id } + profile
        file.writeText(json.encodeToString(updated))
    }

    fun delete(id: String) {
        file.writeText(json.encodeToString(profiles().filterNot { it.id == id }))
    }

    fun create(name: String): Profile = Profile(id = UUID.randomUUID().toString(), name = name)

    fun export(profile: Profile): String = json.encodeToString(ProfileBundle(profile = profile))

    fun import(text: String): Profile {
        val bundle = json.decodeFromString<ProfileBundle>(text)
        require(bundle.schemaVersion == 1) { "Unsupported profile schema ${bundle.schemaVersion}" }
        val imported = bundle.profile.copy(id = UUID.randomUUID().toString())
        save(imported)
        return imported
    }

    fun saveFailure(report: FailureReport) = failureFile.writeText(json.encodeToString(report))
    fun lastFailure(): FailureReport? = runCatching {
        if (!failureFile.exists()) null else json.decodeFromString<FailureReport>(failureFile.readText())
    }.getOrNull()
}
