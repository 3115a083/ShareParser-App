package cc.stkmn.shareparser.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cc.stkmn.shareparser.data.AppSettings
import cc.stkmn.shareparser.data.DateTimeLocale
import cc.stkmn.shareparser.data.ProfileRepository

@Composable
internal fun RegionalSettingsScreen(repository: ProfileRepository) {
    var settings by remember { mutableStateOf(repository.settings()) }

    fun select(locale: DateTimeLocale) {
        settings = settings.copy(dateTimeLocale = locale)
        repository.saveSettings(settings)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Regionale Datums- und Zeitformate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Legt fest, wie mehrdeutige Angaben interpretiert werden. Eindeutige ISO-Daten wie 2026-12-31 funktionieren in allen Modi.")
            }
        }

        DateTimeLocale.entries.forEach { locale ->
            item(key = locale.name) {
                val info = localeInfo(locale)
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = settings.dateTimeLocale == locale,
                            onClick = { select(locale) }
                        )
                        Column(
                            Modifier.padding(start = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(info.title, fontWeight = FontWeight.SemiBold)
                            Text(info.description, style = MaterialTheme.typography.bodySmall)
                            Text("Beispiele: ${info.examples}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Bei Jahresangaben mit zwei Ziffern interpretiert ShareParser 00 bis 69 als 2000 bis 2069 und 70 bis 99 als 1970 bis 1999. Fehlt das Jahr vollständig, wird das aktuelle Jahr verwendet.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private data class LocaleInfo(
    val title: String,
    val description: String,
    val examples: String
)

private fun localeInfo(locale: DateTimeLocale): LocaleInfo = when (locale) {
    DateTimeLocale.DE_DE -> LocaleInfo(
        "Deutsch, Deutschland",
        "Tag vor Monat, 24-Stunden-Zeit sowie deutsche Begriffe wie heute, morgen und Montag.",
        "14.12.2026, 14/12/26, 14.12., morgen 12-14, 12:30 Uhr"
    )
    DateTimeLocale.EN_US -> LocaleInfo(
        "English, United States",
        "Monat vor Tag. AM/PM wird unterstützt und bei US-Texten erwartet.",
        "12/14/2026, 12/14/26, December 14, 2026, tomorrow 2:30 PM, 2 PM to 4 PM"
    )
    DateTimeLocale.EN_GB -> LocaleInfo(
        "English, United Kingdom",
        "Tag vor Monat, überwiegend 24-Stunden-Zeit sowie englische Datumsbegriffe.",
        "14/12/2026, 14 December 2026, tomorrow 14:30, 14:00 to 16:00"
    )
    DateTimeLocale.ISO -> LocaleInfo(
        "ISO / international",
        "Bevorzugt eindeutige Jahr-Monat-Tag-Angaben und 24-Stunden-Zeit. Deutsche und englische relative Begriffe bleiben erkennbar.",
        "2026-12-14, 2026-12-14 14:30"
    )
    DateTimeLocale.SYSTEM -> LocaleInfo(
        "Geräteeinstellung",
        "Leitet Datumsreihenfolge und 12/24-Stunden-Präferenz aus der Gerätesprache und Region ab.",
        "abhängig von den Android-Regionseinstellungen"
    )
}
