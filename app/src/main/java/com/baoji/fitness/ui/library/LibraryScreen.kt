package com.baoji.fitness.ui.library

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.designsystem.icons.LucideIcons
import com.baoji.fitness.core.theme.AppleBlack
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleGray6
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.data.repository.WorkoutRepository

@Composable
fun LibraryScreen(
    repository: WorkoutRepository,
    modifier: Modifier = Modifier
) {
    val exercises by repository.getAllExercises().collectAsState(initial = emptyList())
    var selectedMuscle by remember { mutableStateOf("全部") }

    val filtered = if (selectedMuscle == "全部") {
        exercises
    } else {
        exercises.filter { it.targetMuscle == selectedMuscle }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                Text(
                    text = "EXERCISE ENCYCLOPEDIA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextSecondary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "动作库与档案",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary,
                    letterSpacing = (-0.5).sp
                )
            }
        }

        // 2. Muscle Filter
        item {
            val muscles = listOf("全部", "胸部", "背部", "腿部", "肩部", "手臂")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                muscles.forEach { muscle ->
                    val isSelected = (muscle == selectedMuscle)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color.White else AppleGray6)
                            .clickable { selectedMuscle = muscle }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = muscle,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else AppleTextSecondary
                        )
                    }
                }
            }
        }

        // 3. Exercise List Card Container
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppleGray6)
                    .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
            ) {
                filtered.forEachIndexed { index, exercise ->
                    ExerciseListItem(exercise = exercise)
                    if (index < filtered.size - 1) {
                        HorizontalDivider(color = AppleHairline, thickness = 0.5.dp)
                    }
                }
            }
        }

        // 4. Biometrics Profile Card (Apple Health Style)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppleGray6)
                    .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "生理参数与健康上下文",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = "已同步",
                        fontSize = 11.sp,
                        color = AppleFitnessGreen
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BioCard("体重", "73.5", "kg", Modifier.weight(1f))
                    BioCard("体脂率", "15.2", "%", Modifier.weight(1f))
                    BioCard("首要目标", "肌肥大", null, Modifier.weight(1f), valueColor = AppleFitnessGreen)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ExerciseListItem(exercise: ExerciseEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = exercise.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
            val prInfo = if (exercise.personalRecordWeight > 0) {
                " · 个人记录 ${exercise.personalRecordWeight} kg"
            } else ""
            Text(
                text = "${exercise.targetMuscle} · ${exercise.equipment}$prInfo",
                fontSize = 11.sp,
                color = AppleTextSecondary,
                modifier = Modifier.padding(top = 2.dp)
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

@Composable
private fun BioCard(
    label: String,
    value: String,
    unit: String?,
    modifier: Modifier = Modifier,
    valueColor: Color = AppleTextPrimary
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppleGray5)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 10.sp, color = AppleTextSecondary)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = valueColor
            )
            unit?.let {
                Text(
                    text = " $it",
                    fontSize = 10.sp,
                    color = AppleTextSecondary
                )
            }
        }
    }
}
