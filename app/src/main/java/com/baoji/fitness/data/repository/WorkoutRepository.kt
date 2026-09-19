package com.baoji.fitness.data.repository

import com.baoji.fitness.core.database.dao.ExerciseDao
import com.baoji.fitness.core.database.dao.WorkoutDao
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.domain.algorithm.DateWindow
import com.baoji.fitness.domain.algorithm.OverloadCalculator
import com.baoji.fitness.domain.algorithm.PeriodReview
import com.baoji.fitness.domain.algorithm.PeriodReviewCalculator
import com.baoji.fitness.domain.algorithm.ReviewPeriod
import com.baoji.fitness.domain.algorithm.TrendPoint
import com.baoji.fitness.domain.model.Exercise
import com.baoji.fitness.domain.model.ExerciseGroupDetail
import com.baoji.fitness.domain.model.ExerciseWithSets
import com.baoji.fitness.domain.model.MuscleVolumeStatus
import com.baoji.fitness.domain.model.WorkoutDayDetail
import com.baoji.fitness.domain.model.WorkoutSet
import com.baoji.fitness.domain.model.workoutTypeLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao
) {
    fun getActiveSession(): Flow<WorkoutSessionEntity?> = workoutDao.getActiveSession()

    /** 训练打卡：当天至少完成过一组训练的自然日 → 训练类型（推/拉/腿）。同天多场取最新。 */
    fun getTrainedDayTypes(): Flow<Map<LocalDate, String>> = combine(
        workoutDao.getAllSessions(),
        workoutDao.getCompletedSetsSince(0L)
    ) { sessions, sets ->
        val trainedSessionIds = sets.map { it.sessionId }.toSet()
        // getAllSessions 是新的在前，反转遍历让同天最新一场覆盖旧的
        sessions.asReversed()
            .filter { it.id in trainedSessionIds }
            .associate { it.startTime.toLocalDate() to workoutTypeLabel(it.title) }
    }

    /** 某一天的训练明细：仅统计完成组，按动作分组。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDayDetail(day: LocalDate): Flow<WorkoutDayDetail?> =
        workoutDao.getAllSessions().flatMapLatest { sessions ->
            val session = sessions.lastOrNull { it.startTime.toLocalDate() == day }
            if (session == null) {
                flowOf(null)
            } else {
                combine(
                    workoutDao.getSetsForSession(session.id),
                    exerciseDao.getAllExercises()
                ) { sets, exercises ->
                    buildDayDetail(day, session, sets, exercises)
                }
            }
        }

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    fun getAllSessions(): Flow<List<WorkoutSessionEntity>> = workoutDao.getAllSessions()

    fun getAllExercises(): Flow<List<ExerciseEntity>> = exerciseDao.getAllExercises()

    suspend fun insertExercise(exercise: ExerciseEntity): Long = exerciseDao.insertExercise(exercise)

    suspend fun startNewSession(title: String = "自由推力训练"): Long {
        workoutDao.closeAllActiveSessions()
        val session = WorkoutSessionEntity(
            title = title,
            startTime = System.currentTimeMillis(),
            status = "ACTIVE"
        )
        return workoutDao.insertSession(session)
    }

    suspend fun finishSession(session: WorkoutSessionEntity, totalVolume: Double, notes: String = "") {
        val updated = session.copy(
            endTime = System.currentTimeMillis(),
            totalVolumeKg = totalVolume,
            status = "COMPLETED",
            notes = notes
        )
        workoutDao.updateSession(updated)
    }

    /** M3a：会话备注（`notes` 列 v2 就存在，此前全项目零读写）。 */
    suspend fun updateSessionNotes(sessionId: Long, notes: String) =
        workoutDao.updateSessionNotes(sessionId, notes)

    /** M3a：某组的主观疲劳度 6~10；null = 未记录。 */
    suspend fun updateSetRpe(setId: Long, rpe: Double?) =
        workoutDao.updateSetRpe(setId, rpe)

    /** M3a：整组覆盖写（重量/次数/类型/RPE 一次落库）。 */
    suspend fun saveSet(set: WorkoutSetEntity) = workoutDao.updateSet(set)

    /** 最近 [limit] 条非空备注，供 M4a 本地总结与训练页文案使用。 */
    fun getRecentNotes(limit: Int = 3): Flow<List<String>> = workoutDao.getRecentNotes(limit)

    fun getSetsForSession(sessionId: Long): Flow<List<WorkoutSetEntity>> =
        workoutDao.getSetsForSession(sessionId)

    fun getExerciseHistory(exerciseId: Long): Flow<List<WorkoutSetEntity>> =
        workoutDao.getHistoryForExercise(exerciseId)

    suspend fun getPreviousSessionSetsForExercise(exerciseId: Long, currentSessionId: Long): Map<Int, WorkoutSetEntity> {
        val history = workoutDao.getHistoricalCompletedSets(exerciseId, currentSessionId)
        if (history.isEmpty()) return emptyMap()

        val lastSessionId = history.first().sessionId
        val setsFromLastSession = history.filter { it.sessionId == lastSessionId }
        return setsFromLastSession.associateBy { it.setOrder }
    }

    suspend fun getExerciseById(exerciseId: Long): ExerciseEntity? =
        exerciseDao.getExerciseById(exerciseId)

    suspend fun removeExerciseFromSession(sessionId: Long, exerciseId: Long) {
        workoutDao.deleteSetsForExerciseInSession(sessionId, exerciseId)
    }

    suspend fun getPreviousExerciseIdsForRoutine(routineTitle: String): List<Long> {
        val targetSession = workoutDao.getLatestCompletedSessionByTitle(routineTitle)
            ?: workoutDao.getLatestSessionByTitle(routineTitle)
            ?: return emptyList()
        return workoutDao.getDistinctExerciseIdsForSession(targetSession.id)
    }

    suspend fun addSet(
        sessionId: Long,
        exerciseId: Long,
        weightKg: Double,
        reps: Int,
        setOrder: Int,
        setType: String = "NORMAL"
    ): Long {
        val entity = WorkoutSetEntity(
            sessionId = sessionId,
            exerciseId = exerciseId,
            setOrder = setOrder,
            setType = setType,
            weightKg = weightKg,
            reps = reps
        )
        return workoutDao.insertSet(entity)
    }

    suspend fun completeSet(set: WorkoutSetEntity, completed: Boolean) {
        val updated = set.copy(
            isCompleted = completed,
            completedAt = if (completed) System.currentTimeMillis() else null
        )
        workoutDao.updateSet(updated)

        // If completed and higher than personal record, update exercise PR
        if (completed) {
            val exercise = exerciseDao.getExerciseById(set.exerciseId)
            if (exercise != null && set.weightKg > exercise.personalRecordWeight) {
                exerciseDao.updatePR(set.exerciseId, set.weightKg, set.reps)
            }
        }
    }

    suspend fun updateSetWeightAndReps(set: WorkoutSetEntity, weightKg: Double, reps: Int) {
        workoutDao.updateSet(set.copy(weightKg = weightKg, reps = reps))
    }

    suspend fun deleteSet(set: WorkoutSetEntity) {
        workoutDao.deleteSet(set)
    }

    suspend fun deleteSetAndReorder(set: WorkoutSetEntity) {
        workoutDao.deleteSet(set)
        val remaining = workoutDao.getSetsForExerciseInSession(set.sessionId, set.exerciseId)
        remaining.forEachIndexed { index, s ->
            val expectedOrder = index + 1
            if (s.setOrder != expectedOrder) {
                workoutDao.updateSet(s.copy(setOrder = expectedOrder))
            }
        }
    }

    // ---------------------------------------------------------------- M1 周期回顾

    /**
     * 训练史单一来源：会话 + 全部完成组 + 动作库。
     * 周期窗口一律在 Kotlin 侧按**会话开始日**切（与日历打卡同一口径），
     * 不做 epoch 换算 —— 那是「跳零点训练被拆到两天」这类 bug 的源头，
     * 而且纯函数层的单测能真正钉住这个约定。
     */
    private val trainingHistory = combine(
        workoutDao.getAllSessions(),
        workoutDao.getCompletedSetsSince(0L),
        exerciseDao.getAllExercises()
    ) { sessions, sets, exercises -> Triple(sessions, sets, exercises) }

    /** 一次周期回顾。「本周」与日历页（周一为首列）是同一个窗口。 */
    fun getPeriodReview(
        period: ReviewPeriod,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<PeriodReview> {
        val windows = PeriodReviewCalculator.resolveWindows(period, today)
        return trainingHistory.map { (sessions, sets, _) ->
            val currentSessions = PeriodReviewCalculator.sessionsIn(sessions, windows.current, zone)
            val previousVolume = windows.previous?.let { prev ->
                PeriodReviewCalculator.volumeOf(
                    PeriodReviewCalculator.setsOf(sets, PeriodReviewCalculator.sessionsIn(sessions, prev, zone))
                )
            }
            PeriodReviewCalculator.buildPeriodReview(
                label = period.label,
                sets = PeriodReviewCalculator.setsOf(sets, currentSessions),
                sessions = currentSessions,
                previousVolumeKg = previousVolume
            )
        }
    }

    /** 近 [weeks] 周容量趋势（含本周，最旧 → 最新）。 */
    fun getWeeklyTrend(
        weeks: Int = 8,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<List<TrendPoint>> {
        val window = PeriodReviewCalculator.lastWeeks(today, weeks)
        return trainingHistory.map { (sessions, sets, _) ->
            val windowSessions = PeriodReviewCalculator.sessionsIn(sessions, window, zone)
            PeriodReviewCalculator.buildWeeklyTrend(
                sets = PeriodReviewCalculator.setsOf(sets, windowSessions),
                sessions = windowSessions,
                weeks = weeks,
                today = today,
                zone = zone
            )
        }
    }

    /**
     * 部位有效组数「本周 vs 上周」。MEV/MAV/MRV 是**周**阈值，
     * 所以这里永远按自然周算，不跟随周期 chips（本月/近8周 与周阈值不可比）。
     */
    fun getWeeklyMuscleComparison(
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Flow<WeeklyMuscleComparison> {
        val thisWeek = PeriodReviewCalculator.currentWeek(today)
        val lastWeek = DateWindow(thisWeek.from.minusWeeks(1), thisWeek.from)

        return trainingHistory.map { (sessions, sets, exercises) ->
            fun counts(window: DateWindow): Map<String, Int> =
                PeriodReviewCalculator.muscleSetCounts(
                    PeriodReviewCalculator.setsOf(
                        sets,
                        PeriodReviewCalculator.sessionsIn(sessions, window, zone)
                    ),
                    exercises
                )
            val cur = counts(thisWeek)
            val prev = counts(lastWeek)
            WeeklyMuscleComparison(
                current = PeriodReviewCalculator.TRACKED_MUSCLES.map {
                    OverloadCalculator.evaluateMuscleVolume(it, cur[it] ?: 0)
                },
                previous = PeriodReviewCalculator.TRACKED_MUSCLES.map {
                    OverloadCalculator.evaluateMuscleVolume(it, prev[it] ?: 0)
                }
            )
        }
    }
}

/** 部位容量进度条需要「本周 vs 上周」两条数，一一对应。 */
data class WeeklyMuscleComparison(
    val current: List<MuscleVolumeStatus>,
    val previous: List<MuscleVolumeStatus>
)

/**
 * 把某天某会话的完成组聚合成明细：按动作分组、按 setOrder 排序、热身组不计入容量。
 * 无完成组时返回 null（避免空日期点出空弹层）。抽出来便于单测。
 */
internal fun buildDayDetail(
    day: LocalDate,
    session: WorkoutSessionEntity,
    sets: List<WorkoutSetEntity>,
    exercises: List<ExerciseEntity>
): WorkoutDayDetail? {
    val exerciseById = exercises.associateBy { it.id }
    val completed = sets.filter { it.isCompleted }
    val groups = completed.groupBy { it.exerciseId }.map { (exerciseId, groupSets) ->
        val exercise = exerciseById[exerciseId]
        ExerciseGroupDetail(
            exerciseName = exercise?.name ?: "动作 #$exerciseId",
            targetMuscle = exercise?.targetMuscle ?: "",
            sets = groupSets.sortedBy { it.setOrder }.map { s ->
                WorkoutSet(
                    id = s.id,
                    sessionId = s.sessionId,
                    exerciseId = s.exerciseId,
                    exerciseName = exercise?.name ?: "",
                    setOrder = s.setOrder,
                    setType = s.setType,
                    weightKg = s.weightKg,
                    reps = s.reps,
                    rpe = s.rpe,
                    isCompleted = true
                )
            }
        )
    }
    if (groups.isEmpty()) return null

    return WorkoutDayDetail(
        date = day,
        title = session.title,
        totalVolumeKg = completed.filter { it.setType != "WARMUP" }
            .sumOf { it.weightKg * it.reps },
        groups = groups,
        notes = session.notes
    )
}
