package com.baoji.fitness

import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.domain.algorithm.SessionDayGate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 跨天归档的判定：**开始日早于今天 且 已闲置 ≥6h** 才归档。
 *
 * 只有"开始日早于今天"会误伤跨午夜训练：23:00 开练、00:30 还在练，
 * 切出去回消息再切回来会触发归档，把正在进行的训练终结掉。
 */
class SessionDayGateTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 9, 19)

    private fun at(day: LocalDate, time: LocalTime): Long =
        day.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private fun session(id: Long, day: LocalDate, time: LocalTime = LocalTime.of(20, 0)) =
        WorkoutSessionEntity(id = id, title = "DAY 1 · 推日", startTime = at(day, time), status = "ACTIVE")

    private fun doneSet(sessionId: Long, atMillis: Long) = WorkoutSetEntity(
        id = sessionId * 100, sessionId = sessionId, exerciseId = 1, setOrder = 1,
        weightKg = 60.0, reps = 8, isCompleted = true, completedAt = atMillis
    )

    /** 昨天开练、之后一直没动 → 归档。 */
    @Test
    fun idleYesterdaySessionIsArchived() {
        val now = at(today, LocalTime.of(10, 0))
        val active = listOf(session(1, today.minusDays(1), LocalTime.of(20, 0)))
        val sets = listOf(doneSet(1, at(today.minusDays(1), LocalTime.of(21, 0))))
        assertEquals(listOf(1L), SessionDayGate.sessionsToArchive(active, sets, now, zone = zone))
    }

    /**
     * 关键回归：跨午夜还在练。23:00 开练、00:30 刚打勾（才过 30 分钟），
     * 绝不能归档 —— 否则切出 App 再回来会把正在练的那场终结掉。
     */
    @Test
    fun activeSessionAcrossMidnightIsNotArchived() {
        val yesterday = today.minusDays(1)
        val now = at(today, LocalTime.of(0, 30))
        val active = listOf(session(1, yesterday, LocalTime.of(23, 0)))
        val sets = listOf(doneSet(1, at(today, LocalTime.of(0, 20)))) // 10 分钟前刚打勾
        assertEquals(
            "刚开始跨午夜训练，闲置不到 6h，不能归档",
            emptyList<Long>(),
            SessionDayGate.sessionsToArchive(active, sets, now, zone = zone)
        )
    }

    /** 今天开的会话永不归档，哪怕闲置很久。 */
    @Test
    fun todaySessionIsNeverArchived() {
        val now = at(today, LocalTime.of(23, 0))
        val active = listOf(session(1, today, LocalTime.of(2, 0)))
        assertEquals(emptyList<Long>(), SessionDayGate.sessionsToArchive(active, emptyList(), now, zone = zone))
    }

    /** 昨天的空壳会话（没打勾过）：用开始时间算闲置 → 归档。 */
    @Test
    fun idleEmptySessionFromYesterdayIsArchived() {
        val now = at(today, LocalTime.of(12, 0))
        val active = listOf(session(1, today.minusDays(1), LocalTime.of(20, 0)))
        assertEquals(listOf(1L), SessionDayGate.sessionsToArchive(active, emptyList(), now, zone = zone))
    }

    /** 刚开的空壳会话（今天）不能被归档。 */
    @Test
    fun freshEmptySessionIsKept() {
        val now = at(today, LocalTime.of(20, 1))
        val active = listOf(session(1, today, LocalTime.of(20, 0)))
        assertEquals(emptyList<Long>(), SessionDayGate.sessionsToArchive(active, emptyList(), now, zone = zone))
    }

    /** 混合：只归档过期的，正在进行的留着。 */
    @Test
    fun mixesIdlePastAndActive() {
        val yesterday = today.minusDays(1)
        val now = at(today, LocalTime.of(21, 0))
        val active = listOf(
            session(1, today.minusDays(3)),                       // 闲置多天 → 归档
            session(2, today.minusDays(1), LocalTime.of(23, 0)),  // 昨晚开、刚还打勾 → 留
            session(3, today)                                     // 今天 → 留
        )
        val sets = listOf(
            doneSet(1, at(today.minusDays(3), LocalTime.of(21, 0))),
            doneSet(2, at(today, LocalTime.of(20, 30)))           // 30 分钟前
        )
        assertEquals(listOf(1L), SessionDayGate.sessionsToArchive(active, sets, now, zone = zone))
    }

    @Test
    fun nothingWhenNoActiveSessions() {
        assertEquals(emptyList<Long>(), SessionDayGate.sessionsToArchive(emptyList(), emptyList(), 0L, zone = zone))
    }
}