package com.baoji.fitness.data.repository

import com.baoji.fitness.core.database.dao.ExerciseDao
import com.baoji.fitness.core.database.dao.WorkoutDao
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.domain.algorithm.OverloadCalculator
import com.baoji.fitness.domain.model.Exercise
import com.baoji.fitness.domain.model.ExerciseWithSets
import com.baoji.fitness.domain.model.MuscleVolumeStatus
import com.baoji.fitness.domain.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao
) {
    fun getActiveSession(): Flow<WorkoutSessionEntity?> = workoutDao.getActiveSession()

    fun getAllSessions(): Flow<List<WorkoutSessionEntity>> = workoutDao.getAllSessions()

    fun getAllExercises(): Flow<List<ExerciseEntity>> = exerciseDao.getAllExercises()

    suspend fun insertExercise(exercise: ExerciseEntity): Long = exerciseDao.insertExercise(exercise)

    suspend fun startNewSession(title: String = "自由推力训练"): Long {
        workoutDao.closeAllActiveSessions()
        val session = WorkoutSessionEntity(
            title = title,
            startTime = System.currentTimeMillis(),
            status = "ACTIVE"
        )
        return workoutDao.insertSession(session)
    }

    suspend fun finishSession(session: WorkoutSessionEntity, totalVolume: Double) {
        val updated = session.copy(
            endTime = System.currentTimeMillis(),
            totalVolumeKg = totalVolume,
            status = "COMPLETED"
        )
        workoutDao.updateSession(updated)
    }

    fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSetEntity>> =
        workoutDao.getSetsForSession(sessionId)

    fun getExerciseHistory(exerciseId: Long): Flow<List<WorkoutSetEntity>> =
        workoutDao.getHistoryForExercise(exerciseId)

    suspend fun getPreviousSessionSetsForExercise(exerciseId: Long, currentSessionId: Long): Map<Int, WorkoutSetEntity> {
        val history = workoutDao.getHistoricalCompletedSets(exerciseId, currentSessionId)
        if (history.isEmpty()) return emptyMap()

        val lastSessionId = history.first().sessionId
        val setsFromLastSession = history.filter { it.sessionId == lastSessionId }
        return setsFromLastSession.associateBy { it.setOrder }
    }

    suspend fun getExerciseById(exerciseId: Long): ExerciseEntity? =
        exerciseDao.getExerciseById(exerciseId)

    suspend fun removeExerciseFromSession(sessionId: Long, exerciseId: Long) {
        workoutDao.deleteSetsForExerciseInSession(sessionId, exerciseId)
    }

    suspend fun getPreviousExerciseIdsForRoutine(routineTitle: String): List<Long> {
        val targetSession = workoutDao.getLatestCompletedSessionByTitle(routineTitle)
            ?: workoutDao.getLatestSessionByTitle(routineTitle)
            ?: return emptyList()
        return workoutDao.getDistinctExerciseIdsForSession(targetSession.id)
    }

    suspend fun addSet(
        sessionId: Long,
        exerciseId: Long,
        weightKg: Double,
        reps: Int,
        setOrder: Int,
        setType: String = "NORMAL"
    ): Long {
        val entity = WorkoutSetEntity(
            sessionId = sessionId,
            exerciseId = exerciseId,
            setOrder = setOrder,
            setType = setType,
            weightKg = weightKg,
            reps = reps
        )
        return workoutDao.insertSet(entity)
    }

    suspend fun completeSet(set: WorkoutSetEntity, completed: Boolean) {
        val updated = set.copy(
            isCompleted = completed,
            completedAt = if (completed) System.currentTimeMillis() else null
        )
        workoutDao.updateSet(updated)

        // If completed and higher than personal record, update exercise PR
        if (completed) {
            val exercise = exerciseDao.getExerciseById(set.exerciseId)
            if (exercise != null && set.weightKg > exercise.personalRecordWeight) {
                exerciseDao.updatePR(set.exerciseId, set.weightKg, set.reps)
            }
        }
    }

    suspend fun updateSetWeightAndReps(set: WorkoutSetEntity, weightKg: Double, reps: Int) {
        workoutDao.updateSet(set.copy(weightKg = weightKg, reps = reps))
    }

    suspend fun deleteSet(set: WorkoutSetEntity) {
        workoutDao.deleteSet(set)
    }

    suspend fun deleteSetAndReorder(set: WorkoutSetEntity) {
        workoutDao.deleteSet(set)
        val remaining = workoutDao.getSetsForExerciseInSession(set.sessionId, set.exerciseId)
        remaining.forEachIndexed { index, s ->
            val expectedOrder = index + 1
            if (s.setOrder != expectedOrder) {
                workoutDao.updateSet(s.copy(setOrder = expectedOrder))
            }
        }
    }

    fun getWeeklyMuscleVolumes(): Flow<List<MuscleVolumeStatus>> {
        // Last 7 days
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return combine(
            workoutDao.getCompletedSetsSince(sevenDaysAgo),
            exerciseDao.getAllExercises()
        ) { sets, exercises ->
            val exerciseMap = exercises.associateBy { it.id }
            val muscleCounts = mutableMapOf<String, Int>()

            for (s in sets) {
                // 口径统一：热身组不计有效组数（与训练页 Volume 一致）
                if (s.setType == "WARMUP") continue
                val exercise = exerciseMap[s.exerciseId]
                val muscle = exercise?.targetMuscle ?: "胸部"
                muscleCounts[muscle] = (muscleCounts[muscle] ?: 0) + 1
            }

            listOf("胸部", "背部", "腿部", "肩部", "手臂").map { muscle ->
                OverloadCalculator.evaluateMuscleVolume(muscle, muscleCounts[muscle] ?: 0)
            }
        }
    }
}
