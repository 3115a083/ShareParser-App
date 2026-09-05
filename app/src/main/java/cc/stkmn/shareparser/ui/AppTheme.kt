package cc.stkmn.shareparser.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cc.stkmn.shareparser.data.AppearanceMode
import cc.stkmn.shareparser.data.AppSettings
import cc.stkmn.shareparser.data.ColorPalette

@Composable
internal fun ShareParserTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.appearanceMode) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val scheme = colorScheme(settings.colorPalette, dark)
    val view = LocalView.current
    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(18.dp),
            extraLarge = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}

@Composable
private fun colorScheme(palette: ColorPalette, dark: Boolean): ColorScheme {
    val context = LocalContext.current
    if (palette == ColorPalette.MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return when (palette) {
        ColorPalette.MATERIAL_YOU, ColorPalette.SLATE -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFB8C7E6),
                onPrimary = Color(0xFF22304A),
                primaryContainer = Color(0xFF384761),
                onPrimaryContainer = Color(0xFFD9E2F5),
                secondary = Color(0xFFC1C7D0),
                background = Color(0xFF111318),
                surface = Color(0xFF111318),
                surfaceVariant = Color(0xFF44474F),
                onSurfaceVariant = Color(0xFFC5C6CF)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF4D5F7A),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD9E2F5),
                onPrimaryContainer = Color(0xFF0B1B31),
                secondary = Color(0xFF5A606B),
                background = Color(0xFFF9F9FC),
                surface = Color(0xFFF9F9FC),
                surfaceVariant = Color(0xFFE2E2EA),
                onSurfaceVariant = Color(0xFF44474F)
            )
        }
        ColorPalette.OCEAN -> if (dark) {
            darkColorScheme(
                primary = Color(0xFF9CCBFF),
                onPrimary = Color(0xFF003258),
                primaryContainer = Color(0xFF004A79),
                onPrimaryContainer = Color(0xFFD1E7FF),
                secondary = Color(0xFFA8CADF),
                background = Color(0xFF0D141A),
                surface = Color(0xFF0D141A),
                surfaceVariant = Color(0xFF3E4850),
                onSurfaceVariant = Color(0xFFBEC8D0)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF00639C),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFD1E7FF),
                onPrimaryContainer = Color(0xFF001D33),
                secondary = Color(0xFF4A6577),
                background = Color(0xFFF7F9FC),
                surface = Color(0xFFF7F9FC),
                surfaceVariant = Color(0xFFDDE3E8),
                onSurfaceVariant = Color(0xFF41484D)
            )
        }
        ColorPalette.PLUM -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFE2B6E8),
                onPrimary = Color(0xFF43204A),
                primaryContainer = Color(0xFF5B3762),
                onPrimaryContainer = Color(0xFFFFD7FF),
                secondary = Color(0xFFD3BFD3),
                background = Color(0xFF171217),
                surface = Color(0xFF171217),
                surfaceVariant = Color(0xFF4B444C),
                onSurfaceVariant = Color(0xFFCEC3CD)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF76527C),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFD7FF),
                onPrimaryContainer = Color(0xFF2D0B34),
                secondary = Color(0xFF685A68),
                background = Color(0xFFFFF7FC),
                surface = Color(0xFFFFF7FC),
                surfaceVariant = Color(0xFFECE0EB),
                onSurfaceVariant = Color(0xFF4E444F)
            )
        }
        ColorPalette.AMBER -> if (dark) {
            darkColorScheme(
                primary = Color(0xFFFFC46B),
                onPrimary = Color(0xFF442B00),
                primaryContainer = Color(0xFF624000),
                onPrimaryContainer = Color(0xFFFFDDB0),
                secondary = Color(0xFFD8C3A5),
                background = Color(0xFF17130D),
                surface = Color(0xFF17130D),
                surfaceVariant = Color(0xFF4D4539),
                onSurfaceVariant = Color(0xFFD2C4B3)
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF825500),
                onPrimary = Color.White,
                primaryContainer = Color(0xFFFFDDB0),
                onPrimaryContainer = Color(0xFF291800),
                secondary = Color(0xFF6E5B40),
                background = Color(0xFFFFF8F2),
                surface = Color(0xFFFFF8F2),
                surfaceVariant = Color(0xFFF0E1CF),
                onSurfaceVariant = Color(0xFF504537)
            )
        }
    }
}


private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
