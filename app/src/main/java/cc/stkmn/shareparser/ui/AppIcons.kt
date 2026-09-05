package cc.stkmn.shareparser.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

internal data class IconChoice(val id: String, val label: String, val vector: ImageVector)

internal val actionIcons = listOf(
    IconChoice("event", "Termin", Icons.Outlined.Event),
    IconChoice("calendar", "Kalender", Icons.Outlined.CalendarMonth),
    IconChoice("schedule", "Zeit", Icons.Outlined.Schedule),
    IconChoice("alarm", "Alarm", Icons.Outlined.Alarm),
    IconChoice("link", "Link", Icons.Outlined.Link),
    IconChoice("open", "Öffnen", Icons.Outlined.OpenInNew),
    IconChoice("share", "Teilen", Icons.Outlined.Share),
    IconChoice("send", "Senden", Icons.Outlined.Send),
    IconChoice("mail", "E-Mail", Icons.Outlined.Mail),
    IconChoice("phone", "Telefon", Icons.Outlined.Phone),
    IconChoice("map", "Karte", Icons.Outlined.Map),
    IconChoice("location", "Ort", Icons.Outlined.LocationOn),
    IconChoice("web", "Web", Icons.Outlined.Language),
    IconChoice("text", "Text", Icons.Outlined.TextFields),
    IconChoice("description", "Dokument", Icons.Outlined.Description),
    IconChoice("paste", "Einfügen", Icons.Outlined.ContentPaste),
    IconChoice("copy", "Kopieren", Icons.Outlined.ContentCopy),
    IconChoice("folder", "Ordner", Icons.Outlined.Folder),
    IconChoice("save", "Speichern", Icons.Outlined.Save),
    IconChoice("download", "Download", Icons.Outlined.Download),
    IconChoice("upload", "Upload", Icons.Outlined.Upload),
    IconChoice("cloud_upload", "Cloud Upload", Icons.Outlined.CloudUpload),
    IconChoice("archive", "Archiv", Icons.Outlined.Archive),
    IconChoice("print", "Drucken", Icons.Outlined.Print),
    IconChoice("edit", "Bearbeiten", Icons.Outlined.Edit),
    IconChoice("search", "Suche", Icons.Outlined.Search),
    IconChoice("sync", "Sync", Icons.Outlined.Sync),
    IconChoice("notification", "Benachrichtigung", Icons.Outlined.Notifications),
    IconChoice("check", "Erledigt", Icons.Outlined.CheckCircle),
    IconChoice("warning", "Warnung", Icons.Outlined.Warning),
    IconChoice("star", "Stern", Icons.Outlined.Star),
    IconChoice("favorite", "Favorit", Icons.Outlined.Favorite),
    IconChoice("bookmark", "Lesezeichen", Icons.Outlined.Bookmark),
    IconChoice("person", "Person", Icons.Outlined.Person),
    IconChoice("home", "Start", Icons.Outlined.Home),
    IconChoice("attach", "Anhang", Icons.Outlined.AttachFile),
    IconChoice("work", "Arbeit", Icons.Outlined.Work),
    IconChoice("tune", "Verarbeiten", Icons.Outlined.Tune)
)

internal fun actionIcon(id: String): ImageVector =
    actionIcons.firstOrNull { it.id == id }?.vector ?: Icons.Outlined.Share
