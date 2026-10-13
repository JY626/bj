package com.baoji.fitness.data.llm

import android.content.Context
import android.content.SharedPreferences
import com.baoji.fitness.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 运行时 LLM 配置（方案 A）：手机上直接填 Key / BaseUrl / 模型，不用重新编译。
 *
 * **Key 不进 APK、不进 git**：它存在 app 私有 SharedPreferences 里（未 root 的设备上
 * 别的应用读不到）。只有开发者本机编译期的 [BuildConfig] 值作为兜底。
 *
 * 读取优先级：用户填的运行时值 → `local.properties` 注入的 BuildConfig → 空（降级本地规则）。
 */
class LlmSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _endpoint = MutableStateFlow(read())
    /** 供 UI 实时刷新「已配置 / 未配置」。 */
    val endpoint: StateFlow<LlmEndpoint> = _endpoint.asStateFlow()

    private val _disableThinking = MutableStateFlow(readDisableThinking())
    /** 是否带 `enable_thinking:false`（百炼/GLM 这类支持，能显著变快）。 */
    val disableThinking: StateFlow<Boolean> = _disableThinking.asStateFlow()

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            _endpoint.value = read()
            _disableThinking.value = readDisableThinking()
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    val current: LlmEndpoint get() = _endpoint.value
    val currentDisableThinking: Boolean get() = _disableThinking.value

    fun save(apiKey: String, baseUrl: String, model: String) {
        prefs.edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_BASE_URL, baseUrl.trim())
            .putString(KEY_MODEL, model.trim())
            .apply()
        _endpoint.value = read()
    }

    fun setDisableThinking(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISABLE_THINKING, enabled).apply()
        _disableThinking.value = enabled
    }

    /** 回到开发机 `local.properties` 的编译期配置。保留思考开关的当前值。 */
    fun reset() {
        val keepThinking = currentDisableThinking
        prefs.edit().clear().apply()
        if (keepThinking) prefs.edit().putBoolean(KEY_DISABLE_THINKING, true).apply()
        _endpoint.value = read()
        _disableThinking.value = readDisableThinking()
    }

    private fun readDisableThinking(): Boolean =
        prefs.getBoolean(KEY_DISABLE_THINKING, true)

    private fun read(): LlmEndpoint {
        val key = prefs.getString(KEY_API_KEY, "").orEmpty().ifBlank { BuildConfig.LLM_API_KEY }
        val baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty().ifBlank { BuildConfig.LLM_BASE_URL }
        val model = prefs.getString(KEY_MODEL, "").orEmpty().ifBlank { BuildConfig.LLM_MODEL }
        return LlmEndpoint(apiKey = key, baseUrl = baseUrl.trimEnd('/'), model = model)
    }

    companion object {
        const val PREFS_NAME = "baoji_llm_settings"
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_BASE_URL = "llm_base_url"
        private const val KEY_MODEL = "llm_model"
        private const val KEY_DISABLE_THINKING = "llm_disable_thinking"
    }
}