package com.baoji.fitness.domain.algorithm

import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.domain.model.MuscleVolumeStatus
import com.baoji.fitness.domain.model.WorkoutDayDetail
import com.baoji.fitness.domain.model.WorkoutSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 将训练数据、本周容量与身体指标格式化为结构化 Markdown，
 * 供用户一键分享/转发给 AI 教练（如 Muse）。
 *
 * 纯函数设计，便于单测钉住口径一致性。
 */
object AiShareTextFormatter {

    /**
     * 构建一键分享给 AI 的结构化文本。
     *
     * @param detail 当天/该次训练的明细（含日期、标题、各动作组、备注）
     * @param includeNotes 预览弹窗中是否勾选"包含备注"
     * @param previousSameTypeVolume 上次同类型训练的有效容量（用于计算环比，null 为无历史）
     * @param weeklySessionCount 本周累计训练次数
     * @param weeklyTotalVolumeKg 本周累计有效容量
     * @param weeklyVolumeDeltaPct 本周容量环比增减（%）
     * @param weeklyMuscles 本周各部位组数监控状态（胸/背/腿/肩/臂/核心）
     * @param latestMetric 最新称重/身体数据
     * @param previousMetric 上一次称重数据（用于计算体重增减）
     */
    fun buildAiShareText(
        detail: WorkoutDayDetail,
        includeNotes: Boolean = true,
        previousSameTypeVolume: Double? = null,
        weeklySessionCount: Int? = null,
        weeklyTotalVolumeKg: Double? = null,
        weeklyVolumeDeltaPct: Double? = null,
        weeklyMuscles: List<MuscleVolumeStatus>? = null,
        latestMetric: BodyMetricEntity? = null,
        previousMetric: BodyMetricEntity? = null
    ): String {
        val sb = StringBuilder()

        // 1. 标题行：# 训练记录 2026-10-13 · 推日 (胸 肩)
        val displayTitle = cleanTitle(detail.title)
        sb.append("# 训练记录 ").append(detail.date.toString()).append(" · ").append(displayTitle).append("\n")

        // 2. 容量行：- 容量 3920kg · 13组（不含热身）· 环比上次推日 +x%
        val effectiveSets = detail.groups.flatMap { it.sets }.filter { it.isCompleted && it.setType != "WARMUP" }
        val totalVolume = effectiveSets.sumOf { it.weightKg * it.reps }
        val setCount = effectiveSets.size

        sb.append("- 容量 ").append(totalVolume.toInt()).append("kg · ")
            .append(setCount).append("组（不含热身）")

        if (previousSameTypeVolume != null && previousSameTypeVolume > 0) {
            val delta = ((totalVolume - previousSameTypeVolume) / previousSameTypeVolume * 100).roundToInt()
            val sign = if (delta >= 0) "+$delta%" else "-${abs(delta)}%"
            val typeLabel = extractTypeLabel(detail.title)
            sb.append(" · 环比上次").append(typeLabel).append(" ").append(sign)
        }
        sb.append("\n")

        // 3. 动作组明细行：- 卧推：15×20 / 12×30 / 12×40×4
        detail.groups.forEach { group ->
            val groupSets = group.sets.filter { it.isCompleted && it.setType != "WARMUP" }
            if (groupSets.isNotEmpty()) {
                val isSelfWeight = group.targetMuscle == "核心" || group.exerciseName.contains("自重") || group.exerciseName.contains("悬垂")
                sb.append("- ").append(group.exerciseName).append("：")
                sb.append(formatSetsSummary(groupSets, trackWeight = !isSelfWeight))
                sb.append("\n")
            }
        }

        // 4. 备注：- 备注：睡眠不足
        if (includeNotes && detail.notes.isNotBlank()) {
            sb.append("- 备注：").append(detail.notes.trim()).append("\n")
        }

        // 5. ## 本周
        if (weeklySessionCount != null && weeklyTotalVolumeKg != null) {
            sb.append("## 本周\n")
            sb.append("- 训练 ").append(weeklySessionCount).append(" 次 · 总容量 ")
                .append(weeklyTotalVolumeKg.toInt()).append("kg")
            if (weeklyVolumeDeltaPct != null) {
                val deltaInt = weeklyVolumeDeltaPct.roundToInt()
                val sign = if (deltaInt >= 0) "+$deltaInt%" else "-${abs(deltaInt)}%"
                sb.append(" · 环比 ").append(sign)
            }
            sb.append("\n")

            // 部位有效组状态：- 胸 6组（低于MEV）· 背 14组（MAV）
            val trainedMuscles = weeklyMuscles?.filter { it.completedSets > 0 }
            if (!trainedMuscles.isNullOrEmpty()) {
                val muscleParts = trainedMuscles.joinToString(" · ") { m ->
                    val setsDisplay = if (m.completedSets % 1.0 == 0.0) "${m.completedSets.toInt()}" else "%.1f".format(Locale.US, m.completedSets)
                    "${m.muscleGroup} ${setsDisplay}组（${m.statusText}）"
                }
                sb.append("- ").append(muscleParts).append("\n")
            }
        }

        // 6. ## 身体数据
        if (latestMetric != null && (latestMetric.weightKg != null || latestMetric.bodyFatPct != null)) {
            sb.append("## 身体数据\n- ")
            val metricParts = mutableListOf<String>()
            val metricDate = if (latestMetric.date > 100_000_000L) {
                Instant.ofEpochMilli(latestMetric.date).atZone(ZoneId.systemDefault()).toLocalDate()
            } else {
                LocalDate.ofEpochDay(latestMetric.date)
            }
            val dateStr = "%02d-%02d".format(metricDate.monthValue, metricDate.dayOfMonth)

            latestMetric.weightKg?.let { w ->
                val wFormatted = if (w % 1.0 == 0.0) "${w.toInt()}" else "%.2f".format(Locale.US, w).trimEnd('0').trimEnd('.')
                metricParts.add("体重 ${wFormatted}kg（$dateStr）")
            }
            latestMetric.bodyFatPct?.let { f ->
                val fFormatted = if (f % 1.0 == 0.0) "${f.toInt()}" else "%.1f".format(Locale.US, f)
                metricParts.add("体脂 $fFormatted%")
            }
            if (latestMetric.weightKg != null && previousMetric?.weightKg != null && previousMetric.id != latestMetric.id) {
                val diff = latestMetric.weightKg - previousMetric.weightKg
                val diffStr = if (diff >= 0) "+%.2f".format(Locale.US, diff) else "-%.2f".format(Locale.US, abs(diff))
                metricParts.add("较上次 ${diffStr.trimEnd('0').trimEnd('.')}kg")
            }
            sb.append(metricParts.joinToString(" · ")).append("\n")
        }

        return sb.toString().trimEnd()
    }

