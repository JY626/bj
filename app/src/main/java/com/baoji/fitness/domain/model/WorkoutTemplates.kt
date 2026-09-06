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
    // DAY 1: PUSH DAY 推日 (胸 肩 三头)
    val DAY1_PUSH = WorkoutRoutineTemplate(
        id = "DAY1_PUSH",
        name = "DAY 1 · 推日 (胸 肩 三头)",
        subtitle = "上斜卧推 · 卧推 · 杠铃肩推 · 侧平飞鸟 · 三头",
        targetMuscles = "胸部, 肩部, 手臂",
        defaultExerciseNames = listOf(
            "上斜卧推",
            "卧推",
            "杠铃肩推",
            "侧平飞鸟",
            "三头下压"
        )
    )

    // DAY 2: PULL DAY 拉日 (背 二头)
    val DAY2_PULL = WorkoutRoutineTemplate(
        id = "DAY2_PULL",
        name = "DAY 2 · 拉日 (背 二头)",
        subtitle = "硬拉 · 引体向上 · 划船 · 后束飞鸟 · 面拉 · 二头弯举",
        targetMuscles = "背部, 肩部, 手臂",
        defaultExerciseNames = listOf(
            "硬拉",
            "引体向上",
            "划船",
            "后束飞鸟",
            "面拉",
            "二头弯举"
        )
    )

    // DAY 3: 腿 + 外形补强
    val DAY3_LEGS_WEAK = WorkoutRoutineTemplate(
        id = "DAY3_LEGS_WEAK",
        name = "DAY 3 · 腿 + 外形补强",
        subtitle = "深蹲 · 杠铃肩推 · 面拉 · 侧平飞鸟 · 腹肌",
        targetMuscles = "腿部, 肩部, 核心",
        defaultExerciseNames = listOf(
            "深蹲",
            "杠铃肩推",
            "面拉",
            "侧平飞鸟",
            "腹肌训练"
        )
    )

    val allTemplates = listOf(DAY1_PUSH, DAY2_PULL, DAY3_LEGS_WEAK)
    val all = allTemplates

    // 兼容别名
    val PUSH = DAY1_PUSH
    val PULL = DAY2_PULL
    val LEGS = DAY3_LEGS_WEAK
}
