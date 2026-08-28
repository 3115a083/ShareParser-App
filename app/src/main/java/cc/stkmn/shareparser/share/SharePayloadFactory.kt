package cc.stkmn.shareparser.share

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import cc.stkmn.shareparser.data.SharedPayload
import java.io.InputStreamReader

object SharePayloadFactory {
    private const val MAX_SHARED_TEXT_CHARS = 4_000_000

    fun from(activity: Activity, intent: Intent): SharedPayload? {
        if (intent.action != Intent.ACTION_SEND) return null

        val streamUri = streamUri(intent)
        val streamText = streamUri?.let { readSharedText(activity, it) }.orEmpty()
        val inlineText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
            ?: ""
        val text = streamText.ifBlank { inlineText }
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
        if (text.isBlank() && subject.isBlank()) return null

        val sourcePackage = resolveSourcePackage(activity, intent)
        val sourceApp = if (sourcePackage.isBlank()) "" else runCatching {
            val info = activity.packageManager.getApplicationInfo(sourcePackage, 0)
            activity.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sourcePackage)
        val fileName = streamUri?.let { displayName(activity, it) }.orEmpty()

        return SharedPayload(
            text = text,
            subject = subject,
            mimeType = intent.type ?: streamUri?.let { activity.contentResolver.getType(it) } ?: "text/plain",
            sourcePackage = sourcePackage,
            sourceApp = sourceApp,
            fileName = fileName
        )
    }

    @Suppress("DEPRECATION")
    private fun streamUri(intent: Intent): Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun referrerUri(intent: Intent): Uri? = intent.getParcelableExtra(Intent.EXTRA_REFERRER)

    private fun resolveSourcePackage(activity: Activity, intent: Intent): String {
        val referrers = buildList {
            activity.referrer?.let(::add)
            referrerUri(intent)?.let(::add)
            intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                ?.let(::add)
        }
        referrers.forEach { uri ->
            if (uri.scheme.equals("android-app", ignoreCase = true) && !uri.host.isNullOrBlank()) {
                return uri.host.orEmpty()
            }
        }
        return activity.callingPackage.orEmpty()
    }

    private fun readSharedText(activity: Activity, uri: Uri): String = runCatching {
        activity.contentResolver.openInputStream(uri)?.use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8192)
                val result = StringBuilder()
                while (result.length < MAX_SHARED_TEXT_CHARS) {
                    val remaining = MAX_SHARED_TEXT_CHARS - result.length
                    val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count <= 0) break
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
        }.orEmpty()
    }.getOrDefault("")

    private fun displayName(activity: Activity, uri: Uri): String = runCatching {
        activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")
}
