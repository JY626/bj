package com.baoji.fitness.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.baoji.fitness.core.database.entity.CoachReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachReportDao {

    /** `date`+`scope` 唯一索引 + REPLACE：同天同范围重新生成是覆盖。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: CoachReportEntity): Long

    /** 某天保存的全部报告（可能有多档范围）。 */
    @Query("SELECT * FROM coach_reports WHERE date = :date ORDER BY id ASC")
    fun observeByDate(date: Long): Flow<List<CoachReportEntity>>

    @Query("SELECT * FROM coach_reports WHERE date = :date AND scope = :scope LIMIT 1")
    suspend fun getByDateAndScope(date: Long, scope: String): CoachReportEntity?

    @Query("SELECT DISTINCT date FROM coach_reports")
    fun observeReportDates(): Flow<List<Long>>
}