package com.baoji.fitness.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.domain.algorithm.OverloadCalculator
import com.baoji.fitness.domain.model.PresetTemplates
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

data class ExerciseGroup(
    val exercise: ExerciseEntity,
    val sets: List<WorkoutSetEntity>,
    val benchmark1RM: Double = 0.0,
    val previousSessionSets: Map<Int, WorkoutSetEntity> = emptyMap(),
    val benchmarkSummary: String = ""
)

class WorkoutViewModel(
    private val repository: WorkoutRepository
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
                        val benchmarkSummary = if (topPrev != null && topPrev.weightKg > 0.0) {
                            "前次基准: ${if (topPrev.weightKg % 1.0 == 0.0) topPrev.weightKg.toInt().toString() else topPrev.weightKg.toString()} kg × ${topPrev.reps}"
                        } else {
                            "首次训练 · 暂无基准"
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

    // Timer State
    private val _restSecondsLeft = MutableStateFlow(0)
    val restSecondsLeft: StateFlow<Int> = _restSecondsLeft.asStateFlow()

    private val _isRestTimerVisible = MutableStateFlow(false)
    val isRestTimerVisible: StateFlow<Boolean> = _isRestTimerVisible.asStateFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            // One-shot check on initial startup: only bootstrap if no active session AND no prior sessions exist
            val currentActive = repository.getActiveSession().first()
            if (currentActive == null) {
                val allSessions = repository.getAllSessions().first()
                if (allSessions.isEmpty()) {
                    startNewWorkoutFromTemplate(PresetTemplates.PUSH)
                }
            }
        }
    }

    fun startNewWorkoutFromTemplate(template: WorkoutRoutineTemplate) {
        viewModelScope.launch {
            val sessionId = repository.startNewSession(template.name)

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
                startRestTimer(90)
            }
        }
    }

    fun updateSet(set: WorkoutSetEntity, weightKg: Double, reps: Int) {
        viewModelScope.launch {
            repository.updateSetWeightAndReps(set, weightKg, reps)
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

    fun finishCurrentWorkout() {
        val session = activeSession.value ?: return
        val sets = exerciseGroups.value.flatMap { it.sets }.filter { it.isCompleted }
        val totalVolume = sets.sumOf { it.weightKg * it.reps }

        viewModelScope.launch {
            repository.finishSession(session, totalVolume)
            _isRestTimerVisible.value = false
            timerJob?.cancel()
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
