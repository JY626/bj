package com.baoji.fitness.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.core.designsystem.icons.LucideIcons
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray4
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleGray6
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.core.theme.AppleTextTertiary
import com.baoji.fitness.domain.algorithm.OverloadCalculator
import com.baoji.fitness.domain.model.ExercisePrSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    prSummary: ExercisePrSummary,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppleGray6,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(AppleGray4)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 动作标题与分类
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = prSummary.exercise.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TagBadge(prSummary.exercise.targetMuscle)
                        TagBadge(prSummary.exercise.equipment)
                        if (prSummary.exercise.isCustom) {
                            TagBadge("自定义动作")
                        }
                    }
                }
            }

            // 个人纪录 (PR) 荣誉墙网格
            Text(
                text = "PERSONAL RECORDS · 个人纪录荣誉墙",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextSecondary,
                letterSpacing = 0.5.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrStatCard(
                    title = "极限 1RM 拟合",
                    value = if (prSummary.bestE1RM > 0) "${formatMetric(prSummary.bestE1RM)} kg" else "--",
                    subtitle = prSummary.bestE1RMSet?.let { "最佳组 ${formatMetric(it.weightKg)}×${it.reps}" } ?: "基础参考",
                    accentColor = AppleFitnessGreen,
                    modifier = Modifier.weight(1f)
                )
                PrStatCard(
                    title = "历史最大重量",
                    value = if (prSummary.maxWeightKg > 0) "${formatMetric(prSummary.maxWeightKg)} kg" else "--",
                    subtitle = prSummary.maxWeightSet?.let { "${it.reps} 次" } ?: "基础参考",
                    accentColor = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PrStatCard(
                    title = "最佳容量组",
                    value = prSummary.bestVolumeSet?.let { "${formatMetric(it.weightKg * it.reps)} kg" } ?: "--",
                    subtitle = prSummary.bestVolumeSet?.let { "${formatMetric(it.weightKg)}kg × ${it.reps}次" } ?: "暂无记录",
                    accentColor = AppleFitnessGreen,
                    modifier = Modifier.weight(1f)
                )
                PrStatCard(
                    title = "总做组与容量",
                    value = "${prSummary.totalCompletedSets} 组",
                    subtitle = "累计 ${prSummary.totalVolumeKg.toInt()} kg",
                    accentColor = AppleTextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }

            // 历史表现时间线
            Text(
                text = "TRAINING HISTORY · 历史做组记录",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextSecondary,
                letterSpacing = 0.5.sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppleGray5)
                    .border(0.5.dp, AppleHairline, RoundedCornerShape(18.dp))
            ) {
                if (prSummary.historySets.isEmpty()) {
                    Text(
                        text = "暂无正式完成记录，开练打勾后即可在此回顾每一次突破与 E1RM 进阶！",
                        fontSize = 12.sp,
                        color = AppleTextTertiary,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    prSummary.historySets.forEachIndexed { index, set ->
                        HistorySetRow(set = set)
                        if (index < prSummary.historySets.size - 1) {
                            HorizontalDivider(color = AppleHairline, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppleGray5)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, fontSize = 10.sp, color = AppleTextSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PrStatCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppleGray5)
            .border(0.5.dp, AppleHairline, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(text = title, fontSize = 10.sp, color = AppleTextSecondary)
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = accentColor,
            modifier = Modifier.padding(vertical = 3.dp)
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = AppleTextTertiary,
            maxLines = 1
        )
    }
}

@Composable
private fun HistorySetRow(set: WorkoutSetEntity) {
    val formatter = remember { DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE) }
    val dateText = remember(set.completedAt) {
        set.completedAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
        } ?: "未记录时间"
    }
    val e1rm = remember(set.weightKg, set.reps) {
        OverloadCalculator.calculateEstimated1RM(set.weightKg, set.reps)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "${formatMetric(set.weightKg)} kg × ${set.reps} 次",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = AppleTextPrimary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(text = dateText, fontSize = 10.sp, color = AppleTextTertiary)
                if (set.setType != "NORMAL") {
                    Text(
                        text = when (set.setType) {
                            "WARMUP" -> "热身"
                            "DROP" -> "递减"
                            "FAILURE" -> "力竭"
                            else -> set.setType
                        },
                        fontSize = 10.sp,
                        color = AppleFitnessGreen
                    )
                }
                set.rpe?.let {
                    Text(text = "RPE $it", fontSize = 10.sp, color = AppleTextSecondary)
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "E1RM",
                fontSize = 9.sp,
                color = AppleTextTertiary
            )
            Text(
                text = "${formatMetric(e1rm)} kg",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = AppleFitnessGreen
            )
        }
    }
}
