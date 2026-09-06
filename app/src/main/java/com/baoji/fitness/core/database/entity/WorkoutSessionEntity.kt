package com.baoji.fitness.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val totalVolumeKg: Double = 0.0,
    val status: String = "ACTIVE", // ACTIVE or COMPLETED
    val notes: String = ""
)
