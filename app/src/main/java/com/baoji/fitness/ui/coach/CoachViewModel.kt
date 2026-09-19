package com.baoji.fitness.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.data.llm.CoachContextPacker
import com.baoji.fitness.data.llm.CoachLlmClient
import com.baoji.fitness.data.llm.CoachLlmContext
import com.baoji.fitness.data.llm.LlmConfig
import com.baoji.fitness.data.llm.PersonalRecordSummary
import com.baoji.fitness.data.llm.SessionSummary
import com.baoji.fitness.data.repository.BodyMetricRepository
import com.baoji.fitness.data.repository.WeeklyMuscleComparison
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.data.settings.ReminderConfig
import com.baoji.fitness.data.settings.ReminderSettings
import com.baoji.fitness.domain.algorithm.CoachBriefing
import com.baoji.fitness.domain.algorithm.CoachInput
import com.baoji.fitness.domain.algorithm.CoachNarrator
import com.baoji.fitness.domain.algorithm.PeriodReview
import com.baoji.fitness.domain.algorithm.PeriodReviewCalculator
import com.baoji.fitness.domain.algorithm.ReviewPeriod
import com.baoji.fitness.domain.algorithm.StreakCalculator
import com.baoji.fitness.domain.model.workoutTypeLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 回答来自本地规则还是云端模型 —— UI 上要标出来，不能让用户以为本地规则是 AI。 */
enum class ReplySource { LOCAL, LLM }

sealed class ChatMessage {
    data class User(val content: String) : ChatMessage()
    data class AssistantText(
        val content: String,
        val source: ReplySource = ReplySource.LOCAL
    ) : ChatMessage()

    data class ActionableCard(
        val title: String,
        val subtitle: String,
        val recommendedExercise: String,
        val targetWeightKg: Double,
        val targetReps: String
    ) : ChatMessage()
}

/** 已经把「今天该练什么」算出来的界面状态。 */
data class CoachUiState(
    val briefing: CoachBriefing? = null,
    val briefingDate: String = "",
    val llmAvailable: Boolean = false,
    val sharingEnabled: Boolean = false
)

private data class CoachCore(
    val weekly: PeriodReview,
    val monthly: PeriodReview,
    val exercises: List<ExerciseEntity>
)

private data class CoachLogs(
    val sessions: List<WorkoutSessionEntity>,
    val notes: List<String>,
    val trainedDays: Map<LocalDate, String>
)

private data class CoachProfile(
    val config: ReminderConfig,
    val latestMetric: BodyMetricEntity?,
    val muscle: WeeklyMuscleComparison
)

private data class CoachDataset(
    val core: CoachCore,
    val logs: CoachLogs,
    val profile: CoachProfile
)

