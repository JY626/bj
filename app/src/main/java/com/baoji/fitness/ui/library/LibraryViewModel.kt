package com.baoji.fitness.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.domain.algorithm.MuscleClassifier
import kotlinx.coroutines.launch

/**
 * 新建自定义动作：基于本地规则自动判断肌群与器械。
 */
class LibraryViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    /**
     * 创建动作。[onDone] 回调在主线程给出结果：
     * null = 成功；否则为要展示给用户的错误文案。
     */
    fun createExercise(name: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch onDone("动作名不能为空")

            repository.findExerciseByName(trimmed)?.let {
                return@launch onDone("「${it.name}」已存在，不用重复添加")
            }

            val classification = MuscleClassifier.classify(trimmed)

            repository.insertExercise(
                ExerciseEntity(
                    name = trimmed,
                    targetMuscle = classification.primary,
                    secondaryMuscles = classification.secondary.joinToString(", "),
                    equipment = classification.equipment ?: "其他",
                    isCustom = true,
                    trackWeight = classification.trackWeight ?: true
                )
            )
            onDone(null)
        }
    }
}