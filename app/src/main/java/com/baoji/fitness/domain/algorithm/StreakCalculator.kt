package com.baoji.fitness.domain.algorithm

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * M2 连续训练天数。**规则定死**：
 * - 今天没练但昨天练了，streak **不算断**（当天还没过完）。
 * - 打卡口径 = 当天至少完成一组（见 `WorkoutRepository.getTrainedDayTypes`），空壳会话不算。
 */
object StreakCalculator {

    fun currentStreak(trainedDays: Set<LocalDate>, today: LocalDate): Int {
        // 今天还没练就从前一天往回数；今天练了就从今天数
        var cursor = if (trainedDays.contains(today)) today else today.minusDays(1)
        if (!trainedDays.contains(cursor)) return 0

        var count = 0
        while (trainedDays.contains(cursor)) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun longestStreak(trainedDays: Set<LocalDate>): Int {
        if (trainedDays.isEmpty()) return 0
        val sorted = trainedDays.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /** 本周（周一为首）计划里、从今天起还没完成的天数 —— UI 上的「本周还剩 N 练」。 */
    fun remainingPlannedDaysThisWeek(
        trainedDays: Set<LocalDate>,
        today: LocalDate,
        plannedDays: Set<DayOfWeek>
    ): Int {
        val monday = PeriodReviewCalculator.startOfWeek(today)
        return (0..6)
            .map { monday.plusDays(it.toLong()) }
            .count { day ->
                day.dayOfWeek in plannedDays && !day.isBefore(today) && !trainedDays.contains(day)
            }
    }

    /** 明天是不是计划里的休息日（用于训练页归档态那句文案）。 */
    fun isRestDayTomorrow(today: LocalDate, plannedDays: Set<DayOfWeek>): Boolean =
        today.plusDays(1).dayOfWeek !in plannedDays
}
