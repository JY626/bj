package com.baoji.fitness.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val targetMuscle: String, // 胸部, 背部, 腿部, 肩部, 手臂, 核心
    val secondaryMuscles: String = "",
    val equipment: String, // 杠铃, 哑铃, 绳索, 史密斯, 机械器械, 自重
    val isCustom: Boolean = false,
    /** false = 自重/仅记次数（如悬垂举腿），录入 UI 隐藏重量步进 */
    val trackWeight: Boolean = true,
    val personalRecordWeight: Double = 0.0,
    val personalRecordReps: Int = 0
)
