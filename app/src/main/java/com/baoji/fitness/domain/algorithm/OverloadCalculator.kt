package com.baoji.fitness.domain.algorithm

import com.baoji.fitness.domain.model.MuscleVolumeStatus
import kotlin.math.roundToInt

object OverloadCalculator {

    /**
     * Epley 极限力量估算公式 (Estimated 1RM)
     * 1RM = Weight * (1 + Reps / 30.0)
     */
    fun calculateEstimated1RM(weightKg: Double, reps: Int): Double {
        if (weightKg <= 0.0 || reps <= 0) return 0.0
        if (reps == 1) return weightKg
        val raw = weightKg * (1.0 + reps / 30.0)
        return (raw * 10.0).roundToInt() / 10.0
    }

    /**
     * 总训练负荷容量 (Total Volume in kg)
     */
    fun calculateTotalVolume(sets: List<Pair<Double, Int>>): Double {
        return sets.sumOf { (weight, reps) -> weight * reps }
    }

    /**
     * 双重渐进法决策 (Double Progression Protocol)
     * 纯次数判定：达到目标次数上限 (如 8次) 即推荐提重 +2.5kg。
     * 不依赖 RPE —— 录入页从不采集 RPE，之前的默认值判定是空转逻辑，已删除。
     */
    fun evaluateProgression(
        currentWeightKg: Double,
        currentReps: Int,
        targetRepMax: Int = 8
    ): ProgressionAdvice {
        return when {
            currentReps >= targetRepMax -> {
                val nextWeight = currentWeightKg + 2.5
                ProgressionAdvice(
                    shouldIncreaseWeight = true,
                    recommendedWeightKg = nextWeight,
                    recommendedReps = 6,
                    message = "连续达成目标次数 (${currentReps}次)，建议首组提重 +2.5kg 至 ${nextWeight}kg，目标 6 次。"
                )
            }
            currentReps < 6 -> {
                ProgressionAdvice(
                    shouldIncreaseWeight = false,
                    recommendedWeightKg = currentWeightKg,
                    recommendedReps = currentReps + 1,
                    message = "当前负荷强度较高，保持 ${currentWeightKg}kg，下周目标增加 1 次。"
                )
            }
            else -> {
                ProgressionAdvice(
                    shouldIncreaseWeight = false,
                    recommendedWeightKg = currentWeightKg,
                    recommendedReps = currentReps + 1,
                    message = "表现稳定，维持 ${currentWeightKg}kg，继续冲击 ${currentReps + 1} 次直至达标。"
                )
            }
        }
    }

    /**
     * RP (Renaissance Periodization) 运动周期理论：周有效组数评估
     */
    fun evaluateMuscleVolume(muscle: String, completedSets: Int): MuscleVolumeStatus {
        val (mev, mav, mrv) = when (muscle) {
            "胸部" -> Triple(10, 16, 22)
            "背部" -> Triple(10, 16, 22)
            "腿部" -> Triple(12, 18, 24)
            "肩部" -> Triple(8, 14, 20)
            "手臂" -> Triple(8, 14, 18)
            else -> Triple(8, 14, 20)
        }

        val statusText = when {
            completedSets < mev -> "低于最低有效容量 (欠练)"
            completedSets in mev..mav -> "最佳超量恢复与增肌区间 (MAV)"
            completedSets in (mav + 1)..mrv -> "高容量疲劳累积中"
            else -> "超过最大恢复容量 (过度训练风险)"
        }

        return MuscleVolumeStatus(
            muscleGroup = muscle,
            completedSets = completedSets,
            mev = mev,
            mav = mav,
            mrv = mrv,
            statusText = statusText
        )
    }

    data class ProgressionAdvice(
        val shouldIncreaseWeight: Boolean,
        val recommendedWeightKg: Double,
        val recommendedReps: Int,
        val message: String
    )
}
