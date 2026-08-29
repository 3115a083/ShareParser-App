package cc.stkmn.shareparser

import android.content.Context
import cc.stkmn.shareparser.data.LauncherIcon

object LauncherIconManager {
    @Suppress("UNUSED_PARAMETER")
    fun apply(context: Context, selected: LauncherIcon) = Unit

    fun normalize(icon: LauncherIcon): LauncherIcon = LauncherIcon.LOGO_3
}
