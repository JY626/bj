package com.baoji.fitness.domain.algorithm

import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.domain.model.workoutTypeLabel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 周期粒度。UI 上对应「本周 / 本月 / 近8周」三个 chips。 */
enum class ReviewPeriod(val label: String) {
    THIS_WEEK("本周"),
    THIS_MONTH("本月"),
    LAST_8_WEEKS("近8周")
}

/** 左闭右开的本地日期窗口 `[from, toExclusive)`。 */
data class DateWindow(val from: LocalDate, val toExclusive: LocalDate) {
    fun contains(day: LocalDate): Boolean = !day.isBefore(from) && day.isBefore(toExclusive)
}

/** 当前周期 + 用于环比的上一个周期（无上期时为 null）。 */
data class PeriodWindows(val current: DateWindow, val previous: DateWindow?)

data class PeriodReview(
    val label: String,
    val sessionCount: Int,
    val totalVolumeKg: Double,
    /** 有效组数，**不含热身** */
    val totalSets: Int,
    /** 推 / 拉 / 腿 / 练 → 训练次数 */
    val typeCounts: Map<String, Int>,
    /** 对比上一周期的容量变化（%）；无上期或上期为 0 时返回 null */
    val volumeDeltaPct: Double?
)

data class TrendPoint(val label: String, val volumeKg: Double)

/**
 * 周期回顾的纯计算层。**所有边界都按本地时区 `LocalDate` 切**，
 * 不使用 SQLite 日期函数（`strftime` 按 UTC 解释会把跨夜训练算到前一天）。
 *
 * 统一口径（与训练页、日历三处必须一致）：
 * - 容量 = Σ `weightKg × reps`，**排除 `setType == "WARMUP"`**
 * - 有效组数 = 同上，排除热身组
 * - **一次训练归属于其会话的开始日**（跨零点的会话整体算前一天，不拆成两天），
 *   与日历打卡 `getTrainedDayTypes()` 同一口径
 *
 * 注意：部位有效组数 vs MEV/MAV/MRV **不在这里算**。那三个阈值是「每周」口径，
 * 放进「本月 / 近8周」窗口必然误判为过度训练，所以部位状态只由
 * [WorkoutRepository.getWeeklyMuscleComparison]（固定自然周）提供单一真相源。
 */
object PeriodReviewCalculator {

    /** 需要参与容量/组数统计的组：已完成 且 非热身。 */
    fun effectiveSets(sets: List<WorkoutSetEntity>): List<WorkoutSetEntity> =
        sets.filter { it.isCompleted && it.setType != "WARMUP" }

    fun volumeOf(sets: List<WorkoutSetEntity>): Double =
        effectiveSets(sets).sumOf { it.weightKg * it.reps }

    /** 完成过至少一组（含热身）的会话 —— 「训练打卡」口径。 */
    fun checkInSessionIds(sets: List<WorkoutSetEntity>): Set<Long> =
        sets.filter { it.isCompleted }.map { it.sessionId }.toSet()

    /**
     * 部位 → 有效组数。排除热身组，以及不在 [TRACKED_MUSCLES] 内的部位（如「核心」）。
     * 周期回顾与本周/上周部位对比共用这一份口径。
     */
    fun muscleSetCounts(
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>
    ): Map<String, Int> {
        val exerciseById = exercises.associateBy { it.id }
        val counts = mutableMapOf<String, Int>()
        for (set in effectiveSets(sets)) {
            val muscle = exerciseById[set.exerciseId]?.targetMuscle ?: continue
            if (muscle !in TRACKED_MUSCLES) continue
            counts[muscle] = (counts[muscle] ?: 0) + 1
        }
        return counts
    }

    fun instantToLocalDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /**
     * 落在窗口内的会话 —— 归期按**会话开始日**（跳零点的整场训练算开始那天），
     * 与日历打卡 `getTrainedDayTypes()` 同一口径。
     */
    fun sessionsIn(
        sessions: List<WorkoutSessionEntity>,
        window: DateWindow,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<WorkoutSessionEntity> =
        sessions.filter { window.contains(instantToLocalDate(it.startTime, zone)) }

    /** 上述会话名下的组。与 [sessionsIn] 配对使用，保证容量与会话次数不会各说一套。 */
    fun setsOf(
        sets: List<WorkoutSetEntity>,
        sessions: List<WorkoutSessionEntity>
    ): List<WorkoutSetEntity> {
        val ids = sessions.map { it.id }.toSet()
        return sets.filter { it.sessionId in ids }
    }

    /** 「本周」以**周一**为一周之首，与日历页首列一致。 */
    fun startOfWeek(day: LocalDate): LocalDate =
        day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun currentWeek(today: LocalDate): DateWindow =
        DateWindow(startOfWeek(today), startOfWeek(today).plusWeeks(1))

    fun currentMonth(today: LocalDate): DateWindow =
        DateWindow(today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1))

