package com.baoji.fitness.domain.algorithm

/**
 * 动作 → 肌群的分类。**LLM 提示词与离线关键词兜底共用这一份规则**，
 * 避免两条路给出不同答案。
 *
 * 为什么必须约束：`PeriodReviewCalculator.muscleSetCounts` 只统计
 * [PeriodReviewCalculator.TRACKED_MUSCLES] 里的 6 个大类，
 * 归类跑到这些之外（如「全身」）该动作的组数就会被静默丢弃。
 */
object MuscleClassifier {

    /** 主肌群只能落在这 6 个里 —— 与部位容量统计同一口径。 */
    val PRIMARY_MUSCLES = PeriodReviewCalculator.TRACKED_MUSCLES

    val EQUIPMENT_OPTIONS = listOf("杠铃", "哑铃", "绳索", "史密斯", "机械器械", "自重", "其他")

    /** 给模型的系统提示词：要求严格 JSON，主肌群限定枚举。 */
    val SYSTEM_PROMPT: String =
        "你是一名力量训练专家，服务于一个中文健身记录 App。" +
            "用户会给你一个训练动作名称，你要判断它主要练哪些肌群。\n" +
            "严格要求：\n" +
            "- 只输出一个 JSON 对象，不要 markdown 代码块、不要任何解释。\n" +
            "- 字段：primary（主肌群，字符串）、secondary（间接肌群，字符串数组，可为空）、" +
            "equipment（器械，字符串）、trackWeight（是否记录重量，布尔）。\n" +
            "- primary 必须**从这 6 个里选且只选一个**：胸部、背部、腿部、肩部、手臂、核心。\n" +
            "- secondary 只能用细分肌群名（如 肱三头肌、三角肌前束、臀大肌、腹直肌），最多 3 个。\n" +
            "- 自重动作（如平板支撑、悬垂举腿）trackWeight = false，其余为 true。\n" +
            "示例输出：{\"primary\":\"背部\",\"secondary\":[\"肱二头肌\",\"三角肌后束\"],\"equipment\":\"绳索\",\"trackWeight\":true}"

    fun userPrompt(exerciseName: String): String = "动作名称：$exerciseName"

