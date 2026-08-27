package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.AppSettings
import cc.stkmn.shareparser.data.DateTimeLocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object FlexibleDateTimeParser {
    data class Result(
        val startEpochMs: Long?,
        val endEpochMs: Long?,
        val warnings: List<String> = emptyList()
    )

    private enum class DateOrder { DMY, MDY, YMD }

    private data class RegionalRules(
        val locale: Locale,
        val dateOrder: DateOrder,
        val englishWords: Boolean,
        val germanWords: Boolean,
        val preferTwelveHour: Boolean
    )

    private val isoDate = Regex("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)")
    private val numericDate = Regex("(?<!\\d)(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})(?:\\s*[./-]\\s*(\\d{2,4})(?!\\s*:))?\\.?(?!\\d)")
    private val germanNamedDate = Regex("(?<!\\d)(\\d{1,2})\\.?\\s+(januar|jan|februar|feb|märz|maerz|mrz|april|apr|mai|juni|jun|juli|jul|august|aug|september|sep|sept|oktober|okt|november|nov|dezember|dez)(?:\\s+(\\d{2,4}))?", RegexOption.IGNORE_CASE)
    private val englishDmyDate = Regex("(?<!\\d)(\\d{1,2})(?:st|nd|rd|th)?\\s+(january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sep|sept|october|oct|november|nov|december|dec)(?:,?\\s+(\\d{2,4}))?", RegexOption.IGNORE_CASE)
    private val englishMdyDate = Regex("\\b(january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sep|sept|october|oct|november|nov|december|dec)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{2,4}))?", RegexOption.IGNORE_CASE)

    private val timeRange = Regex(
        "(?<!\\d)(\\d{1,2})(?:[:.]([0-5]\\d))?\\s*(am|pm|uhr)?\\s*(?:-|–|—|bis|to)\\s*(\\d{1,2})(?:[:.]([0-5]\\d))?\\s*(am|pm|uhr)?(?!\\d)",
        RegexOption.IGNORE_CASE
    )
    private val singleTime = Regex(
        "(?<!\\d)(\\d{1,2})(?:[:.]([0-5]\\d))?\\s*(am|pm|uhr)?(?!\\d)",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        startValue: String,
        endValue: String = "",
        allDay: Boolean = false,
        settings: AppSettings = AppSettings(),
        reference: ZonedDateTime = ZonedDateTime.now()
    ): Result {
        if (startValue.isBlank()) return Result(null, null, listOf("Beginn fehlt und muss im Kalender manuell ergänzt werden."))

        val zone = reference.zone
        val rules = rules(settings)
        val startPart = parsePart(startValue, reference.toLocalDate(), rules)
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
        } else if (startPart.date != null && startPart.endTime != null && startPart.startTime != null) {
            var endDate = startPart.date
            if (startPart.endTime.isBefore(startPart.startTime)) endDate = endDate.plusDays(1)
            end = LocalDateTime.of(endDate, startPart.endTime).atZone(zone).toInstant().toEpochMilli()
        } else if (endValue.isNotBlank()) {
            val endPart = parsePart(endValue, startPart.date ?: reference.toLocalDate(), rules)
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

    private fun parsePart(value: String, fallbackDate: LocalDate, rules: RegionalRules): Part {
        val raw = value.trim()
        val lower = raw.lowercase(rules.locale)
        val dateMatch = findDate(lower, fallbackDate, rules)
        val withoutDate = dateMatch?.matchedText?.let { raw.replace(it, " ", ignoreCase = true) } ?: raw

        val range = timeRange.find(withoutDate)
        if (range != null) {
            val first = parseTime(range.groupValues[1], range.groupValues[2], range.groupValues[3], rules)
            val second = parseTime(range.groupValues[4], range.groupValues[5], range.groupValues[6], rules)
            if (first != null && second != null) return Part(dateMatch?.date, first, second)
        }

        val single = singleTime.find(withoutDate)
        val parsedTime = single?.let { parseTime(it.groupValues[1], it.groupValues[2], it.groupValues[3], rules) }
        return Part(date = dateMatch?.date, startTime = parsedTime, endTime = null)
    }

    private data class DateMatch(val date: LocalDate, val matchedText: String)

    private fun findDate(value: String, referenceDate: LocalDate, rules: RegionalRules): DateMatch? {
        relativeDate(value, referenceDate, rules)?.let { return it }

        isoDate.find(value)?.let { match ->
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { return DateMatch(it, match.value) }
        }

        numericDate.find(value)?.let { match ->
            val first = match.groupValues[1].toInt()
            val second = match.groupValues[2].toInt()
            val year = resolveYear(match.groupValues[3], referenceDate.year)
            val (day, month) = when (rules.dateOrder) {
                DateOrder.DMY -> first to second
                DateOrder.MDY -> second to first
                DateOrder.YMD -> first to second
            }
            runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { return DateMatch(it, match.value) }
        }

        if (rules.germanWords) {
            germanNamedDate.find(value)?.let { match ->
                val day = match.groupValues[1].toInt()
                val month = germanMonth(match.groupValues[2]) ?: return@let
                val year = resolveYear(match.groupValues[3], referenceDate.year)
                runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { return DateMatch(it, match.value) }
            }
        }

        if (rules.englishWords) {
            englishDmyDate.find(value)?.let { match ->
                val day = match.groupValues[1].toInt()
                val month = englishMonth(match.groupValues[2]) ?: return@let
                val year = resolveYear(match.groupValues[3], referenceDate.year)
                runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { return DateMatch(it, match.value) }
            }
            englishMdyDate.find(value)?.let { match ->
                val month = englishMonth(match.groupValues[1]) ?: return@let
                val day = match.groupValues[2].toInt()
                val year = resolveYear(match.groupValues[3], referenceDate.year)
                runCatching { LocalDate.of(year, month, day) }.getOrNull()?.let { return DateMatch(it, match.value) }
            }
        }

        weekdayDate(value, referenceDate, rules)?.let { return it }
        return null
    }

    private fun relativeDate(value: String, referenceDate: LocalDate, rules: RegionalRules): DateMatch? {
        val words = buildList {
            if (rules.germanWords) {
                add("übermorgen" to referenceDate.plusDays(2))
                add("uebermorgen" to referenceDate.plusDays(2))
                add("morgen" to referenceDate.plusDays(1))
                add("heute" to referenceDate)
            }
            if (rules.englishWords) {
                add("day after tomorrow" to referenceDate.plusDays(2))
                add("tomorrow" to referenceDate.plusDays(1))
                add("today" to referenceDate)
            }
        }
        return words.firstNotNullOfOrNull { (word, date) ->
            Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE).find(value)?.let { DateMatch(date, it.value) }
        }
    }

    private fun weekdayDate(value: String, referenceDate: LocalDate, rules: RegionalRules): DateMatch? {
        val weekdays = linkedMapOf<String, DayOfWeek>()
        if (rules.germanWords) {
            weekdays += mapOf(
                "montag" to DayOfWeek.MONDAY,
                "dienstag" to DayOfWeek.TUESDAY,
                "mittwoch" to DayOfWeek.WEDNESDAY,
                "donnerstag" to DayOfWeek.THURSDAY,
                "freitag" to DayOfWeek.FRIDAY,
                "samstag" to DayOfWeek.SATURDAY,
                "sonntag" to DayOfWeek.SUNDAY
            )
        }
        if (rules.englishWords) {
            weekdays += mapOf(
                "monday" to DayOfWeek.MONDAY,
                "tuesday" to DayOfWeek.TUESDAY,
                "wednesday" to DayOfWeek.WEDNESDAY,
                "thursday" to DayOfWeek.THURSDAY,
                "friday" to DayOfWeek.FRIDAY,
                "saturday" to DayOfWeek.SATURDAY,
                "sunday" to DayOfWeek.SUNDAY
            )
        }
        for ((word, dayOfWeek) in weekdays) {
            val prefix = if (rules.germanWords) "(?:nächsten|naechsten|kommenden|am)?" else "(?:next|this|on)?"
            val match = Regex("\\b$prefix\\s*$word\\b", RegexOption.IGNORE_CASE).find(value) ?: continue
            val strictNext = listOf("nächsten", "naechsten", "kommenden", "next").any { match.value.contains(it, true) }
            val date = if (strictNext) referenceDate.with(TemporalAdjusters.next(dayOfWeek))
            else referenceDate.with(TemporalAdjusters.nextOrSame(dayOfWeek))
            return DateMatch(date, match.value)
        }
        return null
    }

    private fun parseTime(hourText: String, minuteText: String, markerText: String, rules: RegionalRules): LocalTime? {
        var hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.ifBlank { "0" }.toIntOrNull() ?: return null
        val marker = markerText.lowercase(Locale.ROOT)
        if (marker == "am" || marker == "pm") {
            if (hour !in 1..12) return null
            if (marker == "am" && hour == 12) hour = 0
            if (marker == "pm" && hour != 12) hour += 12
        } else {
            if (hour !in 0..23) return null
            if (rules.preferTwelveHour && hour in 1..12) {
                // Without AM/PM the value is ambiguous. Keep the literal hour instead of guessing.
            }
        }
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }

    private fun resolveYear(text: String, currentYear: Int): Int {
        if (text.isBlank()) return currentYear
        val value = text.toInt()
        return if (text.length <= 2) {
            if (value <= 69) 2000 + value else 1900 + value
        } else value
    }

    private fun rules(settings: AppSettings): RegionalRules = when (settings.dateTimeLocale) {
        DateTimeLocale.DE_DE -> RegionalRules(Locale.GERMANY, DateOrder.DMY, englishWords = false, germanWords = true, preferTwelveHour = false)
        DateTimeLocale.EN_US -> RegionalRules(Locale.US, DateOrder.MDY, englishWords = true, germanWords = false, preferTwelveHour = true)
        DateTimeLocale.EN_GB -> RegionalRules(Locale.UK, DateOrder.DMY, englishWords = true, germanWords = false, preferTwelveHour = false)
        DateTimeLocale.ISO -> RegionalRules(Locale.ROOT, DateOrder.YMD, englishWords = true, germanWords = true, preferTwelveHour = false)
        DateTimeLocale.SYSTEM -> {
            val locale = Locale.getDefault()
            val us = locale.country.equals("US", true)
            val german = locale.language.equals("de", true)
            RegionalRules(
                locale = locale,
                dateOrder = if (us) DateOrder.MDY else DateOrder.DMY,
                englishWords = locale.language.equals("en", true),
                germanWords = german,
                preferTwelveHour = us
            )
        }
    }

    private fun germanMonth(value: String): Int? = when (value.lowercase(Locale.GERMAN)) {
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

    private fun englishMonth(value: String): Int? = when (value.lowercase(Locale.ENGLISH)) {
        "january", "jan" -> 1
        "february", "feb" -> 2
        "march", "mar" -> 3
        "april", "apr" -> 4
        "may" -> 5
        "june", "jun" -> 6
        "july", "jul" -> 7
        "august", "aug" -> 8
        "september", "sep", "sept" -> 9
        "october", "oct" -> 10
        "november", "nov" -> 11
        "december", "dec" -> 12
        else -> null
    }
}
