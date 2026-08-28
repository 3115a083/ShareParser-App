package cc.stkmn.shareparser

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import cc.stkmn.shareparser.data.LauncherIcon

object LauncherIconManager {
    private val aliases = mapOf(
        LauncherIcon.LOGO_1 to "LauncherLogo1",
        LauncherIcon.LOGO_2 to "LauncherLogo2",
        LauncherIcon.LOGO_3 to "LauncherLogo3",
        LauncherIcon.LOGO_4 to "LauncherLogo4",
        LauncherIcon.LOGO_5 to "LauncherLogo5",
        LauncherIcon.LOGO_6 to "LauncherLogo6"
    )

    fun apply(context: Context, selected: LauncherIcon) {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        aliases[selected]?.let { alias ->
            pm.setComponentEnabledSetting(
                component(appContext, alias),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        aliases.forEach { (icon, alias) ->
            if (icon != selected) {
                pm.setComponentEnabledSetting(
                    component(appContext, alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    private fun component(context: Context, alias: String) =
        ComponentName(context.packageName, "${context.packageName}.$alias")
}
