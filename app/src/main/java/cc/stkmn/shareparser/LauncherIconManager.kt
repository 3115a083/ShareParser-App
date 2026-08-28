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
        LauncherIcon.LOGO_4 to "LauncherLogo4"
    )

    fun apply(context: Context, selected: LauncherIcon) {
        val appContext = context.applicationContext
        val pm = appContext.packageManager
        val normalized = normalize(selected)

        aliases.forEach { (icon, alias) ->
            setStateIfNeeded(
                pm = pm,
                component = component(appContext, alias),
                desiredState = if (icon == normalized) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            )
        }

        // Old settings values from builds that exposed six icons remain readable.
        // Their aliases stay disabled and migrate visually to logo 1.
        listOf("LauncherLogo5", "LauncherLogo6").forEach { alias ->
            setStateIfNeeded(
                pm = pm,
                component = component(appContext, alias),
                desiredState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            )
        }
    }

    fun normalize(icon: LauncherIcon): LauncherIcon = when (icon) {
        LauncherIcon.LOGO_1,
        LauncherIcon.LOGO_2,
        LauncherIcon.LOGO_3,
        LauncherIcon.LOGO_4 -> icon
        LauncherIcon.LOGO_5,
        LauncherIcon.LOGO_6 -> LauncherIcon.LOGO_1
    }

    private fun setStateIfNeeded(pm: PackageManager, component: ComponentName, desiredState: Int) {
        if (pm.getComponentEnabledSetting(component) == desiredState) return
        pm.setComponentEnabledSetting(
            component,
            desiredState,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun component(context: Context, alias: String) =
        ComponentName(context.packageName, "${context.packageName}.$alias")
}
