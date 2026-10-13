package com.baoji.fitness.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.baoji.fitness.data.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek

/**
 * M2 提醒设置。只有 4 个标量，`SharedPreferences` 足够 ——
 * 个人单机 App 引入 DataStore / DI 框架是纯负债。
 */
data class ReminderConfig(
    /** 训练日提醒总开关。默认关：不给用户发他没要过的通知。 */
    val enabled: Boolean = false,
    val hour: Int = 20,
    val minute: Int = 0,
    /** 每周计划训练日；默认对应 App 内置的三日分化。 */
    val trainingDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    /**
     * M4b 隐私开关：**必须**由用户显式打开才会把训练摘要发给大模型。
     * 默认关，因为备注与身体数据都会外发。
     */
    val llmSharingEnabled: Boolean = false,
    /** 默认组间休息秒数（打勾后自动开始）。大重量复合动作可调到 180。 */
    val restSeconds: Int = 90
)

class ReminderSettings(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<ReminderConfig> = _config.asStateFlow()

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _config.value = read() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // 每次冷启动重排一次：WorkManager 的任务会跨重启保留，但时间设置可能被改过
        ReminderScheduler.apply(appContext, _config.value)
    }

    fun update(config: ReminderConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putInt(KEY_HOUR, config.hour)
            .putInt(KEY_MINUTE, config.minute)
            .putStringSet(KEY_DAYS, config.trainingDays.map { it.name }.toSet())
            .putBoolean(KEY_LLM_SHARING, config.llmSharingEnabled)
            .putInt(KEY_REST_SECONDS, config.restSeconds)
            .apply()
        _config.value = config
        ReminderScheduler.apply(appContext, config)
    }

    fun setEnabled(enabled: Boolean) = update(_config.value.copy(enabled = enabled))
    fun setTime(hour: Int, minute: Int) = update(_config.value.copy(hour = hour, minute = minute))
    fun setTrainingDays(days: Set<DayOfWeek>) = update(_config.value.copy(trainingDays = days))
    fun setLlmSharing(enabled: Boolean) = update(_config.value.copy(llmSharingEnabled = enabled))
    fun setRestSeconds(seconds: Int) = update(_config.value.copy(restSeconds = seconds.coerceIn(30, 300)))

    private fun read(): ReminderConfig {
        val days = prefs.getStringSet(KEY_DAYS, null)
            ?.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
            ?.toSet()
        return ReminderConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            hour = prefs.getInt(KEY_HOUR, 20),
            minute = prefs.getInt(KEY_MINUTE, 0),
            trainingDays = days ?: ReminderConfig().trainingDays,
            llmSharingEnabled = prefs.getBoolean(KEY_LLM_SHARING, false),
            restSeconds = prefs.getInt(KEY_REST_SECONDS, 90)
        )
    }

    companion object {
        const val PREFS_NAME = "baoji_reminder_settings"
        private const val KEY_ENABLED = "reminder_enabled"
        private const val KEY_HOUR = "reminder_hour"
        private const val KEY_MINUTE = "reminder_minute"
        private const val KEY_DAYS = "reminder_training_days"
        private const val KEY_LLM_SHARING = "llm_sharing_enabled"
        private const val KEY_REST_SECONDS = "rest_seconds"
    }
}
