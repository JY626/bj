package com.baoji.fitness.domain.model

data class WorkoutRoutineTemplate(
    val id: String,
    val name: String,
    val subtitle: String,
    val targetMuscles: String,
    val defaultExerciseNames: List<String>
) {
    val title: String get() = name
    val focusSplit: String get() = subtitle
    val exercises: List<String> get() = defaultExerciseNames
}

object PresetTemplates {
    // DAY 1: PUSH DAY 推日 (胸 肩)
    val DAY1_PUSH = WorkoutRoutineTemplate(
        id = "DAY1_PUSH",
        name = "DAY 1 · 推日 (胸 肩)",
        subtitle = "上斜卧推 · 卧推 · 史密斯推肩 · 侧平飞鸟",
        targetMuscles = "胸部, 肩部",
        defaultExerciseNames = listOf(
            "上斜卧推",
            "卧推",
            "史密斯推肩",
            "侧平飞鸟"
        )
    )

    // DAY 2: PULL DAY 拉日 (背 二头)
    val DAY2_PULL = WorkoutRoutineTemplate(
        id = "DAY2_PULL",
        name = "DAY 2 · 拉日 (背 二头)",
        subtitle = "硬拉 · 高位下拉 · 划船 · 面拉 · 二头弯举",
        targetMuscles = "背部, 肩部, 手臂",
        defaultExerciseNames = listOf(
            "硬拉",
            "高位下拉",
            "划船",
            "面拉",
            "二头弯举"
        )
    )

    // DAY 3: 腿 + 外形补强
    val DAY3_LEGS_WEAK = WorkoutRoutineTemplate(
        id = "DAY3_LEGS_WEAK",
        name = "DAY 3 · 腿 + 外形补强",
        subtitle = "史密斯深蹲 · 史密斯推肩 · 面拉 · 侧平飞鸟 · 悬垂举腿",
        targetMuscles = "腿部, 肩部, 核心",
        defaultExerciseNames = listOf(
            "史密斯深蹲",
            "史密斯推肩",
            "面拉",
            "侧平飞鸟",
            "悬垂举腿"
        )
    )

    val allTemplates = listOf(DAY1_PUSH, DAY2_PULL, DAY3_LEGS_WEAK)
    val all = allTemplates

    // 兼容别名
    val PUSH = DAY1_PUSH
    val PULL = DAY2_PULL
    val LEGS = DAY3_LEGS_WEAK
}

/** 从会话标题推断训练类型标签（推 / 拉 / 腿）；自定义训练认不出时返回「练」。 */
fun workoutTypeLabel(title: String): String = when {
    title.contains("推") -> "推"
    title.contains("拉") -> "拉"
    title.contains("腿") -> "腿"
    else -> "练"
}
