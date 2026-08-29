package cc.stkmn.shareparser

import android.content.Context
import cc.stkmn.shareparser.data.LauncherIcon

object LauncherIconManager {
    fun apply(context: Context, selected: LauncherIcon) {
        // Launcher icon switching is intentionally disabled. The manifest exposes
        // one stable launcher entry using logo 3 to avoid device-specific alias
        // caching problems.
        context.applicationContext
        selected.ordinal
    }

    fun normalize(icon: LauncherIcon): LauncherIcon = LauncherIcon.LOGO_3
}
