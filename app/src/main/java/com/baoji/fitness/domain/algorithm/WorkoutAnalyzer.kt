package com.baoji.fitness.domain.algorithm

import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.domain.model.MuscleVolumeStatus
import kotlin.math.roundToInt

/**
 * 身体画像（身高/体重/体脂）—— 与训练量一起看才能判断“练得够不够、吃对没对”。
 */
data class BodyProfile(
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val bodyFatPct: Double? = null,
    val waistCm: Double? = null,
    val armCm: Double? = null,
    val chestCm: Double? = null,
    /** 体重相对上次记录的变化（kg），负=降。 */
    val weightDeltaKg: Double? = null,
    /** 体脂率相对上次记录的变化（百分点）。 */
    val bodyFatDeltaPct: Double? = null
) {
    /** BMI = 体重 / 身高²。缺任一则为 null，不算半成品数字。四舍五入到 1 位。 */
    val bmi: Double? get() {
        val w = weightKg ?: return null
        val h = heightCm?.takeIf { it > 0 } ?: return null
        val m = h / 100.0
        return (w / (m * m) * 10).roundToInt() / 10.0
    }
}

/** 训练强度 / 饮食建议要看的“体重 × 训练量”关系。 */
data class BodyContext(
    val profile: BodyProfile,
    /** 近 4 周总容量，用于估算相对体重的训练量。 */
    val volumePerBodyKg: Double?,
    /** 每周平均训练次数（近 4 周）。 */
    val weeklySessions: Double
)

/**
 * 一份「给模型读的」训练事实体检。**所有数字都在这里用 Kotlin 算好**，
 * 模型只负责把事实解读成人话 —— 不让 LLM 碰原始数据做算数（它会编）。
 *
 * 纯函数、零 IO、可单测；数据源是仓储已有的全量历史。
 */
data class TrainingFacts(
    val weeksCovered: Int,    val sessionCount: Int,
    val totalVolumeKg: Double,
    val volumeDeltaPct: Double?,
    /** (周标签, 容量 kg)，最旧在前，用于看趋势斜率。 */
    val weeklyVolume: List<Pair<String, Double>>,
    /** 五个追踪部位的组数（含间接折算）与 MEV/MAV/MRV。 */
    val muscles: List<MuscleVolumeStatus>,
    /** 推 / 拉 / 腿 / 练 → 组数，用于推拉失衡判断。 */
    val pushPullSets: Map<String, Double>,
    /** 停滞或退步的动作（最近无 e1RM 提升）。 */
    val stalls: List<StallFact>,
    /** 一周内 RPE 明显抬高的动作（同重量更吃力 = 疲劳累积）。 */
    val rpeDrift: List<RpeDriftFact>,
    /** 组内衰减过大的动作（末组次数掉得太多 = 重量偏重）。 */
    val setFade: List<SetFadeFact>,
    val recentNotes: List<String>,
    val latestWeightKg: Double?,
    val streakDays: Int,
    /** 身体画像（身高/体重/体脂等）；无记录时为全 null。 */
    val body: BodyProfile = BodyProfile(),
    /** 本次窗口内练到的动作，相对“上次练它”的进步（无上次记录的不列）。 */
    val exerciseProgress: List<ExerciseProgress> = emptyList(),
    /** 本次窗口内练到的肌群，相对“上次练它”的组数变化。 */
    val muscleProgress: List<MuscleProgress> = emptyList()
)

data class StallFact(
    val exercise: String,
    /** 最近连续几次训练 e1RM 没有提升。 */
    val sessionsFlat: Int,
    val lastWeightKg: Double,
    val lastReps: Int,
    val lastE1rm: Double,
    val trendLast3: List<Double>
)

data class RpeDriftFact(
    val exercise: String,
    val weightKg: Double,
    val earlierRpe: Double,
    val recentRpe: Double
)

data class SetFadeFact(
    val exercise: String,
    val firstSetReps: Int,
    val lastSetReps: Int,
    val fadePct: Double
)

/**
 * 单个动作相对**上次练它**的进步对比。
 *
 * “上次” = 窗口开始日之前、最近一次包含该动作的会话（不是固定周期），
 * 所以今日和本周两份报告都能用同一套规则 —— 用户问的就是“比上次怎么样”。
 */
