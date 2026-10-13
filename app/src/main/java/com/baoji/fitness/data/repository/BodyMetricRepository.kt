package com.baoji.fitness.data.repository

import com.baoji.fitness.core.database.dao.BodyMetricDao
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 身体数据（体重 / 体脂 / 围度）。一天一条，日期以**本地 0 点**为键，
 * 与日历页的「当天」语义保持一致。
 */
class BodyMetricRepository(
    private val dao: BodyMetricDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val onMetricSaved: (suspend (BodyMetricEntity) -> Unit)? = null
) {
    fun observeLatest(): Flow<BodyMetricEntity?> = dao.getLatest()

    /** 按日期升序，供折线图使用。 */
    fun observeAll(): Flow<List<BodyMetricEntity>> = dao.getAll()

    fun observeForDate(day: LocalDate): Flow<BodyMetricEntity?> = dao.observeByDate(startOfDayMillis(day))

    fun startOfDayMillis(day: LocalDate): Long =
        day.atStartOfDay(zone).toInstant().toEpochMilli()

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /**
     * 写入某天的身体数据。传 null 表示该项当天没测 —— 会**保留**已存在的旧值，
     * 避免「只称了体重」把上次记的腰围清空。
     */
    suspend fun upsert(
        day: LocalDate,
        weightKg: Double? = null,
        bodyFatPct: Double? = null,
        heightCm: Double? = null,
        chestCm: Double? = null,
        waistCm: Double? = null,
        armCm: Double? = null,
        note: String = ""
    ) {
        val date = startOfDayMillis(day)
        val existing = dao.getByDate(date)
        val entity = BodyMetricEntity(
            id = existing?.id ?: 0,
            date = date,
            weightKg = weightKg ?: existing?.weightKg,
            bodyFatPct = bodyFatPct ?: existing?.bodyFatPct,
            heightCm = heightCm ?: existing?.heightCm ?: dao.getLatestHeight(),
            chestCm = chestCm ?: existing?.chestCm,
            waistCm = waistCm ?: existing?.waistCm,
            armCm = armCm ?: existing?.armCm,
            note = note.ifBlank { existing?.note ?: "" }
        )
        val id = dao.upsert(entity)
        val saved = entity.copy(id = if (entity.id == 0L) id else entity.id)
        onMetricSaved?.invoke(saved)
    }
}
