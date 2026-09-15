package cc.stkmn.shareparser

import android.content.Context
import android.content.Intent

data class ShareSourceApp(
    val label: String,
    val packageName: String
)

object ShareSourceAppCatalog {
    fun list(context: Context, includePackage: String = "", includeLabel: String = ""): List<ShareSourceApp> {
        val appContext = context.applicationContext
        val pm = appContext.packageManager

        fun resolve(intent: Intent): List<ShareSourceApp> =
            pm.queryIntentActivities(intent, 0).mapNotNull { info ->
                val packageName = info.activityInfo?.packageName.orEmpty()
                if (packageName.isBlank() || packageName == appContext.packageName) null
                else ShareSourceApp(
                    label = info.loadLabel(pm)?.toString()?.ifBlank { packageName } ?: packageName,
                    packageName = packageName
                )
            }

        val apps = buildList {
            addAll(
                resolve(
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                )
            )
            addAll(
                resolve(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                )
            )
        }.toMutableList()

        if (includePackage.isNotBlank() && apps.none { it.packageName == includePackage }) {
            apps += ShareSourceApp(includeLabel.ifBlank { includePackage }, includePackage)
        }

        return apps
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}