data class ExerciseProgress(
    val exercise: String,
    val prevDate: java.time.LocalDate,
    val prevTopWeightKg: Double,
    val prevTopReps: Int,
    val prevE1rm: Double,
    val curTopWeightKg: Double,
    val curTopReps: Int,
    val curE1rm: Double
) {
    val e1rmDelta: Double get() = ((curE1rm - prevE1rm) * 10).roundToInt() / 10.0
    val e1rmDeltaPct: Double get() = if (prevE1rm > 0.0) (curE1rm - prevE1rm) / prevE1rm * 100.0 else 0.0
    /** 有提升（给 0.05kg 容差，避免浮点噪声报成“进步”） */
    val improved: Boolean get() = e1rmDelta > 0.05
}

/** 单个肌群相对**上次练它**的组数对比。 */
data class MuscleProgress(
    val muscle: String,
    val prevDate: java.time.LocalDate,
    val prevSets: Double,
    val curSets: Double
) {
    val deltaSets: Double get() = ((curSets - prevSets) * 10).roundToInt() / 10.0
}

/** 今天这一次训练的具体内容（今日报告用）。 */
data class TodayWorkout(
    val dateLabel: String,
    val title: String,
    val volumeKg: Double,
    val completedSets: Int,
    val durationMinutes: Int?,
    val notes: String,
    val exercises: List<TodayExercise>
)

data class TodayExercise(val name: String, val sets: List<TodaySet>)

/** 只保留必要的字段，不把原始 id/时间戳发给模型。 */
data class TodaySet(val weightKg: Double, val reps: Int, val type: String, val rpe: Double?)

/**
 * 训练事实计算器。输入全是仓储已有的三份列表，输出纯数据。
 *
 * @param sets 该动作在**所有已完成会话**里的完成组（未完成/热身会被内部过滤）。
 */
object WorkoutAnalyzer {

    /** 连续多少次训练 e1RM 没提升就判定停滞。3 次 = 约 1.5~3 周，够长但不过敏。 */
    const val STALL_SESSIONS = 3
    /** 同一动作内 RPE 抬升多少算疲劳漂移。 */
    const val RPE_DRIFT_THRESHOLD = 1.0
    /** 末组比首组掉多少次数算重量偏重。 */
    const val SET_FADE_THRESHOLD_PCT = 20.0

