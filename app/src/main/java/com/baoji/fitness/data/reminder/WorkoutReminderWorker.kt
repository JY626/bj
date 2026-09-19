package com.baoji.fitness.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.baoji.fitness.BaojiApplication
import com.baoji.fitness.data.settings.ReminderConfig
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * M2 训练日提醒。
 *
 * 取舍（文档里也写明）：WorkManager 的最小周期是 15 分钟，**做不到精确到点**，
 * 实际触发时间是目标时间 ±15 分钟。刻意不引入 `SCHEDULE_EXACT_ALARM` ——
 * Android 14 起要用户手动授权，体验更差。个人自用 ±15 分钟完全够。
 *
 * 因此调度器只负责「每天跑一次」，真正的判断（是不是训练日、今天有没有打卡）都在这里。
 */
class WorkoutReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? BaojiApplication ?: return Result.success()
        val config: ReminderConfig = app.reminderSettings.config.value
        val today = LocalDate.now()

        if (!config.enabled) return Result.success()
        // 今天不在计划的训练日里 → 休息日不打扰
        if (today.dayOfWeek !in config.trainingDays) return Result.success()

        val trainedToday = app.workoutRepository.getTrainedDayTypes().first().containsKey(today)
        if (trainedToday) return Result.success()

        ReminderNotifications.notifyMissedTraining(applicationContext, config.hour, config.minute)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "baoji_daily_workout_reminder"
    }
}
