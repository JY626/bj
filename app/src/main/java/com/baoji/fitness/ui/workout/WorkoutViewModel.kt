package com.baoji.fitness.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.data.settings.ReminderSettings
import com.baoji.fitness.domain.algorithm.OverloadCalculator
import com.baoji.fitness.domain.algorithm.SessionDayGate
import com.baoji.fitness.domain.algorithm.StreakCalculator
import com.baoji.fitness.domain.model.PresetTemplates
import com.baoji.fitness.domain.model.WorkoutDayDetail
import com.baoji.fitness.domain.model.WorkoutRoutineTemplate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExerciseGroup(
    val exercise: ExerciseEntity,
    val sets: List<WorkoutSetEntity>,
    val benchmark1RM: Double = 0.0,
    val previousSessionSets: Map<Int, WorkoutSetEntity> = emptyMap(),
    val benchmarkSummary: String = ""
)

/** M2：训练页归档态那句「连续 13 天，明天是休息日」。 */
data class WorkoutHabit(
    val currentStreak: Int = 0,
    val isRestDayTomorrow: Boolean = false
)

class WorkoutViewModel(
    private val repository: WorkoutRepository,
    private val reminderSettings: ReminderSettings
) : ViewModel() {

    // Reactive active session from Room
    val activeSession: StateFlow<WorkoutSessionEntity?> = repository.getActiveSession()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Pure reactive Exercise Groups pipeline driven by activeSession
    @OptIn(ExperimentalCoroutinesApi::class)
    val exerciseGroups: StateFlow<List<ExerciseGroup>> = activeSession
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                combine(
                    repository.getSetsForSession(session.id),
                    repository.getAllExercises()
                ) { sets, exercises ->
                    val exerciseMap = exercises.associateBy { it.id }
                    val grouped = sets.groupBy { it.exerciseId }
                    val exerciseOrder = sets.map { it.exerciseId }.distinct()

                    exerciseOrder.mapNotNull { exerciseId ->
                        val setList = grouped[exerciseId] ?: return@mapNotNull null
                        val exercise = exerciseMap[exerciseId] ?: return@mapNotNull null

                        val prevSetsMap = repository.getPreviousSessionSetsForExercise(exerciseId, session.id)
                        val topPrev = prevSetsMap.values.maxByOrNull { it.weightKg }
                        val benchmarkSummary = when {
                            topPrev == null || (exercise.trackWeight && topPrev.weightKg <= 0.0) -> "首次训练 · 暂无基准"
                            !exercise.trackWeight -> "前次基准: × ${topPrev.reps} 次"
                            else -> "前次基准: ${if (topPrev.weightKg % 1.0 == 0.0) topPrev.weightKg.toInt().toString() else topPrev.weightKg.toString()} kg × ${topPrev.reps}"
                        }

                        val bestWeight = topPrev?.weightKg ?: exercise.personalRecordWeight
                        val bestReps = topPrev?.reps ?: exercise.personalRecordReps
                        val e1rm = OverloadCalculator.calculateEstimated1RM(bestWeight, bestReps)

                        ExerciseGroup(
                            exercise = exercise,
                            sets = setList.sortedBy { it.setOrder },
                            benchmark1RM = e1rm,
                            previousSessionSets = prevSetsMap,
                            benchmarkSummary = benchmarkSummary
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // All available exercises for adding to session
    val allExercises: StateFlow<List<ExerciseEntity>> = repository.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 「今天是哪天」的可观察版本：App 常在后台活过午夜，写死 `LocalDate.now()` 会导致
     * 跨天后仍展示昨天的归档明细/打卡状态。这里用一个可变的"今天"，
     * 由 [rollOverIfNewDay] 在回到前台时推进。
     */
    private val _today = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun rollOverIfNewDay() {
        val actual = LocalDate.now()
        if (actual != _today.value) _today.value = actual
        viewModelScope.launch { archiveIdlePastSessions(System.currentTimeMillis()) }
    }

    /**
     * 归档「过期且已闲置」的会话。只归档、**绝不自动开新场**。
     * 自动开新场会在每次冷启动都生一场垃圾会话（曾经的 bug）。
     */
    private suspend fun archiveIdlePastSessions(now: Long) {
        val stale = SessionDayGate.sessionsToArchive(
            activeSessions = repository.getActiveSessions(),
            sets = repository.getAllCompletedSets().first(),
            now = now
        )
        if (stale.isNotEmpty()) repository.archiveSessions(stale)
    }

    /** 今日已完成（归档）训练的明细：会话结束后训练页仍然展示当天数据。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val todaySummary: StateFlow<WorkoutDayDetail?> = _today
        .flatMapLatest { repository.getDayDetail(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** M2：归档态的习惯文案数据。 */
    val habit: StateFlow<WorkoutHabit> = combine(
        repository.getTrainedDayTypes(),
        reminderSettings.config,
        _today
    ) { days, config, today ->
        WorkoutHabit(
            currentStreak = StreakCalculator.currentStreak(days.keys, today),
            isRestDayTomorrow = StreakCalculator.isRestDayTomorrow(today, config.trainingDays)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkoutHabit())

    // Timer State
    private val _restSecondsLeft = MutableStateFlow(0)
    val restSecondsLeft: StateFlow<Int> = _restSecondsLeft.asStateFlow()

    private val _isRestTimerVisible = MutableStateFlow(false)
    val isRestTimerVisible: StateFlow<Boolean> = _isRestTimerVisible.asStateFlow()

    private var timerJob: Job? = null

    /**
     * 本次训练已用时（秒）。会话开始时起算，每秒推进。
     * 用 ticker 而不是在 Composable 里重算 —— 后者只在重组时刷新，不会走秒。
     */
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private var sessionTickerJob: Job? = null

    private fun startSessionTicker(startTime: Long) {
        sessionTickerJob?.cancel()
        sessionTickerJob = viewModelScope.launch {
            while (true) {
                _elapsedSeconds.value = ((System.currentTimeMillis() - startTime) / 1000L).coerceAtLeast(0L)
                delay(1000)
            }
        }
    }

    init {
        viewModelScope.launch {
            // 跨天：把「开始日早于今天、且已闲置 6h」的会话归档。
            // 只归档，不自动开新场 —— 开新场交给用户点「计划」。
            archiveIdlePastSessions(System.currentTimeMillis())

            val currentActive = repository.getActiveSession().first()
            currentActive?.let { startSessionTicker(it.startTime) }
            when {
                currentActive == null -> {
                    // 全新安装（一条会话都没有）：给一场推日，否则首页没东西可点。
                    // 已有历史时保持归档态，让用户自己选今天练哪天。
                    if (repository.getAllSessions().first().isEmpty()) {
                        startNewWorkoutFromTemplate(PresetTemplates.PUSH)
                    }
                }
                // 空壳会话（动作库为空时初始化挂死留下的残留，或历史 bug）按原标题重新填动作
                repository.getSetsForSession(currentActive.id).first().isEmpty() -> {
                    val matchingTemplate = PresetTemplates.allTemplates.firstOrNull {
                        it.name == currentActive.title || currentActive.title.contains(it.name)
                    } ?: PresetTemplates.PUSH
                    startNewWorkoutFromTemplate(matchingTemplate)
                }
            }
        }
    }

    fun startNewWorkoutFromTemplate(template: WorkoutRoutineTemplate) {
        viewModelScope.launch {
            val sessionId = repository.startNewSession(template.name)
            // 新场次：计时归零重走
            startSessionTicker(System.currentTimeMillis())

            // 核心功能：优先读取上一次该训练日实际做过的动作列表（动态记忆用户习惯）
            val previousExerciseIds = repository.getPreviousExerciseIdsForRoutine(template.name)

            val exercisesToUse: List<ExerciseEntity> = if (previousExerciseIds.isNotEmpty()) {
                previousExerciseIds.mapNotNull { repository.getExerciseById(it) }
            } else {
                // 首次开始该训练日，使用默认模板动作初始化
                val allExercises = repository.getAllExercises().first { it.isNotEmpty() }
                val exerciseMap = allExercises.associateBy { it.name }

                template.defaultExerciseNames.map { exerciseName ->
                    var exercise = exerciseMap[exerciseName]
                        ?: allExercises.find { it.name.contains(exerciseName) || exerciseName.contains(it.name) }

                    if (exercise == null) {
                        val targetMuscle = when {
                            exerciseName.contains("卧推") || exerciseName.contains("胸") -> "胸部"
                            exerciseName.contains("硬拉") || exerciseName.contains("引体") || exerciseName.contains("划船") || exerciseName.contains("背") -> "背部"
                            exerciseName.contains("深蹲") || exerciseName.contains("腿") -> "腿部"
                            exerciseName.contains("肩推") || exerciseName.contains("飞鸟") || exerciseName.contains("面拉") || exerciseName.contains("肩") -> "肩部"
                            exerciseName.contains("二头") || exerciseName.contains("三头") || exerciseName.contains("手臂") -> "手臂"
                            exerciseName.contains("腹肌") || exerciseName.contains("核心") -> "核心"
                            else -> "全身"
                        }
                        val newEntity = ExerciseEntity(
                            name = exerciseName,
                            targetMuscle = targetMuscle,
                            equipment = "自由重量",
                            personalRecordWeight = 0.0,
                            personalRecordReps = 0
                        )
                        val newId = repository.insertExercise(newEntity)
                        exercise = newEntity.copy(id = newId)
                    }
                    exercise
                }
            }

            // 对选定的动作列表（继承自上次训练或首次默认），初始化 3 组并继承上一次各组真实成绩
            exercisesToUse.forEach { exercise ->
                val prevSets = repository.getPreviousSessionSetsForExercise(exercise.id, sessionId)
                val defaultWeight = prevSets[1]?.weightKg ?: 0.0
                val defaultReps = prevSets[1]?.reps ?: 10

                repository.addSet(sessionId, exercise.id, defaultWeight, defaultReps, 1, "NORMAL")
                repository.addSet(sessionId, exercise.id, defaultWeight, defaultReps, 2, "NORMAL")
                repository.addSet(sessionId, exercise.id, defaultWeight, defaultReps, 3, "NORMAL")
            }
        }
    }

    fun startCustomWorkout(title: String) {
        viewModelScope.launch {
            repository.startNewSession(title.ifBlank { "自定义训练日" })
            startSessionTicker(System.currentTimeMillis())
        }
    }

    fun resetCurrentSession() {
        val session = activeSession.value ?: return
        val matchingTemplate = PresetTemplates.allTemplates.find {
            it.name == session.title || session.title.contains(it.name) || it.name.contains(session.title)
        } ?: PresetTemplates.DAY1_PUSH
        startNewWorkoutFromTemplate(matchingTemplate)
    }

    fun toggleSetCompletion(set: WorkoutSetEntity) {
        viewModelScope.launch {
            val newState = !set.isCompleted
            repository.completeSet(set, newState)

            // 取消勾选不再杀死正在跑的休息计时（之前改错组会顺手把计时弄丢）
            if (newState && set.setType != "WARMUP") {
                // 打勾即采用：本组重量/次数自动继承给同动作下一未完成组，
                // 健身房 90% 的组不做改动，改了首组后续全部自动跟随。
                val nextOpenSet = exerciseGroups.value
                    .find { it.exercise.id == set.exerciseId }
                    ?.sets
                    ?.let { nextOpenSetAfter(it, set) }

                if (nextOpenSet != null) {
                    repository.updateSetWeightAndReps(nextOpenSet, set.weightKg, set.reps)
                }
                // 休息时长由设置决定，不再写死 90s（复合动作该给更久）
                startRestTimer(reminderSettings.config.value.restSeconds)
            }
        }
    }

    fun updateSet(set: WorkoutSetEntity, weightKg: Double, reps: Int) {
        viewModelScope.launch {
            repository.updateSetWeightAndReps(set, weightKg, reps)
        }
    }

    /** M3a：一次落库重量/次数/组类型/RPE（RPE 只作记录，不参与渐进判定）。 */
    fun applySetEdit(
        set: WorkoutSetEntity,
        weightKg: Double,
        reps: Int,
        setType: String,
        rpe: Double?
    ) {
        viewModelScope.launch {
            repository.saveSet(
                set.copy(weightKg = weightKg, reps = reps, setType = setType, rpe = rpe)
            )
        }
    }

    fun removeExerciseFromSession(exerciseId: Long) {
        val session = activeSession.value ?: return
        viewModelScope.launch {
            repository.removeExerciseFromSession(session.id, exerciseId)
        }
    }

    fun addExerciseToSession(exerciseId: Long) {
        val session = activeSession.value ?: return
        viewModelScope.launch {
            val prevSets = repository.getPreviousSessionSetsForExercise(exerciseId, session.id)
            val defaultWeight = prevSets[1]?.weightKg ?: 0.0
            val defaultReps = prevSets[1]?.reps ?: 8

            // Insert 3 initial sets for this newly added exercise
            repository.addSet(session.id, exerciseId, defaultWeight, defaultReps, 1, "NORMAL")
            repository.addSet(session.id, exerciseId, defaultWeight, defaultReps, 2, "NORMAL")
            repository.addSet(session.id, exerciseId, defaultWeight, defaultReps, 3, "NORMAL")
        }
    }

    fun addSet(exerciseId: Long) {
        val session = activeSession.value ?: return
        val currentSets = exerciseGroups.value.find { it.exercise.id == exerciseId }?.sets ?: emptyList()
        val nextOrder = currentSets.size + 1
        val lastSet = currentSets.lastOrNull()
        val weight = lastSet?.weightKg ?: 0.0
        val reps = lastSet?.reps ?: 8

        viewModelScope.launch {
            repository.addSet(session.id, exerciseId, weight, reps, nextOrder, "NORMAL")
        }
    }

    fun deleteSet(set: WorkoutSetEntity) {
        viewModelScope.launch {
            repository.deleteSetAndReorder(set)
        }
    }

    fun finishCurrentWorkout(notes: String = "") {
        val session = activeSession.value ?: return
        // 口径与训练页一致：热身组不计入总容量
        val sets = exerciseGroups.value.flatMap { it.sets }
            .filter { it.isCompleted && it.setType != "WARMUP" }
        val totalVolume = sets.sumOf { it.weightKg * it.reps }

        viewModelScope.launch {
            repository.finishSession(session, totalVolume, notes.trim())
            _isRestTimerVisible.value = false
            timerJob?.cancel()
            // 归档后停掉计时，归零（下次开练重新计）
            sessionTickerJob?.cancel()
            _elapsedSeconds.value = 0L
        }
    }

    private fun startRestTimer(seconds: Int) {
        timerJob?.cancel()
        _restSecondsLeft.value = seconds
        _isRestTimerVisible.value = true

        timerJob = viewModelScope.launch {
            while (_restSecondsLeft.value > 0) {
                delay(1000)
                _restSecondsLeft.value -= 1
            }
            _isRestTimerVisible.value = false
        }
    }

    fun add30sRest() {
        _restSecondsLeft.value += 30
    }

    fun skipRestTimer() {
        _restSecondsLeft.value = 0
        _isRestTimerVisible.value = false
        timerJob?.cancel()
    }

    companion object {
        /** 找同动作内 setOrder 大于刚完成组的第一条未完成组（打勾继承目标） */
        fun nextOpenSetAfter(sets: List<WorkoutSetEntity>, justCompleted: WorkoutSetEntity): WorkoutSetEntity? =
            sets.filter {
                !it.isCompleted && it.id != justCompleted.id && it.setOrder > justCompleted.setOrder
            }.minByOrNull { it.setOrder }
    }
}