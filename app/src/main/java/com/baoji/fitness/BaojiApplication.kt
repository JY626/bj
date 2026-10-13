package com.baoji.fitness

import android.app.Application
import com.baoji.fitness.core.database.BaojiDatabase
import com.baoji.fitness.data.repository.BodyMetricRepository
import com.baoji.fitness.data.repository.CoachReportRepository
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.data.llm.LlmSettings
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

    /** M3b：身体数据。 */
    val bodyMetricRepository by lazy {
        BodyMetricRepository(database.bodyMetricDao())
    }

    /** 保存下来的私教分析报告（日历回看）。 */
    val coachReportRepository by lazy {
        CoachReportRepository(database.coachReportDao())
    }

    /** M2：提醒设置（SharedPreferences，3~4 个标量，不引入 DataStore）。 */
    val reminderSettings by lazy { ReminderSettings(this) }

    /** M4b：运行时 LLM 配置（方案 A，App 内直接填 Key）。 */
    val llmSettings by lazy { LlmSettings(this) }
}
