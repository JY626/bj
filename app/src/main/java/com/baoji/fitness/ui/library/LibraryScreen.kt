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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.designsystem.icons.LucideIcons
import com.baoji.fitness.core.theme.AppleBlack
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray4
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleGray6
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.core.theme.AppleTextTertiary
import com.baoji.fitness.data.repository.BodyMetricRepository
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.ui.components.ChartPoint
import com.baoji.fitness.ui.components.MinimalLineChart
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 数值 → 文本：整数不显示小数点，其余保留一位。 */
internal fun formatMetric(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

@Composable
fun LibraryScreen(
    repository: WorkoutRepository,
    bodyMetricRepository: BodyMetricRepository,
    modifier: Modifier = Modifier
) {
    val exercises by repository.getAllExercises().collectAsState(initial = emptyList())
    val latestMetric by bodyMetricRepository.observeLatest().collectAsState(initial = null)
    val allMetrics by bodyMetricRepository.observeAll().collectAsState(initial = emptyList())

    var selectedMuscle by remember { mutableStateOf("全部") }
    var showMetricSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

        // 4. Body Metrics Card —— M3b：改读真实记录（此前是写死的 73.5 / 15.2）
        item {
            BodyMetricsCard(
                latest = latestMetric,
                history = allMetrics,
                onRecord = { showMetricSheet = true }
            )
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (showMetricSheet) {
        BodyMetricSheet(
            day = LocalDate.now(),
            latest = latestMetric,
            onDismiss = { showMetricSheet = false },
            onSave = { weight, fat, chest, waist, arm ->
                scope.launch {
                    bodyMetricRepository.upsert(
                        day = LocalDate.now(),
                        weightKg = weight,
                        bodyFatPct = fat,
                        chestCm = chest,
                        waistCm = waist,
                        armCm = arm
                    )
                }
                showMetricSheet = false
            }
        )
    }
}

@Composable
private fun BodyMetricsCard(
    latest: BodyMetricEntity?,
    history: List<BodyMetricEntity>,
    onRecord: () -> Unit
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "生理参数与健康上下文",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Text(
                    text = latest?.let {
                        "最近记录 " + millisToDay(it.date)
                            .format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE))
                    } ?: "还没有记录",
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleGray5)
                    .clickable { onRecord() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = LucideIcons.Plus,
                        contentDescription = null,
                        tint = AppleFitnessGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (latest == null) "记一次" else "记录",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleFitnessGreen
                    )
                }
            }
        }

        if (latest == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleGray5)
                    .clickable { onRecord() }
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "还没有记录，点这里记一次",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleFitnessGreen
                    )
                    Text(
                        text = "体重 / 体脂 / 围度 · 一天一条",
                        fontSize = 10.sp,
                        color = AppleTextTertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BioCard(
                    "体重",
                    latest.weightKg?.let { formatMetric(it) } ?: "--",
                    "kg",
                    Modifier.weight(1f)
                )
                BioCard(
                    "体脂率",
                    latest.bodyFatPct?.let { formatMetric(it) } ?: "--",
                    "%",
                    Modifier.weight(1f)
                )
                BioCard(
                    "腰围",
                    latest.waistCm?.let { formatMetric(it) } ?: "--",
                    "cm",
                    Modifier.weight(1f)
                )
            }

            // 围度补充行（只有录过才显示）
            val extra = listOfNotNull(
                latest.chestCm?.let { "胸围 ${formatMetric(it)} cm" },
                latest.armCm?.let { "臂围 ${formatMetric(it)} cm" }
            )
            if (extra.isNotEmpty()) {
                Text(
                    text = extra.joinToString(" · "),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppleTextSecondary
                )
            }
        }

        // 趋势图：至少两次记录才有意义
        val weightPoints = history.mapNotNull { m ->
            m.weightKg?.let { ChartPoint(toShortDate(m.date), it) }
        }
        val waistPoints = history.mapNotNull { m ->
            m.waistCm?.let { ChartPoint(toShortDate(m.date), it) }
        }

        if (weightPoints.size >= 2) {
            MetricTrend(
                title = "体重趋势",
                unit = "kg",
                points = weightPoints,
                zeroBased = false
            )
        }
        if (waistPoints.size >= 2) {
            MetricTrend(
                title = "腰围趋势",
                unit = "cm",
                points = waistPoints,
                zeroBased = false
            )
        }
    }
}

