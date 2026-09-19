package com.baoji.fitness.data.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.baoji.fitness.data.settings.ReminderConfig
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * 按 [ReminderConfig] 登记/取消每日提醒任务。
 *
 * 用**唯一任务名**保证不会堆出多个任务（也就是不会一天发好几条通知）。
 * 时间变更时用 CANCEL_AND_REENQUEUE 重建，否则 WorkManager 会保留旧的 enqueue 时间，
 * 改了时间也不生效。
 */
object ReminderScheduler {

    fun apply(context: Context, config: ReminderConfig) {
        val workManager = WorkManager.getInstance(context)
        if (!config.enabled || config.trainingDays.isEmpty()) {
            workManager.cancelUniqueWork(WorkoutReminderWorker.UNIQUE_WORK_NAME)
            return
        }

        ReminderNotifications.ensureChannel(context)

        // 首次延迟到今天的目标时间；已经过了就顺延到明天
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(config.hour, config.minute)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val delayMinutes = Duration.between(now, target).toMinutes().coerceAtLeast(0)

        val request = PeriodicWorkRequestBuilder<WorkoutReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .addTag(WorkoutReminderWorker.UNIQUE_WORK_NAME)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WorkoutReminderWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }
}
