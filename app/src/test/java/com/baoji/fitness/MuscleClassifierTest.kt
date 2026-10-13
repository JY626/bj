package com.baoji.fitness

import com.baoji.fitness.domain.algorithm.MuscleClassifier
import com.baoji.fitness.domain.algorithm.PeriodReviewCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义动作的肌群归类。
 *
 * 最关键的一条约束：**归类结果必须落在 6 个追踪大类里**，
 * 否则该动作的组数会被 `muscleSetCounts` 静默丢弃（用户以为记了，实际没算）。
 */
class MuscleClassifierTest {

    // ---- 模型返回的解析 --------------------------------------------------------

    @Test
    fun parsesCleanJson() {
        val c = MuscleClassifier.parse(
            """{"primary":"背部","secondary":["肱二头肌","三角肌后束"],"equipment":"绳索","trackWeight":true}"""
        )!!
        assertEquals("背部", c.primary)
        assertEquals(listOf("肱二头肌", "三角肌后束"), c.secondary)
        assertEquals("绳索", c.equipment)
        assertEquals(true, c.trackWeight)
    }

    /** 模型常给 markdown 围栏，必须能剥掉。 */
    @Test
    fun stripsMarkdownFence() {
        val c = MuscleClassifier.parse(
            "```json\n{\"primary\":\"胸部\",\"secondary\":[],\"equipment\":\"杠铃\",\"trackWeight\":true}\n```"
        )!!
        assertEquals("胸部", c.primary)
    }

    /** 模型给细分名（肱三头肌）→ 归一到「手臂」。 */
    @Test
    fun normalizesFineGrainedMuscleToTrackedGroup() {
        assertEquals("手臂", MuscleClassifier.normalizePrimary("肱三头肌"))
        assertEquals("肩部", MuscleClassifier.normalizePrimary("三角肌前束"))
        assertEquals("腿部", MuscleClassifier.normalizePrimary("股四头肌"))
        assertEquals("核心", MuscleClassifier.normalizePrimary("腹直肌"))
        assertEquals("背部", MuscleClassifier.normalizePrimary("背部"))
    }

    /** 模型给出不可识别的肌群（如「全身」）→ 归一失败，调用方会兜底。 */
    @Test
    fun unrecognizedMuscleNameFailsNormalization() {
        assertNull(MuscleClassifier.normalizePrimary("全身"))
        assertNull(MuscleClassifier.normalizePrimary(""))
    }

    /** primary 无法归一 → parse 整体返回 null（宁可兜底也不写脏数据）。 */
    @Test
    fun parseReturnsNullWhenPrimaryUnusable() {
        assertNull(MuscleClassifier.parse("""{"primary":"全身","secondary":[],"equipment":"杠铃"}"""))
        assertNull(MuscleClassifier.parse("完全不是 JSON"))
        assertNull(MuscleClassifier.parse(""))
    }

    @Test
    fun secondaryIsCappedAtThree() {
        val c = MuscleClassifier.parse(
            """{"primary":"背部","secondary":["a","b","c","d","e"],"equipment":"杠铃"}"""
        )!!
        assertEquals(3, c.secondary.size)
    }

    // ---- 离线兜底 --------------------------------------------------------------

    /**
     * 兜底输出**必须**永远落在 6 大类内 —— 这是最核心的不变式。
     * 遍历一批真实动作名验证。
     */
    @Test
    fun offlineFallbackAlwaysLandsInTrackedGroups() {
        val names = listOf(
            "坐姿划船", "保加利亚分腿蹲", "上斜卧推", "哑铃侧平举", "绳索下压",
            "悬垂举腿", "硬拉", "面拉", "史密斯推肩", "平板支撑",
            "引体向上", "臀推", "提踵", "二头弯举", "完全不知所云的动作"
        )
        names.forEach { n ->
            val c = MuscleClassifier.classifyOffline(n)
            assertTrue(
                "「$n」归类为 ${c.primary}，必须在追踪大类内",
                c.primary in PeriodReviewCalculator.TRACKED_MUSCLES
            )
        }
    }

    @Test
    fun offlineFallbackClassifiesCommonExercises() {
        assertEquals("背部", MuscleClassifier.classifyOffline("坐姿划船").primary)
        assertEquals("胸部", MuscleClassifier.classifyOffline("上斜卧推").primary)
        assertEquals("腿部", MuscleClassifier.classifyOffline("保加利亚分腿蹲").primary)
        assertEquals("肩部", MuscleClassifier.classifyOffline("哑铃侧平举").primary)
        assertEquals("手臂", MuscleClassifier.classifyOffline("绳索下压").primary)
        assertEquals("核心", MuscleClassifier.classifyOffline("悬垂举腿").primary)
    }

    /** 自重动作不记重量 —— 与既有「悬垂举腿」种子数据一致。 */
    @Test
    fun selfWeightExercisesDoNotTrackWeight() {
        assertEquals(false, MuscleClassifier.classifyOffline("悬垂举腿").trackWeight)
        assertEquals(false, MuscleClassifier.classifyOffline("平板支撑").trackWeight)
        assertEquals(true, MuscleClassifier.classifyOffline("坐姿划船").trackWeight)
    }

    /** 离线兜底也要给间接肌群，让「手臂」这类小部位能吃到间接组数。 */
    @Test
    fun offlineFallbackProvidesSecondaryMuscles() {
        val row = MuscleClassifier.classifyOffline("坐姿划船")
        assertTrue(row.secondary.any { it.contains("二头") })
    }

    /** 提示词必须写死 6 个大类，否则模型会自由发挥。 */
    @Test
    fun systemPromptEnumeratesTrackedMuscles() {
        PeriodReviewCalculator.TRACKED_MUSCLES.forEach { m ->
            assertTrue("提示词必须包含「$m」", MuscleClassifier.SYSTEM_PROMPT.contains(m))
        }
    }
}