package com.baoji.fitness.domain.algorithm

import com.baoji.fitness.domain.model.MuscleVolumeStatus
import com.baoji.fitness.domain.model.formatSets

/**
 * 零网络的固定分区报告 —— 断网 / 没配 Key / 用户关了共享时的降级路径。
 *
 * 它和 LLM 报告**同一套分区、同一批事实**，所以两边观感一致，
 * 区别只是写的句子是模板还是模型生成的。这保证「私教永远有东西给你看」。
 */
object CoachingLocalReport {

    /** 按范围出本地降级报告 —— 分区与 LLM 报告完全一致。 */
    fun build(
        f: TrainingFacts,
        today: TodayWorkout? = null,
        scope: CoachReport.Scope = CoachReport.Scope.THIS_WEEK
    ): String = when (scope) {
        CoachReport.Scope.TODAY -> todayReport(f, today)
        CoachReport.Scope.THIS_WEEK -> weeklyReport(f)
        CoachReport.Scope.LAST_4_WEEKS -> trendReport(f)
    }

    private fun todayReport(f: TrainingFacts, t: TodayWorkout?): String {
        val sections = CoachReport.sectionsFor(CoachReport.Scope.TODAY)
        if (t == null) {
            return "## ${sections[0]}\n还没有训练记录。\n\n" +
                "## ${sections[1]}\n先完成一次训练，我才有东西可分析。\n\n" +
                "## ${sections[2]}\n首次记录，暂无对比。\n\n" +
                "## ${sections[3]}\n从任意一个训练日开始都行。"
        }
        val mostFaded = f.setFade.firstOrNull()
        val what = t.exercises.joinToString("、") { ex ->
            val top = ex.sets.filter { it.type != "WARMUP" }.maxByOrNull { it.weightKg }
            if (top == null) "${ex.name}(自重)" else "${ex.name} ${trim(top.weightKg)}kg×${top.reps}"
        }
        return buildString {
            append("## ${sections[0]}\n")
            append("${t.title}，`${t.dateLabel}`，共 ${t.completedSets} 组、容量 ${t.volumeKg.toInt()} kg")
            t.durationMinutes?.takeIf { it > 0 }?.let { append("，用时 ${it} 分钟") }
            append("。动作：$what。\n\n")
            append("## ${sections[1]}\n")
            append(
                when {
                    mostFaded != null ->
                        "有训练量，但 ${mostFaded.exercise} 组间掉得较多，整体偏“拼重量”。"
                    t.completedSets >= 12 -> "组数够量（${t.completedSets} 组），完成度不错。"
                    else -> "完成了 ${t.completedSets} 组，量适中；关键在于稳住渐进。"
                }
            )
            append(bodyLine(f.body))
            append("\n\n## ${sections[2]}\n")
            append(progressLine(f))
            append("\n\n## ${sections[3]}\n")
            append(
                mostFaded?.let {
                    "${it.exercise} 降一档重量（约 -5%）保质量，宁少也标准。"
                } ?: if (t.notes.isNotBlank()) "今天备注「${t.notes}」，按状态决定下次是否加重。"
                else "下次同部位：首组达标就 +2.5kg，目标 6 次。"
            )
        }
    }

