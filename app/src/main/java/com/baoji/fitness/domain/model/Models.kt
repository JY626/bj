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
    val completedSets: Int,
    val mev: Int, // Minimum Effective Volume
    val mav: Int, // Maximum Adaptive Volume
    val mrv: Int, // Maximum Recoverable Volume
    val statusText: String
)

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