@Composable
private fun MetricTrend(
    title: String,
    unit: String,
    points: List<ChartPoint>,
    zeroBased: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, fontSize = 12.sp, color = AppleTextSecondary)
            Text(
                text = "${formatMetric(points.last().value)} $unit",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = AppleFitnessGreen
            )
        }
        MinimalLineChart(
            points = points,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            zeroBased = zeroBased,
            showXLabels = true
        )
    }
}

/** date 是当天本地 0 点 millis，直接换算回日期做短标签。 */
private fun toShortDate(dateMillis: Long): String {
    val day = millisToDay(dateMillis)
    return "${day.monthValue}/${day.dayOfMonth}"
}

private fun millisToDay(dateMillis: Long): LocalDate =
    java.time.Instant.ofEpochMilli(dateMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()

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

/**
 * M3b 录入弹层。预填最新一条记录（无记录时给中性基准），
 * 用步进 pills 调整 —— 健身房里单手可操作的交互。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BodyMetricSheet(
    day: LocalDate,
    latest: BodyMetricEntity?,
    onDismiss: () -> Unit,
    onSave: (Double?, Double?, Double?, Double?, Double?) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var weight by remember { mutableStateOf(latest?.weightKg ?: 75.0) }
    var fat by remember { mutableStateOf(latest?.bodyFatPct ?: 18.0) }
    var chest by remember { mutableStateOf(latest?.chestCm ?: 100.0) }
    var waist by remember { mutableStateOf(latest?.waistCm ?: 80.0) }
    var arm by remember { mutableStateOf(latest?.armCm ?: 35.0) }

    /** 单位转换：厘米 ↔ 斤不做，全部按 kg / cm / %。 */
    fun step(current: Double, delta: Double): Double =
        ((current + delta).coerceAtLeast(0.0) * 10).toInt() / 10.0

    val dateFormat = remember { DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE) }

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "记录身体数据",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary
                )
                Text(
                    text = "${day.format(dateFormat)} · 同一天重复记录会覆盖",
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
            }

            MetricStepper(
                label = "体重",
                unit = "kg",
                value = weight,
                deltas = listOf(-1.0, -0.5, 0.5, 1.0),
                onDelta = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    weight = step(weight, it)
                }
            )
            MetricStepper(
                label = "体脂率",
                unit = "%",
                value = fat,
                deltas = listOf(-1.0, -0.5, 0.5, 1.0),
                onDelta = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    fat = step(fat, it)
                }
            )
            MetricStepper(
                label = "胸围",
                unit = "cm",
                value = chest,
                deltas = listOf(-2.0, -1.0, 1.0, 2.0),
                onDelta = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    chest = step(chest, it)
                }
            )
            MetricStepper(
                label = "腰围",
                unit = "cm",
                value = waist,
                deltas = listOf(-2.0, -1.0, 1.0, 2.0),
                onDelta = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    waist = step(waist, it)
                }
            )
            MetricStepper(
                label = "臂围",
                unit = "cm",
                value = arm,
                deltas = listOf(-2.0, -1.0, 1.0, 2.0),
                onDelta = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    arm = step(arm, it)
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleFitnessGreen)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSave(weight, fat, chest, waist, arm)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "保存",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun MetricStepper(
    label: String,
    unit: String,
    value: Double,
    deltas: List<Double>,
    onDelta: (Double) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppleGray5)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 12.sp, color = AppleTextSecondary)
            Text(
                text = "${formatMetric(value)} $unit",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = AppleTextPrimary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            deltas.forEach { delta ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppleGray6)
                        .clickable { onDelta(delta) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (delta > 0) "+${formatMetric(delta)}" else formatMetric(delta),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary
                    )
                }
            }
        }
    }
}
