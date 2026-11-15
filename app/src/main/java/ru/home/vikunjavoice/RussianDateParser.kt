package ru.home.vikunjavoice

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Результат разбора: очищенный заголовок и срок (если он был во фразе). */
data class ParsedTask(val title: String, val due: ZonedDateTime?)

/**
 * Локальный разбор русской фразы: вытаскивает дату/время и очищает заголовок.
 * Работает полностью на телефоне, без сети и LLM.
 *
 * Поддерживается: сегодня, завтра, послезавтра, «через N дней/недель/месяцев/часов/минут»,
 * «в понедельник…воскресенье», «20 ноября», «двадцатого ноября», «в 10», «в 10:30»,
 * «в 10 утра/дня/вечера/ночи», «в полдень/полночь».
 */
object RussianDateParser {

    private const val LETTER = "\\p{L}"

    private val MONTHS = mapOf(
        "января" to 1, "февраля" to 2, "марта" to 3, "апреля" to 4,
        "мая" to 5, "июня" to 6, "июля" to 7, "августа" to 8,
        "сентября" to 9, "октября" to 10, "ноября" to 11, "декабря" to 12,
    )

    private val WEEKDAYS = mapOf(
        "понедельник" to DayOfWeek.MONDAY,
        "вторник" to DayOfWeek.TUESDAY,
        "среду" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY,
        "пятницу" to DayOfWeek.FRIDAY,
        "субботу" to DayOfWeek.SATURDAY,
        "воскресенье" to DayOfWeek.SUNDAY,
    )

    /** Порядковые числительные в родительном падеже: «двадцатого ноября». */
    private val ORDINALS = mapOf(
        "первого" to 1, "второго" to 2, "третьего" to 3, "четвертого" to 4,
        "пятого" to 5, "шестого" to 6, "седьмого" to 7, "восьмого" to 8,
        "девятого" to 9, "десятого" to 10, "одиннадцатого" to 11,
        "двенадцатого" to 12, "тринадцатого" to 13, "четырнадцатого" to 14,
        "пятнадцатого" to 15, "шестнадцатого" to 16, "семнадцатого" to 17,
        "восемнадцатого" to 18, "девятнадцатого" to 19, "двадцатого" to 20,
        "двадцать первого" to 21, "двадцать второго" to 22,
        "двадцать третьего" to 23, "двадцать четвертого" to 24,
        "двадцать пятого" to 25, "двадцать шестого" to 26,
        "двадцать седьмого" to 27, "двадцать восьмого" to 28,
        "двадцать девятого" to 29, "тридцатого" to 30,
        "тридцать первого" to 31,
    )

    private fun word(s: String) = "(?<!$LETTER)$s(?!$LETTER)"

    fun parse(raw: String, now: ZonedDateTime = ZonedDateTime.now()): ParsedTask {
        val text = raw.trim()
        val lower = text.lowercase(Locale("ru"))
        val spans = mutableListOf<IntRange>()

        var date: LocalDate? = null
        var time: LocalTime? = null
        var relative: ZonedDateTime? = null

        fun take(m: MatchResult) {
            spans.add(m.range)
        }

        fun setDate(d: LocalDate) {
            if (date == null) date = d
        }

        // --- относительное время: «через час», «через полчаса», «через 30 минут» ---
        Regex("через\\s+полчаса").find(lower)?.let { m ->
            relative = now.plusMinutes(30); take(m)
        }
        Regex("через\\s+(\\d+)\\s+(?:час|часа|часов)").find(lower)?.let { m ->
            relative = relative ?: now.plusHours(m.groupValues[1].toLong()); take(m)
        }
        Regex("через\\s+(?:час|часик)").find(lower)?.let { m ->
            relative = relative ?: now.plusHours(1); take(m)
        }
        Regex("через\\s+(\\d+)\\s+(?:минуту|минуты|минут)").find(lower)?.let { m ->
            relative = relative ?: now.plusMinutes(m.groupValues[1].toLong()); take(m)
        }

        // --- относительные даты ---
        Regex("через\\s+(\\d+)\\s+(?:день|дня|дней)").find(lower)?.let { m ->
            setDate(now.toLocalDate().plusDays(m.groupValues[1].toLong())); take(m)
        }
        Regex("через\\s+(?:(\\d+)\\s+)?(?:неделю|недели|недель)").find(lower)?.let { m ->
            val k = m.groupValues[1].toLongOrNull() ?: 1L
            setDate(now.toLocalDate().plusWeeks(k)); take(m)
        }
        Regex("через\\s+(?:(\\d+)\\s+)?(?:месяц|месяца|месяцев)").find(lower)?.let { m ->
            val k = m.groupValues[1].toLongOrNull() ?: 1L
            setDate(now.toLocalDate().plusMonths(k)); take(m)
        }

        // --- «послезавтра» раньше «завтра», чтобы не перепутать ---
        Regex(word("послезавтра")).find(lower)?.let { m ->
            setDate(now.toLocalDate().plusDays(2)); take(m)
        }
        Regex(word("завтра")).find(lower)?.let { m ->
            setDate(now.toLocalDate().plusDays(1)); take(m)
        }
        Regex(word("сегодня")).find(lower)?.let { m ->
            setDate(now.toLocalDate()); take(m)
        }

        // --- день недели: «в пятницу» ---
        val weekdaysPattern = WEEKDAYS.keys.joinToString("|")
        Regex("(?<!$LETTER)(?:в|во)\\s+($weekdaysPattern)(?!$LETTER)").find(lower)?.let { m ->
            val target = WEEKDAYS.getValue(m.groupValues[1])
            val candidate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(target))
            setDate(if (candidate.isBefore(now.toLocalDate())) candidate.plusWeeks(1) else candidate)
            take(m)
        }

