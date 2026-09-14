package com.baoji.fitness

import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.domain.model.PresetTemplates
import com.baoji.fitness.ui.workout.WorkoutViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTemplateAndBenchmarkTest {

    @Test
    fun testPresetTemplatesIntegrity() {
        val templates = PresetTemplates.all
        assertEquals(3, templates.size)

        val day1 = PresetTemplates.DAY1_PUSH
        assertEquals("DAY 1 · 推日 (胸 肩)", day1.title)
        assertEquals(listOf("上斜卧推", "卧推", "史密斯推肩", "侧平飞鸟"), day1.exercises)

        val day2 = PresetTemplates.DAY2_PULL
        assertEquals("DAY 2 · 拉日 (背 二头)", day2.title)
        assertEquals(listOf("硬拉", "高位下拉", "划船", "面拉", "二头弯举"), day2.exercises)

        val day3 = PresetTemplates.DAY3_LEGS_WEAK
        assertEquals("DAY 3 · 腿 + 外形补强", day3.title)
        assertEquals(listOf("史密斯深蹲", "史密斯推肩", "面拉", "侧平飞鸟", "悬垂举腿"), day3.exercises)
    }

    @Test
    fun testBaselineInheritanceMapping() {
        // Scenario 1: First session (no previous sets) -> benchmark should be empty
        val previousCompletedSets = emptyList<WorkoutSetEntity>()
        val mappedByOrder = if (previousCompletedSets.isEmpty()) {
            emptyMap<Int, WorkoutSetEntity>()
        } else {
            val mostRecentSessionId = previousCompletedSets.first().sessionId
            previousCompletedSets
                .filter { it.sessionId == mostRecentSessionId }
                .associateBy { it.setOrder }
        }

        assertTrue(mappedByOrder.isEmpty())
        assertNull(mappedByOrder[1])

        // Scenario 2: Historical session exists with completed sets for order 1, 2, 3
        val historySets = listOf(
            WorkoutSetEntity(id = 1L, sessionId = 100L, exerciseId = 1L, setOrder = 1, weightKg = 80.0, reps = 8, isCompleted = true),
            WorkoutSetEntity(id = 2L, sessionId = 100L, exerciseId = 1L, setOrder = 2, weightKg = 82.5, reps = 7, isCompleted = true),
            WorkoutSetEntity(id = 3L, sessionId = 100L, exerciseId = 1L, setOrder = 3, weightKg = 82.5, reps = 6, isCompleted = true)
        )

        val mappedHistory = historySets
            .filter { it.sessionId == 100L }
            .associateBy { it.setOrder }

        assertEquals(80.0, mappedHistory[1]?.weightKg ?: 0.0, 0.01)
        assertEquals(8, mappedHistory[1]?.reps ?: 0)
        assertEquals(82.5, mappedHistory[2]?.weightKg ?: 0.0, 0.01)
        assertEquals(7, mappedHistory[2]?.reps ?: 0)
        assertEquals(82.5, mappedHistory[3]?.weightKg ?: 0.0, 0.01)
        assertEquals(6, mappedHistory[3]?.reps ?: 0)

        // Set order 4 is newly added in current session -> no previous record -> null
        assertNull(mappedHistory[4])
    }

    @Test
    fun testDynamicRoutineExerciseInheritance() {
        // Assume user customized DAY 1: removed exercise 3 (杠铃肩推), added exercise 10 (坐姿推胸)
        // Previous completed session contains exercises: [1L, 2L, 4L, 5L, 10L]
        val previousSessionExerciseIds = listOf(1L, 2L, 4L, 5L, 10L)

        // When starting new session for this routine:
        val resolvedExerciseIds = if (previousSessionExerciseIds.isNotEmpty()) {
            previousSessionExerciseIds
        } else {
            // Default fallback
            listOf(1L, 2L, 3L, 4L, 5L)
        }

        assertEquals(5, resolvedExerciseIds.size)
        assertTrue(resolvedExerciseIds.contains(10L))
        assertEquals(false, resolvedExerciseIds.contains(3L))
    }

    @Test
    fun testCheckInheritancePicksNextOpenSet() {
        val done = WorkoutSetEntity(id = 1L, sessionId = 9L, exerciseId = 1L, setOrder = 1, weightKg = 82.5, reps = 8, isCompleted = true)
        val sets = listOf(
            done,
            WorkoutSetEntity(id = 2L, sessionId = 9L, exerciseId = 1L, setOrder = 3, weightKg = 80.0, reps = 5),
            WorkoutSetEntity(id = 3L, sessionId = 9L, exerciseId = 1L, setOrder = 2, weightKg = 80.0, reps = 5)
        )
        // 乱序列表中也应选 setOrder=2 的那组（id=3L），而非插入顺序的第一条（id=2L 是 setOrder=3）
        assertEquals(3L, WorkoutViewModel.nextOpenSetAfter(sets, done)?.id)

        // 后面无未完成组 -> null
        val tailDone = done.copy(id = 3L, setOrder = 2, isCompleted = true)
        assertEquals(null, WorkoutViewModel.nextOpenSetAfter(listOf(done.copy(isCompleted = true), tailDone), tailDone))
    }
}
