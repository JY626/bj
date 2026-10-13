package com.baoji.fitness.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.data.llm.CoachLlmClient
import com.baoji.fitness.data.llm.LlmConfig
import com.baoji.fitness.data.llm.LlmSettings
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.domain.algorithm.MuscleClassifier
import kotlinx.coroutines.launch

/**
 * 新建自定义动作：**先让 LLM 判断肌群，失败回落关键词匹配**。
 *
 * 为什么不让 LLM 直接写库：模型可能给出 6 大类之外的词（如「全身」），
 * 那样该动作的组数会被 `muscleSetCounts` 静默丢弃。所以模型输出**必须**过
 * [MuscleClassifier.normalizePrimary] 归一，归一失败就用离线兜底。
 */
class LibraryViewModel(
    private val repository: WorkoutRepository,
    private val llmSettings: LlmSettings? = null,
    private val llmClient: CoachLlmClient = CoachLlmClient(
        endpoint = { llmSettings?.current ?: LlmConfig.endpoint }
    )
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

            val classification = classify(trimmed)

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

    /** 先试云端；未配 Key / 断网 / 返回不可解析 → 回落关键词。 */
    private suspend fun classify(name: String): MuscleClassifier.Classification {
        if (llmEndpointConfigured()) {
            val result = llmClient.complete(
                systemPrompt = MuscleClassifier.SYSTEM_PROMPT,
                userContent = MuscleClassifier.userPrompt(name),
                disableThinking = llmSettings?.currentDisableThinking ?: true
            )
            result.getOrNull()
                ?.let { MuscleClassifier.parse(it) }
                ?.let { return it }
        }
        return MuscleClassifier.classifyOffline(name)
    }

    private fun llmEndpointConfigured(): Boolean =
        (llmSettings?.current ?: LlmConfig.endpoint).isConfigured
}