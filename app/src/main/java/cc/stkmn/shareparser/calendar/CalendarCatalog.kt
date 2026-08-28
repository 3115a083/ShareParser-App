package cc.stkmn.shareparser.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

data class CalendarChoice(
    val id: Long,
    val displayName: String,
    val accountName: String
)

object CalendarCatalog {
    fun list(context: Context): List<CalendarChoice> {
        val appContext = context.applicationContext
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        return runCatching {
            buildList {
                appContext.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    projection,
                    "${CalendarContract.Calendars.VISIBLE}=1",
                    null,
                    "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE ASC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        add(
                            CalendarChoice(
                                id = cursor.getLong(0),
                                displayName = cursor.getString(1).orEmpty().ifBlank { "Kalender" },
                                accountName = cursor.getString(2).orEmpty()
                            )
                        )
                    }
                }
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }
}
