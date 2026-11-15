package ru.home.vikunjavoice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class RussianDateParserTest {

    private val zone = ZoneId.of("Europe/Moscow")

    // Воскресенье, 15 ноября 2026, 12:00 по Москве
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 15, 12, 0, 0, 0, zone)

    private fun parse(text: String) = RussianDateParser.parse(text, now)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)

    @Test
    fun `обычная фраза без даты`() {
        val p = parse("купить хлеб")
        assertEquals("Купить хлеб", p.title)
        assertNull(p.due)
    }

    @Test
    fun `завтра в 10`() {
        val p = parse("завтра в 10 позвонить в налоговую")
        assertEquals("Позвонить в налоговую", p.title)
        assertEquals(at(2026, 11, 16, 10), p.due)
    }

    @Test
    fun `послезавтра с минутами`() {
        val p = parse("послезавтра в 18:30 созвон")
        assertEquals("Созвон", p.title)
        assertEquals(at(2026, 11, 17, 18, 30), p.due)
    }

    @Test
    fun `день недели`() {
        val p = parse("в пятницу в 15:00 отчёт")
        assertEquals("Отчёт", p.title)
        assertEquals(DayOfWeek.FRIDAY, p.due?.dayOfWeek)
        assertEquals(15, p.due?.hour)
    }

    @Test
    fun `через три дня`() {
        val p = parse("через 3 дня купить молоко")
        assertEquals("Купить молоко", p.title)
        assertEquals(at(2026, 11, 18, 9), p.due)
    }

    @Test
    fun `через час`() {
        val p = parse("через час позвонить")
        assertEquals("Позвонить", p.title)
        assertEquals(now.plusHours(1), p.due)
    }

    @Test
    fun `вечером сегодня`() {
        val p = parse("в 10 вечера позвонить")
        assertEquals("Позвонить", p.title)
        assertEquals(at(2026, 11, 15, 22), p.due)
    }

    @Test
    fun `утро уже прошло - значит завтра`() {
        val p = parse("в 10 утра позвонить")
        assertEquals("Позвонить", p.title)
        assertEquals(at(2026, 11, 16, 10), p.due)
    }

    @Test
    fun `абсолютная дата`() {
        val p = parse("20 ноября в 14:00 встреча")
        assertEquals("Встреча", p.title)
        assertEquals(at(2026, 11, 20, 14), p.due)
    }

    @Test
    fun `абсолютная дата словами`() {
        val p = parse("двадцатого ноября в 9 утра встреча")
        assertEquals("Встреча", p.title)
        assertEquals(at(2026, 11, 20, 9), p.due)
    }

    @Test
    fun `прошедшая дата уезжает на следующий год`() {
        val p = parse("1 марта в 10:00 отчёт")
        assertEquals(at(2027, 3, 1, 10), p.due)
    }

    @Test
    fun `полдень`() {
        val p = parse("в полдень позвонить")
        assertEquals("Позвонить", p.title)
        assertEquals(at(2026, 11, 15, 12), p.due)
    }

    @Test
    fun `напоминание без даты`() {
        val p = parse("напомни купить хлеб")
        assertEquals("Купить хлеб", p.title)
        assertNull(p.due)
    }

    @Test
    fun `напоминание с датой`() {
        val p = parse("напомни завтра в 9 выпить таблетки")
        assertEquals("Выпить таблетки", p.title)
        assertEquals(at(2026, 11, 16, 9), p.due)
    }

    @Test
    fun `через неделю`() {
        val p = parse("через неделю проверить отчёт")
        assertEquals("Проверить отчёт", p.title)
        assertEquals(at(2026, 11, 22, 9), p.due)
    }

    @Test
    fun `через две недели`() {
        val p = parse("через 2 недели созвон")
        assertEquals("Созвон", p.title)
        assertEquals(at(2026, 11, 29, 9), p.due)
    }
}
