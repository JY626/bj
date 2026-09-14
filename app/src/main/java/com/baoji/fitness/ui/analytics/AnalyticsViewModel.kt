package com.baoji.fitness.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.domain.algorithm.OverloadCalculator
import com.baoji.fitness.domain.model.MuscleVolumeStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OverloadDataPoint(
    val label: String,
    val e1rm: Double,
    val isPR: Boolean = false
)

data class ExerciseAnalyticsState(
    val currentE1RM: Double = 0.0,
    val bestSetText: String = "暂无正式组",
    val percentageIncreaseText: String = "稳步提升中",
    val points: List<OverloadDataPoint> = emptyList(),
    val adviceMessage: String = "持续完成正式组记录，系统将自动基于双重渐进算法生成加重建议。"
)

class AnalyticsViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    val weeklyMuscleVolumes: StateFlow<List<MuscleVolumeStatus>> = repository.getWeeklyMuscleVolumes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exercises: StateFlow<List<ExerciseEntity>> = repository.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedExerciseId = MutableStateFlow<Long?>(null)
    val selectedExerciseId: StateFlow<Long?> = _selectedExerciseId.asStateFlow()

    private val _analyticsState = MutableStateFlow(ExerciseAnalyticsState())
    val analyticsState: StateFlow<ExerciseAnalyticsState> = _analyticsState.asStateFlow()

    init {
        // Auto-select first exercise (typically Bench Press) when loaded
        viewModelScope.launch {
            exercises.collect { list ->
                if (_selectedExerciseId.value == null && list.isNotEmpty()) {
                    val defaultEx = list.find { it.name == "卧推" } ?: list.first()
                    _selectedExerciseId.value = defaultEx.id
                }
            }
        }

        // Observe history for selected exercise
        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            _selectedExerciseId.flatMapLatest { id ->
                if (id != null) {
                    repository.getExerciseHistory(id)
                } else {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                }
            }.collect { sets ->
                val currentId = _selectedExerciseId.value
                val exercise = exercises.value.find { it.id == currentId }
                computeAnalyticsState(exercise, sets)
            }
        }
    }

    private fun computeAnalyticsState(
        exercise: ExerciseEntity?,
        completedSets: List<com.baoji.fitness.core.database.entity.WorkoutSetEntity>
    ) {
        if (completedSets.isEmpty()) {
            // If no recorded sets yet, use personalRecord if available, or clean starter state
            val baseWeight = exercise?.personalRecordWeight ?: 0.0
            val baseReps = exercise?.personalRecordReps ?: 0
            if (baseWeight > 0 && baseReps > 0) {
                val e1rm = OverloadCalculator.calculateEstimated1RM(baseWeight, baseReps)
                _analyticsState.value = ExerciseAnalyticsState(
                    currentE1RM = e1rm,
                    bestSetText = "$baseWeight kg × $baseReps",
                    percentageIncreaseText = "基准参考",
                    points = listOf(
                        OverloadDataPoint("基准", e1rm, isPR = true)
                    ),
                    adviceMessage = "已设置基准历史记录。今天在训练中完成任意一组，即可生成首个突破点！"
                )
            } else {
                _analyticsState.value = ExerciseAnalyticsState()
            }
            return
        }

        // Group sets by sessionId
        val sessionsGrouped = completedSets.groupBy { it.sessionId }
        var maxHistoricalE1RM = 0.0
        val points = mutableListOf<OverloadDataPoint>()

        var sessionIndex = 1
        sessionsGrouped.forEach { (_, setsInSession) ->
            val maxE1RMInSession = setsInSession.maxOfOrNull {
                OverloadCalculator.calculateEstimated1RM(it.weightKg, it.reps)
            } ?: 0.0

            val isPR = maxE1RMInSession > maxHistoricalE1RM
            if (isPR) {
                maxHistoricalE1RM = maxE1RMInSession
            }

            points.add(
                OverloadDataPoint(
                    label = "S$sessionIndex",
                    e1rm = maxE1RMInSession,
                    isPR = isPR
                )
            )
            sessionIndex++
        }

        val bestOverallSet = completedSets.maxByOrNull {
            OverloadCalculator.calculateEstimated1RM(it.weightKg, it.reps)
        }
        val latestSet = completedSets.last()
        val advice = OverloadCalculator.evaluateProgression(
            currentWeightKg = latestSet.weightKg,
            currentReps = latestSet.reps,
            targetRepMax = 8
        )

        val firstE1RM = points.firstOrNull()?.e1rm ?: maxHistoricalE1RM
        val diffPercent = if (firstE1RM > 0) {
            val inc = ((maxHistoricalE1RM - firstE1RM) / firstE1RM) * 100
            String.format("+%.1f%%", if (inc > 0) inc else 0.0)
        } else "稳步提升中"

        _analyticsState.value = ExerciseAnalyticsState(
            currentE1RM = maxHistoricalE1RM,
            bestSetText = "${bestOverallSet?.weightKg ?: 0.0} kg × ${bestOverallSet?.reps ?: 0}",
            percentageIncreaseText = "$diffPercent 本周期",
            points = points,
            adviceMessage = advice.message
        )
    }

    fun selectExercise(exerciseId: Long) {
        _selectedExerciseId.value = exerciseId
    }
}
