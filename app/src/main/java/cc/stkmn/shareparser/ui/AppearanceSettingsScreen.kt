package cc.stkmn.shareparser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Darstellung", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Helligkeit und Farbpalette ändern die Farben, nicht den Aufbau der App.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                            ColorPalette.FOREST -> "Wald"
                            ColorPalette.SLATE -> "Schiefer"
                            ColorPalette.AMBER -> "Bernstein"
                        },
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
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
