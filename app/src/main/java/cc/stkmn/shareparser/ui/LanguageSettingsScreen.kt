package cc.stkmn.shareparser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.stkmn.shareparser.AppLocale
import cc.stkmn.shareparser.data.AppLanguage
import cc.stkmn.shareparser.data.ProfileRepository

@Composable
internal fun LanguageSettingsScreen(repository: ProfileRepository) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(repository.settings()) }

    fun select(language: AppLanguage) {
        settings = settings.copy(appLanguage = language)
        repository.saveSettings(settings)
        AppLocale.apply(context, language)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "App-Sprache",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Standard ist die Systemsprache. Weitere Übersetzungen können hier später ergänzt werden.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            LanguageChoiceCard(
                selected = settings.appLanguage == AppLanguage.SYSTEM,
                title = "Systemstandard",
                description = "Verwendet die Sprache des Geräts.",
                onClick = { select(AppLanguage.SYSTEM) }
            )
        }
        item {
            LanguageChoiceCard(
                selected = settings.appLanguage == AppLanguage.DE,
                title = "Deutsch",
                description = "Deutsch als App-Sprache verwenden.",
                onClick = { select(AppLanguage.DE) }
            )
        }
        item {
            LanguageChoiceCard(
                selected = settings.appLanguage == AppLanguage.EN,
                title = "English",
                description = "Use English as the app language.",
                onClick = { select(AppLanguage.EN) }
            )
        }
    }
}

@Composable
private fun LanguageChoiceCard(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Top
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
