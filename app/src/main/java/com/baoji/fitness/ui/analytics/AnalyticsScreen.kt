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
    val exercises by viewModel.exercises.collectAsState()
    val selectedExerciseId by viewModel.selectedExerciseId.collectAsState()
    val analyticsState by viewModel.analyticsState.collectAsState()
    val muscleVolumes by viewModel.weeklyMuscleVolumes.collectAsState()

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

        // Dynamic Exercise Filter Chips from Database
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                exercises.forEach { ex ->
                    val isSelected = (ex.id == selectedExerciseId)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color.White else AppleGray6)
                            .clickable { viewModel.selectExercise(ex.id) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = ex.name,
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
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = if (analyticsState.currentE1RM > 0) "${analyticsState.currentE1RM}" else "--",
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
                                text = analyticsState.percentageIncreaseText,
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
                            text = analyticsState.bestSetText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = AppleTextPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Minimalist Canvas Line Chart
                if (analyticsState.points.isNotEmpty()) {
                    OverloadCanvasChart(
                        points = analyticsState.points,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppleGray5),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无该动作历史完成组，在训练中打勾记录后将自动绘制",
                            fontSize = 11.sp,
                            color = AppleTextTertiary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
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
                        text = "数据实时同步中",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppleFitnessGreen
                    )
                }

                // Muscle Group Bars from Repository
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    muscleVolumes.forEach { status ->
                        val color = when {
                            status.completedSets < status.mev -> AppleCautionOrange
                            status.completedSets <= status.mav -> AppleFitnessGreen
                            else -> Color.White
                        }
                        VolumeProgressBar(
                            muscle = status.muscleGroup,
                            current = status.completedSets,
                            target = status.mav,
                            color = color,
                            label = status.statusText
                        )
                    }
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
                    text = analyticsState.adviceMessage,
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
    val safeTarget = if (target > 0) target else 16
    val fraction = (current.toFloat() / safeTarget).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = muscle, fontSize = 12.sp, color = AppleTextPrimary)
            Text(
                text = "$current / $safeTarget 组 · $label",
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
    points: List<OverloadDataPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val values = points.map { it.e1rm }
    val min = (values.minOrNull() ?: 60.0) - 5.0
    val max = (values.maxOrNull() ?: 100.0) + 5.0

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height - 24f // reserve space for bottom labels
        val stepX = if (points.size > 1) w / (points.size - 1) else w / 2

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

        // Draw Line Path if 2 or more points
        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { i, pt ->
                val x = i * stepX
                val normY = (pt.e1rm - min) / (max - min)
                val y = (h - normY * h).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = AppleFitnessGreen,
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
        }

        // Draw Points & PR Highlights
        points.forEachIndexed { i, pt ->
            val x = if (points.size > 1) i * stepX else w / 2
            val normY = (pt.e1rm - min) / (max - min)
            val y = (h - normY * h).toFloat()
            val isLast = (i == points.size - 1)

            drawCircle(
                color = if (pt.isPR) AppleFitnessGreen else AppleGray6,
                radius = if (pt.isPR || isLast) 9f else 6f,
                center = Offset(x, y)
            )
            drawCircle(
                color = if (pt.isPR) Color.White else AppleFitnessGreen,
                radius = if (pt.isPR || isLast) 4.5f else 3f,
                center = Offset(x, y)
            )
        }
    }
}
