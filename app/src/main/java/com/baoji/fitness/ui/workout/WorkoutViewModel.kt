package com.baoji.fitness.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.domain.algorithm.OverloadCalculator
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
    val benchmark1RM: Double = 0.0
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
                        val bestWeight = exercise.personalRecordWeight
                        val bestReps = exercise.personalRecordReps
                        val e1rm = OverloadCalculator.calculateEstimated1RM(bestWeight, bestReps)
                        ExerciseGroup(
                            exercise = exercise,
                            sets = setList.sortedBy { it.setOrder },
                            benchmark1RM = e1rm
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
                    startNewWorkout("推力日 A · 胸肩三头")
                }
            }
        }
    }

    fun startNewWorkout(title: String = "推力日 A · 胸肩三头") {
        viewModelScope.launch {
            val sessionId = repository.startNewSession(title)
            val exercises = repository.getAllExercises().first { it.isNotEmpty() }
            val bench = exercises.find { it.name.contains("平板杠铃卧推") } ?: exercises.firstOrNull()
            val incline = exercises.find { it.name.contains("上斜哑铃") }

            bench?.let {
                repository.addSet(sessionId, it.id, 60.0, 12, 1, "WARMUP")
                repository.addSet(sessionId, it.id, 80.0, 8, 2, "NORMAL")
                repository.addSet(sessionId, it.id, 82.5, 6, 3, "NORMAL")
            }
            incline?.let {
                repository.addSet(sessionId, it.id, 26.0, 10, 1, "NORMAL")
                repository.addSet(sessionId, it.id, 26.0, 9, 2, "NORMAL")
                repository.addSet(sessionId, it.id, 26.0, 8, 3, "NORMAL")
            }
        }
    }

    fun resetCurrentSession() {
        val session = activeSession.value ?: return
        startNewWorkout(session.title)
    }

    fun toggleSetCompletion(set: WorkoutSetEntity) {
        viewModelScope.launch {
            val newState = !set.isCompleted
            repository.completeSet(set, newState)

            if (newState) {
                startRestTimer(90)
            } else {
                skipRestTimer()
            }
        }
    }

    fun updateSet(set: WorkoutSetEntity, weightKg: Double, reps: Int) {
        viewModelScope.launch {
            repository.updateSetWeightAndReps(set, weightKg, reps)
        }
    }

    fun addExerciseToSession(exerciseId: Long) {
        val session = activeSession.value ?: return
        viewModelScope.launch {
            val exercise = allExercises.value.find { it.id == exerciseId }
            val defaultWeight = if ((exercise?.personalRecordWeight ?: 0.0) > 0.0) {
                exercise!!.personalRecordWeight
            } else 40.0
            val defaultReps = if ((exercise?.personalRecordReps ?: 0) > 0) {
                exercise!!.personalRecordReps
            } else 8

            // Insert 3 initial sets for this newly added exercise
            repository.addSet(session.id, exerciseId, defaultWeight, defaultReps, 1, "NORMAL")
            repository.addSet(session.id, exerciseId, defaultWeight, defaultReps, 2, "NORMAL")
            repository.addSet(session.id, exerciseId, defaultWeight, defaultReps, 3, "NORMAL")
        }
    }

    fun addSet(exerciseId: Long) {
        val session = activeSession.value ?: return
        val currentSets = exerciseGroups.value.find { it.exercise.id == exerciseId }?.sets ?: emptyList()
        val lastSet = currentSets.lastOrNull()
        val weight = lastSet?.weightKg ?: 60.0
        val reps = lastSet?.reps ?: 8
        val nextOrder = currentSets.size + 1

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
}
