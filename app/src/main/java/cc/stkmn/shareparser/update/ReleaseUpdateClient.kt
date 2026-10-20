package cc.stkmn.shareparser.update

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class ReleaseUpdateClient(
    private val openConnection: () -> HttpURLConnection = {
        URL("https://api.github.com/repos/3115a083/ShareParser-App/releases/latest")
            .openConnection() as HttpURLConnection
    }
) {
    suspend fun latestTag(version: String): String = suspendCancellableCoroutine { continuation ->
        val connection = openConnection()
        val executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "shareparser-update").apply { isDaemon = true }
        }
        val future = executor.submit {
            var outcome: Result<String>? = null
            try {
                if (!continuation.isActive) return@submit
                connection.connectTimeout = 6_000
                connection.readTimeout = 6_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "ShareParser/" + version)
                check(continuation.isActive)
                check(connection.responseCode in 200..299) { "Release request failed" }
                val body = connection.inputStream.use { readBounded(it) }
                val tag = Json.parseToJsonElement(body).jsonObject["tag_name"]?.jsonPrimitive?.takeIf { it.isString }?.content
                    ?: error("No release version")
                require(tag.isNotBlank() && tag.length <= 100)
                outcome = Result.success(tag)
            } catch (error: Exception) {
                outcome = Result.failure(error)
            } finally {
                runCatching { connection.disconnect() }
                executor.shutdown()
            }
            if (continuation.isActive) outcome?.let { continuation.resumeWith(it) }
        }
        continuation.invokeOnCancellation {
            future.cancel(true)
            connection.disconnect()
            executor.shutdownNow()
        }
    }

    companion object {
        const val MAX_BYTES = 512 * 1024
        internal fun readBounded(input: InputStream): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                val count = input.read(buffer)
                if (count < 0) break
                require(count <= MAX_BYTES - output.size()) { "Release response exceeds 512 KiB" }
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}
