package com.baoji.fitness.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.domain.algorithm.OverloadCalculator
import com.baoji.fitness.domain.model.MuscleVolumeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

data class OverloadDataPoint(
    val weekLabel: String,
    val e1rm: Double,
    val isPR: Boolean = false
)

class AnalyticsViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    val weeklyMuscleVolumes: StateFlow<List<MuscleVolumeStatus>> = repository.getWeeklyMuscleVolumes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedExercise = MutableStateFlow("平板杠铃卧推")
    val selectedExercise: StateFlow<String> = _selectedExercise.asStateFlow()

    // 8-week progressive overload historical E1RM curve
    val e1rmHistory: StateFlow<List<OverloadDataPoint>> = MutableStateFlow(
        listOf(
            OverloadDataPoint("W1", 85.0),
            OverloadDataPoint("W2", 86.0),
            OverloadDataPoint("W3", 87.5),
            OverloadDataPoint("W4", 90.0),
            OverloadDataPoint("W5", 92.0),
            OverloadDataPoint("W6", 93.5),
            OverloadDataPoint("W7", 95.0),
            OverloadDataPoint("W8", 98.5, isPR = true)
        )
    ).asStateFlow()

    val currentProgressionAdvice = OverloadCalculator.evaluateProgression(
        currentWeightKg = 80.0,
        currentReps = 8,
        targetRepMax = 8,
        rpe = 8.0
    )

    fun selectExercise(name: String) {
        _selectedExercise.value = name
    }
}
