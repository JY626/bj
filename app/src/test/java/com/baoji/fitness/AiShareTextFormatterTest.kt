package com.baoji.fitness

import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.domain.algorithm.AiShareTextFormatter
import com.baoji.fitness.domain.model.ExerciseGroupDetail
import com.baoji.fitness.domain.model.MuscleVolumeStatus
import com.baoji.fitness.domain.model.WorkoutDayDetail
import com.baoji.fitness.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AiShareTextFormatterTest {

    @Test
    fun formatsSetsSummary_collapsesConsecutiveSets() {
        val sets = listOf(
            WorkoutSet(id = 1, sessionId = 1, exerciseId = 1, setOrder = 1, setType = "NORMAL", weightKg = 20.0, reps = 15, isCompleted = true),
            WorkoutSet(id = 2, sessionId = 1, exerciseId = 1, setOrder = 2, setType = "NORMAL", weightKg = 30.0, reps = 12, isCompleted = true),
            WorkoutSet(id = 3, sessionId = 1, exerciseId = 1, setOrder = 3, setType = "NORMAL", weightKg = 40.0, reps = 12, isCompleted = true),
            WorkoutSet(id = 4, sessionId = 1, exerciseId = 1, setOrder = 4, setType = "NORMAL", weightKg = 40.0, reps = 12, isCompleted = true),
            WorkoutSet(id = 5, sessionId = 1, exerciseId = 1, setOrder = 5, setType = "NORMAL", weightKg = 40.0, reps = 12, isCompleted = true),
            WorkoutSet(id = 6, sessionId = 1, exerciseId = 1, setOrder = 6, setType = "NORMAL", weightKg = 40.0, reps = 12, isCompleted = true)
        )

        val summary = AiShareTextFormatter.formatSetsSummary(sets, trackWeight = true)
        assertEquals("15×20 / 12×30 / 12×40×4", summary)
    }

    @Test
    fun formatsSetsSummary_selfWeightExercises() {
        val sets = listOf(
            WorkoutSet(id = 1, sessionId = 1, exerciseId = 1, setOrder = 1, setType = "NORMAL", weightKg = 0.0, reps = 15, isCompleted = true),
            WorkoutSet(id = 2, sessionId = 1, exerciseId = 1, setOrder = 2, setType = "NORMAL", weightKg = 0.0, reps = 12, isCompleted = true),
            WorkoutSet(id = 3, sessionId = 1, exerciseId = 1, setOrder = 3, setType = "NORMAL", weightKg = 0.0, reps = 12, isCompleted = true)
        )

        val summary = AiShareTextFormatter.formatSetsSummary(sets, trackWeight = false)
        assertEquals("15次 / 12次×2", summary)
    }

    @Test
    fun excludesWarmupSetsFromVolumeAndDetails() {
        val detail = WorkoutDayDetail(
            date = LocalDate.of(2026, 10, 13),
            title = "DAY 1 · 推日 (胸 肩)",
            totalVolumeKg = 640.0,
            groups = listOf(
                ExerciseGroupDetail(
                    exerciseName = "卧推",
                    targetMuscle = "胸部",
                    sets = listOf(
                        WorkoutSet(id = 1, sessionId = 1, exerciseId = 1, setOrder = 1, setType = "WARMUP", weightKg = 40.0, reps = 10, isCompleted = true),
                        WorkoutSet(id = 2, sessionId = 1, exerciseId = 1, setOrder = 2, setType = "NORMAL", weightKg = 80.0, reps = 8, isCompleted = true)
                    )
                )
            )
        )

        val text = AiShareTextFormatter.buildAiShareText(detail)

        // 容量应只计 80*8 = 640kg，组数 1 组（不含热身）
        assertTrue("必须排除热身组容量", text.contains("容量 640kg · 1组（不含热身）"))
        assertTrue("动作组明细只含正式组", text.contains("- 卧推：8×80"))
        assertFalse("动作组明细不包含热身组", text.contains("10×40"))
    }

    @Test
    fun generatesFullTemplate_matchingSpecification() {
        val detail = WorkoutDayDetail(
            date = LocalDate.of(2026, 10, 13),
            title = "DAY 1 · 推日 (胸 肩)",
            totalVolumeKg = 3920.0,
            notes = "睡眠不足",
            groups = listOf(
                ExerciseGroupDetail(
                    exerciseName = "卧推",
                    targetMuscle = "胸部",
                    sets = listOf(
                        WorkoutSet(id = 1, sessionId = 1, exerciseId = 1, setOrder = 1, setType = "NORMAL", weightKg = 20.0, reps = 15, isCompleted = true),
                        WorkoutSet(id = 2, sessionId = 1, exerciseId = 1, setOrder = 2, setType = "NORMAL", weightKg = 30.0, reps = 12, isCompleted = true),
                        WorkoutSet(id = 3, sessionId = 1, exerciseId = 1, setOrder = 3, setType = "NORMAL", weightKg = 40.0, reps = 12, isCompleted = true),
                        WorkoutSet(id = 4, sessionId = 1, exerciseId = 1, setOrder = 4, setType = "NORMAL", weightKg = 40.0, reps = 12, isCompleted = true),
                        WorkoutSet(id = 5, sessionId = 1, exerciseId = 1, setOrder = 5, setType = "NORMAL", weightKg = 40.0, reps = 12, isCompleted = true),
                        WorkoutSet(id = 6, sessionId = 1, exerciseId = 1, setOrder = 6, setType = "NORMAL", weightKg = 40.0, reps = 12, isCompleted = true)
                    )
                ),
                ExerciseGroupDetail(
                    exerciseName = "上斜卧推",
                    targetMuscle = "胸部",
                    sets = listOf(
                        WorkoutSet(id = 7, sessionId = 1, exerciseId = 2, setOrder = 1, setType = "NORMAL", weightKg = 35.0, reps = 10, isCompleted = true),
                        WorkoutSet(id = 8, sessionId = 1, exerciseId = 2, setOrder = 2, setType = "NORMAL", weightKg = 35.0, reps = 10, isCompleted = true),
                        WorkoutSet(id = 9, sessionId = 1, exerciseId = 2, setOrder = 3, setType = "NORMAL", weightKg = 35.0, reps = 8, isCompleted = true)
                    )
                ),
                ExerciseGroupDetail(
                    exerciseName = "蝴蝶机夹胸",
                    targetMuscle = "胸部",
                    sets = listOf(
                        WorkoutSet(id = 10, sessionId = 1, exerciseId = 3, setOrder = 1, setType = "NORMAL", weightKg = 5.0, reps = 12, isCompleted = true),
                        WorkoutSet(id = 11, sessionId = 1, exerciseId = 3, setOrder = 2, setType = "NORMAL", weightKg = 5.0, reps = 20, isCompleted = true),
                        WorkoutSet(id = 12, sessionId = 1, exerciseId = 3, setOrder = 3, setType = "NORMAL", weightKg = 5.0, reps = 20, isCompleted = true),
                        WorkoutSet(id = 13, sessionId = 1, exerciseId = 3, setOrder = 4, setType = "NORMAL", weightKg = 5.0, reps = 20, isCompleted = true)
                    )
                )
            )
        )

        val weeklyMuscles = listOf(
            MuscleVolumeStatus("胸部", 6.0, 6, 12, 20, "低于MEV"),
            MuscleVolumeStatus("背部", 14.0, 8, 14, 22, "MAV"),
            MuscleVolumeStatus("腿部", 0.0, 8, 14, 20, "低于MEV")
        )

        val latestMetric = BodyMetricEntity(
            id = 2,
            date = LocalDate.of(2026, 10, 13).toEpochDay(),
            weightKg = 83.95,
            bodyFatPct = 26.6,
            note = ""
        )
        val prevMetric = BodyMetricEntity(
            id = 1,
            date = LocalDate.of(2026, 10, 10).toEpochDay(),
            weightKg = 84.25,
            note = ""
        )

        val text = AiShareTextFormatter.buildAiShareText(
            detail = detail,
            includeNotes = true,
            previousSameTypeVolume = 3600.0,
            weeklySessionCount = 3,
            weeklyTotalVolumeKg = 8500.0,
            weeklyVolumeDeltaPct = 8.0,
            weeklyMuscles = weeklyMuscles,
            latestMetric = latestMetric,
            previousMetric = prevMetric
        )

        assertTrue(text.contains("# 训练记录 2026-10-13 · 推日 (胸 肩)"))
        assertTrue(text.contains("- 容量 3920kg · 13组（不含热身） · 环比上次推日 +9%"))
        assertTrue(text.contains("- 卧推：15×20 / 12×30 / 12×40×4"))
        assertTrue(text.contains("- 上斜卧推：10×35×2 / 8×35"))
        assertTrue(text.contains("- 蝴蝶机夹胸：12×5 / 20×5×3"))
        assertTrue(text.contains("- 备注：睡眠不足"))
        assertTrue(text.contains("## 本周"))
        assertTrue(text.contains("- 训练 3 次 · 总容量 8500kg · 环比 +8%"))
        assertTrue(text.contains("胸部 6组（低于MEV） · 背部 14组（MAV）"))
        assertTrue(text.contains("## 身体数据"))
        assertTrue(text.contains("体重 83.95kg（10-13） · 体脂 26.6% · 较上次 -0.3kg"))
    }
}
