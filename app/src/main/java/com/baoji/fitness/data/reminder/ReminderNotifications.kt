package com.baoji.fitness.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.baoji.fitness.MainActivity

/** 通知渠道与发通知的唯一出口。 */
internal object ReminderNotifications {

    private const val CHANNEL_ID = "baoji_workout_reminder"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "训练日提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "计划训练日当天还没打卡时提醒一次" }
        )
    }

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * 发一条「今天还没练」提醒。
     * 用固定的 [NOTIFICATION_ID]：重复触发只会替换同一条，不会刷出一堆。
     */
    fun notifyMissedTraining(context: Context, hour: Int, minute: Int) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val time = String.format("%02d:%02d", hour, minute)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("今天是训练日")
            .setContentText("你计划的 $time 训练还没开始 —— 完成一组就算今天没白过。")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 用户在系统里关掉了通知权限，静默跳过
        }
    }
}