    /**
     * 将同个动作的完成组序列智能折叠：
     * 相同次数和重量的连续组折叠为：`次数×重量×组数`（如 `12×40×4`）；
     * 单组表示为：`次数×重量`（如 `15×20`）；
     * 纯自重不记重量动作表示为：`次数次`（如 `15次 / 12次×3`）。
     */
    fun formatSetsSummary(sets: List<WorkoutSet>, trackWeight: Boolean): String {
        if (sets.isEmpty()) return ""
        val chunks = mutableListOf<Pair<WorkoutSet, Int>>()
        for (set in sets) {
            val last = chunks.lastOrNull()
            if (last != null && last.first.reps == set.reps && (!trackWeight || last.first.weightKg == set.weightKg)) {
                chunks[chunks.lastIndex] = Pair(last.first, last.second + 1)
            } else {
                chunks.add(Pair(set, 1))
            }
        }

        return chunks.joinToString(" / ") { (set, count) ->
            if (!trackWeight) {
                if (count > 1) "${set.reps}次×$count" else "${set.reps}次"
            } else {
                val w = formatWeight(set.weightKg)
                if (count > 1) "${set.reps}×${w}×${count}" else "${set.reps}×${w}"
            }
        }
    }

    private fun formatWeight(weight: Double): String {
        return if (weight % 1.0 == 0.0) weight.toInt().toString()
        else String.format(Locale.US, "%.1f", weight)
    }

    private fun cleanTitle(title: String): String {
        val t = title.replace(Regex("^DAY\\s*\\d+\\s*·?\\s*"), "").trim()
        return if (t.isBlank()) title else t
    }

    private fun extractTypeLabel(title: String): String {
        return when {
            title.contains("推") -> "推日"
            title.contains("拉") -> "拉日"
            title.contains("腿") -> "腿日"
            else -> "同类型训练"
        }
    }
}
