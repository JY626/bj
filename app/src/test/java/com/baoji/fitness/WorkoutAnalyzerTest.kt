package com.baoji.fitness

import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.domain.algorithm.CoachReport
import com.baoji.fitness.domain.algorithm.CoachingLocalReport
import com.baoji.fitness.domain.algorithm.WorkoutAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分析器与报告契约的验收：**数字算对**（停滞/推拉/RPE）与**分区切对**。
 * 数字错 → 模型会把错的东西说得头头是道，所以这层比文案更值得钉。
 */
class WorkoutAnalyzerTest {

    private val bench = ExerciseEntity(id = 1, name = "卧推", targetMuscle = "胸部", secondaryMuscles = "肱三头肌, 三角肌前束", equipment = "杠铃")
    private val row = ExerciseEntity(id = 2, name = "划船", targetMuscle = "背部", secondaryMuscles = "肱二头肌", equipment = "杠铃")
    private val exercises = listOf(bench, row)

    private fun session(id: Long, title: String, at: Long) =
        WorkoutSessionEntity(id = id, title = title, startTime = at, status = "COMPLETED")

    private fun set(
        id: Long, sessionId: Long, exerciseId: Long, weight: Double, reps: Int,
        order: Int = 1, rpe: Double? = null, at: Long = 1_700_000_000_000L
    ) = WorkoutSetEntity(
        id = id, sessionId = sessionId, exerciseId = exerciseId, setOrder = order,
        setType = "NORMAL", weightKg = weight, reps = reps, rpe = rpe,
        isCompleted = true, completedAt = at
    )

    /** 4 次训练同一个动作、第 2~4 次重量次数完全一样 → 停滞必须被抓出来。
     *  （需要至少 1 次更早的历史作基准，否则无从判定“没进步”。） */
    @Test
    fun stallIsDetectedWhenE1rmFlatForThreeSessions() {
        val sessions = listOf(
            session(1, "DAY 1 · 推日", 1_000), session(2, "DAY 1 · 推日", 2_000),
            session(3, "DAY 1 · 推日", 3_000), session(4, "DAY 1 · 推日", 4_000)
        )
        val sets = listOf(
            set(1, 1, bench.id, 80.0, 8, at = 1_000),
            set(2, 2, bench.id, 80.0, 8, at = 2_000),
            set(3, 3, bench.id, 80.0, 8, at = 3_000),
            set(4, 4, bench.id, 80.0, 8, at = 4_000)
        )
        val stalls = WorkoutAnalyzer.detectStalls(sets, exercises)
        assertEquals(1, stalls.size)
        assertEquals("卧推", stalls.first().exercise)
    }

    /** 每次都在涨 → 不能误报停滞。 */
    @Test
    fun noStallWhenProgressing() {
        val sessions = listOf(
            session(1, "DAY 1 · 推日", 1_000), session(2, "DAY 1 · 推日", 2_000),
            session(3, "DAY 1 · 推日", 3_000), session(4, "DAY 1 · 推日", 4_000)
        )
        val sets = listOf(
            set(1, 1, bench.id, 70.0, 8, at = 1_000),
            set(2, 2, bench.id, 75.0, 8, at = 2_000),
            set(3, 3, bench.id, 80.0, 8, at = 3_000),
            set(4, 4, bench.id, 85.0, 8, at = 4_000)
        )
        assertTrue(WorkoutAnalyzer.detectStalls(sets, exercises).isEmpty())
    }

    /** 同重量 RPE 从 7 抬到 9 → 疲劳漂移。 */
    @Test
    fun rpeDriftIsDetected() {
        val sets = listOf(
            set(1, 1, bench.id, 80.0, 8, rpe = 7.0, at = 1_000),
            set(2, 2, bench.id, 80.0, 8, rpe = 7.0, at = 2_000),
            set(3, 3, bench.id, 80.0, 8, rpe = 9.5, at = 3_000),
            set(4, 4, bench.id, 80.0, 8, rpe = 9.5, at = 4_000)
        )
        val drift = WorkoutAnalyzer.detectRpeDrift(sets, exercises)
        assertEquals(1, drift.size)
        assertTrue(drift.first().recentRpe > drift.first().earlierRpe)
    }

