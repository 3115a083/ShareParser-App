package cc.stkmn.shareparser.share

import android.app.Activity
import android.content.Intent
import cc.stkmn.shareparser.data.SharedPayload

object SharePayloadFactory {
    fun from(activity: Activity, intent: Intent): SharedPayload? {
        if (intent.action != Intent.ACTION_SEND) return null
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
            ?: ""
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()
        if (text.isBlank() && subject.isBlank()) return null

        val referrer = activity.referrer
        val sourcePackage = when {
            referrer?.scheme.equals("android-app", ignoreCase = true) -> referrer?.host.orEmpty()
            else -> ""
        }
        val sourceApp = if (sourcePackage.isBlank()) "" else runCatching {
            val info = activity.packageManager.getApplicationInfo(sourcePackage, 0)
            activity.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sourcePackage)

        return SharedPayload(
            text = text,
            subject = subject,
            mimeType = intent.type ?: "text/plain",
            sourcePackage = sourcePackage,
            sourceApp = sourceApp
        )
    }
}
