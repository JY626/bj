package com.baoji.fitness.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.domain.algorithm.OverloadCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    val activeSession: StateFlow<WorkoutSessionEntity?> = repository.getActiveSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _exerciseGroups = MutableStateFlow<List<ExerciseGroup>>(emptyList())
    val exerciseGroups: StateFlow<List<ExerciseGroup>> = _exerciseGroups.asStateFlow()

    // Timer State
    private val _restSecondsLeft = MutableStateFlow(0)
    val restSecondsLeft: StateFlow<Int> = _restSecondsLeft.asStateFlow()

    private val _isRestTimerVisible = MutableStateFlow(false)
    val isRestTimerVisible: StateFlow<Boolean> = _isRestTimerVisible.asStateFlow()

    private var timerJob: Job? = null

    init {
        // Observe active session and populate exercises
        viewModelScope.launch {
            activeSession.collect { session ->
                if (session != null) {
                    observeSessionData(session.id)
                } else {
                    // Automatically bootstrap default push session for first-time use
                    val newId = repository.startNewSession("推力日 A · 胸肩三头")
                    observeSessionData(newId)
                }
            }
        }
    }

    private fun observeSessionData(sessionId: Long) {
        viewModelScope.launch {
            combine(
                repository.getSetsForSession(sessionId),
                repository.getAllExercises()
            ) { sets, exercises ->
                val exerciseMap = exercises.associateBy { it.id }

                // If session has no sets yet, populate default routine
                if (sets.isEmpty() && exercises.isNotEmpty()) {
                    populateInitialRoutine(sessionId, exercises)
                    return@combine emptyList<ExerciseGroup>()
                }

                val grouped = sets.groupBy { it.exerciseId }
                grouped.mapNotNull { (exerciseId, setList) ->
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
            }.collect { groups ->
                if (groups.isNotEmpty()) {
                    _exerciseGroups.value = groups
                }
            }
        }
    }

    private suspend fun populateInitialRoutine(sessionId: Long, exercises: List<ExerciseEntity>) {
        val bench = exercises.find { it.name.contains("平板杠铃卧推") } ?: exercises.firstOrNull()
        val incline = exercises.find { it.name.contains("上斜哑铃") }

        bench?.let {
            repository.addSet(sessionId, it.id, 60.0, 12, 1, "WARMUP")
            repository.addSet(sessionId, it.id, 80.0, 8, 2, "NORMAL")
            repository.addSet(sessionId, it.id, 82.5, 6, 3, "NORMAL")
            repository.addSet(sessionId, it.id, 77.5, 8, 4, "NORMAL")
        }
        incline?.let {
            repository.addSet(sessionId, it.id, 26.0, 10, 1, "NORMAL")
            repository.addSet(sessionId, it.id, 26.0, 9, 2, "NORMAL")
        }
    }

    fun toggleSetCompletion(set: WorkoutSetEntity) {
        viewModelScope.launch {
            val newState = !set.isCompleted
            repository.completeSet(set, newState)

            if (newState) {
                startRestTimer(90)
            }
        }
    }

    fun updateSet(set: WorkoutSetEntity, weightKg: Double, reps: Int) {
        viewModelScope.launch {
            repository.updateSetWeightAndReps(set, weightKg, reps)
        }
    }

    val allExercises = repository.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        val nextOrder = (lastSet?.setOrder ?: 0) + 1

        viewModelScope.launch {
            repository.addSet(session.id, exerciseId, weight, reps, nextOrder)
        }
    }

    fun deleteSet(set: WorkoutSetEntity) {
        viewModelScope.launch {
            repository.deleteSet(set)
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
