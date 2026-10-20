package cc.stkmn.shareparser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import java.nio.ByteBuffer
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

    fun launcherAsset(icon: LauncherIcon): String = "branding/app_logo_3.png"

    fun loadBitmap(context: Context, assetPath: String): Bitmap? = runCatching {
        val bytes = context.applicationContext.assets.open(assetPath).use { it.readBytes() }
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        if (assetPath == FOREGROUND_ASSET) removeDarkBackground(decoded) else decoded
    }.getOrNull()

    private fun removeDarkBackground(source: Bitmap): Bitmap {
        val bitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val red = android.graphics.Color.red(color)
            val green = android.graphics.Color.green(color)
            val blue = android.graphics.Color.blue(color)
            val max = maxOf(red, green, blue)
            if (max <= 18) {
                pixels[index] = android.graphics.Color.TRANSPARENT
            } else if (max < 42) {
                val alpha = ((max - 18) * 255 / 24).coerceIn(0, 255)
                pixels[index] = android.graphics.Color.argb(alpha, red, green, blue)
            }
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return bitmap
    }
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
