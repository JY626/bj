package com.baoji.fitness

import android.app.Application
import com.baoji.fitness.core.database.BaojiDatabase
import com.baoji.fitness.data.healthconnect.HealthConnectWriter
import com.baoji.fitness.data.repository.BodyMetricRepository
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.data.settings.ReminderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BaojiApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { BaojiDatabase.getDatabase(this, applicationScope) }

    /** 提醒设置（SharedPreferences，轻量标量配置）。 */
    val reminderSettings by lazy { ReminderSettings(this) }

    val workoutRepository by lazy {
        WorkoutRepository(
            workoutDao = database.workoutDao(),
            exerciseDao = database.exerciseDao(),
            onSessionArchived = { session, sets, exercises ->
                if (reminderSettings.config.value.healthConnectEnabled) {
                    applicationScope.launch {
                        HealthConnectWriter.writeWorkout(this@BaojiApplication, session, sets, exercises)
                    }
                }
            }
        )
    }

    /** 身体数据（体重、体脂率、围度等）。 */
    val bodyMetricRepository by lazy {
        BodyMetricRepository(
            dao = database.bodyMetricDao(),
            onMetricSaved = { metric ->
                if (reminderSettings.config.value.healthConnectEnabled) {
                    applicationScope.launch {
                        HealthConnectWriter.writeBodyMetrics(this@BaojiApplication, metric)
                    }
                }
            }
        )
    }
}
