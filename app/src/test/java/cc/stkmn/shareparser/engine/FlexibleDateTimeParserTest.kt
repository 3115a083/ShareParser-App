package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.AppSettings
import cc.stkmn.shareparser.data.DateTimeLocale
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FlexibleDateTimeParserTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val reference = ZonedDateTime.of(2026, 8, 27, 10, 0, 0, 0, zone)

    @Test
    fun parsesGermanFullDateAndTimeRange() {
        val result = FlexibleDateTimeParser.parse("14.12.2026 12 Uhr bis 14 Uhr", reference = reference)
        assertEquals(ZonedDateTime.of(2026, 12, 14, 12, 0, 0, 0, zone).toInstant().toEpochMilli(), result.startEpochMs)
        assertEquals(ZonedDateTime.of(2026, 12, 14, 14, 0, 0, 0, zone).toInstant().toEpochMilli(), result.endEpochMs)
    }

    @Test
    fun parsesShortYearAndSlashDate() {
        val result = FlexibleDateTimeParser.parse("14/12/26 12:30", reference = reference)
        assertEquals(ZonedDateTime.of(2026, 12, 14, 12, 30, 0, 0, 0, zone).toInstant().toEpochMilli(), result.startEpochMs)
    }

    @Test
    fun fillsCurrentYearWhenYearIsMissing() {
        val result = FlexibleDateTimeParser.parse("14.12. 09:00", reference = reference)
        assertEquals(ZonedDateTime.of(2026, 12, 14, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), result.startEpochMs)
    }

    @Test
    fun understandsTomorrowAndCompactRange() {
        val result = FlexibleDateTimeParser.parse("morgen 12-14", reference = reference)
        assertEquals(ZonedDateTime.of(2026, 8, 28, 12, 0, 0, 0, zone).toInstant().toEpochMilli(), result.startEpochMs)
        assertEquals(ZonedDateTime.of(2026, 8, 28, 14, 0, 0, 0, zone).toInstant().toEpochMilli(), result.endEpochMs)
    }

    @Test
    fun parsesUsMonthDayAndAmPm() {
        val settings = AppSettings(DateTimeLocale.EN_US)
        val result = FlexibleDateTimeParser.parse("12/14/2026 2:30 PM to 4 PM", settings = settings, reference = reference)
        assertEquals(ZonedDateTime.of(2026, 12, 14, 14, 30, 0, 0, zone).toInstant().toEpochMilli(), result.startEpochMs)
        assertEquals(ZonedDateTime.of(2026, 12, 14, 16, 0, 0, 0, zone).toInstant().toEpochMilli(), result.endEpochMs)
    }

    @Test
    fun parsesUsNamedDateAndTomorrow() {
        val settings = AppSettings(DateTimeLocale.EN_US)
        val named = FlexibleDateTimeParser.parse("December 14, 2026 9 AM", settings = settings, reference = reference)
        val relative = FlexibleDateTimeParser.parse("tomorrow 8:15 AM", settings = settings, reference = reference)
        assertEquals(ZonedDateTime.of(2026, 12, 14, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), named.startEpochMs)
        assertEquals(ZonedDateTime.of(2026, 8, 28, 8, 15, 0, 0, zone).toInstant().toEpochMilli(), relative.startEpochMs)
    }

    @Test
    fun warnsInsteadOfFailingWhenTimeIsMissing() {
        val result = FlexibleDateTimeParser.parse("14.12.2026", reference = reference)
        assertEquals(null, result.startEpochMs)
        assertNotNull(result.warnings.firstOrNull { it.contains("Uhrzeit") })
    }
}