class CoachViewModel(
    private val repository: WorkoutRepository,
    private val bodyMetricRepository: BodyMetricRepository,
    private val reminderSettings: ReminderSettings,
    private val llmClient: CoachLlmClient = CoachLlmClient()
) : ViewModel() {

    private val coreFlow = combine(
        repository.getPeriodReview(ReviewPeriod.THIS_WEEK),
        repository.getPeriodReview(ReviewPeriod.THIS_MONTH),
        repository.getAllExercises()
    ) { weekly, monthly, exercises -> CoachCore(weekly, monthly, exercises) }

    private val logsFlow = combine(
        repository.getAllSessions(),
        repository.getRecentNotes(3),
        repository.getTrainedDayTypes()
    ) { sessions, notes, trainedDays -> CoachLogs(sessions, notes, trainedDays) }

    private val profileFlow = combine(
        reminderSettings.config,
        bodyMetricRepository.observeLatest(),
        repository.getWeeklyMuscleComparison()
    ) { config, metric, muscle -> CoachProfile(config, metric, muscle) }

    private val dataset: StateFlow<CoachDataset?> = combine(
        coreFlow, logsFlow, profileFlow
    ) { core, logs, profile -> CoachDataset(core, logs, profile) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** M4a：本地简报，随数据实时刷新。 */
    val uiState: StateFlow<CoachUiState> = dataset
        .map { data ->
            if (data == null) {
                CoachUiState()
            } else {
                CoachUiState(
                    briefing = CoachNarrator.brief(data.toInput()),
                    briefingDate = LocalDate.now()
                        .format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE)),
                    llmAvailable = LlmConfig.isConfigured,
                    sharingEnabled = data.profile.config.llmSharingEnabled
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachUiState())

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isReplying = MutableStateFlow(false)
    val isReplying: StateFlow<Boolean> = _isReplying.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        val question = text.trim()
        _messages.value = _messages.value + ChatMessage.User(question)

        viewModelScope.launch {
            _isReplying.value = true
            val data = dataset.first()
            val answer: ChatMessage = if (data == null) {
                ChatMessage.AssistantText(CoachNarrator.brief(CoachInput()).text)
            } else {
                resolveAnswer(question, data)
            }
            val updated: List<ChatMessage> = _messages.value + answer
            _messages.value = updated
            _isReplying.value = false
        }
    }

    /**
     * M4b 的降级路径与 M4a 完全重合：
     * 没开共享 / 没配 Key / 请求失败 → 一律回本地规则，不白屏不崩。
     */
    private suspend fun resolveAnswer(question: String, data: CoachDataset): ChatMessage.AssistantText {
        val sharing = data.profile.config.llmSharingEnabled
        if (!sharing || !LlmConfig.isConfigured) {
            val reason = if (!LlmConfig.isConfigured) "未配置云端密钥" else "你还没打开「让私教读取我的数据」"
            return ChatMessage.AssistantText(
                content = localAnswer(question, data) + "\n（本地规则回答 · $reason）",
                source = ReplySource.LOCAL
            )
        }

        val contextJson = CoachContextPacker.toJson(data.toLlmContext())
        return llmClient.ask(question, contextJson).fold(
            onSuccess = { ChatMessage.AssistantText(it, ReplySource.LLM) },
            onFailure = { error ->
                ChatMessage.AssistantText(
                    content = localAnswer(question, data) +
                        "\n（云端暂时不可用：${error.message ?: "未知错误"}，已改用本地规则）",
                    source = ReplySource.LOCAL
                )
            }
        )
    }

    private fun localAnswer(question: String, data: CoachDataset): String {
        val briefing = CoachNarrator.brief(data.toInput())
        return when {
            question.contains("饮食") || question.contains("吃") || question.contains("营养") ->
                "本轮不做饮食记录。要涨重量，先保证每天 1.6~2.2 g/kg 体重的蛋白质和 7 小时睡眠，比记餐更划算。"
            question.contains("痛") || question.contains("疼") || question.contains("伤") ->
                "有疼痛先停掉诱发动作，换无痛角度并降 20% 重量；超过一周不缓解就去看医生，别硬顶。"
            question.contains("平台") || question.contains("突破") || question.contains("加") ->
                briefing.lines.firstOrNull { it.contains("下一次") || it.contains("减量") } ?: briefing.text
            else -> briefing.text
        }
    }
}

private fun CoachDataset.toInput(): CoachInput {
    val today = LocalDate.now()
    val tracked = profile.muscle.current
    return CoachInput(
        label = "本周",
        sessionCount = core.weekly.sessionCount,
        volumeDeltaPct = core.weekly.volumeDeltaPct,
        totalVolumeKg = core.weekly.totalVolumeKg,
        streakDays = StreakCalculator.currentStreak(logs.trainedDays.keys, today),
        remainingPlannedDays = StreakCalculator.remainingPlannedDaysThisWeek(
            trainedDays = logs.trainedDays.keys,
            today = today,
            plannedDays = profile.config.trainingDays
        ),
        muscleVolumes = tracked,
        recentNotes = logs.notes,
        latestWeightKg = profile.latestMetric?.weightKg
    )
}

private fun CoachDataset.toLlmContext(): CoachLlmContext {
    val today = LocalDate.now()
    val dateFormat = DateTimeFormatter.ofPattern("M-d")
    // 只发近 5 次已完成训练的摘要
    val recent = logs.sessions
        .filter { it.status == "COMPLETED" }
        .take(5)
        .map {
            SessionSummary(
                date = PeriodReviewCalculator.instantToLocalDate(it.startTime).format(dateFormat),
                type = workoutTypeLabel(it.title),
                title = it.title,
                volumeKg = it.totalVolumeKg
            )
        }
    return CoachLlmContext(
        today = today,
        weekly = core.weekly,
        monthly = core.monthly,
        muscles = profile.muscle.current,
        recentSessions = recent,
        personalRecords = core.exercises
            .filter { it.personalRecordWeight > 0.0 }
            .map { PersonalRecordSummary(it.name, it.personalRecordWeight, it.personalRecordReps) },
        recentNotes = logs.notes,
        weightKg = profile.latestMetric?.weightKg,
        bodyFatPct = profile.latestMetric?.bodyFatPct,
        streakDays = StreakCalculator.currentStreak(logs.trainedDays.keys, today)
    )
}
