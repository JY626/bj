package com.baoji.fitness.data.llm

import com.baoji.fitness.BuildConfig

/**
 * M4b：LLM 配置。
 *
 * **Key 绝不硬编码进 APK** —— 反编译即泄露。这里只读 `local.properties` 注入的
 * `BuildConfig` 字段（该文件已在 .gitignore 内）：
 *
 * ```
 * # local.properties
 * llm.apiKey=sk-xxxx
 * llm.baseUrl=https://api.openai.com/v1   # 任何 OpenAI 兼容端点都可以
 * llm.model=gpt-4o-mini
 * ```
 *
 * 空 Key 时 [isConfigured] 为 false，App 会降级到 M4a 的本地总结，不崩、不白屏。
 *
 * 想给别人用必须走自建后端代理：客户端持 Key 无法真正保密。
 */
object LlmConfig {
    val apiKey: String get() = BuildConfig.LLM_API_KEY
    val baseUrl: String get() = BuildConfig.LLM_BASE_URL.trimEnd('/')
    val model: String get() = BuildConfig.LLM_MODEL

    val endpoint: LlmEndpoint get() = LlmEndpoint(apiKey, baseUrl, model)

    val isConfigured: Boolean get() = endpoint.isConfigured
}