    /**
     * 进步对比（本地版）：逐个动作说清相对上次是进步/持平/退步。
     * 与 LLM 报告的「进步对比」分区同一数据源（`exerciseProgress`）。
     */
    private fun progressLine(f: TrainingFacts): String {
        if (f.exerciseProgress.isEmpty()) {
            return "这些动作都是首次记录，还没有上次可对比的基准。"
        }
        val parts = f.exerciseProgress.take(6).map { p ->
            val arrow = when {
                p.improved -> "↑"
                p.e1rmDelta < -0.05 -> "↓"
                else -> "→"
            }
            "$arrow ${p.exercise} ${trim(p.prevTopWeightKg)}kg×${p.prevTopReps} → " +
                "${trim(p.curTopWeightKg)}kg×${p.curTopReps}（e1RM ${signed(p.e1rmDeltaPct)}%）"
        }
        val improvedCount = f.exerciseProgress.count { it.improved }
        val regressed = f.exerciseProgress.filter { it.e1rmDelta < -0.05 }
        return buildString {
            append(parts.joinToString("；"))
            append("。")
            append("其中 ${improvedCount}/${f.exerciseProgress.size} 个动作有进步。")
            if (regressed.isNotEmpty()) {
                append("退步的：${regressed.joinToString("、") { it.exercise }}，先查睡眠与总组数，再考虑降重量重来。")
            }
            val muscleMoves = f.muscleProgress.filter { kotlin.math.abs(it.deltaSets) >= 1.0 }
            if (muscleMoves.isNotEmpty()) {
                append("肌群组数变化：")
                append(muscleMoves.joinToString("、") { "${it.muscle} ${signed(it.deltaSets)} 组" })
                append("。")
            }
        }
    }

    private fun signed(v: Double): String = if (v >= 0) "+${trim(v)}" else trim(v)

    private fun weeklyReport(f: TrainingFacts): String = buildString {
        append("## 总体判断\n").append(verdict(f)).append("\n\n")
        append("## 做对的\n").append(good(f)).append("\n\n")
        append("## 进步对比\n").append(progressLine(f)).append("\n\n")
        append("## 要改的\n").append(fix(f)).append(bodyLine(f.body)).append("\n\n")
        append("## 下周计划\n").append(plan(f))
    }

    private fun trendReport(f: TrainingFacts): String {
        val sections = CoachReport.sectionsFor(CoachReport.Scope.LAST_4_WEEKS)
        return buildString {
            append("## ${sections[0]}\n").append(verdict(f)).append(bodyLine(f.body)).append("\n\n")
            append("## ${sections[1]}\n").append(good(f)).append("\n\n")
            append("## ${sections[2]}\n").append(fix(f)).append("\n\n")
            append("## ${sections[3]}\n").append(plan(f))
        }
    }

    /** 身体画像一句话：体重/体脂的变化方向已由分析器算好，这里只翻译。 */
    private fun bodyLine(b: BodyProfile): String {
        if (b.weightKg == null && b.bodyFatPct == null) return ""
        val parts = mutableListOf<String>()
        b.bmi?.let { parts += "BMI ${trim(it)}" }
        b.weightDeltaKg?.let {
            parts += if (it > 0.2) "体重 +${trim(it)}kg" else if (it < -0.2) "体重 ${trim(it)}kg" else "体重持平"
        }
        b.bodyFatDeltaPct?.let {
            parts += if (it > 0.2) "体脂 +${trim(it)}%" else if (it < -0.2) "体脂 ${trim(it)}%" else "体脂持平"
        }
        if (parts.isEmpty()) return ""
        return "\n\n身体：${parts.joinToString("、")}。"
    }

    private fun verdict(f: TrainingFacts): String {
        // weeksCovered == 0 表示单日（今日）模式 —— 措辞不能说成“近 N 周”
        val isToday = f.weeksCovered == 0
        val scopeWord = if (isToday) "今天" else "近 ${f.weeksCovered} 周"
        if (f.sessionCount == 0) {
            return "${scopeWord}还没有完整训练记录，先练起来我才有东西可分析。"
        }
        val volume = when (val d = f.volumeDeltaPct) {
            null -> if (isToday) "容量 ${f.totalVolumeKg.toInt()} kg"
            else "容量 ${f.totalVolumeKg.toInt()} kg（上期无可比数据）"
            in 1.0..Double.MAX_VALUE -> "容量 ${f.totalVolumeKg.toInt()} kg，环比 +${d.toInt()}%"
            in Double.NEGATIVE_INFINITY..-1.0 -> "容量 ${f.totalVolumeKg.toInt()} kg，环比 ${d.toInt()}%，掉得有点多"
            else -> "容量 ${f.totalVolumeKg.toInt()} kg，与上期基本持平"
        }
        val lagging = f.muscles.minByOrNull { it.completedSets - it.mev }
        val lagText = lagging?.let {
            if (it.completedSets < it.mev) {
                "最欠练的是${it.muscleGroup}（${formatSets(it.completedSets)} 组，MEV ${it.mev}）"
            } else "各部位都过了最低有效容量"
        } ?: ""
        return "${scopeWord}练了 ${f.sessionCount} 次，$volume。$lagText。"
    }

