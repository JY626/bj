package com.baoji.fitness.ui.calendar

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.database.entity.BodyMetricEntity
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
import com.baoji.fitness.data.settings.ReminderConfig
import com.baoji.fitness.domain.model.WorkoutDayDetail
import com.baoji.fitness.ui.components.ExerciseGroupCard
import com.baoji.fitness.ui.library.BodyMetricSheet
import com.baoji.fitness.ui.library.formatMetric
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val trainedDayTypes by viewModel.trainedDayTypes.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val dayDetail by viewModel.dayDetail.collectAsState()
    val dayMetric by viewModel.dayMetric.collectAsState()
    val habit by viewModel.habit.collectAsState()
    val reminderConfig by viewModel.reminderConfig.collectAsState()

    var showDayMetricSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Column {
            Text(
                text = "TRAINING LOG",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextSecondary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "每日记录",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary,
                letterSpacing = (-0.5).sp
            )
        }

        // M2：习惯徽章 + 本周还剩几练
        HabitBanner(
            currentStreak = habit.currentStreak,
            longestStreak = habit.longestStreak,
            remainingThisWeek = habit.remainingThisWeek
        )

        TrainingCheckInCalendar(
            trainedDayTypes = trainedDayTypes,
            onDaySelected = { viewModel.selectDay(it) }
        )

        Text(
            text = "点日期查看当天训练明细与身体数据",
            fontSize = 12.sp,
            color = AppleTextTertiary
        )

        // 训练设置：组间休息时长（打勾后自动开始）
        RestDurationCard(
            restSeconds = reminderConfig.restSeconds,
            onChange = { viewModel.setRestSeconds(it) }
        )

        // M2：训练日提醒设置
        ReminderSettingsCard(
            config = reminderConfig,
            onToggleEnabled = { viewModel.setReminderEnabled(it) },
            onTimeChange = { h, m -> viewModel.setReminderTime(h, m) },
            onToggleDay = { viewModel.toggleTrainingDay(it) }
        )

        Spacer(modifier = Modifier.height(80.dp))
    }

    // 当天训练明细弹层
    if (selectedDay != null) {
        DayDetailSheet(
            day = selectedDay!!,
            detail = dayDetail,
            metric = dayMetric,
            onRecordMetric = { showDayMetricSheet = true },
            onDismiss = { viewModel.selectDay(null) }
        )
    }

    // M3b：当天顺手记身体数据
    if (showDayMetricSheet && selectedDay != null) {
        BodyMetricSheet(
            day = selectedDay!!,
            latest = dayMetric,
            onDismiss = { showDayMetricSheet = false },
            onSave = { weight, fat, height, chest, waist, arm ->
                viewModel.saveMetric(selectedDay!!, weight, fat, height, chest, waist, arm)
                showDayMetricSheet = false
            }
        )
    }
}

/** 🔥 连续 N 天 · 最长 M 天 + 本周还剩 K 练。 */
@Composable
private fun HabitBanner(
    currentStreak: Int,
    longestStreak: Int,
    remainingThisWeek: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🔥 ", fontSize = 14.sp)
                Text(
                    text = "连续 $currentStreak 天",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (currentStreak > 0) AppleFitnessGreen else AppleTextTertiary
                )
            }
            Text(
                text = if (longestStreak > 0) "最长 $longestStreak 天" else "还没有连续记录",
                fontSize = 11.sp,
                color = AppleTextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (remainingThisWeek > 0) AppleCautionOrange.copy(alpha = 0.14f)
                    else AppleFitnessGreen.copy(alpha = 0.14f)
                )
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                text = if (remainingThisWeek > 0) "本周还剩 $remainingThisWeek 练" else "本周计划已完成",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (remainingThisWeek > 0) AppleCautionOrange else AppleFitnessGreen
            )
        }
    }
}

/** M2 提醒设置：开关 / 时间 / 每周训练日。 */
@Composable
private fun ReminderSettingsCard(
    config: ReminderConfig,
    onToggleEnabled: (Boolean) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit
) {    Column(
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
                    text = "训练日提醒",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Text(
                    text = "实际触发时间允许 ±15 分钟（系统限制）",
                    fontSize = 10.sp,
                    color = AppleTextTertiary
                )
            }
            TogglePill(checked = config.enabled, onCheckedChange = onToggleEnabled)
        }

        if (config.enabled) {
            SettingRowLabel("提醒时间")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (6..22).forEach { hour ->
                    ChipBox(
                        text = String.format(Locale.US, "%02d", hour),
                        selected = config.hour == hour
                    ) { onTimeChange(hour, config.minute) }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0, 15, 30, 45).forEach { minute ->
                    ChipBox(
                        text = String.format(Locale.US, ":%02d", minute),
                        selected = config.minute == minute
                    ) { onTimeChange(config.hour, minute) }
                }
            }

            SettingRowLabel("每周训练日")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WEEK_LABELS.forEach { (day, label) ->
                    ChipBox(
                        text = label,
                        selected = day in config.trainingDays,
                        modifier = Modifier.weight(1f)
                    ) { onToggleDay(day) }
                }
            }
        }
    }
}

