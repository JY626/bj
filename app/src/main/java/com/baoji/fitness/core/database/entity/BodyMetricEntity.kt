package com.baoji.fitness.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 身体数据（体重 / 体脂 / 围度）。**一天一条**：`date` 存当天本地 0 点的 epoch millis，
 * 并加唯一索引，同一天记两次走 `@Insert(onConflict = REPLACE)` 覆盖而不是新增两条。
 *
 * 该表在 MIGRATION_2_3 里创建（v2 → v3）。
 */
@Entity(
    tableName = "body_metrics",
    indices = [Index(value = ["date"], unique = true)]
)
data class BodyMetricEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 当天本地 0 点 epoch millis */
    val date: Long,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    /** 身高，用于算 BMI。不常变，但写一次会一直往后沿用。 */
    val heightCm: Double? = null,
    val chestCm: Double? = null,
    val waistCm: Double? = null,
    val armCm: Double? = null,
    val note: String = ""
)
