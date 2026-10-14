package com.baoji.fitness.domain.model

import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.domain.algorithm.OverloadCalculator

/**
 * 某个动作的个人纪录 (PR) 与历史表现四维模型
 */
data class ExercisePrSummary(
    val exercise: ExerciseEntity,
    /** 历史最大做组重量 (kg) */
    val maxWeightKg: Double = 0.0,
    /** 达成历史最大做组重量的组信息 */
    val maxWeightSet: WorkoutSetEntity? = null,
    /** 极限估算 1RM (kg, Epley 拟合) */
    val bestE1RM: Double = 0.0,
    /** 达成极限 1RM 的组信息 */
    val bestE1RMSet: WorkoutSetEntity? = null,
    /** 最佳单组容量组 (重量 × 次数) */
    val bestVolumeSet: WorkoutSetEntity? = null,
    /** 历史总累计完成有效组数 */
    val totalCompletedSets: Int = 0,
    /** 历史总累计容量 (kg) */
    val totalVolumeKg: Double = 0.0,
    /** 历史所有完成组列表（按完成时间降序排列） */
    val historySets: List<WorkoutSetEntity> = emptyList()
)

object PersonalRecordCalculator {

    /**
     * 计算单动作的 PR 概览与历史组
     */
    fun calculate(
        exercise: ExerciseEntity,
        sets: List<WorkoutSetEntity>
    ): ExercisePrSummary {
        val effectiveSets = sets.filter { it.isCompleted && it.setType != "WARMUP" }
        if (effectiveSets.isEmpty()) {
            return ExercisePrSummary(
                exercise = exercise,
                maxWeightKg = exercise.personalRecordWeight,
                bestE1RM = if (exercise.personalRecordReps > 0) {
                    OverloadCalculator.calculateEstimated1RM(exercise.personalRecordWeight, exercise.personalRecordReps)
                } else exercise.personalRecordWeight
            )
        }

        val maxWeightSet = effectiveSets.maxByOrNull { it.weightKg }
        val maxWeightKg = maxOf(maxWeightSet?.weightKg ?: 0.0, exercise.personalRecordWeight)

        val bestE1RMSet = effectiveSets.maxByOrNull {
            OverloadCalculator.calculateEstimated1RM(it.weightKg, it.reps)
        }
        val calculatedE1RM = bestE1RMSet?.let {
            OverloadCalculator.calculateEstimated1RM(it.weightKg, it.reps)
        } ?: 0.0
        val baseE1RM = if (exercise.personalRecordReps > 0) {
            OverloadCalculator.calculateEstimated1RM(exercise.personalRecordWeight, exercise.personalRecordReps)
        } else exercise.personalRecordWeight

        val bestVolumeSet = effectiveSets.maxByOrNull { it.weightKg * it.reps }
        val totalVolume = effectiveSets.sumOf { it.weightKg * it.reps }

        return ExercisePrSummary(
            exercise = exercise,
            maxWeightKg = maxWeightKg,
            maxWeightSet = maxWeightSet,
            bestE1RM = maxOf(calculatedE1RM, baseE1RM),
            bestE1RMSet = bestE1RMSet,
            bestVolumeSet = bestVolumeSet,
            totalCompletedSets = effectiveSets.size,
            totalVolumeKg = totalVolume,
            historySets = effectiveSets.sortedByDescending { it.completedAt ?: 0L }
        )
    }
}
