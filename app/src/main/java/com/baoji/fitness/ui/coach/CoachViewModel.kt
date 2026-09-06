package com.baoji.fitness.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ChatMessage {
    data class User(val content: String) : ChatMessage()
    data class AssistantText(val content: String) : ChatMessage()
    data class ActionableCard(
        val title: String,
        val subtitle: String,
        val recommendedExercise: String,
        val targetWeightKg: Double,
        val targetReps: String,
        val onApply: () -> Unit
    ) : ChatMessage()
    data class FoodNutritionCard(
        val mealName: String,
        val calories: Int,
        val proteinG: Double,
        val carbsG: Double,
        val fatG: Double,
        val tip: String
    ) : ChatMessage()
}

class CoachViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage.AssistantText("你好。我已经同步了你过去 32 天的推拉腿负荷数据。今天有什么需要调整？例如：器械被占用、某部位关节酸胀、或是拍摄饮食测算营养成分。"),
            ChatMessage.User("我今天右肩前束稍微有点刺痛感，而且深蹲架人满，帮我把原计划微调一下。"),
            ChatMessage.ActionableCard(
                title = "右肩减压与动作替换建议",
                subtitle = "杠铃下落过深可能造成肩峰撞击，建议转换为哑铃对握",
                recommendedExercise = "30° 对握哑铃卧推 (Neutral Grip)",
                targetWeightKg = 24.0,
                targetReps = "4 组 × 8-10 次",
                onApply = {}
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val current = _messages.value.toMutableList()
        current.add(ChatMessage.User(text))
        _messages.value = current

        // Simulate intelligent Agent response
        viewModelScope.launch {
            if (text.contains("饭") || text.contains("饮食") || text.contains("拍照")) {
                current.add(
                    ChatMessage.FoodNutritionCard(
                        mealName = "香煎鸡胸肉便当 · 紫米饭 · 西蓝花",
                        calories = 540,
                        proteinG = 46.0,
                        carbsG = 58.0,
                        fatG = 12.0,
                        tip = "本餐蛋白质达到今日目标的 32%，摄入充足。"
                    )
                )
            } else {
                current.add(
                    ChatMessage.AssistantText("收到你的反馈。结合运动生物力学与你的恢复负荷，如果处于疲劳状态，可将正式组数降为 2 组 (Deload 减量周协议)，重点保持动作品质。")
                )
            }
            _messages.value = current.toList()
        }
    }

    fun triggerPhotoScan() {
        sendMessage("拍照分析这顿饭的蛋白质与热量")
    }
}
