package com.baoji.fitness.data.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** OpenAI 兼容端点的三要素。 */
data class LlmEndpoint(
    val apiKey: String,
    val baseUrl: String,
    val model: String
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && baseUrl.isNotBlank()
}

/**
 * M4b-1：真 LLM 单轮问答（发上下文 → 收一段文字）。
 *
 * 刻意**不做**流式（SSE）与结构化处方卡，那是 M4b-2；先把「能通」这件事做出来。
 * 所有失败路径都返回 [Result.failure]，由 ViewModel 降级回 M4a 的本地总结 ——
 * 断网、没配 Key、模型返回异常，都不许白屏或崩溃。
 */
class CoachLlmClient(
    private val endpoint: () -> LlmEndpoint = { LlmConfig.endpoint },
    private val client: OkHttpClient = defaultClient()
) {

    suspend fun ask(question: String, contextJson: String): Result<String> = withContext(Dispatchers.IO) {
        val cfg = endpoint()
        if (!cfg.isConfigured) {
            return@withContext Result.failure(IllegalStateException("未配置 LLM API Key"))
        }

        val request = Request.Builder()
            .url("${cfg.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${cfg.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(buildRequestBody(cfg.model, question, contextJson).toRequestBody(JSON))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${body.take(200)}"))
                }
                val content = extractContent(body)
                if (content.isNullOrBlank()) {
                    Result.failure(IOException("响应里没有 content 字段"))
                } else {
                    Result.success(content.trim())
                }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    internal companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        /**
         * 只取 `"content"` 后面的第一个字符串字面量。
         *
         * 不用 `org.json` 的原因：它在 Android 运行时是平台实现，但在本地 JVM 单测里是 stub
         * （抛 "not mocked"），这样这段解析就没法被测到。手写反而更可控。
         */
        fun extractContent(responseJson: String): String? {
            val key = "\"content\""
            var searchFrom = 0
            while (searchFrom < responseJson.length) {
                val keyIndex = responseJson.indexOf(key, searchFrom)
                if (keyIndex < 0) return null
                var i = keyIndex + key.length
                while (i < responseJson.length && responseJson[i].isWhitespace()) i++
                if (i >= responseJson.length || responseJson[i] != ':') {
                    searchFrom = keyIndex + key.length
                    continue
                }
                i++
                while (i < responseJson.length && responseJson[i].isWhitespace()) i++
                if (i >= responseJson.length) return null
                if (responseJson.startsWith("null", i)) {
                    // 流式/工具调用场景 content 可能是 null，继续往后找
                    searchFrom = i + 4
                    continue
                }
                if (responseJson[i] != '"') {
                    searchFrom = i
                    continue
                }
                return readJsonString(responseJson, i)
            }
            return null
        }

        fun readJsonString(source: String, startQuoteIndex: Int): String? {
            val sb = StringBuilder()
            var i = startQuoteIndex + 1
            while (i < source.length) {
                when (val ch = source[i]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i + 1 >= source.length) return null
                        when (val esc = source[i + 1]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 5 >= source.length) return null
                                val hex = source.substring(i + 2, i + 6)
                                val code = hex.toIntOrNull(16) ?: return null
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> sb.append(esc)
                        }
                        i += 2
                    }
                    else -> {
                        sb.append(ch)
                        i++
                    }
                }
            }
            return null
        }

        fun buildRequestBody(model: String, question: String, contextJson: String): String = buildString {
            append("{\"model\":").append(CoachContextPacker.quote(model))
            append(",\"temperature\":0.3")
            append(",\"messages\":[")
            append("{\"role\":\"system\",\"content\":")
            append(CoachContextPacker.quote(SYSTEM_PROMPT))
            append("}")
            append(",{\"role\":\"user\",\"content\":")
            append(
                CoachContextPacker.quote(
                    "我的训练数据（JSON 摘要）：\n$contextJson\n\n我的问题：$question"
                )
            )
            append("}]}")
        }

        const val SYSTEM_PROMPT =
            "你是一名力量训练教练，服务于一个中文健身记录 App 的用户。" +
                "用户会给你一份训练数据摘要 JSON。请用简体中文回答，控制在 120 字以内，" +
                "给可执行的建议（重量、组数、次数、休息），不要复述数据，不要客套话。" +
                "如果数据不足以判断，直接说明还需要什么数据。"
    }
}
