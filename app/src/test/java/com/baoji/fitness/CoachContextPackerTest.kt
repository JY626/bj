package com.baoji.fitness

import com.baoji.fitness.data.llm.CoachContextPacker
import com.baoji.fitness.data.llm.CoachLlmClient
import com.baoji.fitness.data.llm.CoachLlmContext
import com.baoji.fitness.data.llm.PersonalRecordSummary
import com.baoji.fitness.data.llm.SessionSummary
import com.baoji.fitness.domain.algorithm.PeriodReview
import com.baoji.fitness.domain.model.MuscleVolumeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** M4b：只发摘要、转义必须干净、解析必须容错。 */
class CoachContextPackerTest {

    private val weekly = PeriodReview(
        label = "本周",
        sessionCount = 3,
        totalVolumeKg = 12480.0,
        totalSets = 42,
        typeCounts = mapOf("推" to 1, "拉" to 2),
        volumeDeltaPct = 8.0
    )

    @Test
    fun quote_escapesEverythingThatBreaksJson() {
        assertEquals("\"\"", CoachContextPacker.quote(""))
        assertEquals("\"睡眠不足\"", CoachContextPacker.quote("睡眠不足"))
        assertEquals("\"a\\\"b\"", CoachContextPacker.quote("a\"b"))
        assertEquals("\"a\\\\b\"", CoachContextPacker.quote("a\\b"))
        assertEquals("\"a\\nb\"", CoachContextPacker.quote("a\nb"))
        assertEquals("\"a\\tb\"", CoachContextPacker.quote("a\tb"))
    }

    @Test
    fun toJson_isParseableAndCarriesTheSummary() {
        val json = CoachContextPacker.toJson(
            CoachLlmContext(
                today = LocalDate.of(2026, 9, 15),
                weekly = weekly,
                monthly = null,
                muscles = listOf(MuscleVolumeStatus("胸部", 6, 10, 16, 22, "低于最低有效容量 (欠练)")),
                recentSessions = listOf(
                    SessionSummary("9-15", "推", "DAY 1 · 推日 (胸 肩)", 4820.0)
                ),
                personalRecords = listOf(PersonalRecordSummary("卧推", 80.0, 8)),
                recentNotes = listOf("状态好"),
                weightKg = 75.0,
                bodyFatPct = null,
                streakDays = 4
            )
        )

        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertTrue(json.contains("\"label\":\"本周\""))
        assertTrue(json.contains("\"volumeDeltaPct\":8"))
        assertTrue(json.contains("\"typeCounts\":{\"推\":1,\"拉\":2}"))
        assertTrue(json.contains("\"muscle\":\"胸部\""))
        assertTrue(json.contains("\"exercise\":\"卧推\""))
        assertTrue(json.contains("\"recentNotes\":[\"状态好\"]"))
        assertTrue(json.contains("\"bodyFatPct\":null"))
        assertTrue(json.contains("\"monthly\":null"))
        assertTrue("不该出现原始逐组明细字段", !json.contains("weightKg\":80,\"reps\":8,\"setType"))
    }

    @Test
    fun toJson_toleratesEmptyContext() {
        val json = CoachContextPacker.toJson(CoachLlmContext(LocalDate.now(), null, null, emptyList(), emptyList(), emptyList(), emptyList()))
        assertTrue(json.contains("\"muscleVolumes\":[]"))
        assertTrue(json.contains("\"personalRecords\":[]"))
        assertTrue(json.contains("\"recentNotes\":[]"))
        assertTrue(json.contains("\"weekly\":null"))
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
            question = "今天该练什么？",
            contextJson = """{"weekly":{"label":"本周"}}"""
        )
        assertTrue(body.contains("\"model\":\"gpt-4o-mini\""))
        assertTrue(body.contains("今天该练什么？"))
        // context 里的引号必须被转义，否则请求体不是合法 JSON
        assertTrue(body.contains("{\\\"weekly\\\":{\\\"label\\\":\\\"本周\\\"}}"))
    }
}
