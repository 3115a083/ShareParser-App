package cc.stkmn.shareparser.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val exactEnglish = mapOf(
    "Profile" to "Profiles",
    "Importieren" to "Import",
    "Einstellungen" to "Settings",
    "Noch kein Profil" to "No profile yet",
    "Profil erstellen" to "Create profile",
    "Bearbeiten" to "Edit",
    "Löschen" to "Delete",
    "Abbrechen" to "Cancel",
    "Profil löschen?" to "Delete profile?",
    "Geteilte Informationen" to "Shared information",
    "Betreff" to "Subject",
    "Text" to "Text",
    "Kein Profil passt" to "No profile matches",
    "Profil aus diesem Text erstellen" to "Create profile from this text",
    "Passendes Profil auswählen" to "Select matching profile",
    "Profil" to "Profile",
    "Wechseln" to "Change",
    "Erkannte Variablen" to "Detected variables",
    "Weiterverarbeitung" to "Processing",
    "Dieses Profil hat noch keine Aktion." to "This profile has no action yet.",
    "Aktion auswählen" to "Select action",
    "Profil bearbeiten" to "Edit profile",
    "Wie weiterverarbeiten?" to "How should this be processed?",
    "Datum und Uhrzeit" to "Date and time",
    "Deutsch (Deutschland)" to "German (Germany)",
    "Geräteeinstellung" to "Device setting",
    "Darstellung" to "Appearance",
    "Sprache / Language" to "Language",
    "Systemstandard / System default" to "System default",
    "Deutsch" to "German",
    "English" to "English",
    "Aktion" to "Action",
    "Kalendereintrag" to "Calendar event",
    "URL öffnen" to "Open URL",
    "Text oder Textdatei" to "Text or text file",
    "Webhook" to "Webhook",
    "Kalenderfelder" to "Calendar fields",
    "Titel" to "Title",
    "Beschreibung" to "Description",
    "Ort" to "Location",
    "Beginn oder Zeitraum" to "Start or time range",
    "Ende, optional" to "End, optional",
    "Dauer, optional" to "Duration, optional",
    "Ganztägig" to "All day",
    "Zielkalender-Verhalten" to "Target calendar behavior",
    "Kalender-App vorausfüllen" to "Prefill calendar app",
    "Zielkalender verbindlich" to "Use target calendar directly",
    "Standard-Browser" to "Default browser",
    "Als Textdatei ausgeben" to "Output as text file",
    "Dateiname" to "File name",
    "Unterordner, optional" to "Subfolder, optional",
    "Leere oder ungültige Dateifelder" to "Empty or invalid file fields",
    "Fallback verwenden" to "Use fallback",
    "Fehler melden und Aktion abbrechen" to "Report error and stop action",
    "Fallback-Dateiname" to "Fallback file name",
    "Fallback-Unterordner, optional" to "Fallback subfolder, optional",
    "Datei verwenden" to "Use file",
    "Teilen" to "Share",
    "Direkt öffnen" to "Open directly",
    "Im Dateisystem speichern" to "Save to file system",
    "Ausführung" to "Execution",
    "Nur bei Auswahl dieser Aktion" to "Only when this action is selected",
    "Immer senden" to "Always send",
    "Leerer POST-Inhalt" to "Empty POST body",
    "Fehler melden" to "Report error",
    "Fallback-Inhalt" to "Fallback body",
    "Variablen als Profilmerkmal" to "Variables as profile criteria",
    "Nicht leer" to "Not empty",
    "5 Ziffern" to "5 digits",
    "Aktive Merkmale" to "Active criteria",
    "Fester Text als Merkmal" to "Fixed text as criterion",
    "Hinzufügen" to "Add",
    "Variablen aus dem Beispiel" to "Variables from the example",
    "Variable" to "Variable",
    "Variablen" to "Variables",
    "Manuell" to "Manual",
    "Erweiterter Modus" to "Advanced mode",
    "Profil-JSON" to "Profile JSON",
    "JSON anwenden" to "Apply JSON",
    "Profil speichern" to "Save profile",
    "Bearbeitungsmodus aktiv" to "Editing mode active",
    "Fehlerstelle hervorgehoben" to "Error location highlighted",
    "Profilname" to "Profile name",
    "Profil aktiv" to "Profile enabled",
    "Profil deaktiviert" to "Profile disabled",
    "Parsing-Reihenfolge" to "Parsing order",
    "Von oben nach unten" to "Top to bottom",
    "Von unten nach oben" to "Bottom to top",
    "Profil automatisch erkennen" to "Automatically detect profile",
    "Als Variable" to "Use as variable",
    "Als Profilmerkmal" to "Use as profile criterion",
    "Text kopieren" to "Copy text",
    "Textfeld maximieren" to "Maximize text field",
    "Textfeld verkleinern" to "Minimize text field",
    "Beispielwert kopieren" to "Copy example value",
    "Aktion entfernen" to "Remove action",
    "Anzeigename" to "Display name",
    "Icon auswählen" to "Select icon",
    "Inhaltstyp" to "Content type",
    "Quelle" to "Source",
    "Pflichtfeld" to "Required field",
    "Aufteilen" to "Split",
    "Leerzeichen entfernen" to "Trim whitespace",
    "Textteil ersetzen / entfernen" to "Replace / remove text",
    "Text davor" to "Prefix text",
    "Text danach" to "Suffix text",
    "Kleinschreibung" to "Lowercase",
    "Großschreibung" to "Uppercase",
    "Nachricht" to "Message",
    "Nachrichtentext" to "Message text",
    "Dateiinhalt" to "File content",
    "Gesamte Nachricht / Dateiinhalt" to "Entire message / file content",
    "Betreff + Nachricht" to "Subject + message",
    "Teilende App" to "Sharing app",
    "Paketname der teilenden App" to "Sharing app package",
    "Inhaltstyp" to "Content type",
    "Kein Fehlerbericht vorhanden." to "No error report available.",
    "Betroffener Bereich" to "Affected area",
    "Technische Details" to "Technical details",
    "Eingabe" to "Input",
    "Profil an Fehlerstelle bearbeiten" to "Edit profile at error location"
)

private fun englishFallback(value: String): String {
    exactEnglish[value]?.let { return it }
    var result = value
    val replacements = listOf(
        "Noch kein Merkmal" to "No criterion yet",
        "Unbekannte Variable" to "Unknown variable",
        "Erkannte Variablen" to "Detected variables",
        "Variablen können" to "Variables can",
        "Geteilt aus" to "Shared from",
        "Dateityp" to "File type",
        "Datei:" to "File:",
        "Markiert:" to "Selected:",
        "Profil:" to "Profile:",
        "Typ:" to "Type:",
        "Variablen ·" to "variables ·",
        "Aktionen" to "actions",
        "Aktion" to "action",
        "Variable" to "variable",
        "Profilmerkmal" to "profile criterion",
        "Fehler" to "Error",
        "Dateiname" to "File name",
        "Unterordner" to "Subfolder",
        "Kalender" to "Calendar",
        "Speichern" to "Save",
        "Öffnen" to "Open",
        "Teilen" to "Share",
        "Entfernen" to "Remove",
        "Auswählen" to "Select",
        "kopieren" to "copy",
        "optional" to "optional"
    )
    replacements.forEach { (de, en) -> result = result.replace(de, en, ignoreCase = false) }
    return result
}

internal fun localized(value: String): String =
    if (Locale.current.language == "de") value else englishFallback(value)

@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    MaterialText(
        text = localized(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
