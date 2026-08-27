package cc.stkmn.shareparser.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

internal data class IconChoice(val id: String, val label: String, val vector: ImageVector)

internal val actionIcons = listOf(
    IconChoice("event", "Termin", Icons.Outlined.Event),
    IconChoice("calendar", "Kalender", Icons.Outlined.CalendarMonth),
    IconChoice("link", "Link", Icons.Outlined.Link),
    IconChoice("open", "Öffnen", Icons.Outlined.OpenInNew),
    IconChoice("share", "Teilen", Icons.Outlined.Share),
    IconChoice("send", "Senden", Icons.Outlined.Send),
    IconChoice("mail", "E-Mail", Icons.Outlined.Mail),
    IconChoice("map", "Ort", Icons.Outlined.Map),
    IconChoice("web", "Web", Icons.Outlined.Language),
    IconChoice("text", "Text", Icons.Outlined.TextFields),
    IconChoice("paste", "Einfügen", Icons.Outlined.ContentPaste),
    IconChoice("work", "Arbeit", Icons.Outlined.Work),
    IconChoice("tune", "Verarbeiten", Icons.Outlined.Tune)
)

internal fun actionIcon(id: String): ImageVector =
    actionIcons.firstOrNull { it.id == id }?.vector ?: Icons.Outlined.Share
