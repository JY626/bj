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
    val PUSH = WorkoutRoutineTemplate(
        id = "PUSH",
        name = "推力日 (Push)",
        subtitle = "胸部 · 三角肌前中束 · 肱三头肌",
        targetMuscles = "胸部, 肩部, 手臂",
        defaultExerciseNames = listOf(
            "平板杠铃卧推",
            "30° 上斜哑铃卧推",
            "站姿杠铃推举",
            "绳索三头下压"
        )
    )

    val PULL = WorkoutRoutineTemplate(
        id = "PULL",
        name = "拉力日 (Pull)",
        subtitle = "背阔肌 · 中下斜方肌 · 肱二头肌",
        targetMuscles = "背部, 手臂",
        defaultExerciseNames = listOf(
            "标准引体向上",
            "杠铃俯身划船",
            "宽握高位下拉",
            "直杆杠铃弯举"
        )
    )

    val LEGS = WorkoutRoutineTemplate(
        id = "LEGS",
        name = "腿部日 (Legs)",
        subtitle = "股四头肌 · 腘绳肌 · 臀大肌",
        targetMuscles = "腿部",
        defaultExerciseNames = listOf(
            "传统杠铃后深蹲",
            "罗马尼亚硬拉 (RDL)",
            "45° 倒蹬机腿举",
            "坐姿腿屈伸"
        )
    )

    val UPPER = WorkoutRoutineTemplate(
        id = "UPPER",
        name = "上肢综合 (Upper)",
        subtitle = "胸 · 背 · 肩 · 手臂全面刺激",
        targetMuscles = "胸部, 背部, 肩部, 手臂",
        defaultExerciseNames = listOf(
            "平板杠铃卧推",
            "宽握高位下拉",
            "哑铃侧平举",
            "绳索三头下压"
        )
    )

    val LOWER = WorkoutRoutineTemplate(
        id = "LOWER",
        name = "下肢综合 (Lower)",
        subtitle = "深蹲主导与后侧链强化",
        targetMuscles = "腿部",
        defaultExerciseNames = listOf(
            "传统杠铃后深蹲",
            "罗马尼亚硬拉 (RDL)",
            "俯卧腿弯举",
            "45° 倒蹬机腿举"
        )
    )

    val allTemplates = listOf(PUSH, PULL, LEGS, UPPER, LOWER)
    val all = allTemplates
}
