package com.baoji.fitness.domain.algorithm

/**
 * 动作 → 肌群的离线规则分类。
 *
 * 为什么必须约束：`PeriodReviewCalculator.muscleSetCounts` 只统计
 * [PeriodReviewCalculator.TRACKED_MUSCLES] 里的 6 个大类，
 * 归类跑到这些之外（如「全身」）该动作的组数就会被静默丢弃。
 */
object MuscleClassifier {

    /** 主肌群只能落在这 6 个里 —— 与部位容量统计同一口径。 */
    val PRIMARY_MUSCLES = PeriodReviewCalculator.TRACKED_MUSCLES

    val EQUIPMENT_OPTIONS = listOf("杠铃", "哑铃", "绳索", "史密斯", "机械器械", "自重", "其他")

    data class Classification(
        val primary: String,
        val secondary: List<String> = emptyList(),
        val equipment: String? = null,
        val trackWeight: Boolean? = null
    )

    /** 把肌群名归一到 6 大类；认不出返回 null。 */
    fun normalizePrimary(raw: String): String? {
        val name = raw.trim()
        if (name.isBlank()) return null
        PRIMARY_MUSCLES.firstOrNull { it == name }?.let { return it }
        // 可能给细分名（"肱三头肌"），借既有桥接表归一
        PeriodReviewCalculator.secondaryMuscleGroup(name)?.let { return it }
        return null
    }

    /**
     * 本地离线关键词匹配分类：
     * 顺序很关键：**先判特殊词，再判宽泛词**。
     * 如「悬垂举腿」含「腿」但不是练腿 —— 若先判「腿」就会错归腿部。
     */
    fun classify(name: String): Classification {
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

    fun classifyOffline(name: String): Classification = classify(name)

    private fun secondaryHints(n: String): List<String> = buildList {
        if (n.contains("卧推") || n.contains("推举")) add("肱三头肌")
        if (n.contains("卧推") || n.contains("推举")) add("三角肌前束")
        if (n.contains("划船") || n.contains("下拉") || n.contains("引体")) add("肱二头肌")
        if (n.contains("深蹲") || n.contains("硬拉")) add("臀大肌")
        if (n.contains("卷腹") || n.contains("举腿") || n.contains("平板")) add("腹直肌")
    }.take(3)
}