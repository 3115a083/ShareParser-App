package cc.stkmn.shareparser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import cc.stkmn.shareparser.data.AppearanceMode
import cc.stkmn.shareparser.data.AppSettings
import cc.stkmn.shareparser.data.ColorPalette

@Composable
internal fun AppearanceSettingsScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Helligkeit und Farbpalette ändern die Farben, nicht den Aufbau der App.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            SettingsTopicCard(
                title = "Helligkeit",
                description = "System folgt der Android-Einstellung."
            ) {
                AppearanceMode.entries.forEach { mode ->
                    CompactChoiceRow(
                        selected = settings.appearanceMode == mode,
                        title = when (mode) {
                            AppearanceMode.SYSTEM -> "System"
                            AppearanceMode.LIGHT -> "Hell"
                            AppearanceMode.DARK -> "Dunkel"
                        },
                        onClick = { onSettingsChanged(settings.copy(appearanceMode = mode)) }
                    )
                }
            }
        }

        item {
            SettingsTopicCard(
                title = "Farbpalette",
                description = "Material You verwendet die Gerätefarben. Die übrigen Paletten bleiben auf allen Geräten gleich."
            ) {
                ColorPalette.entries.forEach { palette ->
                    CompactChoiceRow(
                        selected = settings.colorPalette == palette,
                        title = when (palette) {
                            ColorPalette.MATERIAL_YOU -> "Material You"
                            ColorPalette.OCEAN -> "Ozean"
                            ColorPalette.PLUM -> "Pflaume"
                            ColorPalette.SLATE -> "Schiefer"
                            ColorPalette.AMBER -> "Bernstein"
                            ColorPalette.FOREST -> "Wald"
                            ColorPalette.ROSE -> "Rose"
                            ColorPalette.TEAL -> "Türkis"
                            ColorPalette.INDIGO -> "Indigo"
                        },
                        previewColors = palettePreviewColors(palette),
                        onClick = { onSettingsChanged(settings.copy(colorPalette = palette)) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsTopicCard(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun CompactChoiceRow(
    selected: Boolean,
    title: String,
    previewColors: List<Color> = emptyList(),
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, modifier = Modifier.weight(1f))
        if (previewColors.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                previewColors.take(4).forEach { color ->
                    Surface(
                        modifier = Modifier.width(18.dp).height(18.dp),
                        color = color,
                        shape = RoundedCornerShape(9.dp)
                    ) {}
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        if (selected) {
            Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}


@Composable
private fun palettePreviewColors(palette: ColorPalette): List<Color> = when (palette) {
    ColorPalette.MATERIAL_YOU -> listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.surfaceVariant
    )
    ColorPalette.OCEAN -> listOf(Color(0xFF00639C), Color(0xFFD1E7FF), Color(0xFF4A6577), Color(0xFFDDE3E8))
    ColorPalette.PLUM -> listOf(Color(0xFF76527C), Color(0xFFFFD7FF), Color(0xFF685A68), Color(0xFFECE0EB))
    ColorPalette.SLATE -> listOf(Color(0xFF4D5F7A), Color(0xFFD9E2F5), Color(0xFF5A606B), Color(0xFFE2E2EA))
    ColorPalette.AMBER -> listOf(Color(0xFF825500), Color(0xFFFFDDB0), Color(0xFF6E5B40), Color(0xFFF0E1CF))
    ColorPalette.FOREST -> listOf(Color(0xFF356A3B), Color(0xFFBCEFBF), Color(0xFF52634F), Color(0xFFDFE7DC))
    ColorPalette.ROSE -> listOf(Color(0xFF98405F), Color(0xFFFFD9E3), Color(0xFF765660), Color(0xFFF2DFE4))
    ColorPalette.TEAL -> listOf(Color(0xFF006A66), Color(0xFF9DF2EA), Color(0xFF4A6360), Color(0xFFDCE8E5))
    ColorPalette.INDIGO -> listOf(Color(0xFF515B92), Color(0xFFDDE1FF), Color(0xFF5C5D72), Color(0xFFE4E1EC))
}
