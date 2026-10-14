package com.baoji.fitness

import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.domain.model.PersonalRecordCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalRecordCalculatorTest {

    private val sampleExercise = ExerciseEntity(
        id = 1L,
        name = "杠铃卧推",
        targetMuscle = "胸部",
        equipment = "杠铃",
        personalRecordWeight = 60.0,
        personalRecordReps = 5
    )

    @Test
    fun calculate_emptySets_returnsDefaultPrFromEntity() {
        val summary = PersonalRecordCalculator.calculate(sampleExercise, emptyList())
        assertEquals("杠铃卧推", summary.exercise.name)
        assertEquals(60.0, summary.maxWeightKg, 0.01)
        assertTrue(summary.bestE1RM > 60.0) // 60kg * (1 + 5/30) = 70kg
        assertEquals(0, summary.totalCompletedSets)
        assertEquals(0.0, summary.totalVolumeKg, 0.01)
    }

    @Test
    fun calculate_withWarmupAndEffectiveSets_filtersWarmupAndCalculatesCorrectly() {
        val sets = listOf(
            WorkoutSetEntity(
                id = 1L,
                sessionId = 100L,
                exerciseId = 1L,
                setOrder = 1,
                weightKg = 40.0,
                reps = 10,
                setType = "WARMUP",
                isCompleted = true,
                completedAt = 1000L
            ),
            WorkoutSetEntity(
                id = 2L,
                sessionId = 100L,
                exerciseId = 1L,
                setOrder = 2,
                weightKg = 80.0,
                reps = 6,
                setType = "NORMAL",
                isCompleted = true,
                completedAt = 2000L
            ),
            WorkoutSetEntity(
                id = 3L,
                sessionId = 100L,
                exerciseId = 1L,
                setOrder = 3,
                weightKg = 90.0,
                reps = 3,
                setType = "NORMAL",
                isCompleted = true,
                completedAt = 3000L
            ),
            WorkoutSetEntity(
                id = 4L,
                sessionId = 100L,
                exerciseId = 1L,
                setOrder = 4,
                weightKg = 95.0,
                reps = 1,
                setType = "NORMAL",
                isCompleted = false, // Not completed
                completedAt = null
            )
        )

        val summary = PersonalRecordCalculator.calculate(sampleExercise, sets)

        // Only sets 2 and 3 are effective completed sets
        assertEquals(2, summary.totalCompletedSets)
        assertEquals(90.0, summary.maxWeightKg, 0.01)
        assertEquals(3L, summary.maxWeightSet?.id)

        // 80kg * 6 reps = 80 * (1 + 6/30) = 96.0 kg E1RM
        // 90kg * 3 reps = 90 * (1 + 3/30) = 99.0 kg E1RM
        assertEquals(99.0, summary.bestE1RM, 0.01)
        assertEquals(3L, summary.bestE1RMSet?.id)

        // Best volume: 80 * 6 = 480 vs 90 * 3 = 270 -> 480 (set 2)
        assertEquals(2L, summary.bestVolumeSet?.id)

        // Total volume: 80 * 6 + 90 * 3 = 480 + 270 = 750
        assertEquals(750.0, summary.totalVolumeKg, 0.01)

        // History sets ordered descending by completedAt: set 3, then set 2
        assertEquals(listOf(3L, 2L), summary.historySets.map { it.id })
    }
}
