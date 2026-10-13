package com.baoji.fitness.ui.analytics

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
import androidx.compose.ui.graphics.Color
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
import com.baoji.fitness.domain.algorithm.ReviewPeriod
import com.baoji.fitness.domain.algorithm.TrendPoint
import com.baoji.fitness.domain.model.formatSets
import com.baoji.fitness.ui.components.ChartPoint
import com.baoji.fitness.ui.components.MinimalLineChart

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val exercises by viewModel.exercises.collectAsState()
    val selectedExerciseId by viewModel.selectedExerciseId.collectAsState()
    val analyticsState by viewModel.analyticsState.collectAsState()
    val period by viewModel.period.collectAsState()
    val periodReview by viewModel.periodReview.collectAsState()
    val trend by viewModel.trend.collectAsState()
    val weeklyMuscle by viewModel.weeklyMuscleComparison.collectAsState()

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

        // ===== M1 周期回顾 =====
        // 1. 周期切换 chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReviewPeriod.values().forEach { candidate ->
                    val isSelected = candidate == period
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color.White else AppleGray6)
                            .clickable { viewModel.selectPeriod(candidate) }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = candidate.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else AppleTextSecondary
                        )
                    }
                }
            }
        }

        // 2. 汇总卡：训练 N 次 · 总容量 X · 有效组数 Y · 环比
        item {
            val review = periodReview
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
                    Text(
                        text = "${review?.label ?: period.label}回顾",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    DeltaBadge(review?.volumeDeltaPct)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryMetric("训练次数", "${review?.sessionCount ?: 0}", "次", AppleTextPrimary)
                    SummaryMetric(
                        "总容量",
                        "${(review?.totalVolumeKg ?: 0.0).toInt()}",
                        "kg",
                        AppleFitnessGreen
                    )
                    SummaryMetric("有效组数", "${review?.totalSets ?: 0}", "组", AppleTextPrimary)
                }

                val typeCounts = review?.typeCounts.orEmpty()
                if (typeCounts.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        typeCounts.forEach { (type, count) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AppleGray5)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "$type $count",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AppleTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. 部位容量进度条：本周 vs 上周 双色条，对照 MEV/MAV/MRV
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
                Column {
                    Text(
                        text = "周肌群有效组数",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = "本周 vs 上周 · RP 运动周期科学区间 (MEV/MAV/MRV)",
                        fontSize = 11.sp,
                        color = AppleTextSecondary
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    weeklyMuscle.current.forEachIndexed { index, status ->
                        val previous = weeklyMuscle.previous.getOrNull(index)
                        val color = when {
                            status.completedSets < status.mev -> AppleCautionOrange
                            status.completedSets <= status.mav -> AppleFitnessGreen
                            else -> Color.White
                        }
                        VolumeProgressBar(
                            muscle = status.muscleGroup,
                            current = status.completedSets,
                            previous = previous?.completedSets ?: 0.0,
                            target = status.mav,
                            color = color,
                            label = status.statusText
                        )
                    }
                }
            }
        }

        // 4. 容量趋势折线（近 8 周）
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppleGray6)
                    .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "容量趋势",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleTextPrimary
                        )
                        Text(
                            text = "近 8 周训练容量 (kg)",
                            fontSize = 11.sp,
                            color = AppleTextSecondary
                        )
                    }
                    Text(
                        text = trend.lastOrNull()?.let { "本周 ${it.volumeKg.toInt()}" } ?: "--",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AppleFitnessGreen
                    )
                }

                if (trend.any { it.volumeKg > 0.0 }) {
                    TrendCanvasChart(
                        points = trend,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
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
                            text = "还没有足够的训练记录，完成几次训练后这里会出现周容量曲线",
                            fontSize = 11.sp,
                            color = AppleTextTertiary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        }

        // ===== 单动作负荷（原有）=====
        item {
            Text(
                text = "单动作负荷",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
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

/** 汇总卡里的单个指标（标签 / 数值 / 单位）。 */
@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    unit: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = label, fontSize = 10.sp, color = AppleTextSecondary)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = valueColor
            )
            Text(text = " $unit", fontSize = 11.sp, color = AppleTextSecondary)
        }
    }
}

/**
 * 环比徽章。训练容量是「越多越好」的语义，所以涨用 App 既有的进度绿、
 * 跌用提醒橙（与股票行情的红涨绿跌不是一套语境）。
 */
@Composable
private fun DeltaBadge(deltaPct: Double?) {
    val (text, color) = when {
        deltaPct == null -> "-- 无上期" to AppleTextTertiary
        deltaPct >= 0.05 -> String.format("↑ +%.0f%%", deltaPct) to AppleFitnessGreen
        deltaPct <= -0.05 -> String.format("↓ %.0f%%", deltaPct) to AppleCautionOrange
        else -> "→ 持平" to AppleTextSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

/**
 * 部位有效组数：本周实心条 + 上周细条，对照 MAV 刻度。
 * 上周用中性灰细条，避免两个高饱和色打架。
 */
@Composable
private fun VolumeProgressBar(
    muscle: String,
    current: Double,
    previous: Double,
    target: Int,
    color: Color,
    label: String
) {
    val safeTarget = if (target > 0) target else 16
    val currentFraction = (current.toFloat() / safeTarget).coerceIn(0f, 1f)
    val previousFraction = (previous.toFloat() / safeTarget).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = muscle, fontSize = 12.sp, color = AppleTextPrimary)
            Text(
                text = "${formatSets(current)} / $safeTarget 组 · $label",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        }
        // 本周
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(AppleGray5)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(currentFraction)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        // 上周
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(AppleGray5)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(previousFraction)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(AppleTextTertiary)
                )
            }
            Text(
                text = " 上周 ${formatSets(previous)}",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = AppleTextTertiary
            )
        }
    }
}

/** 近 N 周容量折线：共用 [MinimalLineChart]，zeroBased + x 轴周标签。 */
@Composable
private fun TrendCanvasChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    MinimalLineChart(
        points = points.map { ChartPoint(label = it.label, value = it.volumeKg) },
        modifier = modifier,
        zeroBased = true,
        showXLabels = true
    )
}

@Composable
private fun OverloadCanvasChart(
    points: List<OverloadDataPoint>,
    modifier: Modifier = Modifier
) {
    MinimalLineChart(
        points = points.map {
            ChartPoint(label = it.label, value = it.e1rm, highlight = it.isPR)
        },
        modifier = modifier,
        zeroBased = false
    )
}
