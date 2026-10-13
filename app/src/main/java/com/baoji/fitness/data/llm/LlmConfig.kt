package com.baoji.fitness.data.llm

import com.baoji.fitness.BuildConfig

/**
 * M4b：LLM 配置的**编译期兜底**。
 *
 * Key 绝不硬编码进 APK —— 反编译即泄露。这里只读 `local.properties` 注入的
 * `BuildConfig` 字段（该文件已在 .gitignore 内）：
 *
 * ```
 * # local.properties
 * llm.apiKey=sk-xxxx
 * llm.baseUrl=https://api.openai.com/v1   # 任何 OpenAI 兼容端点都可以
 * llm.model=gpt-4o-mini
 * ```
 *
 * 运行时还可以在 App 内直接填（见 [LlmSettings]，方案 A）—— 那时以运行时值为准，
 * 这里的值只作为回落。两者都空时 [isConfigured] 为 false，App 降级到 M4a 本地总结。
 */
object LlmConfig {
    val apiKey: String get() = BuildConfig.LLM_API_KEY
    val baseUrl: String get() = BuildConfig.LLM_BASE_URL.trimEnd('/')
    val model: String get() = BuildConfig.LLM_MODEL

    val endpoint: LlmEndpoint get() = LlmEndpoint(apiKey, baseUrl, model)

    val isConfigured: Boolean get() = endpoint.isConfigured
}