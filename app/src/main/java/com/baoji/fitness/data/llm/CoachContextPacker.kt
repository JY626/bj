package com.baoji.fitness.data.llm

import com.baoji.fitness.domain.algorithm.PeriodReview
import com.baoji.fitness.domain.model.MuscleVolumeStatus
import java.time.LocalDate
import java.util.Locale

data class SessionSummary(
    val date: String,
    val type: String,
    val title: String,
    val volumeKg: Double
)

data class PersonalRecordSummary(
    val exercise: String,
    val weightKg: Double,
    val reps: Int
)

/** 要发给模型的东西 —— 全部是**摘要**，没有任何原始逐组明细。 */
data class CoachLlmContext(
    val today: LocalDate,
    val weekly: PeriodReview?,
    val monthly: PeriodReview?,
    val muscles: List<MuscleVolumeStatus>,
    val recentSessions: List<SessionSummary>,
    val personalRecords: List<PersonalRecordSummary>,
    val recentNotes: List<String>,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val streakDays: Int = 0
)

/**
 * M4b：上下文打包。**只发摘要不发全量** —— 一周 100+ 组的逐组记录会让 token 直接爆炸，
 * 而且模型也读不出更多东西。
 *
 * 手写 JSON 而不是引序列化框架：结构只有一层半，加个 kotlinx.serialization 插件不划算。
 */
object CoachContextPacker {

    fun toJson(ctx: CoachLlmContext): String = buildString {
        append("{")
        append("\"today\":").append(quote(ctx.today.toString())).append(",")
        append("\"streakDays\":").append(ctx.streakDays).append(",")
        append("\"weekly\":").append(reviewJson(ctx.weekly)).append(",")
        append("\"monthly\":").append(reviewJson(ctx.monthly)).append(",")
        append("\"muscleVolumes\":").append(musclesJson(ctx.muscles)).append(",")
        append("\"recentSessions\":").append(sessionsJson(ctx.recentSessions)).append(",")
        append("\"personalRecords\":").append(prsJson(ctx.personalRecords)).append(",")
        append("\"recentNotes\":").append(notesJson(ctx.recentNotes)).append(",")
        append("\"body\":").append(bodyJson(ctx.weightKg, ctx.bodyFatPct))
        append("}")
    }

    private fun reviewJson(review: PeriodReview?): String {
        if (review == null) return "null"
        return buildString {
            append("{")
            append("\"label\":").append(quote(review.label)).append(",")
            append("\"sessions\":").append(review.sessionCount).append(",")
            append("\"volumeKg\":").append(num(review.totalVolumeKg)).append(",")
            append("\"effectiveSets\":").append(review.totalSets).append(",")
            append("\"volumeDeltaPct\":").append(review.volumeDeltaPct?.let { num(it) } ?: "null").append(",")
            append("\"typeCounts\":").append(
                review.typeCounts.entries.joinToString(",", "{", "}") { (type, count) ->
                    quote(type) + ":" + count
                }
            )
            append("}")
        }
    }

    private fun musclesJson(muscles: List<MuscleVolumeStatus>): String =
        muscles.joinToString(",", "[", "]") { m ->
            buildString {
                append("{")
                append("\"muscle\":").append(quote(m.muscleGroup)).append(",")
                append("\"sets\":").append(m.completedSets).append(",")
                append("\"mev\":").append(m.mev).append(",")
                append("\"mav\":").append(m.mav).append(",")
                append("\"mrv\":").append(m.mrv).append(",")
                append("\"status\":").append(quote(m.statusText))
                append("}")
            }
        }

    private fun sessionsJson(sessions: List<SessionSummary>): String =
        sessions.joinToString(",", "[", "]") { s ->
            buildString {
                append("{")
                append("\"date\":").append(quote(s.date)).append(",")
                append("\"type\":").append(quote(s.type)).append(",")
                append("\"title\":").append(quote(s.title)).append(",")
                append("\"volumeKg\":").append(num(s.volumeKg))
                append("}")
            }
        }

    private fun prsJson(prs: List<PersonalRecordSummary>): String =
        prs.joinToString(",", "[", "]") { pr ->
            buildString {
                append("{")
                append("\"exercise\":").append(quote(pr.exercise)).append(",")
                append("\"weightKg\":").append(num(pr.weightKg)).append(",")
                append("\"reps\":").append(pr.reps)
                append("}")
            }
        }

    private fun notesJson(notes: List<String>): String =
        notes.joinToString(",", "[", "]") { quote(it) }

    private fun bodyJson(weightKg: Double?, bodyFatPct: Double?): String =
        buildString {
            append("{")
            append("\"weightKg\":").append(weightKg?.let { num(it) } ?: "null").append(",")
            append("\"bodyFatPct\":").append(bodyFatPct?.let { num(it) } ?: "null")
            append("}")
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

    private fun num(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else String.format(Locale.US, "%.1f", value)
}