    /** 5 组 100/100/60 → 末组掉 40%，超过 20% 阈值。 */
    @Test
    fun setFadeIsDetected() {
        val sets = (listOf(100, 90, 60).mapIndexed { i, reps ->
            set((i + 1).toLong(), 1, bench.id, 80.0, reps, order = i + 1, at = 1_000)
        })
        val fades = WorkoutAnalyzer.detectSetFade(sets, exercises)
        assertEquals(1, fades.size)
        assertEquals(40, fades.first().fadePct.toInt())
    }

    /** 推拉失衡：推 3 组在推日、拉 1 组在拉日 → ratio ≥ 1.5，本地报告要点名。 */
    @Test
    fun pushPullImbalanceIsReportedLocally() {
        val facts = WorkoutAnalyzer.analyze(
            sessions = listOf(
                session(1, "DAY 1 · 推日", System.currentTimeMillis()),
                session(2, "DAY 2 · 拉日", System.currentTimeMillis())
            ),
            sets = listOf(
                set(1, 1, bench.id, 80.0, 8), set(2, 1, bench.id, 80.0, 8), set(3, 1, bench.id, 80.0, 8),
                set(4, 2, row.id, 60.0, 10)
            ),
            exercises = exercises
        )
        assertEquals(3.0, facts.pushPullSets["推"]!!, 0.001)
        assertEquals(1.0, facts.pushPullSets["拉"]!!, 0.001)
        assertTrue(CoachingLocalReport.build(facts).contains("推拉失衡"))
    }

    /** 报告必须切成 4 张卡，且标题与契约一致。 */
    @Test
    fun markdownSplitsIntoFourFixedSections() {
        val md = """
            ## 总体判断
            练得不错。
            ## 做对的
            背部到位。
            ## 要改的
            卧推停滞。
            ## 下周计划
            降到 72.5kg。
        """.trimIndent()
        val sections = CoachReport.splitSections(md)
        assertEquals(listOf("总体判断", "做对的", "要改的", "下周计划"), sections.map { it.title })
        assertEquals("卧推停滞。", sections[2].body)
    }

    /** 模型没按标题输出时，整段不能丢 —— 回落成一张卡。 */
    @Test
    fun markdownWithoutHeadingsFallsBackToSingleCard() {
        val sections = CoachReport.splitSections("就是一段没有标题的分析。")
        assertEquals(1, sections.size)
        assertEquals("总体判断", sections.first().title)
        assertTrue(sections.first().body.contains("没有标题"))
    }

    /** 本地降级报告本身必须能切出 4 个分区（保证 UI 两边观感一致）。 */
    @Test
    fun localReportAlwaysProducesAllFourSections() {
        val facts = WorkoutAnalyzer.analyze(
            sessions = listOf(session(1, "DAY 1 · 推日", System.currentTimeMillis())),
            sets = listOf(set(1, 1, bench.id, 80.0, 8)),
            exercises = exercises
        )
        val sections = CoachReport.splitSections(CoachingLocalReport.build(facts))
        // 本周报告现在含「进步对比」（新增分区），逐项对齐 sectionsFor
        assertEquals(CoachReport.sectionsFor(CoachReport.Scope.THIS_WEEK), sections.map { it.title })
    }

    // ---- 本轮新增：三档范围 + 身体数据 ------------------------------------

    /** 三个范围的标题各不同，切卡要用对应范围，不能混。 */
    @Test
    fun eachScopeHasItsOwnSectionTitles() {
        assertTrue(CoachReport.sectionsFor(CoachReport.Scope.TODAY).contains("今天做了什么"))
        assertTrue(CoachReport.sectionsFor(CoachReport.Scope.LAST_4_WEEKS).contains("总体趋势"))
        // 今日与本周都必须有「进步对比」分区（本轮需求）
        assertTrue(
            "今日要有进步对比",
            CoachReport.sectionsFor(CoachReport.Scope.TODAY).contains("进步对比")
        )
        assertTrue(
            "本周要有进步对比",
            CoachReport.sectionsFor(CoachReport.Scope.THIS_WEEK).contains("进步对比")
        )
    }

    /** 今日报告：模型输出 3 个小标题，必须按 TODAY 的标题切。 */
    @Test
    fun todayReportSplitsOnItsOwnHeadings() {
        val md = """
            ## 今天做了什么
            卧推 80kg×8 三组。
            ## 练得怎样
            完成度不错。
            ## 下次注意
            保持重量。
        """.trimIndent()
        val sections = CoachReport.splitSections(md, CoachReport.Scope.TODAY)
        assertEquals(listOf("今天做了什么", "练得怎样", "下次注意"), sections.map { it.title })
    }

