package com.baoji.fitness

import android.app.Application
import com.baoji.fitness.core.database.BaojiDatabase
import com.baoji.fitness.data.repository.BodyMetricRepository
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.data.settings.ReminderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BaojiApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { BaojiDatabase.getDatabase(this, applicationScope) }
    val workoutRepository by lazy {
        WorkoutRepository(
            workoutDao = database.workoutDao(),
            exerciseDao = database.exerciseDao()
        )
    }

    /** 身体数据（体重、体脂率、围度等）。 */
    val bodyMetricRepository by lazy {
        BodyMetricRepository(database.bodyMetricDao())
    }

    /** 提醒设置（SharedPreferences，轻量标量配置）。 */
    val reminderSettings by lazy { ReminderSettings(this) }
}
