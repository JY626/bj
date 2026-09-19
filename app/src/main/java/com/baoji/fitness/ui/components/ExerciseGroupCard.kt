package com.baoji.fitness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.domain.model.ExerciseGroupDetail

/** 只读的动作明细卡：动作名 + 部位，逐组列出「组号 · 次数 × 重量」。归档页与日历明细共用。 */
@Composable
fun ExerciseGroupCard(group: ExerciseGroupDetail, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppleGray5)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = group.exerciseName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
            if (group.targetMuscle.isNotBlank()) {
                Text(text = group.targetMuscle, fontSize = 11.sp, color = AppleTextSecondary)
            }
        }
        group.sets.forEach { s ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${s.setOrder}${if (s.setType == "WARMUP") " · 热身" else ""}",
                        fontSize = 12.sp,
                        color = AppleTextSecondary
                    )
                    // M3a：录了 RPE 才显示
                    s.rpe?.let { rpe ->
                        Text(
                            text = "  RPE ${if (rpe % 1.0 == 0.0) rpe.toInt().toString() else rpe.toString()}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppleFitnessGreen
                        )
                    }
                }
                Text(
                    text = "${s.reps} 次 × ${s.weightKg.toInt()} kg",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppleTextPrimary
                )
            }
        }
    }
}