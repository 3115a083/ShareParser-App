package cc.stkmn.shareparser.share

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Spanned
import android.text.style.URLSpan
import cc.stkmn.shareparser.data.SharedPayload
import java.io.InputStreamReader

object SharePayloadFactory {
    private const val MAX_SHARED_TEXT_CHARS = 4_000_000
    private const val EXTRA_SOURCE_PACKAGE = "android.intent.extra.PACKAGE_NAME"
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")
    private val supportedTextExtensions = setOf(
        "txt", "text", "md", "markdown", "html", "htm", "xhtml", "json", "xml", "csv", "tsv", "log", "ics", "yaml", "yml"
    )

    data class SourceHints(val referrer: Uri? = null, val callingPackage: String? = null, val callingActivityPackage: String? = null)
    fun sourceHints(activity: Activity): SourceHints = SourceHints(runCatching { activity.referrer }.getOrNull(), activity.callingPackage, activity.callingActivity?.packageName)
    fun from(context: Context, intent: Intent, hints: SourceHints = SourceHints()): SharedPayload? {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return null
            val scheme = uri.scheme?.lowercase().orEmpty()
            val targetType = when (scheme) {
                "http", "https" -> "web"
                "geo" -> "map"
                "tel" -> "phone"
                "mailto" -> "email"
                else -> return null
            }
            val value = uri.toString()
            val sourcePackage = resolveSourcePackage(context, intent, null, hints)
            val sourceApp = if (sourcePackage.isBlank()) "" else runCatching {
                val info = context.packageManager.getApplicationInfo(sourcePackage, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(sourcePackage)
            return SharedPayload(
                text = value,
                mimeType = "text/uri-list",
                sourcePackage = sourcePackage,
                sourceApp = sourceApp,
                linkTargets = listOf(value),
                target = value,
                targetType = targetType
            )
        }
        if (intent.action != Intent.ACTION_SEND) return null

        val streamUri = streamUri(intent)?.takeIf { it.scheme.equals("content", true) }
        val fileName = streamUri?.let { displayName(context, it) }.orEmpty().take(255)
        val mimeType = intent.type ?: streamUri?.let { runCatching { context.contentResolver.getType(it) }.getOrNull() } ?: "text/plain"
        val streamText = streamUri
            ?.takeIf { isSupportedTextPayload(mimeType, fileName) }
            ?.let { readSharedText(context, it) }
            .orEmpty()
        val inlineValue = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        val htmlText = intent.getStringExtra(Intent.EXTRA_HTML_TEXT).orEmpty().take(MAX_SHARED_TEXT_CHARS)
        val inlineText = (inlineValue?.toString() ?: htmlText).take(MAX_SHARED_TEXT_CHARS)
        val text = streamText.ifBlank { inlineText }
        val linkTargets = buildList {
            if (inlineValue is Spanned) {
                inlineValue.getSpans(0, inlineValue.length, URLSpan::class.java)
                    .mapNotNull { it.url?.trim()?.takeIf(String::isNotBlank) }
                    .forEach(::add)
            }
            Regex("(?i)href\\s*=\\s*[\"']([^\"']+)[\"']")
                .findAll(htmlText)
                .mapNotNull { it.groups[1]?.value?.trim()?.takeIf(String::isNotBlank) }
                .forEach(::add)
        }.distinct()
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty().take(MAX_SHARED_TEXT_CHARS)
        if (text.isBlank() && subject.isBlank()) return null

        val sourcePackage = resolveSourcePackage(context, intent, streamUri, hints)
        val sourceApp = if (sourcePackage.isBlank()) "" else runCatching {
            val info = context.packageManager.getApplicationInfo(sourcePackage, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sourcePackage)

        return SharedPayload(
            text = text,
            subject = subject,
            mimeType = mimeType,
            sourcePackage = sourcePackage,
            sourceApp = sourceApp,
            fileName = fileName,
            linkTargets = linkTargets,
            target = "",
            targetType = ""
        )
    }

    @Suppress("DEPRECATION")
    private fun streamUri(intent: Intent): Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun referrerUri(intent: Intent): Uri? = intent.getParcelableExtra(Intent.EXTRA_REFERRER)

    @Suppress("DEPRECATION")
    private fun resolveSourcePackage(context: Context, intent: Intent, streamUri: Uri?, hints: SourceHints): String {
        val candidates = linkedSetOf<String>()

        fun addPackageLike(value: String?) {
            val raw = value?.trim().orEmpty()
            if (raw.isBlank()) return
            if (packagePattern.matches(raw)) candidates += raw
            runCatching { Uri.parse(raw) }.getOrNull()?.let { uri ->
                if (uri.scheme.equals("android-app", ignoreCase = true)) uri.host?.let(candidates::add)
            }
        }

        fun addUri(uri: Uri?) {
            if (uri == null) return
            if (uri.scheme.equals("android-app", ignoreCase = true)) uri.host?.let(candidates::add)
            authorityPackageCandidates(uri.authority).forEach(candidates::add)
        }

        addUri(hints.referrer)
        addUri(referrerUri(intent))
        addPackageLike(intent.getStringExtra(Intent.EXTRA_REFERRER_NAME))
        addPackageLike(hints.callingActivityPackage)
        addPackageLike(hints.callingPackage)
        addPackageLike(intent.getStringExtra(EXTRA_SOURCE_PACKAGE))
        addUri(streamUri)

        intent.extras?.keySet()?.take(64)?.forEach { key ->
            when (val value = intent.extras?.get(key)) {
                is String -> if (key != Intent.EXTRA_TEXT && key != Intent.EXTRA_HTML_TEXT && key != Intent.EXTRA_SUBJECT) addPackageLike(value)
                is Uri -> addUri(value)
            }
        }

        return candidates
            .asSequence()
            .flatMap { packageAndParents(it).asSequence() }
            .filter { it != context.packageName && it !in ignoredSourcePackages }
            .firstOrNull { candidate ->
                runCatching { context.packageManager.getApplicationInfo(candidate, 0) }.isSuccess
            }
            .orEmpty()
    }

    private fun authorityPackageCandidates(authority: String?): List<String> {
        val value = authority?.trim().orEmpty()
        if (value.isBlank() || !packagePattern.matches(value)) return emptyList()
        return packageAndParents(value)
    }

    private fun packageAndParents(value: String): List<String> = buildList {
        var current = value
        while (packagePattern.matches(current)) {
            add(current)
            val parent = current.substringBeforeLast('.', "")
            if (parent.isBlank() || parent == current) break
            current = parent
        }
    }

    private fun isSupportedTextPayload(mimeType: String, fileName: String): Boolean {
        val normalizedMime = mimeType.substringBefore(';').trim().lowercase()
        if (normalizedMime.startsWith("text/")) return true
        if (normalizedMime in setOf(
                "application/json",
                "application/ld+json",
                "application/xml",
                "application/xhtml+xml",
                "application/markdown",
                "application/x-markdown",
                "text/markdown"
            )
        ) return true
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in supportedTextExtensions
    }

    private fun readSharedText(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8192)
                val result = StringBuilder()
                while (result.length < MAX_SHARED_TEXT_CHARS) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val remaining = MAX_SHARED_TEXT_CHARS - result.length
                    val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count <= 0) break
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
        }.orEmpty()
    }.getOrDefault("")

    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

    private val ignoredSourcePackages = setOf(
        "android",
        "com.android.intentresolver",
        "com.google.android.documentsui"
    )
}
