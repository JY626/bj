package com.baoji.fitness.data.repository

import com.baoji.fitness.core.database.dao.CoachReportDao
import com.baoji.fitness.core.database.entity.CoachReportEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** 保存下来的私教分析：一天一个范围一条，日历里回看。 */
class CoachReportRepository(
    private val dao: CoachReportDao,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    suspend fun save(
        day: LocalDate,
        scope: String,
        content: String,
        source: String,
        generatedAt: Long = System.currentTimeMillis()
    ) {
        dao.upsert(
            CoachReportEntity(
                // 带上已有行的 id：唯一索引是 (date, scope)，REPLACE 只在重复时命中
                id = dao.getByDateAndScope(startOfDayMillis(day), scope)?.id ?: 0,
                date = startOfDayMillis(day),
                scope = scope,
                content = content,
                source = source,
                generatedAt = generatedAt
            )
        )
    }

    fun observeForDate(day: LocalDate): Flow<List<CoachReportEntity>> =
        dao.observeByDate(startOfDayMillis(day))

    /** 有报告的日子（用于日历小圆点）。 */
    fun observeReportDays(): Flow<Set<LocalDate>> =
        dao.observeReportDates().map { millis ->
            millis.map { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()
        }

    private fun startOfDayMillis(day: LocalDate): Long =
        day.atStartOfDay(zone).toInstant().toEpochMilli()
}