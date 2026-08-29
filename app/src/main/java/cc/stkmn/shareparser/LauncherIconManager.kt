package cc.stkmn.shareparser

import android.content.Context
import cc.stkmn.shareparser.data.LauncherIcon

/**
 * Launcher icon switching was removed because several Android launchers cached
 * activity aliases unreliably. ShareParser now uses logo 3 exclusively.
 */
object LauncherIconManager {
    fun apply(context: Context, selected: LauncherIcon) = Unit

    fun normalize(icon: LauncherIcon): LauncherIcon = LauncherIcon.LOGO_3
}
