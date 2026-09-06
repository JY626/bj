package com.baoji.fitness.ui.analytics

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.theme.AppleBlack
import com.baoji.fitness.core.theme.AppleCautionOrange
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray4
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleGray6
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.core.theme.AppleTextTertiary

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val selectedExercise by viewModel.selectedExercise.collectAsState()
    val e1rmHistory by viewModel.e1rmHistory.collectAsState()
    val muscleVolumes by viewModel.weeklyMuscleVolumes.collectAsState()
    val advice = viewModel.currentProgressionAdvice

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                Text(
                    text = "PROGRESSIVE OVERLOAD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextSecondary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "负荷与表现",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary,
                    letterSpacing = (-0.5).sp
                )
            }
        }

        // Exercise Filter Chips
        item {
            val chips = listOf("平板杠铃卧推", "传统杠铃后深蹲", "罗马尼亚硬拉", "站姿杠铃推举")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { name ->
                    val isSelected = (name == selectedExercise)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color.White else AppleGray6)
                            .clickable { viewModel.selectExercise(name) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = name,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else AppleTextSecondary
                        )
                    }
                }
            }
        }

        // Big Metric & Canvas E1RM Chart Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppleGray6)
                    .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "当前极限估算 (E1RM)",
                            fontSize = 12.sp,
                            color = AppleTextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.Baseline,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "98.5",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = AppleTextPrimary
                            )
                            Text(
                                text = "kg",
                                fontSize = 13.sp,
                                color = AppleTextSecondary
                            )
                            Text(
                                text = "↑ 6.5% 本周期",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleFitnessGreen
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "最佳正式组",
                            fontSize = 10.sp,
                            color = AppleTextSecondary
                        )
                        Text(
                            text = "82.5 kg × 6",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = AppleTextPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Minimalist Canvas Line Chart
                OverloadCanvasChart(
                    data = e1rmHistory.map { it.e1rm },
                    labels = e1rmHistory.map { it.weekLabel },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                )
            }
        }

        // Weekly Muscle Volume RP Landmarks Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppleGray6)
                    .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "周肌群有效组数",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleTextPrimary
                        )
                        Text(
                            text = "RP 运动周期科学区间 (MAV/MRV)",
                            fontSize = 11.sp,
                            color = AppleTextSecondary
                        )
                    }
                    Text(
                        text = "正常进展中",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppleFitnessGreen
                    )
                }

                // Muscle Group Bars
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    VolumeProgressBar(
                        muscle = "胸部 (Chest)",
                        current = 14,
                        target = 18,
                        color = AppleFitnessGreen,
                        label = "适中"
                    )
                    VolumeProgressBar(
                        muscle = "背部 (Back)",
                        current = 8,
                        target = 14,
                        color = AppleCautionOrange,
                        label = "低于有效容量"
                    )
                    VolumeProgressBar(
                        muscle = "腿部 (Legs)",
                        current = 16,
                        target = 20,
                        color = Color.White,
                        label = "接近上限"
                    )
                }
            }
        }

        // Overload Protocol Decision Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppleGray6)
                    .border(1.dp, AppleHairline, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "算法过载决策",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextSecondary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = advice.message,
                    fontSize = 13.sp,
                    color = AppleTextPrimary,
                    lineHeight = 18.sp
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun VolumeProgressBar(
    muscle: String,
    current: Int,
    target: Int,
    color: Color,
    label: String
) {
    val fraction = (current.toFloat() / target).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = muscle, fontSize = 12.sp, color = AppleTextPrimary)
            Text(
                text = "$current / $target 组 · $label",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(AppleGray5)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun OverloadCanvasChart(
    data: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val min = (data.minOrNull() ?: 80.0) - 5.0
    val max = (data.maxOrNull() ?: 100.0) + 5.0

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height - 24f // leave space for bottom labels
        val stepX = w / (data.size - 1)

        // Draw horizontal subtle grid lines
        for (i in 0..3) {
            val y = h - (i / 3f) * h
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        // Draw Line Path
        val path = Path()
        data.forEachIndexed { i, valD ->
            val x = i * stepX
            val normY = (valD - min) / (max - min)
            val y = (h - normY * h).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = AppleFitnessGreen,
            style = Stroke(width = 5f, cap = StrokeCap.Round)
        )

        // Draw Points
        data.forEachIndexed { i, valD ->
            val x = i * stepX
            val normY = (valD - min) / (max - min)
            val y = (h - normY * h).toFloat()
            val isLast = (i == data.size - 1)

            drawCircle(
                color = if (isLast) AppleFitnessGreen else AppleGray6,
                radius = if (isLast) 10f else 6f,
                center = Offset(x, y)
            )
            drawCircle(
                color = if (isLast) Color.White else AppleFitnessGreen,
                radius = if (isLast) 5f else 3f,
                center = Offset(x, y)
            )
        }
    }
}
