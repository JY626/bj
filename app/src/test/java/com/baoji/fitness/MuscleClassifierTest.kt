package com.baoji.fitness

import com.baoji.fitness.domain.algorithm.MuscleClassifier
import com.baoji.fitness.domain.algorithm.PeriodReviewCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义动作的肌群归类测试。
 *
 * 最关键的一条约束：**归类结果必须落在 6 个追踪大类里**，
 * 否则该动作的组数会被 `muscleSetCounts` 静默丢弃（用户以为记了，实际没算）。
 */
class MuscleClassifierTest {

    @Test
    fun normalizesFineGrainedMuscleToTrackedGroup() {
        assertEquals("手臂", MuscleClassifier.normalizePrimary("肱三头肌"))
        assertEquals("肩部", MuscleClassifier.normalizePrimary("三角肌前束"))
        assertEquals("腿部", MuscleClassifier.normalizePrimary("股四头肌"))
        assertEquals("核心", MuscleClassifier.normalizePrimary("腹直肌"))
        assertEquals("背部", MuscleClassifier.normalizePrimary("背部"))
    }

    @Test
    fun unrecognizedMuscleNameFailsNormalization() {
        assertNull(MuscleClassifier.normalizePrimary("全身"))
        assertNull(MuscleClassifier.normalizePrimary(""))
    }

    /**
     * 规则输出**必须**永远落在 6 大类内 —— 这是最核心的不变式。
     * 遍历一批真实动作名验证。
     */
    @Test
    fun classificationAlwaysLandsInTrackedGroups() {
        val names = listOf(
            "坐姿划船", "保加利亚分腿蹲", "上斜卧推", "哑铃侧平举", "绳索下压",
            "悬垂举腿", "硬拉", "面拉", "史密斯推肩", "平板支撑",
            "引体向上", "臀推", "提踵", "二头弯举", "完全不知所云的动作"
        )
        names.forEach { n ->
            val c = MuscleClassifier.classify(n)
            assertTrue(
                "「$n」归类为 ${c.primary}，必须在追踪大类内",
                c.primary in PeriodReviewCalculator.TRACKED_MUSCLES
            )
        }
    }

    @Test
    fun classifiesCommonExercises() {
        assertEquals("背部", MuscleClassifier.classify("坐姿划船").primary)
        assertEquals("胸部", MuscleClassifier.classify("上斜卧推").primary)
        assertEquals("腿部", MuscleClassifier.classify("保加利亚分腿蹲").primary)
        assertEquals("肩部", MuscleClassifier.classify("哑铃侧平举").primary)
        assertEquals("手臂", MuscleClassifier.classify("绳索下压").primary)
        assertEquals("核心", MuscleClassifier.classify("悬垂举腿").primary)
    }

    /** 自重动作不记重量 —— 与既有「悬垂举腿」种子数据一致。 */
    @Test
    fun selfWeightExercisesDoNotTrackWeight() {
        assertEquals(false, MuscleClassifier.classify("悬垂举腿").trackWeight)
        assertEquals(false, MuscleClassifier.classify("平板支撑").trackWeight)
        assertEquals(true, MuscleClassifier.classify("坐姿划船").trackWeight)
    }

    /** 也要给出间接肌群，让「手臂」这类小部位能吃到间接组数。 */
    @Test
    fun providesSecondaryMuscles() {
        val row = MuscleClassifier.classify("坐姿划船")
        assertTrue(row.secondary.any { it.contains("二头") })
    }
}