        // --- абсолютная дата: «20 ноября», «20 ноября 2027» ---
        val monthsPattern = MONTHS.keys.joinToString("|")
        Regex("(?<!$LETTER)(\\d{1,2})\\s+($monthsPattern)(?:\\s+(\\d{4}))?(?!$LETTER)")
            .find(lower)?.let { m ->
                val day = m.groupValues[1].toInt()
                val month = MONTHS.getValue(m.groupValues[2])
                val year = m.groupValues[3].toIntOrNull()
                resolveDate(now.toLocalDate(), day, month, year)?.let { setDate(it) }
                take(m)
            }

        // --- абсолютная дата словами: «двадцатого ноября» ---
        val ordinalsPattern = ORDINALS.keys.joinToString("|")
        Regex("(?<!$LETTER)($ordinalsPattern)\\s+($monthsPattern)(?:\\s+(\\d{4}))?(?!$LETTER)")
            .find(lower)?.let { m ->
                val day = ORDINALS.getValue(m.groupValues[1])
                val month = MONTHS.getValue(m.groupValues[2])
                val year = m.groupValues[3].toIntOrNull()
                resolveDate(now.toLocalDate(), day, month, year)?.let { setDate(it) }
                take(m)
            }

        // --- время суток ---
        Regex("(?<!$LETTER)(?:в|во)\\s+полдень(?!$LETTER)").find(lower)?.let { m ->
            time = LocalTime.of(12, 0); take(m)
        }
        Regex("(?<!$LETTER)(?:в|во)\\s+полночь(?!$LETTER)").find(lower)?.let { m ->
            time = LocalTime.of(0, 0); take(m)
        }

        // --- «в 10», «в 10:30», «к 9 утра» ---
        Regex("(?<!$LETTER)(?:в|во|к|ко)\\s+(\\d{1,2})(?:[:.](\\d{2}))?(?:\\s*(утра|вечера|дня|ночи))?(?!\\d)")
            .find(lower)?.let { m ->
                val hourRaw = m.groupValues[1].toInt()
                val minute = m.groupValues[2].toIntOrNull() ?: 0
                val daypart = m.groupValues[3]
                if (hourRaw in 0..23 && minute in 0..59) {
                    time = applyDaypart(hourRaw, minute, daypart)
                    take(m)
                }
            }

        // --- собрать результат ---
        val due: ZonedDateTime? = when {
            relative != null -> relative
            date == null && time == null -> null
            else -> {
                var day = date ?: now.toLocalDate()
                var at = time ?: LocalTime.of(9, 0)
                if (date == null && time != null && at.isBefore(now.toLocalTime())) {
                    day = day.plusDays(1)   // «в 10», а уже 12:00 — значит завтра
                }
                if (time == null) at = LocalTime.of(9, 0)
                ZonedDateTime.of(day, at, now.zone)
            }
        }

        return ParsedTask(title = cleanTitle(text, spans), due = due)
    }

    private fun resolveDate(today: LocalDate, day: Int, month: Int, year: Int?): LocalDate? {
        if (day !in 1..31) return null
        val safeYear = year ?: today.year
        val date = runCatching { LocalDate.of(safeYear, month, day) }.getOrNull() ?: return null
        return if (year == null && date.isBefore(today)) date.plusYears(1) else date
    }

    private fun applyDaypart(hour: Int, minute: Int, daypart: String): LocalTime {
        var h = hour
        when (daypart) {
            "утра" -> if (h == 12) h = 0
            "дня" -> if (h in 1..11) h += 12
            "вечера" -> if (h in 1..11) h += 12
            "ночи" -> if (h == 12) h = 0
        }
        return LocalTime.of(h.coerceIn(0, 23), minute)
    }

    private fun cleanTitle(text: String, spans: List<IntRange>): String {
        val sb = StringBuilder()
        var pos = 0
        for (r in spans.sortedBy { it.first }) {
            if (r.first > pos) sb.append(text, pos, r.first)
            pos = maxOf(pos, r.last + 1)
        }
        if (pos < text.length) sb.append(text, pos, text.length)

        var title = sb.toString().replace(Regex("\\s+"), " ").trim()
        val prompts = Regex(
            "^(?:напомни|напомнить|добавь|добавить|создай|создать|запиши|записать|" +
                "поставь|поставить|задача|задачу|мне)(?![\\p{L}])[\\s,:;-]*"
        )
        repeat(3) {
            val next = title.replaceFirst(prompts, "").trim()
            if (next == title) return@repeat
            title = next
        }
        title = title.trim(',', '.', ';', ':', '-', '—', ' ')
        return capitalized(title.ifBlank { text })
    }

    private fun capitalized(s: String): String =
        s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
}
