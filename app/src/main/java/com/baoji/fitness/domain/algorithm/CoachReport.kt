package com.baoji.fitness.domain.algorithm

import com.baoji.fitness.domain.model.MuscleVolumeStatus
import com.baoji.fitness.domain.model.formatSets
import java.util.Locale

/**
 * 固定分区报告的契约：**4 个固定小标题**，客户端按标题切卡。
 *
 * 用 Markdown 而不是严格 JSON：现有的响应解析是手写的、只抓第一个 `"content"` 字符串，
 * 换成字段化 JSON 得把解析器重写。固定小标题既稳又零解析成本 ——
 * 标题对不上时整段回落到第一张卡，不会丢内容。
 */
object CoachReport {

    /** 兼容用（本周报告的分区）。真实取名请用 [sectionsFor]。 */
    val SECTIONS = listOf("总体判断", "做对的", "进步对比", "要改的", "下周计划")

    /** 报告范围：今日 / 本周 / 近 4 周。UI 上是一个分段控件。 */
    enum class Scope(val label: String) {
        TODAY("今日"),
        THIS_WEEK("本周"),
        LAST_4_WEEKS("近4周")
    }

    /** 每个范围的分区不同：今日看单次质量，本周看部位/节奏，近 4 周看趋势。 */
    fun sectionsFor(scope: Scope): List<String> = when (scope) {
        Scope.TODAY -> listOf("今天做了什么", "练得怎样", "进步对比", "下次注意")
        Scope.THIS_WEEK -> listOf("总体判断", "做对的", "进步对比", "要改的", "下周计划")
        Scope.LAST_4_WEEKS -> listOf("总体趋势", "做对的", "要改的", "下阶段计划")
    }

    private const val BODY_RULES =
        "如果给了 body（身高/体重/体脂率/BMI），必须把训练量、容量趋势与体重体脂一起看：" +
            "体重上升时关注体脂是否同涨（增肌还是增脂）、体重下降时提醒保蛋白与保力量（别掉肌肉）。"

    private const val PROGRESS_RULES =
        "**进步对比（重点）**：`exerciseProgress` 与 `muscleProgress` 是相对“上次练它”的变化。\n" +
            "- 对每个有数据的动作，必须明确说进步/持平/退步，并引用具体数字" +
            "（如「卧推 80kg×8 → 82.5kg×7，e1RM +2.4%」、「划船 持平」）。\n" +
            "- 判定用 e1rmDeltaPct（不是只看重量）：重量不变但次数变多也算进步。\n" +
            "- 对退步的（e1rmDeltaPct < 0）要给出原因推测与调整（降重量/补组数/排查睡眠）。\n" +
            "- 对没列在 exerciseProgress 里的动作说明“首次记录，暂无对比”，不要编一个上次成绩。"

    private fun systemPromptFor(scope: Scope): String {
        val sections = sectionsFor(scope)
        val headings = sections.joinToString("\n") { "## $it" }
        val scopeRule = when (scope) {
            CoachReport.Scope.TODAY ->
                "这是**今日单次训练**分析。你要看的是 `today` 字段（今天那一场）与 `muscleVolumes`/`totalVolumeKg`/`weeklyVolume`，" +
                    "**本次统计窗口就是今天这一天，这些数字都是今天的，不是本周的**。\n" +
                    "评价这次练的完成质量（重量是否偏重导致掉次数、组间休息、总容量够不够），" +
                    "**并与上次同动作/同肌群对比，说清进步或退步**（见 progress 数据），" +
                    "再给下一次同部位的具体调整。不要评价“本周趋势”，那是另一份报告。"
            CoachReport.Scope.THIS_WEEK ->
                "这是**本周**分析：结合各部位有效组数 vs MEV/MAV/MRV、推拉平衡、本周容量环比，" +
                    "**逐个说清各动作相对上次的进步或退步**（见 progress 数据），" +
                    "点名欠练或过量的部位，给下周安排。"
            CoachReport.Scope.LAST_4_WEEKS ->
                "这是**近 4 周**分析：重点看容量趋势斜率、停滞动作、体重/体脂变化与训练量的关系，" +
                    "判断是在稳步进步还是到了该减量（Deload）的节点。"
        }
        return "你是一名力量训练教练，服务于一个中文健身记录 App 的用户。" +
            "用户会给你一份**已经算好的训练事实**（JSON）。这些数字是可信的，不要重新计算、不要质疑。\n" +
            BODY_RULES + "\n" +
            PROGRESS_RULES + "\n" +
            scopeRule + "\n" +
            "输出要求：\n" +
            "- 只输出简体中文，不要客套话、不要复述原始数据。\n" +
            "- 严格使用这 ${sections.size} 个 Markdown 二级标题，顺序不变，每个标题下 2~4 句话：\n" +
            headings + "\n" +
            "- 必须点名具体动作与数字（如「卧推停滞 3 次，建议降 10% 到 72.5kg」）。\n" +
            "- 计划类分区的建议要具体到重量/组数/次数/频率，不要笼统说「继续努力」。\n" +
            "- 如果某个分区没有数据支撑，就直说还缺什么数据，不要编。"
    }

