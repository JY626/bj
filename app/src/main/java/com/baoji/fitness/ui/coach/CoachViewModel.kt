package com.baoji.fitness.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.data.llm.CoachLlmClient
import com.baoji.fitness.data.llm.LlmConfig
import com.baoji.fitness.data.llm.LlmEndpoint
import com.baoji.fitness.data.llm.LlmSettings
import com.baoji.fitness.data.repository.BodyMetricRepository
import com.baoji.fitness.data.repository.CoachReportRepository
import com.baoji.fitness.data.repository.WeeklyMuscleComparison
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.data.settings.ReminderConfig
import com.baoji.fitness.data.settings.ReminderSettings
import com.baoji.fitness.domain.algorithm.BodyProfile
import com.baoji.fitness.domain.algorithm.CoachBriefing
import com.baoji.fitness.domain.algorithm.CoachInput
import com.baoji.fitness.domain.algorithm.CoachNarrator
import com.baoji.fitness.domain.algorithm.CoachReport
import com.baoji.fitness.domain.algorithm.CoachingLocalReport
import com.baoji.fitness.domain.algorithm.PeriodReview
import com.baoji.fitness.domain.algorithm.PeriodReviewCalculator
import com.baoji.fitness.domain.algorithm.ReportSection
import com.baoji.fitness.domain.algorithm.ReviewPeriod
import com.baoji.fitness.domain.algorithm.StreakCalculator
import com.baoji.fitness.domain.algorithm.TrainingFacts
import com.baoji.fitness.domain.algorithm.WorkoutAnalyzer
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

/** 报告来自本地规则还是云端模型 —— UI 上要标出来，不能让用户以为本地规则是 AI。 */
enum class ReplySource { LOCAL, LLM }

/**
 * 固定分区报告。问答已下线（B4）：只留「生成分析」一条路径。
 * [source] 标明这份报告是模型写的还是本地规则算的。
 */
data class CoachReportState(
    val sections: List<ReportSection> = emptyList(),
    val source: ReplySource = ReplySource.LOCAL,
    val generatedAt: String = "",
    val scope: CoachReport.Scope = CoachReport.Scope.THIS_WEEK
)

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
    /** 全部身体记录（升序），用于算体重/体脂相对上次的变化。 */
    val metrics: List<BodyMetricEntity>,
    val muscle: WeeklyMuscleComparison
)

private data class CoachDataset(
    val core: CoachCore,
    val logs: CoachLogs,
    val profile: CoachProfile,
    /** 全量历史（与分析器同一口径），用于算 TrainingFacts。 */
    val allSets: List<com.baoji.fitness.core.database.entity.WorkoutSetEntity>
)

