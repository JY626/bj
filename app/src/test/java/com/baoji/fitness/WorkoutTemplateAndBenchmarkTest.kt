package com.baoji.fitness

import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.domain.model.PresetTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutTemplateAndBenchmarkTest {

    @Test
    fun testPresetTemplatesIntegrity() {
        val templates = PresetTemplates.all
        assertEquals(5, templates.size)

        val push = PresetTemplates.PUSH
        assertEquals("推力日 (Push)", push.title)
        assertTrue(push.exercises.contains("平板杠铃卧推"))

        val pull = PresetTemplates.PULL
        assertEquals("拉力日 (Pull)", pull.title)
        assertTrue(pull.exercises.contains("标准引体向上"))

        val legs = PresetTemplates.LEGS
        assertEquals("腿部日 (Legs)", legs.title)
        assertTrue(legs.exercises.contains("传统杠铃后深蹲"))

        val upper = PresetTemplates.UPPER
        assertEquals("上肢综合 (Upper)", upper.title)

        val lower = PresetTemplates.LOWER
        assertEquals("下肢综合 (Lower)", lower.title)
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
}