/** 组间休息时长：打勾后自动倒计时用的秒数。 */
@Composable
private fun RestDurationCard(restSeconds: Int, onChange: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = "组间休息时长",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
            Text(
                text = "打勾后自动开始倒计时；大重量复合动作可调到 180s",
                fontSize = 10.sp,
                color = AppleTextTertiary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(60 to "60s", 90 to "90s", 120 to "2min", 180 to "3min", 240 to "4min").forEach { (sec, label) ->
                ChipBox(
                    text = label,
                    selected = restSeconds == sec,
                    modifier = Modifier.weight(1f)
                ) { onChange(sec) }
            }
        }
    }
}


@Composable
private fun SettingRowLabel(text: String) {
    Text(text = text, fontSize = 11.sp, color = AppleTextSecondary)
}

@Composable
private fun ChipBox(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else AppleGray5)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = if (selected) Color.Black else AppleTextSecondary
        )
    }
}

/** 没有真实 Switch 组件依赖时的轻量开关。 */
@Composable
private fun TogglePill(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(if (checked) AppleFitnessGreen else AppleGray4)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) Color.Black else Color.White)
        )
    }
}

private val WEEK_LABELS = listOf(
    DayOfWeek.MONDAY to "一",
    DayOfWeek.TUESDAY to "二",
    DayOfWeek.WEDNESDAY to "三",
    DayOfWeek.THURSDAY to "四",
    DayOfWeek.FRIDAY to "五",
    DayOfWeek.SATURDAY to "六",
    DayOfWeek.SUNDAY to "日"
)

@Composable
private fun TrainingCheckInCalendar(
    trainedDayTypes: Map<LocalDate, String>,
    onDaySelected: (LocalDate) -> Unit
) {
    var monthState by remember { mutableStateOf(YearMonth.now()) }
    val month = monthState
    val today = LocalDate.now()

    // 周一为首列；末尾补 null 保证每行 7 格对齐
    val cells = remember(month) { monthGridCells(month) }
    val monthTrainedCount = cells.count { it != null && trainedDayTypes.containsKey(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppleGray6)
            .border(1.dp, AppleHairline, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "本月打卡 $monthTrainedCount 天",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppleTextPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonthNavButton("‹") { monthState = month.minusMonths(1) }
                Text(
                    text = "${month.year}年${month.monthValue}月",
                    fontSize = 12.sp,
                    color = AppleTextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                MonthNavButton("›") { monthState = month.plusMonths(1) }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    text = it,
                    fontSize = 10.sp,
                    color = AppleTextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        DayCell(
                            day = day,
                            typeLabel = trainedDayTypes[day],
                            isToday = day == today,
                            onClick = { onDaySelected(day) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    typeLabel: String?,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trained = typeLabel != null
    Column(
        modifier = modifier
            .height(46.dp)
            .clickable(enabled = trained) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (trained) AppleFitnessGreen else Color.Transparent)
                .then(
                    if (isToday && !trained) Modifier.border(1.dp, AppleFitnessGreen, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${day.dayOfMonth}",
                fontSize = 12.sp,
                fontWeight = if (trained || isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (trained) Color.Black else if (isToday) AppleFitnessGreen else AppleTextSecondary
            )
        }
        // 训练类型标签：推 / 拉 / 腿
        Text(
            text = typeLabel ?: "",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppleFitnessGreen,
            maxLines = 1
        )
    }
}

@Composable
private fun MonthNavButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(AppleGray5)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontSize = 13.sp, color = AppleTextPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(
    day: LocalDate,
    detail: WorkoutDayDetail?,
    metric: BodyMetricEntity?,
    onRecordMetric: () -> Unit,
    onDismiss: () -> Unit
) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = day.format(dateFormat),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleFitnessGreen,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = detail?.title ?: "加载中…",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppleTextPrimary
                )
            }

            if (detail != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppleGray5)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    DetailMetric("累计容量", "${detail.totalVolumeKg.toInt()} kg", AppleFitnessGreen)
                    DetailMetric("完成组数", "${detail.groups.sumOf { it.sets.size }} 组", AppleTextPrimary)
                    DetailMetric("动作数", "${detail.groups.size}", AppleTextPrimary)
                }

                // M3a：训练当天写的自述备注
                if (detail.notes.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppleGray5)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "笔记", fontSize = 10.sp, color = AppleTextSecondary)
                        Text(
                            text = detail.notes,
                            fontSize = 12.sp,
                            color = AppleTextPrimary,
                            lineHeight = 17.sp
                        )
                    }
                }

                detail.groups.forEach { group ->
                    ExerciseGroupCard(group)
                }
            }

            // M3b：当天称重就顺手记
            Column(
                modifier = Modifier
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
                        text = "当天身体数据",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleTextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppleGray6)
                            .clickable { onRecordMetric() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (metric == null) "记一次" else "修改",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleFitnessGreen
                        )
                    }
                }
                Text(
                    text = metric?.let {
                        listOfNotNull(
                            it.weightKg?.let { v -> "体重 ${formatMetric(v)} kg" },
                            it.bodyFatPct?.let { v -> "体脂 ${formatMetric(v)} %" },
                            it.chestCm?.let { v -> "胸围 ${formatMetric(v)} cm" },
                            it.waistCm?.let { v -> "腰围 ${formatMetric(v)} cm" },
                            it.armCm?.let { v -> "臂围 ${formatMetric(v)} cm" }
                        ).joinToString(" · ").ifBlank { "这一天没有记录数值" }
                    } ?: "这一天还没称",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (metric == null) AppleTextTertiary else AppleTextPrimary
                )
            }
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = AppleTextSecondary)
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}
