package com.baoji.fitness.domain.model

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
