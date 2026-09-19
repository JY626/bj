package com.baoji.fitness.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1")
    fun getActiveSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    fun getSessionById(sessionId: Long): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<WorkoutSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("UPDATE workout_sessions SET status = 'COMPLETED' WHERE status = 'ACTIVE'")
    suspend fun closeAllActiveSessions()

    // Sets
    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId ORDER BY exerciseId ASC, setOrder ASC, id ASC")
    fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId ORDER BY setOrder ASC, id ASC")
    suspend fun getSetsForExerciseInSession(sessionId: Long, exerciseId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId AND isCompleted = 1 ORDER BY completedAt DESC LIMIT :limit")
    suspend fun getLastCompletedSetsForExercise(exerciseId: Long, limit: Int = 10): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId AND isCompleted = 1 AND sessionId != :currentSessionId ORDER BY completedAt DESC, id DESC")
    suspend fun getHistoricalCompletedSets(exerciseId: Long, currentSessionId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE exerciseId = :exerciseId AND isCompleted = 1 ORDER BY completedAt ASC")
    fun getHistoryForExercise(exerciseId: Long): Flow<List<WorkoutSetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: WorkoutSetEntity): Long

    @Update
    suspend fun updateSet(set: WorkoutSetEntity)

    @Delete
    suspend fun deleteSet(set: WorkoutSetEntity)

    @Query("DELETE FROM workout_sets WHERE sessionId = :sessionId")
    suspend fun deleteSetsForSession(sessionId: Long)

    @Query("SELECT * FROM workout_sets WHERE completedAt >= :sinceTimestamp AND isCompleted = 1")
    fun getCompletedSetsSince(sinceTimestamp: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sessions WHERE title = :title AND status = 'COMPLETED' ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestCompletedSessionByTitle(title: String): WorkoutSessionEntity?

    /** M3a/M4a：最近的会话备注（跳过空串），用于本地总结。 */
    @Query("SELECT notes FROM workout_sessions WHERE notes != '' AND status = 'COMPLETED' ORDER BY startTime DESC LIMIT :limit")
    fun getRecentNotes(limit: Int): Flow<List<String>>

    @Query("SELECT * FROM workout_sessions WHERE title = :title ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestSessionByTitle(title: String): WorkoutSessionEntity?

    @Query("SELECT DISTINCT exerciseId FROM workout_sets WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getDistinctExerciseIdsForSession(sessionId: Long): List<Long>

    @Query("DELETE FROM workout_sets WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun deleteSetsForExerciseInSession(sessionId: Long, exerciseId: Long)

    // ---- M3a：复活两个从来没被写过的列（列已存在，零迁移）----

    @Query("UPDATE workout_sessions SET notes = :notes WHERE id = :sessionId")
    suspend fun updateSessionNotes(sessionId: Long, notes: String)

    @Query("UPDATE workout_sets SET rpe = :rpe WHERE id = :setId")
    suspend fun updateSetRpe(setId: Long, rpe: Double?)
}