    fun lastWeeks(today: LocalDate, weeks: Int): DateWindow =
        DateWindow(startOfWeek(today).minusWeeks((weeks - 1).coerceAtLeast(0).toLong()), startOfWeek(today).plusWeeks(1))

    fun resolveWindows(period: ReviewPeriod, today: LocalDate): PeriodWindows = when (period) {
        ReviewPeriod.THIS_WEEK -> {
            val cur = currentWeek(today)
            PeriodWindows(cur, DateWindow(cur.from.minusWeeks(1), cur.from))
        }
        ReviewPeriod.THIS_MONTH -> {
            val cur = currentMonth(today)
            val prevStart = cur.from.minusMonths(1)
            PeriodWindows(cur, DateWindow(prevStart, cur.from))
        }
        // 近 8 周的上期 = 再往前 8 周
        ReviewPeriod.LAST_8_WEEKS -> {
            val cur = lastWeeks(today, 8)
            PeriodWindows(cur, DateWindow(cur.from.minusWeeks(8), cur.from))
        }
    }

    /**
     * 组装一次周期回顾。
     *
     * @param sets 当前窗口内的组（可含未完成/热身，函数内部按口径过滤）。
     *   调用方必须按**会话开始日**取窗口，与 [sessions] 同一区间，否则会出现
     *   「训练 0 次却有 4820kg 容量」这种自相矛盾的卡片。
     * @param sessions 当前窗口内的会话（按 startTime 取）
     * @param previousVolumeKg 上一窗口容量；null 表示无上期（环比显示为 null 而非 0%）
     */
    fun buildPeriodReview(
        label: String,
        sets: List<WorkoutSetEntity>,
        sessions: List<WorkoutSessionEntity>,
        previousVolumeKg: Double? = null
    ): PeriodReview {
        val effective = effectiveSets(sets)

        val checkInIds = checkInSessionIds(sets)
        val trainedSessions = sessions.filter { it.id in checkInIds }

        val typeCounts = trainedSessions
            .groupingBy { workoutTypeLabel(it.title) }
            .eachCount()
            .toSortedMap()

        val totalVolume = effective.sumOf { it.weightKg * it.reps }
        val delta = if (previousVolumeKg != null && previousVolumeKg > 0.0) {
            (totalVolume - previousVolumeKg) / previousVolumeKg * 100.0
        } else {
            null
        }

        return PeriodReview(
            label = label,
            sessionCount = trainedSessions.size,
            totalVolumeKg = totalVolume,
            totalSets = effective.size,
            typeCounts = typeCounts,
            volumeDeltaPct = delta
        )
    }

    /**
     * 近 N 周容量趋势（含当前这一周，最旧 → 最新）。
     * [sets] 只需覆盖这 N 周即可，多余的组会被窗口过滤掉。
     * 归期口径同 [buildPeriodReview]：按**会话开始日**，不是组的完成时刻。
     */
    fun buildWeeklyTrend(
        sets: List<WorkoutSetEntity>,
        sessions: List<WorkoutSessionEntity>,
        weeks: Int,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TrendPoint> {
        val dayOfSession = sessions.associate { it.id to instantToLocalDate(it.startTime, zone) }
        val thisWeekStart = startOfWeek(today)
        val firstWeekStart = thisWeekStart.minusWeeks((weeks - 1).coerceAtLeast(0).toLong())
        val effective = effectiveSets(sets)

        return (0 until weeks).map { index ->
            val start = firstWeekStart.plusWeeks(index.toLong())
            val end = start.plusWeeks(1)
            val volume = effective
                .filter { set ->
                    val day = dayOfSession[set.sessionId] ?: return@filter false
                    !day.isBefore(start) && day.isBefore(end)
                }
                .sumOf { it.weightKg * it.reps }
            TrendPoint(label = "${start.monthValue}/${start.dayOfMonth}", volumeKg = volume)
        }
    }

    val TRACKED_MUSCLES = listOf("胸部", "背部", "腿部", "肩部", "手臂")
}