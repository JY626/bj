package com.baoji.fitness

import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.domain.algorithm.PeriodReviewCalculator
import com.baoji.fitness.domain.algorithm.ReviewPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * M1 验收：空周期不崩、跨周边界、热身组被排除、环比无上期返回 null、
 * 以及**跨零点训练整场归属于会话开始日**（审计时发现的口径分裂回归测试）。
 *
 * 固定时区跑，避免本机时区变化影响结果。
 */
class PeriodReviewCalculatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    // 2026-09-15 是周二；本周 = 周一 09-14 → 周一 09-21，上周 = 09-07 → 09-14
    private val today: LocalDate = LocalDate.of(2026, 9, 15)

    private fun at(day: LocalDate, time: LocalTime): Long =
        day.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private val chest = ExerciseEntity(id = 1, name = "卧推", targetMuscle = "胸部", equipment = "杠铃")
    private val back = ExerciseEntity(id = 2, name = "硬拉", targetMuscle = "背部", equipment = "杠铃")
    private val core = ExerciseEntity(id = 3, name = "悬垂举腿", targetMuscle = "核心", equipment = "自重", trackWeight = false)
    private val exercises = listOf(chest, back, core)

    private fun set(
        id: Long,
        sessionId: Long,
        exerciseId: Long,
        weight: Double,
        reps: Int,
        setType: String = "NORMAL",
        completedAt: Long?
    ) = WorkoutSetEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        setOrder = id.toInt(),
        setType = setType,
        weightKg = weight,
        reps = reps,
        isCompleted = completedAt != null,
        completedAt = completedAt
    )

    private fun session(
        id: Long,
        title: String,
        day: LocalDate = today,
        time: LocalTime = LocalTime.of(19, 0)
    ) = WorkoutSessionEntity(
        id = id,
        title = title,
        startTime = at(day, time),
        status = "COMPLETED"
    )

    @Test
    fun emptyPeriod_returnsZerosAndDoesNotCrash() {
        val review = PeriodReviewCalculator.buildPeriodReview(
            label = "本周",
            sets = emptyList(),
            sessions = emptyList()
        )

        assertEquals(0, review.sessionCount)
        assertEquals(0.0, review.totalVolumeKg, 0.001)
        assertEquals(0, review.totalSets)
        assertTrue(review.typeCounts.isEmpty())
        assertNull("无上期时环比必须是 null，不能是 0%", review.volumeDeltaPct)
    }

    @Test
    fun warmupSetsAreExcludedFromVolumeAndSetCount() {
        val sets = listOf(
            set(1, 1, chest.id, 40.0, 12, setType = "WARMUP", completedAt = at(today, LocalTime.of(19, 5))),
            set(2, 1, chest.id, 80.0, 8, completedAt = at(today, LocalTime.of(19, 10))),
            set(3, 1, chest.id, 80.0, 7, completedAt = at(today, LocalTime.of(19, 15)))
        )

        val review = PeriodReviewCalculator.buildPeriodReview(
            label = "本周",
            sets = sets,
            sessions = listOf(session(1, "DAY 1 · 推日 (胸 肩)"))
        )

        assertEquals("热身组不计容量", 80.0 * 8 + 80.0 * 7, review.totalVolumeKg, 0.001)
        assertEquals("热身组不计有效组数", 2, review.totalSets)
    }

    /**
     * 回归：周日 23:30 开练、周一 00:05 打完的会话，**整场**必须算周日（上一周）。
     * 审计前实现是「组按 completedAt、会话按 startTime」两套窗口，
     * 于是本周卡片会出现「训练 0 次 · 总容量 1280kg」这种自相矛盾。
     */
    @Test
    fun crossMidnightSessionBelongsToItsStartDayEntirely() {
        val sundayNight = session(
            id = 1,
            title = "DAY 1 · 推日 (胸 肩)",
            day = LocalDate.of(2026, 9, 13),
            time = LocalTime.of(23, 30)
        )
        val setsAfterMidnight = listOf(
            set(1, 1, chest.id, 80.0, 8, completedAt = at(LocalDate.of(2026, 9, 14), LocalTime.of(0, 5))),
            set(2, 1, chest.id, 80.0, 8, completedAt = at(LocalDate.of(2026, 9, 14), LocalTime.of(0, 10)))
        )
        val windows = PeriodReviewCalculator.resolveWindows(ReviewPeriod.THIS_WEEK, today)

        val inThisWeek = PeriodReviewCalculator.sessionsIn(listOf(sundayNight), windows.current, zone)
        val inLastWeek = PeriodReviewCalculator.sessionsIn(listOf(sundayNight), windows.previous!!, zone)

        assertTrue("跨零点会话不属于本周", inThisWeek.isEmpty())
        assertEquals("整场归入开始日所在的上一周", listOf(1L), inLastWeek.map { it.id })

        val lastWeekReview = PeriodReviewCalculator.buildPeriodReview(
            label = "上周",
            sets = PeriodReviewCalculator.setsOf(setsAfterMidnight, inLastWeek),
            sessions = inLastWeek
        )
        assertEquals("会话次数与容量必须同源", 1, lastWeekReview.sessionCount)
        assertEquals(1280.0, lastWeekReview.totalVolumeKg, 0.001)
        assertEquals(mapOf("推" to 1), lastWeekReview.typeCounts)

        val thisWeekReview = PeriodReviewCalculator.buildPeriodReview(
            label = "本周",
            sets = PeriodReviewCalculator.setsOf(setsAfterMidnight, inThisWeek),
            sessions = inThisWeek
        )
        assertEquals("本周既没有次数也没有容量，两者一致", 0, thisWeekReview.sessionCount)
        assertEquals(0.0, thisWeekReview.totalVolumeKg, 0.001)
    }

    @Test
    fun setsOfOnlyReturnsSetsOfGivenSessions() {
        val a = session(1, "DAY 1 · 推日")
        val b = session(2, "DAY 2 · 拉日")
        val sets = listOf(
            set(1, 1, chest.id, 80.0, 8, completedAt = at(today, LocalTime.of(9, 0))),
            set(2, 2, back.id, 100.0, 5, completedAt = at(today, LocalTime.of(9, 0)))
        )

        assertEquals(listOf(1L), PeriodReviewCalculator.setsOf(sets, listOf(a)).map { it.id })
        assertEquals(2, PeriodReviewCalculator.setsOf(sets, listOf(a, b)).size)
        assertTrue("没有会话就没有组", PeriodReviewCalculator.setsOf(sets, emptyList()).isEmpty())
    }

    @Test
    fun volumeDeltaIsNullWithoutPreviousAndComputedWithIt() {
        val sets = listOf(set(1, 1, chest.id, 100.0, 10, completedAt = at(today, LocalTime.of(19, 0))))
        val sessions = listOf(session(1, "DAY 1 · 推日"))

        val noPrevious = PeriodReviewCalculator.buildPeriodReview(
            label = "本周", sets = sets, sessions = sessions, previousVolumeKg = null
        )
        assertNull(noPrevious.volumeDeltaPct)

        val zeroPrevious = PeriodReviewCalculator.buildPeriodReview(
            label = "本周", sets = sets, sessions = sessions, previousVolumeKg = 0.0
        )
        assertNull("上期容量为 0 时无法算百分比", zeroPrevious.volumeDeltaPct)

        val growing = PeriodReviewCalculator.buildPeriodReview(
            label = "本周", sets = sets, sessions = sessions, previousVolumeKg = 1000.0
        )
        assertEquals(0.0, growing.volumeDeltaPct!!, 0.001)

        val shrinking = PeriodReviewCalculator.buildPeriodReview(
            label = "本周", sets = sets, sessions = sessions, previousVolumeKg = 500.0
        )
        assertEquals(100.0, shrinking.volumeDeltaPct!!, 0.001)
    }

    @Test
    fun sessionCountOnlyCountsSessionsWithAtLeastOneCompletedSet() {
        val sets = listOf(
            set(1, 1, chest.id, 80.0, 8, completedAt = at(today, LocalTime.of(19, 0))),
            // 会话 2 是空壳：组存在但一组都没打勾
            set(2, 2, back.id, 100.0, 5, completedAt = null)
        )

        val review = PeriodReviewCalculator.buildPeriodReview(
            label = "本周",
            sets = sets,
            sessions = listOf(session(1, "DAY 1 · 推日 (胸 肩)"), session(2, "DAY 2 · 拉日 (背 二头)"))
        )

        assertEquals("空壳会话不算打卡", 1, review.sessionCount)
        assertEquals(mapOf("推" to 1), review.typeCounts)
    }

    @Test
    fun typeCountsAggregatePushPullLegs() {
        val sets = listOf(
            set(1, 1, chest.id, 80.0, 8, completedAt = at(today, LocalTime.of(9, 0))),
            set(2, 2, back.id, 100.0, 5, completedAt = at(today, LocalTime.of(12, 0))),
            set(3, 3, chest.id, 80.0, 8, completedAt = at(today, LocalTime.of(18, 0)))
        )
        val sessions = listOf(
            session(1, "DAY 1 · 推日 (胸 肩)"),
            session(2, "DAY 2 · 拉日 (背 二头)"),
            session(3, "自由肩臂训练")
        )

        val review = PeriodReviewCalculator.buildPeriodReview(
            label = "本周", sets = sets, sessions = sessions
        )

        assertEquals(mapOf("推" to 1, "拉" to 1, "练" to 1), review.typeCounts)
        assertEquals(3, review.sessionCount)
    }

    /**
     * 部位组数的唯一口径函数。MEV/MAV/MRV 是**周**阈值，所以部位状态只由固定自然周的
     * `getWeeklyMuscleComparison` 提供，不进周期回顾（否则「本月」必然被判过度训练）。
     */
    @Test
    fun muscleSetCountsSkipsWarmupAndUncompleted() {
        val sets = listOf(
            set(1, 1, chest.id, 80.0, 8, completedAt = at(today, LocalTime.of(9, 0))),
            set(2, 1, chest.id, 80.0, 8, completedAt = at(today, LocalTime.of(9, 5))),
            set(3, 1, back.id, 100.0, 5, completedAt = at(today, LocalTime.of(9, 10))),
            set(4, 1, chest.id, 40.0, 12, setType = "WARMUP", completedAt = at(today, LocalTime.of(8, 50))),
            set(5, 1, core.id, 0.0, 15, completedAt = at(today, LocalTime.of(9, 15))),
            set(6, 1, chest.id, 80.0, 8, completedAt = null)
        )

        val counts = PeriodReviewCalculator.muscleSetCounts(sets, exercises)

        // 热身与未完成不计，核心已被纳入追踪
        assertEquals(mapOf("胸部" to 2.0, "背部" to 1.0, "核心" to 1.0), counts)
    }

    /**
     * 本次修复的核心：一个动作同时给多个肌群计组 —— 高位下拉既算背部(1.0)也算手臂(0.5)。
     * 修复前手臂只能靠「二头弯举」一个动作，拉日练完仍是 0 组。
     */
    @Test
    fun oneExerciseContributesToBothPrimaryAndSecondaryMuscles() {
        val latPullDown = ExerciseEntity(
            id = 10, name = "高位下拉", targetMuscle = "背部",
            secondaryMuscles = "肱二头肌", equipment = "绳索"
        )
        val bench = ExerciseEntity(
            id = 11, name = "卧推", targetMuscle = "胸部",
            secondaryMuscles = "肱三头肌, 三角肌前束", equipment = "杠铃"
        )
        val sets = listOf(
            set(1, 1, latPullDown.id, 60.0, 10, completedAt = at(today, LocalTime.of(9, 0))),
            set(2, 1, bench.id, 80.0, 8, completedAt = at(today, LocalTime.of(9, 5)))
        )

        val counts = PeriodReviewCalculator.muscleSetCounts(sets, listOf(latPullDown, bench))

        assertEquals(1.0, counts["背部"]!!, 0.001)   // 高位下拉主肌群
        assertEquals(1.0, counts["手臂"]!!, 0.001)   // 高位下拉的间接（肱二头）+ 卧推的间接（肱三头）= 0.5+0.5
        assertEquals(0.5, counts["肩部"]!!, 0.001)   // 卧推的三角肌前束
        assertEquals(1.0, counts["胸部"]!!, 0.001)
    }

    /** 细分名→大类的桥接：三束都要归到肩部，「核心」是单独一类。 */
    @Test
    fun secondaryMuscleNamesMapToTrackedGroups() {
        assertEquals("肩部", PeriodReviewCalculator.secondaryMuscleGroup("三角肌前束"))
        assertEquals("肩部", PeriodReviewCalculator.secondaryMuscleGroup("三角肌中束"))
        assertEquals("肩部", PeriodReviewCalculator.secondaryMuscleGroup("三角肌后束"))
        assertEquals("手臂", PeriodReviewCalculator.secondaryMuscleGroup("肱三头肌"))
        assertEquals("腿部", PeriodReviewCalculator.secondaryMuscleGroup("股四头肌"))
        assertEquals("核心", PeriodReviewCalculator.secondaryMuscleGroup("腹直肌"))
        assertNull(PeriodReviewCalculator.secondaryMuscleGroup("全身"))
        assertNull(PeriodReviewCalculator.secondaryMuscleGroup(""))
    }

    @Test
    fun weeklyTrendBucketsBySessionStartDayAndIsOldestFirst() {
        val sessions = listOf(
            session(1, "DAY 1 · 推日", day = LocalDate.of(2026, 7, 28)),
            session(2, "DAY 2 · 拉日", day = LocalDate.of(2026, 9, 9)),
            // 上周日深夜开练，组打到本周一 00:30 —— 整场归上周
            session(4, "DAY 3 · 腿 + 外形补强", day = LocalDate.of(2026, 9, 13), time = LocalTime.of(23, 50)),
            session(3, "DAY 1 · 推日", day = LocalDate.of(2026, 9, 15))
        )
        val sets = listOf(
            set(1, 1, chest.id, 100.0, 10, completedAt = at(LocalDate.of(2026, 7, 28), LocalTime.of(19, 0))),
            set(2, 2, chest.id, 50.0, 10, completedAt = at(LocalDate.of(2026, 9, 9), LocalTime.of(19, 0))),
            set(3, 4, chest.id, 80.0, 10, completedAt = at(LocalDate.of(2026, 9, 14), LocalTime.of(0, 30))),
            set(4, 3, chest.id, 80.0, 10, completedAt = at(LocalDate.of(2026, 9, 15), LocalTime.of(19, 0)))
        )

        val trend = PeriodReviewCalculator.buildWeeklyTrend(
            sets = sets, sessions = sessions, weeks = 8, today = today, zone = zone
        )

        assertEquals(8, trend.size)
        assertEquals("第一周应从 7 周前的周一开始", "7/27", trend.first().label)
        assertEquals("最后一周是本周", "9/14", trend.last().label)
        assertEquals(1000.0, trend[0].volumeKg, 0.001)
        assertEquals("周一 00:30 的那组必须落在上一周", 1300.0, trend[6].volumeKg, 0.001)
        assertEquals(800.0, trend[7].volumeKg, 0.001)
        assertEquals("没有训练的那几周是 0，而不是被跳过", 0.0, trend[3].volumeKg, 0.001)
    }

    @Test
    fun weekWindowIsMondayFirstAndRightOpen() {
        val windows = PeriodReviewCalculator.resolveWindows(ReviewPeriod.THIS_WEEK, today)
        assertEquals(LocalDate.of(2026, 9, 14), windows.current.from)
        assertEquals(LocalDate.of(2026, 9, 21), windows.current.toExclusive)
        assertEquals(LocalDate.of(2026, 9, 7), windows.previous!!.from)
        assertEquals(LocalDate.of(2026, 9, 14), windows.previous!!.toExclusive)

        // 右开区间：下周一 00:00 属于下一周
        val nextMonday = session(9, "DAY 1 · 推日", day = LocalDate.of(2026, 9, 21), time = LocalTime.MIDNIGHT)
        assertTrue(PeriodReviewCalculator.sessionsIn(listOf(nextMonday), windows.current, zone).isEmpty())
        // 本周一 00:00 属于本周
        val thisMonday = session(8, "DAY 1 · 推日", day = LocalDate.of(2026, 9, 14), time = LocalTime.MIDNIGHT)
        assertEquals(1, PeriodReviewCalculator.sessionsIn(listOf(thisMonday), windows.current, zone).size)
    }

    @Test
    fun monthAndEightWeekWindowsAreResolvedLocally() {
        val month = PeriodReviewCalculator.resolveWindows(ReviewPeriod.THIS_MONTH, today)
        assertEquals(LocalDate.of(2026, 9, 1), month.current.from)
        assertEquals(LocalDate.of(2026, 10, 1), month.current.toExclusive)
        assertEquals(LocalDate.of(2026, 8, 1), month.previous!!.from)

        val eight = PeriodReviewCalculator.resolveWindows(ReviewPeriod.LAST_8_WEEKS, today)
        assertEquals(LocalDate.of(2026, 7, 27), eight.current.from)
        assertEquals(LocalDate.of(2026, 9, 21), eight.current.toExclusive)
        // 上期 = 再往前 8 周，两块窗口必须首尾相接
        assertEquals(eight.current.from, eight.previous!!.toExclusive)
    }

    /**
     * 回归：**间接肌群与主肌群同类时，不能被降成 0.5**。
     *
     * 真实 bug：`buildMap + put` 会让「三角肌前束」（归肩部）覆盖主肌群的 1.0，
     * 史密斯推肩 7 组被算成 3.5。11 个种子动作里 9 个中招。
     */
    @Test
    fun secondaryFromSameGroupDoesNotDowngradePrimary() {
        val shoulderPress = ExerciseEntity(
            id = 20, name = "史密斯推肩", targetMuscle = "肩部",
            secondaryMuscles = "三角肌前束, 三头肌", equipment = "史密斯"
        )
        val sets = (1..7).map {
            set(it.toLong(), 1, shoulderPress.id, 25.0, 10, completedAt = at(today, LocalTime.of(19, it)))
        }

        val counts = PeriodReviewCalculator.muscleSetCounts(sets, listOf(shoulderPress))

        assertEquals("主肌群必须计满 7 组，不能被间接的 0.5 覆盖", 7.0, counts["肩部"]!!, 0.001)
        assertEquals("三头肌作为间接，另计 3.5 组", 3.5, counts["手臂"]!!, 0.001)
    }

    /** 主肌群与间接同类时，取较大者而非相加（不能把 7 组算成 10.5）。 */
    @Test
    fun sameGroupContributionIsMaxNotSum() {
        val ex = ExerciseEntity(
            id = 21, name = "测试动作", targetMuscle = "背部",
            secondaryMuscles = "背阔肌", equipment = "杠铃"
        )
        val sets = (1..4).map {
            set(it.toLong(), 1, ex.id, 50.0, 8, completedAt = at(today, LocalTime.of(19, it)))
        }
        val counts = PeriodReviewCalculator.muscleSetCounts(sets, listOf(ex))
        assertEquals("4 组不能变成 4.5 或 6", 4.0, counts["背部"]!!, 0.001)
    }
}

