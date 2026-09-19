package com.baoji.fitness.domain.algorithm

import com.baoji.fitness.domain.model.MuscleVolumeStatus
import java.util.Locale

/**
 * 私教本地简报的输入。全部来自已有数据，零网络。
 */
data class CoachInput(
    val label: String = "本周",
    val sessionCount: Int = 0,
    val volumeDeltaPct: Double? = null,
    val totalVolumeKg: Double = 0.0,
    val streakDays: Int = 0,
    /**
     * 本周计划里还剩几天没练。**null = 没有计划信息**（不要和 0「计划已排满」混为一谈：
     * 默认值如果是 0，任何不传这个字段的调用方都会被误判成本周已经练完）。
     */
    val remainingPlannedDays: Int? = null,
    val muscleVolumes: List<MuscleVolumeStatus> = emptyList(),
    /** 最近的训练备注，最新的在前。 */
    val recentNotes: List<String> = emptyList(),
    val latestWeightKg: Double? = null
)

/** 逐行输出的简报，便于快照断言与 UI 逐条渲染。 */
data class CoachBriefing(val lines: List<String>) {
    val text: String get() = lines.joinToString(" ")
    val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * M4a：**不接任何模型**，用已有数据生成一句人话总结。
 *
 * 这一步就完成了「私教有价值」的 60%，且零网络、零费用、零隐私问题；
 * M4b 的真 LLM 不可用（断网 / 没配 Key / 用户关了共享）时也降级回到这里。
 */
object CoachNarrator {

    fun brief(input: CoachInput): CoachBriefing {
        val lines = mutableListOf<String>()

        // 1. 连续性（连续 3 天以上才值得提一句）
        if (input.streakDays >= 3) {
            lines += "已经连续训练 ${input.streakDays} 天，节奏稳。"
        }

        // 2. 周期容量
        lines += volumeLine(input)

        // 3. 部位容量：挑最欠练的和已经到位的
        muscleLine(input)?.let { lines += it }

        // 4. 下一次建议
        lines += suggestionLine(input)

        // 5. 状态/伤病提示（读 M3a 的备注）
        lines += noteLine(input)

        if (input.latestWeightKg != null) {
            lines += "最近体重 ${trim(input.latestWeightKg)} kg，配合容量趋势一起看更有意义。"
        }

        return CoachBriefing(lines)
    }

    private fun volumeLine(input: CoachInput): String {
        if (input.sessionCount == 0) {
            return "${input.label}还没有训练记录 —— 先完成一次，我才有东西可分析。"
        }
        val base = "${input.label}练了 ${input.sessionCount} 次，累计容量 ${input.totalVolumeKg.toInt()} kg"
        val delta = input.volumeDeltaPct
        return when {
            delta == null -> "$base，上一周期没有可比数据。"
            delta >= 1.0 -> "$base，容量环比 +${delta.toInt()}%。"
            delta <= -1.0 -> "$base，容量环比 ${delta.toInt()}% —— 掉得有点多，先看看本周是不是少练了一天。"
            else -> "$base，容量与上一周期基本持平。"
        }
    }

    private fun muscleLine(input: CoachInput): String? {
        if (input.muscleVolumes.isEmpty()) return null

        // 「一组都没练」也算欠练，而且是最欠的那种 —— 不能因为它是 0 就过滤掉
        val lagging = input.muscleVolumes
            .filter { it.completedSets < it.mev }
            .minByOrNull { it.completedSets - it.mev }
        val onTarget = input.muscleVolumes
            .filter { it.completedSets in it.mev..it.mav }
            .maxByOrNull { it.completedSets }

        val parts = mutableListOf<String>()
        lagging?.let {
            parts += if (it.completedSets == 0) {
                "${it.muscleGroup}这周一组都没练（MEV ${it.mev}）"
            } else {
                "${it.muscleGroup}只有 ${it.completedSets} 组，低于 MEV(${it.mev})"
            }
        }
        onTarget?.let { parts += "${it.muscleGroup} ${it.completedSets} 组正处在 MAV 区间" }
        if (parts.isEmpty()) {
            val over = input.muscleVolumes.maxByOrNull { it.completedSets } ?: return null
            parts += "${over.muscleGroup} ${over.completedSets} 组已经顶到 MRV(${over.mrv}) 附近，注意别过量"
        }
        return parts.joinToString("；") + "。"
    }

    private fun suggestionLine(input: CoachInput): String {
        if (input.sessionCount == 0) {
            return "从任意一个训练日开始都行，先把数据填上。"
        }
        if (input.remainingPlannedDays == 0) {
            return "本周计划已经排满，剩下的时间留给恢复。"
        }
        val mostDeficient = input.muscleVolumes
            .filter { it.completedSets < it.mev }
            .minByOrNull { it.completedSets - it.mev }

        if (mostDeficient != null) {
            val day = trainingDayFor(mostDeficient.muscleGroup)
            return "下一次建议安排$day，优先补${mostDeficient.muscleGroup}。"
        }

        val over = input.muscleVolumes.filter { it.completedSets > it.mav }
        if (over.isNotEmpty()) {
            return "各部位基本都到位了，下一次可以减量（Deload）或者换个动作角度重新刺激。"
        }
        return "各部位都在有效区间内，下一次按原计划推进就好。"
    }

    private fun noteLine(input: CoachInput): String {
        val note = input.recentNotes.firstOrNull()
            ?: return "训练后在完成弹窗里记一句感受，我下次能给出更准的建议。"

        return when {
            note.contains("睡眠") ->
                "上次备注「$note」，若今晚仍睡不好，主项重量下调一档。"
            note.contains("累") || note.contains("疲劳") ->
                "上次备注「$note」，先做 2 组试探，状态回来再加回去。"
            note.contains("痛") || note.contains("疼") || note.contains("伤") ->
                "上次备注提到「$note」，相关动作先降重量、缩小行程，别硬顶。"
            note.contains("好") ->
                "上次备注「$note」，可以按计划冲击加重。"
            else ->
                "上次备注「$note」，按原计划推进即可。"
        }
    }

    private fun trainingDayFor(muscle: String): String = when (muscle) {
        "胸部", "肩部" -> "推日"
        "背部" -> "拉日"
        "腿部" -> "腿日"
        "手臂" -> "拉日"
        else -> "训练日"
    }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)
}
