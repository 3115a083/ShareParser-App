package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.AppSettings
import cc.stkmn.shareparser.data.DateTimeLocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object FlexibleDateTimeParser {
    data class Result(
        val startEpochMs: Long?,
        val endEpochMs: Long?,
        val warnings: List<String> = emptyList()
    )

    private val numericDate = Regex("(?<!\\d)(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})(?:\\s*[./-]\\s*(\\d{2,4})(?!\\s*:))?\\.?", RegexOption.IGNORE_CASE)
    private val namedDate = Regex("(?<!\\d)(\\d{1,2})\\.?\\s+(januar|jan|februar|feb|märz|maerz|mrz|april|apr|mai|juni|jun|juli|jul|august|aug|september|sep|sept|oktober|okt|november|nov|dezember|dez)(?:\\s+(\\d{2,4}))?", RegexOption.IGNORE_CASE)
    private val timeRange = Regex("(?<!\\d)([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\s*(?:uhr)?\\s*(?:-|–|—|bis)\\s*([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\s*(?:uhr)?(?!\\d)", RegexOption.IGNORE_CASE)
    private val singleTime = Regex("(?<!\\d)([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\s*(?:uhr)?(?!\\d)", RegexOption.IGNORE_CASE)

    fun parse(
        startValue: String,
        endValue: String = "",
        allDay: Boolean = false,
        settings: AppSettings = AppSettings(),
        reference: ZonedDateTime = ZonedDateTime.now()
    ): Result {
        if (startValue.isBlank()) return Result(null, null, listOf("Beginn fehlt und muss im Kalender manuell ergänzt werden."))

        val zone = reference.zone
        val startPart = parsePart(startValue, reference.toLocalDate(), settings)
        val warnings = mutableListOf<String>()

        if (startPart.date == null) warnings += "Datum konnte nicht erkannt werden und muss im Kalender geprüft werden."
        if (!allDay && startPart.startTime == null) warnings += "Uhrzeit konnte nicht erkannt werden und muss im Kalender ergänzt werden."

        val start = when {
            startPart.date == null -> null
            allDay -> startPart.date.atStartOfDay(zone).toInstant().toEpochMilli()
            startPart.startTime != null -> LocalDateTime.of(startPart.date, startPart.startTime).atZone(zone).toInstant().toEpochMilli()
            else -> null
        }

        var end: Long? = null
        if (allDay && startPart.date != null) {
            end = startPart.date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        } else if (startPart.date != null && startPart.endTime != null) {
            var endDate = startPart.date
            if (startPart.endTime.isBefore(startPart.startTime)) endDate = endDate.plusDays(1)
            end = LocalDateTime.of(endDate, startPart.endTime).atZone(zone).toInstant().toEpochMilli()
        } else if (endValue.isNotBlank()) {
            val endPart = parsePart(endValue, startPart.date ?: reference.toLocalDate(), settings)
            val endDate = endPart.date ?: startPart.date
            val endTime = endPart.startTime
            if (endDate != null && endTime != null) {
                var actualEndDate = endDate
                if (start != null && LocalDateTime.of(actualEndDate, endTime).atZone(zone).toInstant().toEpochMilli() <= start) {
                    if (endPart.date == null) actualEndDate = actualEndDate.plusDays(1)
                }
                end = LocalDateTime.of(actualEndDate, endTime).atZone(zone).toInstant().toEpochMilli()
            } else {
                warnings += "Endzeit konnte nicht erkannt werden und muss im Kalender ergänzt werden."
            }
        }

        return Result(start, end, warnings.distinct())
    }

    private data class Part(
        val date: LocalDate?,
        val startTime: LocalTime?,
        val endTime: LocalTime?
    )

    private fun parsePart(value: String, fallbackDate: LocalDate, settings: AppSettings): Part {
        val raw = value.trim()
        val lower = raw.lowercase(locale(settings))
        val dateMatch = findDate(lower, fallbackDate)
        val withoutDate = dateMatch?.matchedText?.let { raw.replace(it, " ", ignoreCase = true) } ?: raw

        val range = timeRange.find(withoutDate)
        if (range != null) {
            return Part(
                date = dateMatch?.date,
                startTime = time(range.groupValues[1], range.groupValues[2]),
                endTime = time(range.groupValues[3], range.groupValues[4])
            )
        }

        val time = singleTime.find(withoutDate)?.let { time(it.groupValues[1], it.groupValues[2]) }
        return Part(date = dateMatch?.date, startTime = time, endTime = null)
    }

    private data class DateMatch(val date: LocalDate, val matchedText: String)

    private fun findDate(value: String, referenceDate: LocalDate): DateMatch? {
        val relative = listOf(
            "übermorgen" to referenceDate.plusDays(2),
            "uebermorgen" to referenceDate.plusDays(2),
            "morgen" to referenceDate.plusDays(1),
            "heute" to referenceDate
        ).firstOrNull { (word, _) -> Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(value) }
        if (relative != null) return DateMatch(relative.second, relative.first)

        numericDate.find(value)?.let { match ->
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val year = resolveYear(match.groupValues[3], referenceDate.year)
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { return DateMatch(it, match.value) }
        }

        namedDate.find(value)?.let { match ->
            val day = match.groupValues[1].toInt()
            val month = monthNumber(match.groupValues[2]) ?: return@let
            val year = resolveYear(match.groupValues[3], referenceDate.year)
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { return DateMatch(it, match.value) }
        }

        val weekdayWords = linkedMapOf(
            "montag" to DayOfWeek.MONDAY,
            "dienstag" to DayOfWeek.TUESDAY,
            "mittwoch" to DayOfWeek.WEDNESDAY,
            "donnerstag" to DayOfWeek.THURSDAY,
            "freitag" to DayOfWeek.FRIDAY,
            "samstag" to DayOfWeek.SATURDAY,
            "sonntag" to DayOfWeek.SUNDAY
        )
        for ((word, dayOfWeek) in weekdayWords) {
            val match = Regex("\\b(?:nächsten|naechsten|kommenden|am)?\\s*$word\\b", RegexOption.IGNORE_CASE).find(value) ?: continue
            val strictNext = match.value.contains("nächsten", true) || match.value.contains("naechsten", true) || match.value.contains("kommenden", true)
            val date = if (strictNext) {
                referenceDate.with(TemporalAdjusters.next(dayOfWeek))
            } else {
                referenceDate.with(TemporalAdjusters.nextOrSame(dayOfWeek))
            }
            return DateMatch(date, match.value)
        }
        return null
    }

    private fun resolveYear(text: String, currentYear: Int): Int {
        if (text.isBlank()) return currentYear
        val value = text.toInt()
        return if (text.length <= 2) {
            if (value <= 69) 2000 + value else 1900 + value
        } else value
    }

    private fun time(hour: String, minute: String): LocalTime = LocalTime.of(hour.toInt(), minute.ifBlank { "0" }.toInt())

    private fun monthNumber(value: String): Int? = when (value.lowercase(Locale.GERMAN)) {
        "januar", "jan" -> 1
        "februar", "feb" -> 2
        "märz", "maerz", "mrz" -> 3
        "april", "apr" -> 4
        "mai" -> 5
        "juni", "jun" -> 6
        "juli", "jul" -> 7
        "august", "aug" -> 8
        "september", "sep", "sept" -> 9
        "oktober", "okt" -> 10
        "november", "nov" -> 11
        "dezember", "dez" -> 12
        else -> null
    }

    private fun locale(settings: AppSettings): Locale = when (settings.dateTimeLocale) {
        DateTimeLocale.DE_DE -> Locale.GERMANY
        DateTimeLocale.SYSTEM -> Locale.getDefault()
    }
}
