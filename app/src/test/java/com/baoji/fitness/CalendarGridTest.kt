package com.baoji.fitness

import com.baoji.fitness.ui.calendar.monthGridCells
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarGridTest {

    private fun firstColumnIsMonday(month: YearMonth) {
        val cells = monthGridCells(month)
        assertEquals("格子数必须是 7 的整数倍", 0, cells.size % 7)
        for (col in 0 until 7) {
            val first = cells[col]
            if (first != null && first.dayOfMonth == 1) {
                // 1 号所在列必须等于它的星期偏移（周一=0）
                assertEquals(first.dayOfWeek.value - 1, col)
                return
            }
        }
        assertTrue("该月起始行前 7 格必含 1 号", false)
    }

    @Test
    fun testSeptember2026StartsOnTuesday() {
        // 2026-09-01 是周二 -> 1 号落在第 2 列（索引 1）
        val cells = monthGridCells(YearMonth.of(2026, 9))
        assertEquals(35, cells.size)
        assertNull(cells[0])
        assertEquals(LocalDate.of(2026, 9, 1), cells[1])
        assertEquals(LocalDate.of(2026, 9, 30), cells[30])
        assertNull(cells[32])
        firstColumnIsMonday(YearMonth.of(2026, 9))
    }

    @Test
    fun testAllDaysPresentExactlyOnce() {
        for (month in listOf(YearMonth.of(2026, 2), YearMonth.of(2024, 2), YearMonth.of(2026, 11))) {
            val days = monthGridCells(month).filterNotNull()
            assertEquals("天数=当月长度", month.lengthOfMonth(), days.size)
            assertEquals("无重复且有序", (1..month.lengthOfMonth()).toList(), days.map { it.dayOfMonth })
        }
    }
}