    fun systemPrompt(scope: Scope): String = systemPromptFor(scope)

    /** 兼容旧调用：默认本周。 */
    val SYSTEM_PROMPT: String get() = systemPromptFor(Scope.THIS_WEEK)

    fun userPrompt(factsJson: String, scope: Scope = Scope.THIS_WEEK): String =
        "我的训练事实（JSON）：\n$factsJson\n\n" +
            "请按上述 ${sectionsFor(scope).size} 个分区给出「${scope.label}」的完整分析。"

    /**
     * 把模型返回的 markdown 按**该范围的分区**切开。
     * 任何一个标题都没匹配到 → 整段当作第一张卡，绝不丢内容。
     */
    fun splitSections(markdown: String, scope: Scope = Scope.THIS_WEEK): List<ReportSection> {
        val titles = sectionsFor(scope)
        val text = markdown.trim()
        if (text.isEmpty()) return emptyList()

        val indices = titles.mapNotNull { title ->
            val idx = text.indexOf("## $title")
            if (idx >= 0) title to idx else null
        }.sortedBy { it.second }

        if (indices.isEmpty()) {
            return listOf(ReportSection(titles.first(), text))
        }

        return indices.mapIndexed { i, (title, start) ->
            val headingEnd = text.indexOf('\n', start).let { if (it < 0) text.length else it + 1 }
            val end = indices.getOrNull(i + 1)?.second ?: text.length
            ReportSection(title, text.substring(headingEnd, end).trim())
        }.filter { it.body.isNotBlank() }
    }

    /**
     * 事实 → 给模型看的 JSON。字段名保持英文、值保持数字，模型读起来最不易误解。
     * 手写 JSON（与 [com.baoji.fitness.data.llm.CoachContextPacker] 同风格），
     * 结构只有一层半，不引序列化框架。
     */
    fun factsToJson(f: TrainingFacts, today: TodayWorkout? = null): String = buildString {
        append("{")
        // 明确声明统计窗口，否则模型会把本日数字说成本周（真实发生过的 bug）
        append("\"window\":${quote(if (f.weeksCovered == 0) "今日（仅今天这一天）" else "近 ${f.weeksCovered} 周")},")
        append("\"weeks\":").append(f.weeksCovered).append(",")
        append("\"sessions\":").append(f.sessionCount).append(",")
        append("\"totalVolumeKg\":").append(num(f.totalVolumeKg)).append(",")
        append("\"volumeDeltaPct\":").append(f.volumeDeltaPct?.let { num(it) } ?: "null").append(",")
        append("\"weeklyVolume\":").append(
            f.weeklyVolume.joinToString(",", "[", "]") { (label, v) ->
                "{\"week\":${quote(label)},\"volumeKg\":${num(v)}}"
            }
        ).append(",")
        append("\"muscles\":").append(
            f.muscles.joinToString(",", "[", "]") { m ->
                "{\"muscle\":${quote(m.muscleGroup)},\"sets\":${formatSets(m.completedSets)}," +
                    "\"mev\":${m.mev},\"mav\":${m.mav},\"mrv\":${m.mrv},\"status\":${quote(m.statusText)}}"
            }
        ).append(",")
        append("\"pushPullSets\":").append(
            f.pushPullSets.entries.joinToString(",", "{", "}") { (k, v) -> "${quote(k)}:${num(v)}" }
        ).append(",")
        append("\"stalls\":").append(
            f.stalls.joinToString(",", "[", "]") { s ->
                "{\"exercise\":${quote(s.exercise)},\"sessionsFlat\":${s.sessionsFlat}," +
                    "\"lastWeightKg\":${num(s.lastWeightKg)},\"lastReps\":${s.lastReps}," +
                    "\"lastE1rm\":${num(s.lastE1rm)},\"recentE1rm\":${s.trendLast3.joinToString(",", "[", "]") { num(it) }}}"
            }
        ).append(",")
        append("\"rpeDrift\":").append(
            f.rpeDrift.joinToString(",", "[", "]") { r ->
                "{\"exercise\":${quote(r.exercise)},\"weightKg\":${num(r.weightKg)}," +
                    "\"earlierRpe\":${num(r.earlierRpe)},\"recentRpe\":${num(r.recentRpe)}}"
            }
        ).append(",")
        append("\"setFade\":").append(
            f.setFade.joinToString(",", "[", "]") { s ->
                "{\"exercise\":${quote(s.exercise)},\"firstSetReps\":${s.firstSetReps}," +
                    "\"lastSetReps\":${s.lastSetReps},\"fadePct\":${num(s.fadePct)}}"
            }
        ).append(",")
        append("\"recentNotes\":").append(f.recentNotes.joinToString(",", "[", "]") { quote(it) }).append(",")
        append("\"exerciseProgress\":").append(
            f.exerciseProgress.joinToString(",", "[", "]") { p ->
                "{\"exercise\":${quote(p.exercise)},\"prevDate\":${quote(p.prevDate.toString())}," +
                    "\"prevTop\":${quote("${num(p.prevTopWeightKg)}kg x ${p.prevTopReps}")}," +
                    "\"curTop\":${quote("${num(p.curTopWeightKg)}kg x ${p.curTopReps}")}," +
                    "\"prevE1rm\":${num(p.prevE1rm)},\"curE1rm\":${num(p.curE1rm)}," +
                    "\"e1rmDeltaPct\":${num(p.e1rmDeltaPct)},\"improved\":${p.improved}}"
            }
        ).append(",")
        append("\"muscleProgress\":").append(
            f.muscleProgress.joinToString(",", "[", "]") { p ->
                "{\"muscle\":${quote(p.muscle)},\"prevDate\":${quote(p.prevDate.toString())}," +
                    "\"prevSets\":${formatSets(p.prevSets)},\"curSets\":${formatSets(p.curSets)}," +
                    "\"deltaSets\":${formatSets(p.deltaSets)}}"
            }
        ).append(",")
        append("\"weightKg\":").append(f.latestWeightKg?.let { num(it) } ?: "null").append(",")
        append("\"streakDays\":").append(f.streakDays).append(",")
        append("\"body\":").append(bodyJson(f.body)).append(",")
        append("\"today\":").append(todayJson(today))
        append("}")
    }

