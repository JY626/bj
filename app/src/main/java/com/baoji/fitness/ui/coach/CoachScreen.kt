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
import androidx.compose.material3.Text
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

@Composable
fun CoachScreen(
    viewModel: CoachViewModel,
    onApplyToWorkout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isReplying by viewModel.isReplying.collectAsState()
    var inputText by remember { mutableStateOf("") }

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

        // 3. 对话
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty() && !isReplying) {
                item {
                    Text(
                        text = "上面是本地规则根据你的真实数据算出来的结论。想问更多细节，可以直接在下面提问。",
                        fontSize = 11.sp,
                        color = AppleTextTertiary,
                        lineHeight = 16.sp
                    )
                }
            }

            items(messages) { msg ->
                when (msg) {
                    is ChatMessage.User -> UserBubble(msg.content)

                    is ChatMessage.AssistantText -> AssistantBubble(
                        content = msg.content,
                        source = msg.source
                    )

                    is ChatMessage.ActionableCard -> PrescriptionCard(
                        message = msg,
                        onApplyToWorkout = onApplyToWorkout
                    )
                }
            }

            if (isReplying) {
                item {
                    Text(
                        text = "正在分析…",
                        fontSize = 11.sp,
                        color = AppleTextTertiary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }

        // 4. Quick Action Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickPill("今天该练什么") { viewModel.sendMessage("今天该练什么？") }
            QuickPill("哪个部位欠练") { viewModel.sendMessage("这周哪个部位练得不够？") }
            QuickPill("怎么突破平台") { viewModel.sendMessage("我卡在平台期了，怎么突破？") }
        }

        // 5. Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AppleGray6)
                .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                textStyle = TextStyle(color = AppleTextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(AppleFitnessGreen),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = "问训练量、加重、部位安排…",
                            color = AppleGray4,
                            fontSize = 13.sp
                        )
                    }
                    innerTextField()
                }
            )

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppleSystemBlue)
                    .clickable {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LucideIcons.ArrowUp,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun UserBubble(content: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(AppleSystemBlue)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = content,
                fontSize = 13.sp,
                color = Color.White,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun AssistantBubble(content: String, source: ReplySource) {
    val corner = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(corner)
                .background(AppleGray6)
                .border(1.dp, AppleHairline, corner)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val tint = if (source == ReplySource.LLM) AppleSystemPurple else AppleCautionOrange
                Icon(
                    imageVector = if (source == ReplySource.LLM) LucideIcons.Sparkles else LucideIcons.Activity,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = if (source == ReplySource.LLM) "云端模型" else "本地规则",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = tint
                )
            }
            Text(
                text = content,
                fontSize = 13.sp,
                color = AppleTextPrimary,
                lineHeight = 18.sp
            )
        }
    }
}

/** M4b-2 会用到的结构化处方卡（当前不会主动产生，保留渲染分支）。 */
@Composable
private fun PrescriptionCard(message: ChatMessage.ActionableCard, onApplyToWorkout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = LucideIcons.Activity,
                contentDescription = null,
                tint = AppleFitnessGreen,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = message.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
        }

        Text(
            text = message.subtitle,
            fontSize = 12.sp,
            color = AppleTextPrimary,
            lineHeight = 16.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppleGray5)
                .border(1.dp, AppleHairline, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = message.recommendedExercise,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "建议负荷: ${message.targetWeightKg} kg",
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
                Text(
                    text = message.targetReps,
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .clickable { onApplyToWorkout() }
                .padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = LucideIcons.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "一键应用到当前训练",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun QuickPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon?.let {
            Icon(imageVector = it, contentDescription = null, tint = AppleTextSecondary, modifier = Modifier.size(12.dp))
        }
        Text(text = text, fontSize = 11.sp, color = AppleTextSecondary)
    }
}
