package com.baoji.fitness.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMetricDao {

    /**
     * `date` 上有唯一索引 + REPLACE：同一天记两次是**覆盖**，不会攒出两条。
     * 覆盖后 id 会变（REPLACE = DELETE + INSERT），本表没有任何外部引用，安全。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metric: BodyMetricEntity): Long

    @Query("SELECT * FROM body_metrics ORDER BY date DESC LIMIT 1")
    fun getLatest(): Flow<BodyMetricEntity?>

    @Query("SELECT * FROM body_metrics ORDER BY date ASC")
    fun getAll(): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metrics WHERE date = :date ORDER BY date DESC LIMIT 1")
    fun observeByDate(date: Long): Flow<BodyMetricEntity?>

    @Query("SELECT * FROM body_metrics WHERE date = :date ORDER BY date DESC LIMIT 1")
    suspend fun getByDate(date: Long): BodyMetricEntity?

    @Query("SELECT COUNT(*) FROM body_metrics")
    suspend fun count(): Int
}