    /** 模型可能返回带 markdown 围栏的 JSON，剥掉再解析。 */
    fun sanitizeJson(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```").trimStart()
            val end = s.lastIndexOf("```")
            if (end >= 0) s = s.substring(0, end)
        }
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        return if (start >= 0 && end > start) s.substring(start, end + 1) else s
    }

    /**
     * 解析模型返回的分类结果。任何解析失败都返回 null，由调用方回落关键词兜底。
     * 手写取值而非引序列化框架 —— 结构只有一层，且主字段是受控枚举。
     */
    fun parse(raw: String): Classification? {
        val json = sanitizeJson(raw)
        val primary = readString(json, "primary")
            ?.let { normalizePrimary(it) } ?: return null
        val secondary = readStringArray(json, "secondary")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
        return Classification(
            primary = primary,
            secondary = secondary,
            equipment = readString(json, "equipment")?.trim()?.ifBlank { null },
            trackWeight = readBoolean(json, "trackWeight")
        )
    }

    data class Classification(
        val primary: String,
        val secondary: List<String> = emptyList(),
        val equipment: String? = null,
        /** null = 模型没说，由调用方决定默认值。 */
        val trackWeight: Boolean? = null
    )

    /** 把模型给出的肌群名归一到 6 大类；认不出返回 null（宁可兜底也不要脏数据）。 */
    fun normalizePrimary(raw: String): String? {
        val name = raw.trim()
        if (name.isBlank()) return null
        PRIMARY_MUSCLES.firstOrNull { it == name }?.let { return it }
        // 模型可能给细分名（"肱三头肌"），借既有桥接表归一
        PeriodReviewCalculator.secondaryMuscleGroup(name)?.let { return it }
        return null
    }

    /**
     * 离线兜底：关键词匹配。**LLM 不可用/未配 Key 时用**，不阻塞用户新建动作。
     * 顺序很关键：**先判特殊词，再判宽泛词**。
     * 如「悬垂举腿」含「腿」但不是练腿 —— 若先判「腿」就会错归腿部。
     */
    fun classifyOffline(name: String): Classification {
        val n = name.trim()
        val primary = when {
            // 核心必须先于腿部（“举腿/抬腿”含“腿”字）
            n.contains("卷腹") || n.contains("举腿") || n.contains("抬腿") ||
                n.contains("平板") || n.contains("腹") || n.contains("核心") -> "核心"
            n.contains("卧推") || n.contains("夹胸") -> "胸部"
            n.contains("胸") -> "胸部"
            n.contains("硬拉") || n.contains("引体") || n.contains("划船") ||
                n.contains("下拉") || n.contains("背") || n.contains("耸肩") -> "背部"
            n.contains("深蹲") || n.contains("腿") || n.contains("臀推") ||
                n.contains("提踵") || n.contains("弓步") || n.contains("小腿") -> "腿部"
            n.contains("推举") || n.contains("推肩") || n.contains("侧平") ||
                n.contains("面拉") || n.contains("飞鸟") || n.contains("肩") -> "肩部"
            n.contains("弯举") || n.contains("臂屈伸") || n.contains("下压") ||
                n.contains("二头") || n.contains("三头") || n.contains("手臂") -> "手臂"
            else -> "背部"
        }
        // 结果必须落在追踪大类内，否则该动作组数会被静默丢弃
        val safePrimary = if (primary in PRIMARY_MUSCLES) primary else "背部"
        val equipment = when {
            n.contains("史密斯") -> "史密斯"
            n.contains("哑铃") -> "哑铃"
            n.contains("绳索") || n.contains("龙门") || n.contains("面拉") -> "绳索"
            n.contains("杠铃") || n.contains("硬拉") || n.contains("深蹲") -> "杠铃"
            n.contains("器械") || n.contains("机") -> "机械器械"
            n.contains("悬垂") || n.contains("平板") || n.contains("自重") -> "自重"
            else -> "其他"
        }
        val selfWeight = n.contains("悬垂") || n.contains("平板") || n.contains("自重") ||
            n.contains("卷腹") || n.contains("举腿")
        return Classification(
            primary = safePrimary,
            secondary = secondaryHints(n),
            equipment = equipment,
            trackWeight = !selfWeight
        )
    }

    private fun secondaryHints(n: String): List<String> = buildList {
        if (n.contains("卧推") || n.contains("推举")) add("肱三头肌")
        if (n.contains("卧推") || n.contains("推举")) add("三角肌前束")
        if (n.contains("划船") || n.contains("下拉") || n.contains("引体")) add("肱二头肌")
        if (n.contains("深蹲") || n.contains("硬拉")) add("臀大肌")
        if (n.contains("卷腹") || n.contains("举腿") || n.contains("平板")) add("腹直肌")
    }.take(3)

    // ---- 极简 JSON 取值（与 CoachLlmClient 手写解析同风格）-------------------

    private fun readString(json: String, key: String): String? {
        val i = json.indexOf("\"$key\"")
        if (i < 0) return null
        var j = json.indexOf(':', i + key.length + 2)
        if (j < 0) return null
        j++
        while (j < json.length && json[j].isWhitespace()) j++
        if (j >= json.length || json[j] != '"') return null
        val sb = StringBuilder()
        var k = j + 1
        while (k < json.length) {
            when (val c = json[k]) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (k + 1 >= json.length) return sb.toString()
                    when (json[k + 1]) {
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        '"' -> sb.append('"'); '\\' -> sb.append('\\')
                        else -> sb.append(json[k + 1])
                    }
                    k += 2
                }
                else -> { sb.append(c); k++ }
            }
        }
        return sb.toString()
    }

    private fun readBoolean(json: String, key: String): Boolean? {
        val i = json.indexOf("\"$key\"")
        if (i < 0) return null
        val j = json.indexOf(':', i + key.length + 2)
        if (j < 0) return null
        val rest = json.substring(j + 1).trimStart()
        return when {
            rest.startsWith("true") -> true
            rest.startsWith("false") -> false
            else -> null
        }
    }

    private fun readStringArray(json: String, key: String): List<String> {
        val i = json.indexOf("\"$key\"")
        if (i < 0) return emptyList()
        val open = json.indexOf('[', i)
        if (open < 0) return emptyList()
        val close = json.indexOf(']', open)
        if (close < 0) return emptyList()
        val body = json.substring(open + 1, close)
        return Regex("\"([^\"]*)\"").findAll(body).map { it.groupValues[1] }.toList()
    }
}