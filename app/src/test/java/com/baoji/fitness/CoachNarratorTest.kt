package com.baoji.fitness

import com.baoji.fitness.domain.algorithm.CoachInput
import com.baoji.fitness.domain.algorithm.CoachNarrator
import com.baoji.fitness.domain.model.MuscleVolumeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M4a 验收：给定固定输入，输出文案做快照断言。
 * 这几条字符串就是「私教价值」的可见形态，改动必须是有意的。
 */
class CoachNarratorTest {

    private fun volume(muscle: String, sets: Int) = when (muscle) {
        "胸部" -> MuscleVolumeStatus("胸部", sets, 10, 16, 22, "x")
        "背部" -> MuscleVolumeStatus("背部", sets, 10, 16, 22, "x")
        "腿部" -> MuscleVolumeStatus("腿部", sets, 12, 18, 24, "x")
        "肩部" -> MuscleVolumeStatus("肩部", sets, 8, 14, 20, "x")
        else -> MuscleVolumeStatus("手臂", sets, 8, 14, 18, "x")
    }

    /** 和路线书里的示例同构：本周 3 练、环比 +8%、背到位、胸欠练、上次睡眠不足。 */
    @Test
    fun fullBriefing_matchesSnapshot() {
        val briefing = CoachNarrator.brief(
            CoachInput(
                label = "本周",
                sessionCount = 3,
                volumeDeltaPct = 8.0,
                totalVolumeKg = 12480.0,
                streakDays = 0,
                remainingPlannedDays = 1,
                muscleVolumes = listOf(
                    volume("胸部", 6),
                    volume("背部", 14),
                    volume("腿部", 14),
                    volume("肩部", 10),
                    volume("手臂", 9)
                ),
                recentNotes = listOf("睡眠不足"),
                latestWeightKg = null
            )
        )

        assertEquals(
            listOf(
                "本周练了 3 次，累计容量 12480 kg，容量环比 +8%。",
                "胸部只有 6 组，低于 MEV(10)；背部 14 组正处在 MAV 区间。",
                "下一次建议安排推日，优先补胸部。",
                "上次备注「睡眠不足」，若今晚仍睡不好，主项重量下调一档。"
            ),
            briefing.lines
        )
    }

    /** 空周期：不能崩，也不能硬凑一句话。 */
    @Test
    fun emptyPeriod_producesHonestPromptInstead() {
        val briefing = CoachNarrator.brief(CoachInput())

        assertEquals(
            listOf(
                "本周还没有训练记录 —— 先完成一次，我才有东西可分析。",
                "从任意一个训练日开始都行，先把数据填上。",
                "训练后在完成弹窗里记一句感受，我下次能给出更准的建议。"
            ),
            briefing.lines
        )
    }

    @Test
    fun noPreviousPeriod_saysThereIsNoComparisonInsteadOfZeroPercent() {
        val briefing = CoachNarrator.brief(
            CoachInput(sessionCount = 1, volumeDeltaPct = null, totalVolumeKg = 3200.0)
        )
        assertTrue(briefing.lines.first().endsWith("上一周期没有可比数据。"))
        assertTrue("绝不能说成 +0%", !briefing.lines.first().contains("+0%"))
    }

    @Test
    fun volumeDrop_isCalledOut() {
        val briefing = CoachNarrator.brief(
            CoachInput(sessionCount = 2, volumeDeltaPct = -22.4, totalVolumeKg = 6000.0)
        )
        assertTrue(briefing.lines.first().contains("容量环比 -22%"))
        assertTrue(briefing.lines.first().contains("少练了一天"))
    }

    @Test
    fun streakIsMentionedOnlyFromThreeDays() {
        val twoDays = CoachNarrator.brief(CoachInput(sessionCount = 1, streakDays = 2))
        assertTrue(twoDays.lines.none { it.contains("连续训练") })

        val threeDays = CoachNarrator.brief(CoachInput(sessionCount = 1, streakDays = 3))
        assertEquals("已经连续训练 3 天，节奏稳。", threeDays.lines.first())
    }

    @Test
    fun allMusclesAtOrAboveMav_suggestsDeload() {
        val briefing = CoachNarrator.brief(
            CoachInput(
                sessionCount = 4,
                muscleVolumes = listOf(
                    volume("胸部", 17),
                    volume("背部", 18),
                    volume("腿部", 19),
                    volume("肩部", 15),
                    volume("手臂", 15)
                )
            )
        )
        assertTrue(briefing.lines.any { it.contains("已经顶到 MRV") })
        assertTrue(briefing.lines.any { it.contains("减量（Deload）") })
    }

    /** 只练了胸背、腿一组没练 —— 这时候该说「补腿」，而不是 Deload。 */
    @Test
    fun zeroSetMuscle_isTreatedAsTheMostUnderTrained() {
        val briefing = CoachNarrator.brief(
            CoachInput(
                sessionCount = 4,
                muscleVolumes = listOf(
                    volume("胸部", 17),
                    volume("背部", 18),
                    volume("腿部", 0),
                    volume("肩部", 0),
                    volume("手臂", 0)
                )
            )
        )
        assertTrue(
            "0 组必须被当成最欠练的部位，不能因为它是 0 就被跳过",
            briefing.lines.any { it.contains("一组都没练") }
        )
        assertTrue(briefing.lines.any { it.contains("下一次建议安排腿日") })
        assertTrue("不该在这时候劝减量", briefing.lines.none { it.contains("Deload") })
    }

    @Test
    fun plannedWeekFinished_tellsYouToRest() {
        val briefing = CoachNarrator.brief(
            CoachInput(
                sessionCount = 3,
                remainingPlannedDays = 0,
                muscleVolumes = listOf(volume("胸部", 5))
            )
        )
        assertTrue(briefing.lines.any { it.contains("本周计划已经排满") })
    }

    @Test
    fun painNote_takesPriorityOverPlanAdvice() {
        val briefing = CoachNarrator.brief(
            CoachInput(
                sessionCount = 2,
                muscleVolumes = listOf(volume("肩部", 3)),
                recentNotes = listOf("右肩有点疼")
            )
        )
        assertTrue(briefing.lines.last().contains("降重量、缩小行程"))
    }

    @Test
    fun latestWeightIsAppendedWhenPresent() {
        val briefing = CoachNarrator.brief(
            CoachInput(sessionCount = 1, latestWeightKg = 75.0)
        )
        assertEquals("最近体重 75 kg，配合容量趋势一起看更有意义。", briefing.lines.last())
    }
}
