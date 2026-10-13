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

    /** 报告模式：系统提示词由 [com.baoji.fitness.domain.algorithm.CoachReport] 给定。 */
    suspend fun complete(
        systemPrompt: String,
        userContent: String,
        disableThinking: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        call(buildRequestBody(endpoint().model, systemPrompt, userContent, contextJson = null, disableThinking = disableThinking))
    }

    /** 测试连接：发一个最小请求，把「密钥/端点正确」与「模型名/额度不对」区分开。 */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        call(buildRequestBody(endpoint().model, SYSTEM_PROMPT, "ping", contextJson = null))
            .map { DEFAULT_TEST_REPLY }
    }

    private fun call(requestBody: String): Result<String> {
        val cfg = endpoint()
        if (!cfg.isConfigured) {
            return Result.failure(IllegalStateException("未配置 LLM API Key"))
        }

        val request = Request.Builder()
            .url("${cfg.baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${cfg.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody(JSON))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val hint = if (response.code == 400 && body.contains("enable_thinking", ignoreCase = true)) {
                        "（该端点不认 enable_thinking 参数，去设置里关掉「关思考模式」）"
                    } else ""
                    return Result.failure(IOException("HTTP ${response.code}: ${body.take(200)}$hint"))
                }
                val content = extractContent(body)
                if (content.isNullOrBlank()) {
                    Result.failure(IOException("响应里没有 content 字段"))
                } else {
                    Result.success(content.trim())
                }
            }
        } catch (t: Throwable) {
            Result.failure(IOException(describeFailure(t, request.url.toString()), t))
        }
    }

    internal companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // 报告是一整篇长输出，慢模型（尤其带思考的）超过 1 分钟很正常，
            // 所以读超时必须给得比“网络请求”宽松得多。
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        /** 连上主机的最长等待。到这一步失败 = 网络到不了该地址。 */
        const val CONNECT_TIMEOUT_SECONDS = 15L
        /** 服务端返回首字节后的最长等待。失败 = 模型太慢，不是网络问题。 */
        const val READ_TIMEOUT_SECONDS = 300L
        /** 整次调用（含连接+发送+接收）的硬上限。 */
        const val CALL_TIMEOUT_SECONDS = 330L

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

        fun buildRequestBody(
            model: String,
            systemPrompt: String,
            userContent: String,
            contextJson: String?,
            disableThinking: Boolean = false
        ): String = buildString {
            append("{\"model\":").append(CoachLlmClient.quote(model))
            append(",\"temperature\":0.3")
            // 百炼/GLM 的混合思考模型认这个参数：false = 直接出答案，快很多。
            // 不是 OpenAI 标准参数，不认它的端点必须关掉，否则报 400。
            if (disableThinking) append(",\"enable_thinking\":false")
            append(",\"messages\":[")
            append("{\"role\":\"system\",\"content\":")
            append(CoachLlmClient.quote(systemPrompt))
            append("}")
            append(",{\"role\":\"user\",\"content\":")
            append(
                CoachLlmClient.quote(
                    if (contextJson == null) userContent
                    else "我的训练数据（JSON 摘要）：\n$contextJson\n\n我的问题：$userContent"
                )
            )
            append("}]}")
        }

        const val DEFAULT_TEST_REPLY = "连接成功"

        /**
         * 把 OkHttp 的底层异常翻成**可自己动手排查**的一句话。
         * 之前只透传 `timeout`，用户根本不知道连不上谁。
         */
        fun describeFailure(t: Throwable, url: String): String {
            val host = url.substringAfter("://").substringBefore('/')
            val raw = t.message.orEmpty()
            return when {
                // 连接超时 = 真·网络到不了；读超时 = 已经连上，只是模型太慢。两者必须分开说。
                t is java.net.SocketTimeoutException && raw.contains("connect", ignoreCase = true) ->
                    "连接 $host 超时（${CONNECT_TIMEOUT_SECONDS}s）：手机网络到不了该地址，检查 BaseUrl / 网络"
                t is java.net.SocketTimeoutException ->
                    "已连上 $host，但 ${READ_TIMEOUT_SECONDS}s 内没返回结果：模型出字太慢或请求太大，" +
                        "换个更快的模型（如 qwen-turbo / qwen-plus）再试"
                raw.contains("timeout", ignoreCase = true) ->
                    "已连上 $host，但等待超时：模型出字太慢，换个更快的模型再试"
                t is java.net.UnknownHostException ->
                    "域名解析失败：$host 不存在，检查 BaseUrl 拼写"
                t is javax.net.ssl.SSLException ->
                    "HTTPS 证书错误：$host 的证书不被信任"
                raw.contains("CLEARTEXT", ignoreCase = true) ->
                    "该地址是 http 明文，Android 默认禁止；请把 BaseUrl 改成 https"
                raw.isBlank() -> t::class.java.simpleName
                else -> raw
            }
        }

        /** JSON 字符串字面量转义。用户备注里出现引号/换行是常态，这里必须处理干净。 */
        fun quote(raw: String): String = buildString {
            append('"')
            for (ch in raw) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
                }
            }
            append('"')
        }

        const val SYSTEM_PROMPT =
            "你是一名力量训练教练，服务于一个中文健身记录 App 的用户。" +
                "用户会给你一份训练数据摘要 JSON。请用简体中文回答，控制在 120 字以内，" +
                "给可执行的建议（重量、组数、次数、休息），不要复述数据，不要客套话。" +
                "如果数据不足以判断，直接说明还需要什么数据。"
    }
}