    private fun good(f: TrainingFacts): String {
        val parts = mutableListOf<String>()
        f.muscles.filter { it.completedSets >= it.mev }
            .maxByOrNull { it.completedSets }
            ?.let { parts += "${it.muscleGroup} ${formatSets(it.completedSets)} 组，已进 MAV 区间，刺激够量" }
        if (f.streakDays >= 2) parts += "已连续训练 ${f.streakDays} 天，节奏没断"
        val push = f.pushPullSets["推"] ?: 0.0
        val pull = f.pushPullSets["拉"] ?: 0.0
        if (pull > 0 && pull >= push) parts += "拉日组数（${pull.toInt()}）不少于推日（${push.toInt()}），肩背平衡没问题"
        if (parts.isEmpty()) parts += "数据还少，先把训练节奏稳定下来就是最大的进步"
        return parts.joinToString("；") + "。"
    }

    private fun fix(f: TrainingFacts): String {
        val parts = mutableListOf<String>()
        f.stalls.forEach {
            val drop = it.lastWeightKg * 0.9
            val suggested = (kotlin.math.round(drop / 2.5) * 2.5)
            parts += "${it.exercise} 连续 ${it.sessionsFlat} 次没进步（e1RM 约 ${it.lastE1rm} kg），" +
                "建议降到 ${trim(suggested)} kg 做两周背载（试试 3 组 12 次）"
        }
        f.rpeDrift.forEach {
            parts += "${it.exercise} 同样 ${trim(it.weightKg)} kg，RPE 从 ${trim(it.earlierRpe)} 涨到 ${trim(it.recentRpe)}，疲劳累积了，这周别加重"
        }
        f.setFade.forEach {
            parts += "${it.exercise} 组内掉得太快（首组 ${it.firstSetReps} 次 → 末组 ${it.lastSetReps} 次），重量偏重，降一档保质量"
        }
        f.muscles.filter { it.completedSets < it.mev }.forEach {
            parts += "${it.muscleGroup} 只有 ${formatSets(it.completedSets)} 组，低于 MEV(${it.mev})，欠练"
        }
        val push = f.pushPullSets["推"] ?: 0.0
        val pull = f.pushPullSets["拉"] ?: 0.0
        if (push > 0 && pull > 0 && push / pull >= 1.5) {
            parts += "推日组数是拉日的 ${trim(push / pull)} 倍，推拉失衡，多加练面拉/划船护肩"
        }
        if (parts.isEmpty()) parts += "没发现明显问题，按当前计划推进即可"
        return parts.joinToString("；") + "。"
    }

    private fun plan(f: TrainingFacts): String {
        if (f.sessionCount == 0) return "先从任意一个训练日开始，把数据填上。"
        val lagging = f.muscles.filter { it.completedSets < it.mev }.minByOrNull { it.completedSets - it.mev }
        if (lagging != null) {
            val day = when (lagging.muscleGroup) {
                "胸部", "肩部" -> "推日"
                "背部", "手臂" -> "拉日"
                "腿部" -> "腿日"
                else -> "训练日"
            }
            return "下次优先安排$day，补${lagging.muscleGroup}，其余动作维持原重量，达标了再 +2.5kg。"
        }
        val over = f.muscles.filter { it.completedSets > it.mav }
        if (over.isNotEmpty()) {
            return "各部位都在有效区间，下周可以减量一周（Deload，重量降 10%、组数减半）再冲下一个周期。"
        }
        return "按原计划推进，每个动作达标（如 8 次）后下次首组 +2.5kg，目标 6 次。"
    }

    private fun trim(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
}