    /** 身体数据：BMI 要算对，缺身高就不能瞎算。 */
    @Test
    fun bmiIsComputedAndMissingHeightGivesNull() {
        val p = com.baoji.fitness.domain.algorithm.BodyProfile(weightKg = 75.0, heightCm = 175.0)
        assertEquals(24.5, p.bmi!!, 0.01)
        assertEquals(null, com.baoji.fitness.domain.algorithm.BodyProfile(weightKg = 75.0).bmi)
        assertEquals(null, com.baoji.fitness.domain.algorithm.BodyProfile(heightCm = 175.0).bmi)
    }

    /** 身高/体重/体脂必须真的进 payload，否则“结合身体数据分析”是空话。 */
    @Test
    fun factsJsonCarriesBodyAndToday() {
        val facts = WorkoutAnalyzer.analyze(
            sessions = listOf(session(1, "DAY 1 · 推日", System.currentTimeMillis())),
            sets = listOf(set(1, 1, bench.id, 80.0, 8)),
            exercises = exercises
        ).copy(
            body = com.baoji.fitness.domain.algorithm.BodyProfile(
                weightKg = 75.0, heightCm = 175.0, bodyFatPct = 18.0, weightDeltaKg = -0.5
            )
        )
        val json = CoachReport.factsToJson(facts)
        assertTrue(json.contains("\"heightCm\":175"))
        assertTrue(json.contains("\"bmi\":24.5"))
        assertTrue(json.contains("\"bodyFatPct\":18"))
        assertTrue(json.contains("\"weightDeltaKg\":-0.5"))
    }

