package com.baoji.fitness.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.designsystem.icons.LucideIcons
import com.baoji.fitness.core.theme.AppleBlack
import com.baoji.fitness.core.theme.AppleCautionOrange
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray4
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleGray6
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleSystemBlue
import com.baoji.fitness.core.theme.AppleSystemPurple
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.core.theme.AppleTextTertiary
import com.baoji.fitness.core.theme.AppleSystemRed
import com.baoji.fitness.domain.algorithm.ReportSection
import com.baoji.fitness.domain.algorithm.CoachReport
import com.baoji.fitness.data.llm.LlmEndpoint
import com.baoji.fitness.data.llm.LlmSettings

@Composable
fun CoachScreen(
    viewModel: CoachViewModel,
    onApplyToWorkout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val report by viewModel.report.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val scope by viewModel.scope.collectAsState()
    var showLlmSettings by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
            .padding(horizontal = 16.dp)
    ) {
        // 1. Header
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(AppleFitnessGreen, AppleSystemBlue, AppleSystemPurple, AppleFitnessGreen)
                            )
                        )
                        .padding(1.5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = LucideIcons.Sparkles,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = "智能私教搭档",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = when {
                            uiState.sharingEnabled && uiState.llmAvailable -> "已接入云端模型 · 读取你的训练数据"
                            uiState.sharingEnabled -> "云端未配置 · 当前为本地规则"
                            else -> "本地规则分析 · 未开启数据共享"
                        },
                        fontSize = 10.sp,
                        color = AppleTextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleGray6)
                    .border(1.dp, AppleHairline, RoundedCornerShape(12.dp))
                    .clickable { showLlmSettings = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (uiState.sharingEnabled && uiState.llmAvailable) "Cloud" else "On-Device",
                    fontSize = 10.sp,
                    color = AppleTextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (showLlmSettings) {
            LlmConfigSheet(
                settings = viewModel.llmSettings,
                testResult = viewModel.testResult.collectAsState().value,
                onTest = { viewModel.testConnection() },
                onDismiss = {
                    viewModel.clearTestResult()
                    showLlmSettings = false
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. M4a 本地简报（真实数据，不再有写死脚本）
        val briefing = uiState.briefing
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AppleGray6)
                .border(1.dp, AppleHairline, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = LucideIcons.Activity,
                        contentDescription = null,
                        tint = AppleFitnessGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "今日简报",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                }
                Text(
                    text = uiState.briefingDate,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppleTextTertiary
                )
            }

            if (briefing == null || briefing.isEmpty) {
                Text(
                    text = "正在读取你的训练数据…",
                    fontSize = 12.sp,
                    color = AppleTextTertiary
                )
            } else {
                briefing.lines.forEach { line ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(AppleFitnessGreen)
                        )
                        Text(
                            text = line,
                            fontSize = 12.sp,
                            color = AppleTextPrimary,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. 分析报告 / 未生成时的引导
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ReportHeader(report = report, isGenerating = isGenerating, scope = scope)
            }

            item { ScopeSelector(scope = scope, onSelect = { viewModel.selectScope(it) }) }

            val current = report
            current?.let { r ->
                r.sections.forEach { section ->
                    item { ReportCard(section = section, source = r.source) }
                }
            }

            if (report == null && !isGenerating) {
                item {
                    Text(
                        text = when (scope) {
                            CoachReport.Scope.TODAY -> "生成后我会分析你今天这一次的完成质量，**并逐个动作对比上次的成绩**（进步/持平/退步）。"
                            CoachReport.Scope.THIS_WEEK -> "生成后我会算本周部位组数、推拉平衡、容量环比，**并说清各动作与肌群相对上次的进步**。"
                            CoachReport.Scope.LAST_4_WEEKS -> "生成后我会看近 4 周容量趋势、停滞动作，并结合体重/体脂判断是否该减量。"
                        },
                        fontSize = 11.sp,
                        color = AppleTextTertiary,
                        lineHeight = 16.sp
                    )
                }
            }

            item {
                ButtonBlock(
                    label = if (isGenerating) "正在分析…" else "生成${scope.label}分析",
                    enabled = !isGenerating,
                    onClick = { viewModel.generateReport() }
                )
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun ReportHeader(report: CoachReportState?, isGenerating: Boolean, scope: CoachReport.Scope) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = LucideIcons.Activity,
                contentDescription = null,
                tint = AppleFitnessGreen,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (report == null) "训练体检" else "训练体检 · ${report.generatedAt}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
        }
        if (report != null) {
            Text(
                text = if (report.source == ReplySource.LLM) "云端模型" else "本地规则",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (report.source == ReplySource.LLM) AppleFitnessGreen else AppleTextTertiary
            )
        }
    }
}

/** 今日 / 本周 / 近4周 —— 单一分段控件，只控制下面那一个「生成」按钮。 */
@Composable
private fun ScopeSelector(scope: CoachReport.Scope, onSelect: (CoachReport.Scope) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppleGray5)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        CoachReport.Scope.entries.forEach { s ->
            val selected = s == scope
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onSelect(s) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = s.label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.Black else AppleTextSecondary
                )
            }
        }
    }
}

