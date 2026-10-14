package com.baoji.fitness.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baoji.fitness.core.database.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseById(id: Long): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getExerciseCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Query("UPDATE exercises SET personalRecordWeight = :weight, personalRecordReps = :reps WHERE id = :exerciseId")
    suspend fun updatePR(exerciseId: Long, weight: Double, reps: Int)

    @Query("SELECT * FROM exercises ORDER BY id ASC")
    suspend fun getAllExercisesList(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE name = :name LIMIT 1")
    suspend fun getExerciseByName(name: String): ExerciseEntity?

    @Query("DELETE FROM exercises")
    suspend fun deleteAllExercises()
}
