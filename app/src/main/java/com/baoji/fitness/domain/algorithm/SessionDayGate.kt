package com.baoji.fitness.domain.algorithm

import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import java.time.ZoneId

/**
 * 跨天归档的判定。**纯函数，可单测**。
 *
 * 为什么需要它：打勾写的是当下的 `completedAt`，而打卡/明细按 `startTime` 的日期归属。
 * 所以「昨天开练、今天才打勾」会把今天的训练算进昨天。隔天必须先归档。
 *
 * 但**不能只要「开始日早于今天」就归档** —— 那会误伤跨午夜的训练：
 * 23:00 开练、练到 00:30，切出去回个消息再切回来（触发 ON_RESUME），
 * 正在进行的训练会被当场归档掉。所以再加一条**闲置门槛**：
 * 距最后一次打勾超过 [IDLE_HOURS] 小时，才算「这场已经结束了」。
 */
object SessionDayGate {

    /** 超过这个小时数没打勾，就认定上一场已经结束。 */
    const val IDLE_HOURS = 6L

    /**
     * 需要归档的会话 id。
     *
     * @param now 当前时刻（epoch millis），传参而非内部取时，便于测试与一致比较。
     */
    fun sessionsToArchive(
        activeSessions: List<WorkoutSessionEntity>,
        sets: List<WorkoutSetEntity>,
        now: Long,
        idleThresholdMs: Long = IDLE_HOURS * 3_600_000L,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Long> {
        val today = PeriodReviewCalculator.instantToLocalDate(now, zone)
        val lastActivity = sets
            .filter { it.isCompleted }
            .groupBy { it.sessionId }
            .mapValues { (_, s) -> s.mapNotNull { it.completedAt }.maxOrNull() }

        return activeSessions.filter { session ->
            val startedBeforeToday =
                PeriodReviewCalculator.instantToLocalDate(session.startTime, zone).isBefore(today)
            if (!startedBeforeToday) return@filter false
            // 没打勾过就用开始时间当活动时间：刚开的空会话不会被立刻归档
            val last = lastActivity[session.id] ?: session.startTime
            now - last >= idleThresholdMs
        }.map { it.id }
    }
}