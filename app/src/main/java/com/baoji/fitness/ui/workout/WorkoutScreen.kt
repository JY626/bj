package com.baoji.fitness.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import com.baoji.fitness.core.designsystem.icons.LucideIcons
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
import com.baoji.fitness.ui.workout.components.RestTimerCapsule

@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    onNavigateToCoach: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeSession by viewModel.activeSession.collectAsState()
    val exerciseGroups by viewModel.exerciseGroups.collectAsState()
    val restSeconds by viewModel.restSecondsLeft.collectAsState()
    val isTimerVisible by viewModel.isRestTimerVisible.collectAsState()

    val completedSets = exerciseGroups.flatMap { it.sets }.filter { it.isCompleted }
    val totalVolume = completedSets.sumOf { it.weightKg * it.reps }.toInt()
    val totalSetsCount = exerciseGroups.flatMap { it.sets }.size

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Bar
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "PUSH SESSION A · 循环训练",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleTextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = activeSession?.title ?: "胸部与肩前束",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppleTextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppleFitnessGreen)
                            .clickable { viewModel.finishCurrentWorkout() }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "完成",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }

            // 2. Metrics Strip (Apple Fitness Style)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppleGray6)
                        .border(1.dp, AppleHairline, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MetricItem(label = "用时", value = "28:45")
                    MetricItem(label = "累计容量", value = "$totalVolume", unit = "kg")
                    MetricItem(
                        label = "完成组数",
                        value = "${completedSets.size}/$totalSetsCount",
                        valueColor = AppleFitnessGreen
                    )
                }
            }

            // 3. Apple Intelligence Suggestion Pill
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(AppleGray6)
                        .border(1.dp, AppleHairline, RoundedCornerShape(18.dp))
                        .clickable { onNavigateToCoach() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(AppleGray5),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = LucideIcons.Sparkles,
                                contentDescription = null,
                                tint = AppleFitnessGreen,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = "上周 80kg 达成 RPE 8，建议第2组加重至 82.5 kg",
                            fontSize = 12.sp,
                            color = AppleTextPrimary,
                            maxLines = 1
                        )
                    }

                    Icon(
                        imageVector = LucideIcons.ChevronRight,
                        contentDescription = null,
                        tint = AppleTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 4. Exercise Cards List
            items(exerciseGroups) { group ->
                ExerciseCard(
                    group = group,
                    onToggleSet = { viewModel.toggleSetCompletion(it) },
                    onUpdateSet = { set, w, r -> viewModel.updateSet(set, w, r) },
                    onAddSet = { viewModel.addSet(group.exercise.id) }
                )
            }

            // Bottom Spacing for Tab Bar and Rest Timer
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        // Floating Rest Timer Capsule
        RestTimerCapsule(
            visible = isTimerVisible,
            secondsLeft = restSeconds,
            onAdd30s = { viewModel.add30sRest() },
            onSkip = { viewModel.skipRestTimer() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    unit: String? = null,
    valueColor: Color = AppleTextPrimary
) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = AppleTextSecondary
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = valueColor
            )
            unit?.let {
                Text(
                    text = " $it",
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    group: ExerciseGroup,
    onToggleSet: (WorkoutSetEntity) -> Unit,
    onUpdateSet: (WorkoutSetEntity, Double, Int) -> Unit,
    onAddSet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Exercise Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = group.exercise.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppleGray5)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = group.exercise.targetMuscle,
                            fontSize = 10.sp,
                            color = AppleTextSecondary
                        )
                    }
                }
                Text(
                    text = "基准参考: ${group.exercise.personalRecordWeight} kg × ${group.exercise.personalRecordReps} · 估算1RM ${group.benchmark1RM} kg",
                    fontSize = 11.sp,
                    color = AppleTextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Replace action
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleGray5)
                    .clickable { /* replace action */ }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = LucideIcons.RefreshCw,
                    contentDescription = null,
                    tint = AppleTextSecondary,
                    modifier = Modifier.size(11.dp)
                )
                Text(text = "替换", fontSize = 11.sp, color = AppleTextSecondary)
            }
        }

        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "组", fontSize = 11.sp, color = AppleTextTertiary, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
            Text(text = "上周记录", fontSize = 11.sp, color = AppleTextTertiary, modifier = Modifier.weight(2.2f), textAlign = TextAlign.Center)
            Text(text = "重量(kg)", fontSize = 11.sp, color = AppleTextTertiary, modifier = Modifier.weight(1.8f), textAlign = TextAlign.Center)
            Text(text = "次数", fontSize = 11.sp, color = AppleTextTertiary, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
            Text(text = "完成", fontSize = 11.sp, color = AppleTextTertiary, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
        }

        // Sets Rows
        group.sets.forEachIndexed { index, set ->
            SetRowItem(
                order = index + 1,
                set = set,
                onToggle = { onToggleSet(set) },
                onUpdate = { w, r -> onUpdateSet(set, w, r) }
            )
        }

        // Add Set Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clickable { onAddSet() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = LucideIcons.Plus,
                    contentDescription = null,
                    tint = AppleFitnessGreen,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "增加组数",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppleFitnessGreen
                )
            }

            Text(
                text = "目标强度 RPE 8.0",
                fontSize = 11.sp,
                color = AppleTextSecondary
            )
        }
    }
}

@Composable
private fun SetRowItem(
    order: Int,
    set: WorkoutSetEntity,
    onToggle: () -> Unit,
    onUpdate: (Double, Int) -> Unit
) {
    var weightText by remember(set.weightKg) { mutableStateOf(set.weightKg.toString()) }
    var repsText by remember(set.reps) { mutableStateOf(set.reps.toString()) }

    val rowBg = if (set.isCompleted) AppleGray5.copy(alpha = 0.5f) else Color.Transparent
    val rowBorder = if (!set.isCompleted && order == 3) AppleFitnessGreen.copy(alpha = 0.3f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBg)
            .border(1.dp, rowBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Order
        Text(
            text = "$order",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (set.isCompleted) AppleFitnessGreen else AppleTextSecondary,
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.Center
        )

        // 2. Previous Benchmark
        Text(
            text = "${set.weightKg} × ${set.reps}",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = if (order == 3 && !set.isCompleted) AppleCautionOrange else AppleTextSecondary,
            modifier = Modifier.weight(2.2f),
            textAlign = TextAlign.Center
        )

        // 3. Weight Input
        Box(
            modifier = Modifier
                .weight(1.8f)
                .clip(RoundedCornerShape(8.dp))
                .background(AppleGray5)
                .padding(vertical = 4.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = weightText,
                onValueChange = {
                    weightText = it
                    it.toDoubleOrNull()?.let { w -> onUpdate(w, set.reps) }
                },
                textStyle = TextStyle(
                    color = AppleTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(AppleFitnessGreen),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.size(4.dp))

        // 4. Reps Input
        Box(
            modifier = Modifier
                .weight(1.2f)
                .clip(RoundedCornerShape(8.dp))
                .background(AppleGray5)
                .padding(vertical = 4.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = repsText,
                onValueChange = {
                    repsText = it
                    it.toIntOrNull()?.let { r -> onUpdate(set.weightKg, r) }
                },
                textStyle = TextStyle(
                    color = AppleTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(AppleFitnessGreen),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }

        // 5. Complete Button (Apple style circular toggle)
        Box(
            modifier = Modifier
                .weight(1.2f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (set.isCompleted) AppleFitnessGreen else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (set.isCompleted) AppleFitnessGreen else AppleHairline,
                        shape = CircleShape
                    )
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (set.isCompleted) {
                    Icon(
                        imageVector = LucideIcons.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