    fun analyze(
        sessions: List<WorkoutSessionEntity>,
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>,
        weeks: Int = 4,
        /**
         * 只看今天那一天（今日报告用）。
         *
         * 为什么必须单独开这个开关：以前「今日」只是把 weeks 设成 1（= 本周），
         * 结果模型看到的是「本周肩 17 组」而用户今天只练了 7 组 —— 数字和标题对不上。
         * 今日报告的部位组数 / 容量 / 次数必须是**当天**的。
         */
        singleDay: Boolean = false,
        today: java.time.LocalDate = java.time.LocalDate.now(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ): TrainingFacts {
        // 口径必须与日历打卡 / 负荷页一致：**「有完成组的会话」就算一次训练**，
        // 不看 session.status。用户常常从不点「完成」，会话一直是 ACTIVE，
        // 按 status 过滤会让整场训练从分析里凭空消失（日历却还显示着）。
        val trainedIds = sets.filter { it.isCompleted }.map { it.sessionId }.toSet()
        val trainedSessions = sessions.filter { it.id in trainedIds }

        // 窗口：单日模式 = 只含今天；否则 = 近 N 周 vs 再往前 N 周
        val currentWindow = if (singleDay) {
            DateWindow(today, today.plusDays(1))
        } else {
            PeriodReviewCalculator.lastWeeks(today, weeks)
        }
        val previousWindow = if (singleDay) {
            // 单日模式不使用上期（容量环比对未来无意义），保留窗口仅为了不破坏结构
            null
        } else {
            DateWindow(currentWindow.from.minusWeeks(weeks.toLong()), currentWindow.from)
        }
        val dayOfSession = trainedSessions.associate { it.id to PeriodReviewCalculator.instantToLocalDate(it.startTime, zone) }
        val inCurrent = trainedSessions.filter { currentWindow.contains(dayOfSession[it.id]!!) }
        val inPrevious = if (previousWindow == null) emptyList()
            else trainedSessions.filter { previousWindow.contains(dayOfSession[it.id]!!) }

        val currentSets = PeriodReviewCalculator.setsOf(sets, inCurrent)
        val previousSets = PeriodReviewCalculator.setsOf(sets, inPrevious)

        val currentVolume = PeriodReviewCalculator.volumeOf(currentSets)
        val previousVolume = PeriodReviewCalculator.volumeOf(previousSets)

        // 推/拉/腿 组数：按会话标题归类
        val typeBySession = trainedSessions.associate { it.id to com.baoji.fitness.domain.model.workoutTypeLabel(it.title) }
        val effective = PeriodReviewCalculator.effectiveSets(currentSets)
        val byType = effective.groupBy { typeBySession[it.sessionId] ?: "练" }
            .mapValues { (_, v) -> v.size.toDouble() }

        val muscleCounts = PeriodReviewCalculator.muscleSetCounts(currentSets, exercises)

        return TrainingFacts(
            // 单日模式用 0 表示“不是周窗口”，供提示词与本地报告区分措辞
            weeksCovered = if (singleDay) 0 else weeks,
            sessionCount = inCurrent.size,
            totalVolumeKg = currentVolume,
            // 单日模式不跟昨天比容量（“今天比昨天多 300kg”没意义，练的部位可能完全不同）
            volumeDeltaPct = if (singleDay) null
                else if (previousVolume > 0) (currentVolume - previousVolume) / previousVolume * 100.0
                else null,
            // 单日模式不需要周趋势线（那是周/月报告的东西，拿它当“今日”会误导）
            weeklyVolume = if (singleDay) emptyList() else
                PeriodReviewCalculator.buildWeeklyTrend(sets, sessions, weeks, today, zone)
                    .map { it.label to it.volumeKg },
            muscles = PeriodReviewCalculator.TRACKED_MUSCLES.map {
                OverloadCalculator.evaluateMuscleVolume(it, muscleCounts[it] ?: 0.0)
            },
            pushPullSets = byType,
            stalls = detectStalls(sets, exercises),
            rpeDrift = detectRpeDrift(sets, exercises),
            setFade = detectSetFade(sets, exercises),
            recentNotes = inCurrent.sortedByDescending { it.startTime }
                .map { it.notes }.filter { it.isNotBlank() }.take(5),
            // 进步对比：比“上次练它”，不看固定周期 —— 今日与本周共用同一套规则
            exerciseProgress = detectExerciseProgress(
                sessions, sets, exercises, currentWindow.from, zone
            ),
            muscleProgress = detectMuscleProgress(
                sessions, sets, exercises, currentWindow.from, zone
            ),
            latestWeightKg = null, // 体重由调用方（需要 BodyMetricRepository）补，保持本函数零额外依赖
            streakDays = StreakCalculator.currentStreak(
                trainedSessions.map { PeriodReviewCalculator.instantToLocalDate(it.startTime, zone) }.toSet(),
                today
            )
        )
    }

    /** 取最近一次有完成组的训练，摆成今日报告要的形状。没练则返回 null。 */
    fun buildToday(
        sessions: List<WorkoutSessionEntity>,
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>,
        today: java.time.LocalDate = java.time.LocalDate.now(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ): TodayWorkout? {
        val nameById = exercises.associate { it.id to it.name }
        val completed = sets.filter { it.isCompleted }
        val sessionIdsWithSets = completed.map { it.sessionId }.toSet()

        // 只看**今天**。以前今天没练会回退到最近一次，导致休息日点“今日分析”
        // 会拿上一次训练冒充“今天”，是错的 —— 今天没练就应该直说没练。
        val candidate = sessions
            .filter { it.id in sessionIdsWithSets }
            .filter { PeriodReviewCalculator.instantToLocalDate(it.startTime, zone) == today }
            .maxByOrNull { it.startTime }
            ?: return null

        val sessionSets = completed.filter { it.sessionId == candidate.id }
        val effective = sessionSets.filter { it.setType != "WARMUP" }
        // 有热身组也列出来，让模型能看出“热身是否做够”
        val grouped = sessionSets.groupBy { it.exerciseId }
        val order = sessionSets.map { it.exerciseId }.distinct()

        return TodayWorkout(
            dateLabel = PeriodReviewCalculator.instantToLocalDate(candidate.startTime, zone).toString(),
            title = candidate.title,
            volumeKg = effective.sumOf { it.weightKg * it.reps },
            completedSets = sessionSets.size,
            durationMinutes = candidate.endTime?.let { ((it - candidate.startTime) / 60000L).toInt() },
            notes = candidate.notes,
            exercises = order.map { exId ->
                TodayExercise(
                    name = nameById[exId] ?: "动作 #$exId",
                    sets = grouped[exId].orEmpty().sortedBy { it.setOrder }.map {
                        TodaySet(it.weightKg, it.reps, it.setType, it.rpe)
                    }
                )
            }
        )
    }

    /**
     * 动作进步对比：窗口内练到的每个动作，各找“窗口开始日之前最近一次练它”的会话，
     * 比 e1RM（不只看重量 —— 60kg×12 比 60kg×8 进步了）。
     *
     * 没练过的动作不列（不编造“上次”）。
     */
    fun detectExerciseProgress(
        sessions: List<WorkoutSessionEntity>,
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>,
        windowStart: java.time.LocalDate,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ): List<ExerciseProgress> {
        val nameById = exercises.associate { it.id to it.name }
        val effective = PeriodReviewCalculator.effectiveSets(sets).filter { it.completedAt != null }
        val dayOfSession = sessions.associate { it.id to PeriodReviewCalculator.instantToLocalDate(it.startTime, zone) }

        // 该动作在某场里的最好一组（按 e1RM 取，而非只看重量）
        fun bestIn(group: List<WorkoutSetEntity>): WorkoutSetEntity? =
            group.maxByOrNull { OverloadCalculator.calculateEstimated1RM(it.weightKg, it.reps) }

        return effective.groupBy { it.exerciseId }.mapNotNull { (exerciseId, exSets) ->
            // 窗口内那场（取最新的）
            val inWindow = exSets.filter { s ->
                val d = dayOfSession[s.sessionId] ?: return@filter false
                !d.isBefore(windowStart)
            }
            if (inWindow.isEmpty()) return@mapNotNull null
            val curSessionId = inWindow.maxByOrNull { it.completedAt!! }!!.sessionId
            val curBest = bestIn(inWindow.filter { it.sessionId == curSessionId }) ?: return@mapNotNull null

            // 窗口开始日前、最近一次练它的那场
            val before = exSets.filter { s ->
                val d = dayOfSession[s.sessionId] ?: return@filter false
                d.isBefore(windowStart)
            }
            if (before.isEmpty()) return@mapNotNull null
            val prevSessionId = before.maxByOrNull { it.completedAt!! }!!.sessionId
            val prevBest = bestIn(before.filter { it.sessionId == prevSessionId }) ?: return@mapNotNull null

            ExerciseProgress(
                exercise = nameById[exerciseId] ?: "动作 #$exerciseId",
                prevDate = dayOfSession[prevSessionId] ?: windowStart,
                prevTopWeightKg = prevBest.weightKg,
                prevTopReps = prevBest.reps,
                prevE1rm = OverloadCalculator.calculateEstimated1RM(prevBest.weightKg, prevBest.reps),
                curTopWeightKg = curBest.weightKg,
                curTopReps = curBest.reps,
                curE1rm = OverloadCalculator.calculateEstimated1RM(curBest.weightKg, curBest.reps)
            )
        }.sortedByDescending { it.e1rmDeltaPct }
    }

    /**
     * 肌群组数对比：窗口内练到的每个肌群 vs “窗口开始日之前、上次练它”的那一天。
     * 口径与 [PeriodReviewCalculator.muscleSetCounts] 完全一致，不另起一套。
     */
    fun detectMuscleProgress(
        sessions: List<WorkoutSessionEntity>,
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>,
        windowStart: java.time.LocalDate,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ): List<MuscleProgress> {
        val trainedIds = sets.filter { it.isCompleted }.map { it.sessionId }.toSet()
        val trained = sessions.filter { it.id in trainedIds }
            .sortedByDescending { it.startTime }
        val dayOfSession = trained.associate { it.id to PeriodReviewCalculator.instantToLocalDate(it.startTime, zone) }

        val inWindow = trained.filter { !(dayOfSession[it.id]!!).isBefore(windowStart) }
        val beforeWindow = trained.filter { (dayOfSession[it.id]!!).isBefore(windowStart) }
        if (inWindow.isEmpty() || beforeWindow.isEmpty()) return emptyList()

        val curCounts = PeriodReviewCalculator.muscleSetCounts(
            PeriodReviewCalculator.setsOf(sets, inWindow), exercises
        )

        // “上次练它” = 往前找最近的、该肌群组数 > 0 的那一天（不是固定上一周期）
        val byDay = beforeWindow.groupBy { dayOfSession[it.id]!! }.toSortedMap()
        val prevCountsPerDay = byDay.mapValues { (_, daySessions) ->
            PeriodReviewCalculator.muscleSetCounts(
                PeriodReviewCalculator.setsOf(sets, daySessions), exercises
            )
        }

        return PeriodReviewCalculator.TRACKED_MUSCLES.mapNotNull { muscle ->
            val cur = curCounts[muscle] ?: 0.0
            if (cur <= 0.0) return@mapNotNull null
            // 从最近的往早找，第一个练过该肌群的日子就是“上次”
            val prevDay = prevCountsPerDay.keys.sortedDescending()
                .firstOrNull { (prevCountsPerDay[it]?.get(muscle) ?: 0.0) > 0.0 }
                ?: return@mapNotNull null
            MuscleProgress(
                muscle = muscle,
                prevDate = prevDay,
                prevSets = prevCountsPerDay.getValue(prevDay)[muscle] ?: 0.0,
                curSets = cur
            )
        }
    }

    /**
     * 停滞 = 最近 [STALL_SESSIONS] 次该动作训练的 e1RM 最大值都没有超过更早的历史最好值。
     * 每次训练取该场的最大 e1RM（一次训练只算一个点，避免组数多寡影响）。
     */
    fun detectStalls(sets: List<WorkoutSetEntity>, exercises: List<ExerciseEntity>): List<StallFact> {
        val nameById = exercises.associate { it.id to it.name }
        return PeriodReviewCalculator.effectiveSets(sets)
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, exSets) ->
                // (会话, 该场最大 e1RM, 该场最好的那组)
                val perSession = exSets.filter { it.completedAt != null }
                    .groupBy { it.sessionId }
                    .map { (sessionId, s) ->
                        val best = s.maxByOrNull { OverloadCalculator.calculateEstimated1RM(it.weightKg, it.reps) }!!
                        Triple(sessionId, OverloadCalculator.calculateEstimated1RM(best.weightKg, best.reps), best)
                    }
                    .sortedBy { it.first } // 会话 id 递增 ≈ 时间递增（自增主键）

                if (perSession.size < STALL_SESSIONS) return@mapNotNull null

                val e1rms = perSession.map { it.second }
                val bestEarlier = e1rms.dropLast(STALL_SESSIONS).maxOrNull() ?: return@mapNotNull null
                val recentWindow = e1rms.takeLast(STALL_SESSIONS)
                val isStalled = recentWindow.none { it > bestEarlier + 0.01 }
                if (!isStalled) return@mapNotNull null

                val last = perSession.last().third
                StallFact(
                    exercise = nameById[exerciseId] ?: "动作 #$exerciseId",
                    sessionsFlat = STALL_SESSIONS,
                    lastWeightKg = last.weightKg,
                    lastReps = last.reps,
                    lastE1rm = OverloadCalculator.calculateEstimated1RM(last.weightKg, last.reps),
                    trendLast3 = recentWindow.map { (it * 10).toInt() / 10.0 }
                )
            }
            .sortedByDescending { it.sessionsFlat }
    }