    private fun bodyJson(b: BodyProfile): String = buildString {
        append("{")
        append("\"weightKg\":").append(b.weightKg?.let { num(it) } ?: "null").append(",")
        append("\"heightCm\":").append(b.heightCm?.let { num(it) } ?: "null").append(",")
        append("\"bodyFatPct\":").append(b.bodyFatPct?.let { num(it) } ?: "null").append(",")
        append("\"bmi\":").append(b.bmi?.let { num(it) } ?: "null").append(",")
        append("\"waistCm\":").append(b.waistCm?.let { num(it) } ?: "null").append(",")
        append("\"armCm\":").append(b.armCm?.let { num(it) } ?: "null").append(",")
        append("\"weightDeltaKg\":").append(b.weightDeltaKg?.let { num(it) } ?: "null").append(",")
        append("\"bodyFatDeltaPct\":").append(b.bodyFatDeltaPct?.let { num(it) } ?: "null")
        append("}")
    }

    private fun todayJson(t: TodayWorkout?): String {
        if (t == null) return "null"
        return buildString {
            append("{")
            append("\"date\":").append(quote(t.dateLabel)).append(",")
            append("\"title\":").append(quote(t.title)).append(",")
            append("\"volumeKg\":").append(num(t.volumeKg)).append(",")
            append("\"completedSets\":").append(t.completedSets).append(",")
            append("\"durationMinutes\":").append(t.durationMinutes ?: 0).append(",")
            append("\"notes\":").append(quote(t.notes)).append(",")
            append("\"exercises\":").append(
                t.exercises.joinToString(",", "[", "]") { ex ->
                    "{\"name\":${quote(ex.name)},\"sets\":[" +
                        ex.sets.joinToString(",") { s ->
                            "{\"weightKg\":${num(s.weightKg)},\"reps\":${s.reps}," +
                                "\"type\":${quote(s.type)},\"rpe\":${s.rpe?.let { num(it) } ?: "null"}}"
                        } + "]}"
                }
            )
            append("}")
        }
    }

    private fun quote(raw: String): String = buildString {
        append('"')
        for (ch in raw) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }

    private fun num(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else String.format(Locale.US, "%.1f", value)
}

data class ReportSection(val title: String, val body: String)