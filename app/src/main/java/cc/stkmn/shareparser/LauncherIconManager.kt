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

        // Enable the new launcher entry first. This avoids a short state in which
        // Samsung Launcher sees no enabled launcher component at all.
        val selectedAlias = aliases.getValue(normalized)
        setStateIfNeeded(
            pm,
            component(appContext, selectedAlias),
            if (normalized == LauncherIcon.LOGO_1) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        )

        aliases.forEach { (icon, alias) ->
            if (icon != normalized) {
                setStateIfNeeded(
                    pm,
                    component(appContext, alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                )
            }
        }

        listOf("LauncherLogo5", "LauncherLogo6").forEach { alias ->
            setStateIfNeeded(
                pm,
                component(appContext, alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
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
        pm.setComponentEnabledSetting(component, desiredState, PackageManager.DONT_KILL_APP)
    }

    private fun component(context: Context, alias: String) =
        ComponentName(context.packageName, "${context.packageName}.$alias")
}