    /** 同动作：最近一组的 RPE 比更早的同重量组高 [RPE_DRIFT_THRESHOLD] → 疲劳累积。 */
    fun detectRpeDrift(sets: List<WorkoutSetEntity>, exercises: List<ExerciseEntity>): List<RpeDriftFact> {
        val nameById = exercises.associate { it.id to it.name }
        return PeriodReviewCalculator.effectiveSets(sets)
            .filter { it.rpe != null }
            .groupBy { it.exerciseId }
            .mapNotNull { (exerciseId, exSets) ->
                val ordered = exSets.filter { it.completedAt != null }.sortedBy { it.completedAt }
                if (ordered.size < 4) return@mapNotNull null
                val earlier = ordered.take(ordered.size / 2).mapNotNull { it.rpe }.average()
                val recent = ordered.takeLast(ordered.size / 2).mapNotNull { it.rpe }.average()
                if (recent - earlier < RPE_DRIFT_THRESHOLD) return@mapNotNull null
                RpeDriftFact(
                    exercise = nameById[exerciseId] ?: "动作 #$exerciseId",
                    weightKg = ordered.last().weightKg,
                    earlierRpe = (earlier * 10).toInt() / 10.0,
                    recentRpe = (recent * 10).toInt() / 10.0
                )
            }
    }

    /** 同一次训练里，某动作末组次数比首组掉超过阈值 → 该重量偏重。 */
    fun detectSetFade(sets: List<WorkoutSetEntity>, exercises: List<ExerciseEntity>): List<SetFadeFact> {
        val nameById = exercises.associate { it.id to it.name }
        return PeriodReviewCalculator.effectiveSets(sets)
            .groupBy { it.sessionId to it.exerciseId }
            .mapNotNull { (key, group) ->
                val ordered = group.filter { it.completedAt != null }.sortedBy { it.setOrder }
                if (ordered.size < 3) return@mapNotNull null
                val first = ordered.first().reps
                val last = ordered.last().reps
                if (first <= 0) return@mapNotNull null
                val fadePct = (first - last) * 100.0 / first
                if (fadePct < SET_FADE_THRESHOLD_PCT) return@mapNotNull null
                SetFadeFact(
                    exercise = nameById[key.second] ?: "动作 #${key.second}",
                    firstSetReps = first,
                    lastSetReps = last,
                    fadePct = (fadePct * 10).toInt() / 10.0
                )
            }
    }
}