    /** 今日训练要能从历史里挑出今天那场，速度/组数/动作都要在。 */
    @Test
    fun todayWorkoutIsExtracted() {
        val today = java.time.LocalDate.now()
        val start = today.atTime(19, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "DAY 1 · 推日", startTime = start, endTime = start + 3_600_000, status = "COMPLETED", notes = "状态好")
        )
        val sets = listOf(
            set(1, 1, bench.id, 80.0, 8, at = start + 1000),
            set(2, 1, bench.id, 80.0, 8, at = start + 2000)
        )
        val tw = WorkoutAnalyzer.buildToday(sessions, sets, exercises, today)!!
        assertEquals("DAY 1 · 推日", tw.title)
        assertEquals(2, tw.completedSets)
        assertEquals(1280.0, tw.volumeKg, 0.01)
        assertEquals(60, tw.durationMinutes)
        assertEquals("状态好", tw.notes)
        assertEquals(1, tw.exercises.size)
        assertEquals("卧推", tw.exercises.first().name)
    }

    /** 没练过任何一组 → 今日报告返回 null，不能编一场不存在的训练。 */
    @Test
    fun todayWorkoutIsNullWithoutCompletedSets() {
        val sessions = listOf(session(1, "DAY 1 · 推日", System.currentTimeMillis()))
        assertEquals(null, WorkoutAnalyzer.buildToday(sessions, emptyList(), exercises))
    }

    // ---- 回归：会话没点「完成」也必须算一次训练 ---------------------------
    // 实际发生过的 bug：analyze 按 status=="COMPLETED" 数会话，但用户从不点完成，
    // 会话一直是 ACTIVE，于是本周练了 3 次却分析成 1 次（而日历显示 3 次）。

    @Test
    fun activeSessionsStillCountAsTraining() {
        val today = java.time.LocalDate.now()
        val zone = java.time.ZoneId.systemDefault()
        // 本周一/二/四各练一场，与会话status无关（全为 ACTIVE）
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        fun at(daysAfterMonday: Long) =
            monday.plusDays(daysAfterMonday).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "DAY 1 · 推日", startTime = at(0), status = "ACTIVE"),
            WorkoutSessionEntity(id = 2, title = "DAY 2 · 拉日", startTime = at(1), status = "ACTIVE"),
            WorkoutSessionEntity(id = 3, title = "DAY 3 · 腿 + 外形补强", startTime = at(3), status = "ACTIVE")
        )
        val sets = listOf(
            set(1, 1, bench.id, 80.0, 8, at = at(0) + 1000),
            set(2, 2, row.id, 60.0, 10, at = at(1) + 1000),
            set(3, 3, bench.id, 60.0, 10, at = at(3) + 1000)
        )

        val facts = WorkoutAnalyzer.analyze(sessions, sets, exercises, weeks = 1, today = today)
        assertEquals("ACTIVE 会话同样算训练，本周应是 3 次", 3, facts.sessionCount)
    }

    /** 空壳会话（没有任何完成组）不能算训练。 */
    @Test
    fun sessionWithoutCompletedSetsIsNotCounted() {
        val today = java.time.LocalDate.now()
        val start = today.atTime(19, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "DAY 1 · 推日", startTime = start, status = "ACTIVE"),
            WorkoutSessionEntity(id = 2, title = "空壳", startTime = start, status = "ACTIVE")
        )
        val sets = listOf(set(1, 1, bench.id, 80.0, 8, at = start + 1000))
        val facts = WorkoutAnalyzer.analyze(sessions, sets, exercises, weeks = 1, today = today)
        assertEquals(1, facts.sessionCount)
    }

    /** 本周窗口必须真的只算本周：上周的不算进来（否则“本周”会说成 4 周的量）。 */
    @Test
    fun weeklyWindowExcludesLastWeek() {
        val today = java.time.LocalDate.now()
        val zone = java.time.ZoneId.systemDefault()
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        fun at(date: java.time.LocalDate) =
            date.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val thisWeek = monday.plusDays(1)
        val lastWeek = monday.minusDays(3)
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "本周", startTime = at(thisWeek), status = "COMPLETED"),
            WorkoutSessionEntity(id = 2, title = "上周", startTime = at(lastWeek), status = "COMPLETED")
        )
        val sets = listOf(
            set(1, 1, bench.id, 80.0, 8, at = at(thisWeek)),
            set(2, 2, bench.id, 80.0, 8, at = at(lastWeek))
        )
        val weekly = WorkoutAnalyzer.analyze(sessions, sets, exercises, weeks = 1, today = today)
        assertEquals("本周窗口不能把上周算进来", 1, weekly.sessionCount)

        val monthlyish = WorkoutAnalyzer.analyze(sessions, sets, exercises, weeks = 4, today = today)
        assertEquals("近 4 周窗口应该包含上周", 2, monthlyish.sessionCount)
    }

    /**
     * 回归：今天没练时，**绝不能拿上一次训练冒充“今天”**（这是实际发生过的 bug）。
     * 以前 buildToday 会回退到最近一场，休息日点今日分析会分析错日子。
     */
    @Test
    fun todayWorkoutIsNullWhenOnlyPastSessionsExist() {
        val zone = java.time.ZoneId.systemDefault()
        val yesterday = java.time.LocalDate.now().minusDays(1)
        val start = yesterday.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "DAY 1 · 推日", startTime = start, endTime = start + 3_600_000, status = "COMPLETED")
        )
        val sets = listOf(
            set(1, 1, bench.id, 80.0, 8, at = start + 1000),
            set(2, 1, bench.id, 80.0, 8, at = start + 2000)
        )
        assertEquals(
            "昨天练的不算今天，今日分析必须为 null",
            null,
            WorkoutAnalyzer.buildToday(sessions, sets, exercises, java.time.LocalDate.now())
        )
    }

    /** 同一天练了两场 → 取时间靠后的那一场（最新的）。 */
    @Test
    fun todayWorkoutTakesLatestSessionOfTheDay() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val morning = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val evening = today.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "上午", startTime = morning, endTime = morning + 3_600_000, status = "COMPLETED"),
            WorkoutSessionEntity(id = 2, title = "晚间", startTime = evening, endTime = evening + 3_600_000, status = "COMPLETED")
        )
        val sets = listOf(
            set(1, 1, bench.id, 70.0, 8, at = morning + 1000),
            set(2, 2, bench.id, 85.0, 8, at = evening + 1000)
        )
        val tw = WorkoutAnalyzer.buildToday(sessions, sets, exercises, today)!!
        assertEquals("晚间", tw.title)
        assertEquals(680.0, tw.volumeKg, 0.01)
    }

    // ---- 回归：今日报告不能用本周的数字 ---------------------------------------
    // 真实 bug：选「今日」时 factsOf 传 weeks=1（= 整个本周），模型看到
    // 「本周肩 17 组」，而用户今天只练了 7 组 —— 数字与标题对不上。

    private val shoulder = ExerciseEntity(
        id = 90, name = "史密斯推肩", targetMuscle = "肩部",
        secondaryMuscles = "三角肌前束, 三头肌", equipment = "史密斯"
    )

    /** 单日模式：muscles/totalVolume 只统计今天，不掺本周其他天。 */
    @Test
    fun singleDayScopeOnlyCountsToday() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        fun at(d: java.time.LocalDate, h: Int) =
            d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

        // 周一练肩 10 组（本周但不是今天）
        val mondayTime = at(monday, 19)
        // 今天练肩 7 组
        val todayTime = at(today, 19)
        val sessionDay = if (monday == today) today else monday

        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "DAY 1 · 推日", startTime = mondayTime, status = "COMPLETED"),
            WorkoutSessionEntity(id = 2, title = "DAY 1 · 推日", startTime = todayTime, status = "COMPLETED")
        )
        val sets = (1..10).map {
            set(it.toLong(), 1, shoulder.id, 20.0, 12, at = mondayTime + it)
        } + (1..7).map {
            set((100 + it).toLong(), 2, shoulder.id, 25.0, 10, at = todayTime + it)
        }

        val todayFacts = WorkoutAnalyzer.analyze(
            sessions, sets, listOf(shoulder), weeks = 1, singleDay = true, today = today, zone = zone
        )
        val shoulderToday = todayFacts.muscles.first { it.muscleGroup == "肩部" }
        assertEquals("今日只算今天的 7 组", 7.0, shoulderToday.completedSets, 0.001)
        assertEquals("weeksCovered=0 标记单日模式", 0, todayFacts.weeksCovered)
        assertEquals("今日容量只含今天那场", 7 * 25.0 * 10, todayFacts.totalVolumeKg, 0.01)
        assertTrue("单日模式不带周趋势线", todayFacts.weeklyVolume.isEmpty())
        assertEquals("单日模式不跟昨天比容量", null, todayFacts.volumeDeltaPct)

        // 同一个数据，本周模式应看到 17 组（证明两者确实不同）
        val weekFacts = WorkoutAnalyzer.analyze(
            sessions, sets, listOf(shoulder), weeks = 1, singleDay = false, today = today, zone = zone
        )
        if (sessionDay != today) {
            val shoulderWeek = weekFacts.muscles.first { it.muscleGroup == "肩部" }
            assertEquals("本周模式含周一+今天 = 17 组", 17.0, shoulderWeek.completedSets, 0.001)
        }
    }

    /** 今天没练 → 单日模式全 0，绝不能回退成本周数据。 */
    @Test
    fun singleDayWithNoTrainingTodayIsEmptyNotWeekly() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val mondayTime = monday.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val sets = (1..10).map { set(it.toLong(), 1, shoulder.id, 20.0, 12, at = mondayTime + it) }
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "DAY 1 · 推日", startTime = mondayTime, status = "COMPLETED")
        )

        val facts = WorkoutAnalyzer.analyze(
            sessions, sets, listOf(shoulder), weeks = 1, singleDay = true, today = today, zone = zone
        )
        val shoulderToday = facts.muscles.first { it.muscleGroup == "肩部" }
        if (monday != today) {
            assertEquals("今天没练就该是 0 组，不能拿周一的数据充数", 0.0, shoulderToday.completedSets, 0.001)
            assertEquals(0, facts.sessionCount)
        }
    }

    /** factsToJson 必须声明窗口，否则模型会把本日数字说成本周。 */
    @Test
    fun jsonDeclaresTheWindow() {
        val facts = WorkoutAnalyzer.analyze(emptyList(), emptyList(), emptyList(), singleDay = true)
        val json = com.baoji.fitness.domain.algorithm.CoachReport.factsToJson(facts)
        assertTrue("单日模式要写明仅今天", json.contains("\"window\":\"今日（仅今天这一天）\""))

        val weekly = WorkoutAnalyzer.analyze(emptyList(), emptyList(), emptyList(), weeks = 4)
        val weeklyJson = com.baoji.fitness.domain.algorithm.CoachReport.factsToJson(weekly)
        assertTrue(weeklyJson.contains("\"window\":\"近 4 周\""))
    }

    /** 本地降级报告的措辞也要跟着窗口走，不能说「近 0 周」。 */
    @Test
    fun localReportUsesTodayWordingInSingleDayMode() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val t = today.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val sessions = listOf(WorkoutSessionEntity(id = 1, title = "DAY 1 · 推日", startTime = t, status = "COMPLETED"))
        val sets = listOf(set(1, 1, shoulder.id, 20.0, 12, at = t + 1))
        val facts = WorkoutAnalyzer.analyze(
            sessions, sets, listOf(shoulder), singleDay = true, today = today, zone = zone
        )
        val text = CoachingLocalReport.build(facts, null, CoachReport.Scope.TODAY)
        assertTrue("不能说成近 0 周：$text", !text.contains("近 0 周"))
        assertTrue("应说今天：$text", text.contains("今天"))
    }

    // ---- 进步对比：比“上次练它”，不是比固定周期 -------------------------------

    private val zone2 = java.time.ZoneId.systemDefault()
    private fun at2(d: java.time.LocalDate, h: Int) =
        d.atTime(h, 0).atZone(zone2).toInstant().toEpochMilli()

    /** 卧推进步：80kg×8 → 82.5kg×7，e1RM 应上升。 */
    @Test
    fun exerciseProgressDetectsImprovement() {
        val today = java.time.LocalDate.now()
        val lastWeek = today.minusDays(7)
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "推日", startTime = at2(lastWeek, 19), status = "COMPLETED"),
            WorkoutSessionEntity(id = 2, title = "推日", startTime = at2(today, 19), status = "COMPLETED")
        )
        val sets = listOf(
            set(1, 1, bench.id, 80.0, 8, at = at2(lastWeek, 19) + 1000),
            set(2, 2, bench.id, 82.5, 7, at = at2(today, 19) + 1000)
        )
        val progress = WorkoutAnalyzer.detectExerciseProgress(
            sessions, sets, exercises, windowStart = today, zone = zone2
        )
        assertEquals(1, progress.size)
        val p = progress.first()
        assertEquals("卧推", p.exercise)
        assertEquals("上次应是上周那场", lastWeek, p.prevDate)
        assertTrue("82.5×7 比 80×8 的 e1RM 高", p.improved)
        assertTrue("e1RM 变化百分比应为正", p.e1rmDeltaPct > 0)
    }

    /** 重量不变但次数变多 → 也算进步（不能只看重量）。 */
    @Test
    fun moreRepsAtSameWeightIsProgress() {
        val today = java.time.LocalDate.now()
        val lastWeek = today.minusDays(7)
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "推日", startTime = at2(lastWeek, 19), status = "COMPLETED"),
            WorkoutSessionEntity(id = 2, title = "推日", startTime = at2(today, 19), status = "COMPLETED")
        )
        val sets = listOf(
            set(1, 1, bench.id, 80.0, 8, at = at2(lastWeek, 19) + 1000),
            set(2, 2, bench.id, 80.0, 10, at = at2(today, 19) + 1000)
        )
        val p = WorkoutAnalyzer.detectExerciseProgress(
            sessions, sets, exercises, windowStart = today, zone = zone2
        ).first()
        assertTrue("同样重量多做 2 次是进步", p.improved)
    }

    /** 退步要能识别（e1RM 下降）。 */
    @Test
    fun exerciseProgressDetectsRegression() {
        val today = java.time.LocalDate.now()
        val lastWeek = today.minusDays(7)
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "推日", startTime = at2(lastWeek, 19), status = "COMPLETED"),
            WorkoutSessionEntity(id = 2, title = "推日", startTime = at2(today, 19), status = "COMPLETED")
        )
        val sets = listOf(
            set(1, 1, bench.id, 90.0, 8, at = at2(lastWeek, 19) + 1000),
            set(2, 2, bench.id, 75.0, 6, at = at2(today, 19) + 1000)
        )
        val p = WorkoutAnalyzer.detectExerciseProgress(
            sessions, sets, exercises, windowStart = today, zone = zone2
        ).first()
        assertTrue("降重量又降次数 = 退步", p.e1rmDelta < 0)
        assertTrue(!p.improved)
    }

    /** 首次训练的动作不能编一个“上次”出来。 */
    @Test
    fun firstTimeExerciseHasNoProgressEntry() {
        val today = java.time.LocalDate.now()
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "推日", startTime = at2(today, 19), status = "COMPLETED")
        )
        val sets = listOf(set(1, 1, bench.id, 80.0, 8, at = at2(today, 19) + 1000))
        val progress = WorkoutAnalyzer.detectExerciseProgress(
            sessions, sets, exercises, windowStart = today, zone = zone2
        )
        assertTrue("没有上次记录就不该列进去", progress.isEmpty())
    }

    /** “上次”是最近的、真的练过的那个动作，不是固定上一周期。 */
    @Test
    fun previousIsMostRecentTimeThatExerciseWasTrained() {
        val today = java.time.LocalDate.now()
        val sessions = listOf(
            // 很久以前练过一次腿（不该被选为卧推的“上次”）
            WorkoutSessionEntity(id = 1, title = "腿日", startTime = at2(today.minusDays(20), 19), status = "COMPLETED"),
            // 3 天前练过卧推 → 这才是“上次”
            WorkoutSessionEntity(id = 2, title = "推日", startTime = at2(today.minusDays(3), 19), status = "COMPLETED"),
            WorkoutSessionEntity(id = 3, title = "推日", startTime = at2(today, 19), status = "COMPLETED")
        )
        val sets = listOf(
            set(1, 1, row.id, 60.0, 10, at = at2(today.minusDays(20), 19) + 1000),
            set(2, 2, bench.id, 80.0, 8, at = at2(today.minusDays(3), 19) + 1000),
            set(3, 3, bench.id, 82.5, 8, at = at2(today, 19) + 1000)
        )
        val benchProgress = WorkoutAnalyzer.detectExerciseProgress(
            sessions, sets, exercises, windowStart = today, zone = zone2
        ).first { it.exercise == "卧推" }
        assertEquals("应选 3 天前那次（最近一次练卧推）", today.minusDays(3), benchProgress.prevDate)
    }

    /** 肌群组数对比：肩 7 组 vs 上次 10 组 → -3 组。 */
    @Test
    fun muscleProgressComparesSetCounts() {
        val today = java.time.LocalDate.now()
        val lastTime = today.minusDays(7)
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "推日", startTime = at2(lastTime, 19), status = "COMPLETED"),
            WorkoutSessionEntity(id = 2, title = "推日", startTime = at2(today, 19), status = "COMPLETED")
        )
        val sets = (1..10).map { set(it.toLong(), 1, shoulder.id, 20.0, 12, at = at2(lastTime, 19) + it) } +
            (1..7).map { set((100 + it).toLong(), 2, shoulder.id, 25.0, 10, at = at2(today, 19) + it) }

        val mp = WorkoutAnalyzer.detectMuscleProgress(
            sessions, sets, listOf(shoulder), windowStart = today, zone = zone2
        ).first { it.muscle == "肩部" }
        assertEquals(10.0, mp.prevSets, 0.001)
        assertEquals(7.0, mp.curSets, 0.001)
        assertEquals(-3.0, mp.deltaSets, 0.001)
        assertEquals(lastTime, mp.prevDate)
    }

    /** 进步对比必须进 JSON，否则模型看不到。 */
    @Test
    fun progressIsSerializedIntoFactsJson() {
        val today = java.time.LocalDate.now()
        val sessions = listOf(
            WorkoutSessionEntity(id = 1, title = "推日", startTime = at2(today.minusDays(7), 19), status = "COMPLETED"),
            WorkoutSessionEntity(id = 2, title = "推日", startTime = at2(today, 19), status = "COMPLETED")
        )
        val sets = listOf(
            set(1, 1, bench.id, 80.0, 8, at = at2(today.minusDays(7), 19) + 1000),
            set(2, 2, bench.id, 82.5, 8, at = at2(today, 19) + 1000)
        )
        val facts = WorkoutAnalyzer.analyze(
            sessions, sets, exercises, singleDay = true, today = today, zone = zone2
        )
        val json = CoachReport.factsToJson(facts)
        assertTrue(json.contains("\"exerciseProgress\""))
        assertTrue(json.contains("\"muscleProgress\""))
        assertTrue("要带 improved 标志", json.contains("\"improved\""))
    }
}

