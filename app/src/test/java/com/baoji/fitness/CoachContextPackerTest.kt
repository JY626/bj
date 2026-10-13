package com.baoji.fitness

import com.baoji.fitness.data.llm.CoachLlmClient
import com.baoji.fitness.data.llm.LlmEndpoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M4b：请求体转义必须干净、响应解析必须容错。 */
class CoachContextPackerTest {

    @Test
    fun quote_escapesEverythingThatBreaksJson() {
        assertEquals("\"\"", CoachLlmClient.quote(""))
        assertEquals("\"睡眠不足\"", CoachLlmClient.quote("睡眠不足"))
        assertEquals("\"a\\\"b\"", CoachLlmClient.quote("a\"b"))
        assertEquals("\"a\\\\b\"", CoachLlmClient.quote("a\\b"))
        assertEquals("\"a\\nb\"", CoachLlmClient.quote("a\nb"))
        assertEquals("\"a\\tb\"", CoachLlmClient.quote("a\tb"))
    }

    // ---- CoachLlmClient 的响应解析 ------------------------------------------

    @Test
    fun extractContent_readsStandardOpenAiResponse() {
        val body = """
            {"id":"chatcmpl-1","object":"chat.completion","choices":[
              {"index":0,"message":{"role":"assistant","content":"先把推日补上，明天 4 组卧推。"},"finish_reason":"stop"}
            ],"usage":{"total_tokens":120}}
        """.trimIndent()

        assertEquals("先把推日补上，明天 4 组卧推。", CoachLlmClient.extractContent(body))
    }

    @Test
    fun extractContent_unescapesQuotesAndNewlines() {
        val body = """{"choices":[{"message":{"content":"第一行\n第二行 \"带引号\""}}]}"""
        assertEquals("第一行\n第二行 \"带引号\"", CoachLlmClient.extractContent(body))
    }

    @Test
    fun extractContent_skipsNullContentAndKeepsLooking() {
        // 流式响应里首个 chunk 的 content 常常是 null
        val body = """{"choices":[{"delta":{"content":null},"message":{"content":"真正的答案"}}]}"""
        assertEquals("真正的答案", CoachLlmClient.extractContent(body))
    }

    @Test
    fun extractContent_returnsNullWhenKeyIsMissing() {
        assertNull(CoachLlmClient.extractContent("""{"choices":[{"message":{"role":"assistant"}}]}"""))
        assertNull(CoachLlmClient.extractContent(""))
    }

    @Test
    fun extractContent_survivesTruncatedResponse() {
        assertNull(CoachLlmClient.extractContent("""{"choices":[{"message":{"content":"被截断的"""))
    }

    @Test
    fun buildRequestBody_embedsQuestionAndContextAsEscapedStrings() {
        val body = CoachLlmClient.buildRequestBody(
            model = "gpt-4o-mini",
            systemPrompt = CoachLlmClient.SYSTEM_PROMPT,
            userContent = "今天该练什么？",
            contextJson = """{"weekly":{"label":"本周"}}"""
        )
        assertTrue(body.contains("\"model\":\"gpt-4o-mini\""))
        assertTrue(body.contains("今天该练什么？"))
        // context 里的引号必须被转义，否则请求体不是合法 JSON
        assertTrue(body.contains("{\\\"weekly\\\":{\\\"label\\\":\\\"本周\\\"}}"))
    }

    // ---- 回归：客户端必须用注入的 endpoint，而不是只读 BuildConfig ----------
    // 曾经 bug：ViewModel 运行时填的 Key 存进 LlmSettings，但 CoachLlmClient 默认只读
    // BuildConfig，于是配置界面显示“已配置”、实际请求却报“未配置 LLM API Key”。

    @Test
    fun clientUsesInjectedEndpointInsteadOfBuildConfig() = runBlocking {
        // 指向必然连不上的本机端口：能走到网络层 = 证明通过了“已配置”校验
        val client = CoachLlmClient(
            endpoint = { LlmEndpoint("sk-runtime", "http://127.0.0.1:1/v1", "gpt-4o-mini") }
        )
        val result = client.testConnection()
        assertTrue("必须真的用注入的 endpoint", result.isFailure)
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(
            "不能报「未配置」——说明没读到注入的 Key：$msg",
            !msg.contains("未配置")
        )
    }

    @Test
    fun clientReportsUnconfiguredWhenEndpointIsBlank() = runBlocking {
        val client = CoachLlmClient(endpoint = { LlmEndpoint("", "", "") })
        val result = client.testConnection()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("未配置"))
    }

    /** 读超时必须说“已连上但模型太慢”，不能再说成“网络到不了”（之前就是这么误导用户的）。 */
    @Test
    fun readTimeoutBlamesSlowModelNotNetwork() {
        val msg = CoachLlmClient.describeFailure(
            java.net.SocketTimeoutException("timeout"),
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        )
        assertTrue("要点出主机", msg.contains("dashscope.aliyuncs.com"))
        assertTrue("要说已连上", msg.contains("已连上"))
        assertTrue("要建议换更快的模型", msg.contains("更快的模型"))
        assertTrue("不能再说网络到不了", !msg.contains("网络到不了"))
    }

    /** 连接超时才是真·网络问题。 */
    @Test
    fun connectTimeoutBlamesNetwork() {
        val msg = CoachLlmClient.describeFailure(
            java.net.SocketTimeoutException("connect timed out"),
            "https://example.com/v1/chat/completions"
        )
        assertTrue(msg.contains("连接") && msg.contains("example.com"))
        assertTrue(msg.contains("网络"))
    }

    /** 关思考模式：开关打开时请求体必须带 enable_thinking:false，否则白搭。 */
    @Test
    fun disableThinkingAddsParameterToRequestBody() {
        val body = CoachLlmClient.buildRequestBody(
            model = "qwen-plus", systemPrompt = "s", userContent = "u", contextJson = null,
            disableThinking = true
        )
        assertTrue(body.contains("\"enable_thinking\":false"))
    }

    /** 不支持的端点（如 OpenAI）开关关掉后，绝不能带这个参数，否则报 400。 */
    @Test
    fun thinkingParameterIsOmittedWhenDisabled() {
        val body = CoachLlmClient.buildRequestBody(
            model = "gpt-4o-mini", systemPrompt = "s", userContent = "u", contextJson = null,
            disableThinking = false
        )
        assertTrue(!body.contains("enable_thinking"))
    }

    @Test
    fun cleartextAndDnsFailuresAreExplainedSeparately() {
        assertTrue(
            CoachLlmClient.describeFailure(
                RuntimeException("CLEARTEXT communication not permitted"),
                "http://example.com/v1/chat/completions"
            ).contains("https")
        )
        assertTrue(
            CoachLlmClient.describeFailure(
                java.net.UnknownHostException("example.invalid"),
                "https://example.invalid/v1/chat/completions"
            ).contains("域名解析")
        )
    }
}
