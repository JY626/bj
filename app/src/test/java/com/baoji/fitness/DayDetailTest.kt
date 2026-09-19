package com.baoji.fitness

import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.data.repository.buildDayDetail
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayDetailTest {

    private val day = LocalDate.of(2026, 9, 14)
    private val session = WorkoutSessionEntity(id = 1, title = "DAY 1 · 推日")

    private fun set(
        exerciseId: Long,
        setOrder: Int,
        weight: Double,
        reps: Int,
        completed: Boolean = true,
        type: String = "NORMAL"
    ) = WorkoutSetEntity(
        sessionId = 1,
        exerciseId = exerciseId,
        setOrder = setOrder,
        setType = type,
        weightKg = weight,
        reps = reps,
        isCompleted = completed
    )

    private val exercises = listOf(
        ExerciseEntity(id = 1, name = "卧推", targetMuscle = "胸部", equipment = "杠铃"),
        ExerciseEntity(id = 2, name = "推肩", targetMuscle = "肩部", equipment = "史密斯")
    )

    @Test
    fun testGroupsByExerciseSortsBySetOrderAndExcludesWarmupFromVolume() {
        val detail = buildDayDetail(
            day, session,
            sets = listOf(
                set(exerciseId = 1, setOrder = 2, weight = 80.0, reps = 8),
                set(exerciseId = 1, setOrder = 1, weight = 40.0, reps = 10, type = "WARMUP"),
                set(exerciseId = 2, setOrder = 1, weight = 30.0, reps = 12)
            ),
            exercises = exercises
        )!!

        assertEquals("DAY 1 · 推日", detail.title)
        assertEquals(day, detail.date)
        // 热身 40*10 不计入容量，仅 80*8 + 30*12
        assertEquals(80.0 * 8 + 30.0 * 12, detail.totalVolumeKg, 0.001)
        assertEquals(listOf("卧推", "推肩"), detail.groups.map { it.exerciseName })
        assertEquals(listOf(1, 2), detail.groups[0].sets.map { it.setOrder })
        assertEquals("胸部", detail.groups[0].targetMuscle)
    }

    @Test
    fun testUncompletedSetsAreIgnoredAndEmptyDayIsNull() {
        assertNull(
            buildDayDetail(day, session, sets = listOf(set(1, 1, 80.0, 8, completed = false)), exercises = exercises)
        )
        assertNull(buildDayDetail(day, session, sets = emptyList(), exercises = exercises))
    }

    @Test
    fun testUnknownExerciseFallsBackToPlaceholderName() {
        val detail = buildDayDetail(day, session, sets = listOf(set(99, 1, 50.0, 5)), exercises = exercises)!!
        assertEquals("动作 #99", detail.groups.single().exerciseName)
    }
}