package com.baoji.fitness

import com.baoji.fitness.domain.algorithm.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * M2 验收：streak 的 5 种边界。
 * 规则定死：**今天没练但昨天练了，streak 不算断**（当天还没过完）。
 */
class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 9, 15) // 周二

    @Test
    fun emptySet_hasNoStreak() {
        val days = emptySet<LocalDate>()
        assertEquals(0, StreakCalculator.currentStreak(days, today))
        assertEquals(0, StreakCalculator.longestStreak(days))
    }

    @Test
    fun onlyToday_countsAsOne() {
        val days = setOf(today)
        assertEquals(1, StreakCalculator.currentStreak(days, today))
        assertEquals(1, StreakCalculator.longestStreak(days))
    }

    @Test
    fun onlyYesterday_isNotBrokenBecauseTodayIsNotOver() {
        val days = setOf(today.minusDays(1))
        assertEquals(1, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun gapOfOneDay_breaksTheStreak() {
        // 今天练了、前天练了、昨天没练 → 只算今天这 1 天
        val days = setOf(today, today.minusDays(2))
        assertEquals(1, StreakCalculator.currentStreak(days, today))
        assertEquals("最长连续仍是 1 天", 1, StreakCalculator.longestStreak(days))
    }

    @Test
    fun consecutiveDays_areCountedBackwards() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(3))
        assertEquals(4, StreakCalculator.currentStreak(days, today))

        // 中间断过一天的长序列：最长段在更早的位置
        val history = setOf(
            today,
            today.minusDays(1),
            today.minusDays(3),
            today.minusDays(4),
            today.minusDays(5),
            today.minusDays(6)
        )
        assertEquals(2, StreakCalculator.currentStreak(history, today))
        assertEquals("最长段是断点之前那 4 天", 4, StreakCalculator.longestStreak(history))
    }

    @Test
    fun trainedYesterdayOnly_doesNotCountDaysBeforeTheGap() {
        // 昨天练了、前天没练 → 1 天（不能跳过缺口继续往前数）
        val days = setOf(today.minusDays(1), today.minusDays(3))
        assertEquals(1, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun remainingPlannedDaysThisWeek_countsFromTodayForward() {
        // 9/15 是周二；本周计划 一/三/五
        val planned = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

        // 周一已练 → 从今天（周二）起还剩 周三、周五 = 2
        assertEquals(
            2,
            StreakCalculator.remainingPlannedDaysThisWeek(setOf(LocalDate.of(2026, 9, 14)), today, planned)
        )
        // 周一、周三都已练 → 还剩 1
        assertEquals(
            1,
            StreakCalculator.remainingPlannedDaysThisWeek(
                setOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)),
                today,
                planned
            )
        )
        // 周一没练也不算「今天之前欠的账」
        assertEquals(
            2,
            StreakCalculator.remainingPlannedDaysThisWeek(emptySet(), today, planned)
        )
        // 周五之后的计划日不会被算进「本周还剩」
        assertEquals(
            0,
            StreakCalculator.remainingPlannedDaysThisWeek(
                setOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 18)),
                today,
                planned
            )
        )
    }

    @Test
    fun restDayTomorrow_isDetectedFromPlannedDays() {
        val planned = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        // 周二 → 明天周三，是训练日
        assertFalse(StreakCalculator.isRestDayTomorrow(today, planned))
        // 周三 → 明天周四，休息日
        assertTrue(StreakCalculator.isRestDayTomorrow(LocalDate.of(2026, 9, 16), planned))
    }
}
