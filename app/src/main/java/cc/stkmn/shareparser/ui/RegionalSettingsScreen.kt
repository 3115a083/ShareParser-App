package cc.stkmn.shareparser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.stkmn.shareparser.data.DateTimeLocale
import cc.stkmn.shareparser.data.ProfileRepository

@Composable
internal fun RegionalSettingsScreen(repository: ProfileRepository, onSettingsChanged: (cc.stkmn.shareparser.data.AppSettings) -> Unit = {}) {
    var settings by remember { mutableStateOf(repository.settings()) }

    fun select(locale: DateTimeLocale) {
        settings = settings.copy(dateTimeLocale = locale)
        repository.saveSettings(settings)
        onSettingsChanged(settings)
    }

    val order = listOf(
        DateTimeLocale.SYSTEM,
        DateTimeLocale.DE_DE,
        DateTimeLocale.EN_US,
        DateTimeLocale.EN_GB,
        DateTimeLocale.ISO
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Legt fest, wie mehrdeutige Datums- und Zeitangaben interpretiert werden.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        order.forEach { locale ->
            item(key = locale.name) {
                val info = localeInfo(locale)
                Card(Modifier.fillMaxWidth().clickable { select(locale) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        RadioButton(selected = settings.dateTimeLocale == locale, onClick = { select(locale) })
                        Column(
                            Modifier.weight(1f).padding(start = 8.dp, top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(info.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                info.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                info.examples,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Zweistellige Jahre: 00–69 → 2000–2069, 70–99 → 1970–1999. Ohne Jahr wird das aktuelle Jahr verwendet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class LocaleInfo(val title: String, val description: String, val examples: String)

private fun localeInfo(locale: DateTimeLocale): LocaleInfo = when (locale) {
    DateTimeLocale.SYSTEM -> LocaleInfo(
        "Geräteeinstellung",
        "Leitet Reihenfolge und 12/24-Stunden-Format aus Android ab.",
        "Beispiele folgen der Geräte-Region."
    )
    DateTimeLocale.DE_DE -> LocaleInfo(
        "Deutsch, Deutschland",
        "Tag vor Monat, 24-Stunden-Zeit und deutsche Datumsbegriffe.",
        "14.12.2026 · morgen 12–14 · 12:30 Uhr"
    )
    DateTimeLocale.EN_US -> LocaleInfo(
        "English, United States",
        "Month before day, AM/PM supported.",
        "12/14/2026 · tomorrow 2:30 PM"
    )
    DateTimeLocale.EN_GB -> LocaleInfo(
        "English, United Kingdom",
        "Day before month, mostly 24-hour time.",
        "14/12/2026 · tomorrow 14:30"
    )
    DateTimeLocale.ISO -> LocaleInfo(
        "ISO / international",
        "Unambiguous year-month-day with 24-hour time.",
        "2026-12-14 · 2026-12-14 14:30"
    )
}