class CoachViewModel(
    private val repository: WorkoutRepository,
    private val bodyMetricRepository: BodyMetricRepository,
    private val reminderSettings: ReminderSettings,
    private val coachReportRepository: CoachReportRepository? = null,
    val llmSettings: LlmSettings? = null,
    private val llmClient: CoachLlmClient = CoachLlmClient(endpoint = { llmSettings?.current ?: LlmConfig.endpoint })
) : ViewModel() {

    /** 运行时填的配置优先；没注入 [llmSettings]（如单测）时回落编译期 [LlmConfig]。 */
    private fun llmEndpoint(): LlmEndpoint = llmSettings?.current ?: LlmConfig.endpoint

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
        bodyMetricRepository.observeAll(),
        repository.getWeeklyMuscleComparison()
    ) { config, metric, metrics, muscle -> CoachProfile(config, metric, metrics, muscle) }

    private val dataset: StateFlow<CoachDataset?> = combine(
        coreFlow, logsFlow, profileFlow, repository.getAllCompletedSets()
    ) { core, logs, profile, allSets ->
        CoachDataset(core = core, logs = logs, profile = profile, allSets = allSets)
    }
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
                    llmAvailable = llmEndpoint().isConfigured,
                    sharingEnabled = data.profile.config.llmSharingEnabled
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachUiState())

    private val _report = MutableStateFlow<CoachReportState?>(null)
    val report: StateFlow<CoachReportState?> = _report.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    /** 当前选中的报告范围（今日 / 本周 / 近4周）。 */
    private val _scope = MutableStateFlow(CoachReport.Scope.THIS_WEEK)
    val scope: StateFlow<CoachReport.Scope> = _scope.asStateFlow()

    fun selectScope(scope: CoachReport.Scope) {
        _scope.value = scope
        _report.value = null // 换范围就丢旧报告，避免「今日」标题下挂着本周内容
    }

    /** 预计算事实 → 本地规则报告（离线可用）；云端可用时会在此基础上换成模型解读。
     *
     * [scope] 决定统计窗口：
     * - 今日 → **只算今天那一天**（单日）：部位组数、容量、次数都是当天的。
     * - 本周 → 近 1 周。
     * - 近 4 周 → 近 4 周。
     *
     * 曾经把「今日」写成 weeks=1（= 整周），导致模型把「本周肩 17 组」当成今日数据，
     * 而用户今天只练了 7 组 —— 数字与标题对不上，已修。
     */
    private fun factsOf(data: CoachDataset, scope: CoachReport.Scope): TrainingFacts {
        val (weeks, singleDay) = when (scope) {
            CoachReport.Scope.TODAY -> 1 to true
            CoachReport.Scope.THIS_WEEK -> 1 to false
            CoachReport.Scope.LAST_4_WEEKS -> 4 to false
        }
        val facts = WorkoutAnalyzer.analyze(
            sessions = data.logs.sessions,
            sets = data.allSets,
            exercises = data.core.exercises,
            weeks = weeks,
            singleDay = singleDay
        )
        return facts.copy(
            latestWeightKg = data.profile.latestMetric?.weightKg,
            body = bodyProfileOf(data)
        )
    }

    /** 身体画像 + 相对上次的变化（体重/体脂）。 */
    private fun bodyProfileOf(data: CoachDataset): BodyProfile {
        val latest = data.profile.latestMetric ?: return BodyProfile()
        val history = data.profile.metrics
        val prev = history.filter { it.date < latest.date }.maxByOrNull { it.date }
        return BodyProfile(
            weightKg = latest.weightKg,
            heightCm = latest.heightCm,
            bodyFatPct = latest.bodyFatPct,
            waistCm = latest.waistCm,
            armCm = latest.armCm,
            chestCm = latest.chestCm,
            weightDeltaKg = delta(latest.weightKg, prev?.weightKg),
            bodyFatDeltaPct = delta(latest.bodyFatPct, prev?.bodyFatPct)
        )
    }

    private fun delta(now: Double?, before: Double?): Double? {
        if (now == null || before == null) return null
        return ((now - before) * 10).toInt() / 10.0
    }

    fun generateReport() {
        viewModelScope.launch {
            val scope = _scope.value
            _isGenerating.value = true
            _report.value = null
            try {
                val data = dataset.first() ?: run {
                    _report.value = CoachReportState(
                        sections = listOf(ReportSection(CoachReport.sectionsFor(scope).first(), "还没有训练数据，先完成一次训练，我才有东西可分析。")),
                        source = ReplySource.LOCAL,
                        generatedAt = nowLabel(),
                        scope = scope
                    )
                    return@launch
                }
                val facts = factsOf(data, scope)
                val today = WorkoutAnalyzer.buildToday(data.logs.sessions, data.allSets, data.core.exercises)
                val sharing = data.profile.config.llmSharingEnabled

                if (!sharing || !llmEndpoint().isConfigured) {
                    val local = CoachingLocalReport.build(facts, today, scope)
                    _report.value = CoachReportState(
                        sections = CoachReport.splitSections(local, scope),
                        source = ReplySource.LOCAL,
                        generatedAt = nowLabel(),
                        scope = scope
                    )
                    persist(local, scope, ReplySource.LOCAL)
                    return@launch
                }

                val factsJson = CoachReport.factsToJson(facts, today)
                llmClient.complete(
                    systemPrompt = CoachReport.systemPrompt(scope),
                    userContent = CoachReport.userPrompt(factsJson, scope),
                    disableThinking = llmSettings?.currentDisableThinking ?: true
                ).fold(
                    onSuccess = {
                        _report.value = CoachReportState(
                            sections = CoachReport.splitSections(it, scope),
                            source = ReplySource.LLM,
                            generatedAt = nowLabel(),
                            scope = scope
                        )
                        persist(it, scope, ReplySource.LLM)
                    },
                    onFailure = { error ->
                        val local = CoachingLocalReport.build(facts, today, scope)
                        _report.value = CoachReportState(
                            sections = CoachReport.splitSections(
                                local + "\n\n（云端不可用：${error.message ?: "未知错误"}，以上为本地规则分析）",
                                scope
                            ),
                            source = ReplySource.LOCAL,
                            generatedAt = nowLabel(),
                            scope = scope
                        )
                        // 云端失败不保存：那是带错误尾巴的降级稿，不值得进日历
                    }
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /** 测试连接：把密钥/端点错、模型名/额度错分开报，别让用户瞎猜。 */
    fun testConnection() {
        viewModelScope.launch {
            val ep = llmEndpoint()
            val target = if (ep.baseUrl.isBlank()) "（BaseUrl 为空）" else "${ep.baseUrl}/chat/completions"
            _testResult.value = "测试中…\n$target"
            llmClient.testConnection().fold(
                onSuccess = { _testResult.value = "连接成功 ✓\n$target" },
                onFailure = { _testResult.value = "失败：${it.message ?: "未知错误"}\n目标：$target" }
            )
        }
    }

    fun clearTestResult() { _testResult.value = null }

    /**
     * 存进日历供回看。**只存「今日」与「本周」**：
     * 日历是按天点开的，近 4 周的报告没有对应的那一天，硬塞进去会误导。
     */
    private fun persist(markdown: String, scope: CoachReport.Scope, source: ReplySource) {
        if (scope == CoachReport.Scope.LAST_4_WEEKS) return
        val repo = coachReportRepository ?: return
        val content = markdown.trim()
        if (content.isEmpty()) return
        viewModelScope.launch {
            repo.save(
                day = LocalDate.now(),
                scope = scope.name,
                content = content,
                source = source.name
            )
        }
    }

    private fun nowLabel(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE))
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