@Composable
private fun ReportCard(section: ReportSection, source: ReplySource) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = section.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppleFitnessGreen
        )
        Text(
            text = section.body,
            fontSize = 12.sp,
            color = AppleTextPrimary,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ButtonBlock(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) AppleFitnessGreen else AppleGray5)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.Black else AppleTextSecondary
        )
    }
}

/** 方案 A：在 App 内直接配 Key / 端点 / 模型，不用重编译。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmConfigSheet(
    settings: LlmSettings?,
    testResult: String?,
    onTest: () -> Unit,
    onDismiss: () -> Unit
) {
    if (settings == null) return
    val saved by settings.endpoint.collectAsState()
    var apiKey by remember { mutableStateOf(saved.apiKey) }
    var baseUrl by remember { mutableStateOf(saved.baseUrl) }
    var model by remember { mutableStateOf(saved.model) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppleGray6
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "云端私教设置",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
            Text(
                text = "填任何 OpenAI 兼容端点。Key 存在本机私有存储，不进 APK、不上传。" +
                    "留空则回落到编译期 local.properties 的值。",
                fontSize = 11.sp,
                color = AppleTextTertiary,
                lineHeight = 16.sp
            )

            LlmField("API Key", apiKey, secret = true) { apiKey = it }
            LlmField("Base URL", baseUrl, placeholder = "https://api.openai.com/v1") { baseUrl = it }
            LlmField("模型", model, placeholder = "gpt-4o-mini") { model = it }

            Text(
                text = "默认关掉思考模式，生成更快；如果模型不认这个参数（报 400），就把它关掉。",
                fontSize = 11.sp,
                color = AppleTextTertiary,
                lineHeight = 16.sp
            )

            val disableThinking by settings.disableThinking.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("关闭思考模式（更快）", fontSize = 12.sp, color = AppleTextPrimary)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (disableThinking) AppleFitnessGreen else AppleGray5)
                        .clickable { settings.setDisableThinking(!disableThinking) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (disableThinking) "已开启" else "已关闭",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (disableThinking) Color.Black else AppleTextSecondary
                    )
                }
            }

            Text(
                text = if (saved.isConfigured) "当前状态：已配置 ✓" else "当前状态：未配置（使用本地规则）",
                fontSize = 11.sp,
                color = if (saved.isConfigured) AppleFitnessGreen else AppleCautionOrange
            )

            testResult?.let {
                val ok = it.startsWith("连接成功")
                Text(
                    text = it,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = if (ok) AppleFitnessGreen else AppleSystemRed
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppleGray5)
                        .clickable { onTest() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "测试连接", fontSize = 13.sp, color = AppleTextPrimary)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppleFitnessGreen)
                        .clickable {
                            settings.save(apiKey, baseUrl, model)
                            onDismiss()
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "保存", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppleGray5)
                        .clickable { settings.reset() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "清除", fontSize = 13.sp, color = AppleTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun LlmField(
    label: String,
    value: String,
    placeholder: String = "",
    secret: Boolean = false,
    onChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, fontSize = 11.sp, color = AppleTextSecondary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppleBlack)
                .border(1.dp, AppleHairline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(text = placeholder, fontSize = 12.sp, color = AppleGray4)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = AppleTextPrimary),
                cursorBrush = SolidColor(AppleFitnessGreen),
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

