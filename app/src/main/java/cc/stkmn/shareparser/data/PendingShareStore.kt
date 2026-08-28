package cc.stkmn.shareparser.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class PendingShareStore(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val directory = File(appContext.cacheDir, "pending-shares").apply { mkdirs() }

    fun put(payload: SharedPayload): PendingShare {
        cleanup()
        val pending = PendingShare(
            id = UUID.randomUUID().toString(),
            payload = payload,
            createdAtEpochMs = System.currentTimeMillis()
        )
        file(pending.id).writeText(json.encodeToString(pending))
        return pending
    }

    fun get(id: String): PendingShare? = runCatching {
        val source = file(id)
        if (!source.exists()) return null
        json.decodeFromString<PendingShare>(source.readText())
            .takeIf { System.currentTimeMillis() - it.createdAtEpochMs <= MAX_AGE_MS }
    }.getOrNull()

    fun remove(id: String) {
        file(id).delete()
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        directory.listFiles()?.forEach { source ->
            if (now - source.lastModified() > MAX_AGE_MS) source.delete()
        }
    }

    private fun file(id: String) = File(directory, "$id.json")

    companion object {
        private const val MAX_AGE_MS = 5 * 60_000L
    }
}
