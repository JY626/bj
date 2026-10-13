package com.baoji.fitness.domain.model

import java.time.LocalDate

data class Exercise(
    val id: Long,
    val name: String,
    val targetMuscle: String,
    val equipment: String,
    val personalRecordWeight: Double = 0.0,
    val personalRecordReps: Int = 0
)

data class WorkoutSet(
    val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val exerciseName: String = "",
    val setOrder: Int,
    val setType: String = "NORMAL", // WARMUP, NORMAL, DROP, FAILURE
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    val isCompleted: Boolean = false,
    val previousWeight: Double? = null,
    val previousReps: Int? = null
)

data class ExerciseWithSets(
    val exercise: Exercise,
    val sets: List<WorkoutSet>,
    val lastBestWeight: Double? = null,
    val lastBestReps: Int? = null,
    val estimated1RM: Double? = null
)

data class MuscleVolumeStatus(
    val muscleGroup: String,
    /** 有效组数，可为小数：主肌群 1.0 / 间接肌群 0.5（高位下拉既算背部也算手臂）。 */
    val completedSets: Double,
    val mev: Int, // Minimum Effective Volume
    val mav: Int, // Maximum Adaptive Volume
    val mrv: Int, // Maximum Recoverable Volume
    val statusText: String
)

/** 「1.5 组」这种小数要能读出来，而不是显示成 1.5 / 2.0 混搭。 */
fun formatSets(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

/** 某一天的训练明细：仅统计完成组，按动作分组。 */
data class WorkoutDayDetail(
    val date: LocalDate,
    val title: String,
    val totalVolumeKg: Double,
    val groups: List<ExerciseGroupDetail>,
    /** M3a：训练当天的自述备注，空串表示没写。 */
    val notes: String = ""
)

data class ExerciseGroupDetail(
    val exerciseName: String,
    val targetMuscle: String,
    val sets: List<WorkoutSet>
)
