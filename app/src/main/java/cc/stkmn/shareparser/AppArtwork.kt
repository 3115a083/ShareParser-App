package cc.stkmn.shareparser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import cc.stkmn.shareparser.data.LauncherIcon

object AppArtwork {
    const val FOREGROUND_ASSET = "branding/ic_launcher_foreground.png"

    fun launcherAsset(icon: LauncherIcon): String = when (LauncherIconManager.normalize(icon)) {
        LauncherIcon.LOGO_1 -> "branding/app_logo_1.png"
        LauncherIcon.LOGO_2 -> "branding/app_logo_2.png"
        LauncherIcon.LOGO_3 -> "branding/app_logo_3.png"
        LauncherIcon.LOGO_4 -> "branding/app_logo_4.png"
        LauncherIcon.LOGO_5, LauncherIcon.LOGO_6 -> "branding/app_logo_1.png"
    }

    fun loadBitmap(context: Context, assetPath: String): Bitmap? = runCatching {
        context.applicationContext.assets.open(assetPath).use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
}

@Composable
fun AppArtworkImage(
    assetPath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val bitmap = remember(assetPath) {
        AppArtwork.loadBitmap(context, assetPath)?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
}
