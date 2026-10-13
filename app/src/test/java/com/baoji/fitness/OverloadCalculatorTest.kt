package com.baoji.fitness

import com.baoji.fitness.domain.algorithm.OverloadCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverloadCalculatorTest {

    @Test
    fun testEstimated1RM_EpleyFormula() {
        // 80kg for 8 reps => 80 * (1 + 8/30) = 101.33 kg
        val e1rm = OverloadCalculator.calculateEstimated1RM(80.0, 8)
        assertEquals(101.3, e1rm, 0.1)

        // 1 rep should equal actual lifted weight
        val single = OverloadCalculator.calculateEstimated1RM(100.0, 1)
        assertEquals(100.0, single, 0.01)
    }

    @Test
    fun testTotalVolumeCalculation() {
        val sets = listOf(
            60.0 to 12, // 720
            80.0 to 8,  // 640
            80.0 to 7   // 560
        )
        val volume = OverloadCalculator.calculateTotalVolume(sets)
        assertEquals(1920.0, volume, 0.01)
    }

    @Test
    fun testDoubleProgressionDecision() {
        // 8 reps reached target maximum (纯次数判定，RPE 参数已删除) -> recommend +2.5kg
        val adviceIncrease = OverloadCalculator.evaluateProgression(
            currentWeightKg = 80.0,
            currentReps = 8,
            targetRepMax = 8
        )
        assertTrue(adviceIncrease.shouldIncreaseWeight)
        assertEquals(82.5, adviceIncrease.recommendedWeightKg, 0.01)
        assertEquals(6, adviceIncrease.recommendedReps)

        // Under 6 reps -> stay and build reps
        val adviceHold = OverloadCalculator.evaluateProgression(
            currentWeightKg = 85.0,
            currentReps = 5,
            targetRepMax = 8
        )
        assertFalse(adviceHold.shouldIncreaseWeight)
        assertEquals(85.0, adviceHold.recommendedWeightKg, 0.01)
    }

    @Test
    fun testRpVolumeLandmarks() {
        // Chest MEV is 10, MAV is 16
        val lowStatus = OverloadCalculator.evaluateMuscleVolume("胸部", 6.0)
        assertTrue(lowStatus.statusText.contains("欠练"))

        val optimalStatus = OverloadCalculator.evaluateMuscleVolume("胸部", 14.0)
        assertTrue(optimalStatus.statusText.contains("MAV"))

        val excessiveStatus = OverloadCalculator.evaluateMuscleVolume("胸部", 25.0)
        assertTrue(excessiveStatus.statusText.contains("过度训练"))
    }

    /** 小数组数（主 1.0 + 间接 0.5）必须走区间，不能被 IntRange 判成「过度训练」。 */
    @Test
    fun fractionalSetsFallInTheRightBands() {
        assertTrue(OverloadCalculator.evaluateMuscleVolume("胸部", 9.5).statusText.contains("欠练"))
        assertTrue(OverloadCalculator.evaluateMuscleVolume("胸部", 9.5 + 0.5).statusText.contains("MAV"))
        assertTrue(OverloadCalculator.evaluateMuscleVolume("胸部", 16.5).statusText.contains("疲劳"))
    }
}
