package com.baoji.fitness.ui.body

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import com.baoji.fitness.data.healthconnect.HealthConnectWriter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.designsystem.icons.LucideIcons
import com.baoji.fitness.core.theme.AppleBlack
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleGray6
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.core.theme.AppleTextTertiary
import com.baoji.fitness.data.repository.BodyMetricRepository
import com.baoji.fitness.ui.components.ChartPoint
import com.baoji.fitness.ui.components.MinimalLineChart
import com.baoji.fitness.ui.library.BodyMetricSheet
import com.baoji.fitness.ui.library.formatMetric
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 「身体」Tab：体重 / 体脂 / 围度 / 身高。
 * 从原「动作库」页拆出来 —— 动作库管动作，这里管身体，两件事不混在一个页面。
 */
@Composable
fun BodyScreen(
    bodyMetricRepository: BodyMetricRepository,
    modifier: Modifier = Modifier
) {
    val latest by bodyMetricRepository.observeLatest().collectAsState(initial = null)
    val history by bodyMetricRepository.observeAll().collectAsState(initial = emptyList())

    var showSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isHealthAvailable = remember { HealthConnectWriter.isAvailable(context) }
    var hasHealthPermissions by remember { mutableStateOf(false) }

    val healthConnectLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        hasHealthPermissions = granted.contains(HealthPermission.getReadPermission(WeightRecord::class)) ||
            granted.contains(HealthPermission.getReadPermission(BodyFatRecord::class))
        if (hasHealthPermissions) {
            scope.launch {
                HealthConnectWriter.syncBodyMetricsFromHealthConnect(context, bodyMetricRepository)
                    .onSuccess { count ->
                        val msg = if (count > 0) "成功从 Health Connect 同步 $count 天数据" else "已是最新，无待同步数据"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, "同步失败: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (isHealthAvailable) {
            hasHealthPermissions = HealthConnectWriter.hasReadPermissions(context)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Column {
            Text(
                text = "身体数据",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppleTextPrimary
            )
            Text(
                text = "体重 / 体脂 / 身高 / 围度 · 一天一条，用来跟训练量一起看",
                fontSize = 11.sp,
                color = AppleTextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        BodyMetricsCard(
            latest = latest,
            history = history,
            onRecord = { showSheet = true },
            onSyncHealth = if (isHealthAvailable) {
                {
                    if (!hasHealthPermissions) {
                        healthConnectLauncher.launch(HealthConnectWriter.PERMISSIONS)
                    } else {
                        scope.launch {
                            HealthConnectWriter.syncBodyMetricsFromHealthConnect(context, bodyMetricRepository)
                                .onSuccess { count ->
                                    val msg = if (count > 0) "成功从 Health Connect 同步 $count 天数据" else "已是最新，无待同步数据"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    Toast.makeText(context, "同步失败: ${err.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }
            } else null
        )

        // 历史记录列表：最近 30 条，方便回看趋势
        if (history.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppleGray6)
                    .border(1.dp, AppleHairline, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "历史记录",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                history.asReversed().take(30).forEach { m ->
                    val parts = listOfNotNull(
                        m.weightKg?.let { "体重 ${formatMetric(it)}" },
                        m.bodyFatPct?.let { "体脂 ${formatMetric(it)}%" },
                        m.heightCm?.let { "身高 ${formatMetric(it)}" },
                        m.waistCm?.let { "腰围 ${formatMetric(it)}" },
                        m.chestCm?.let { "胸围 ${formatMetric(it)}" },
                        m.armCm?.let { "臂围 ${formatMetric(it)}" }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = millisToDay(m.date)
                                .format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE)),
                            fontSize = 11.sp,
                            color = AppleTextSecondary
                        )
                        Text(
                            text = parts.joinToString(" · ").ifBlank { "--" },
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppleTextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showSheet) {
        BodyMetricSheet(
            day = LocalDate.now(),
            latest = latest,
            onDismiss = { showSheet = false },
            onSave = { weight, fat, height, chest, waist, arm ->
                scope.launch {
                    bodyMetricRepository.upsert(
                        day = LocalDate.now(),
                        weightKg = weight,
                        bodyFatPct = fat,
                        heightCm = height,
                        chestCm = chest,
                        waistCm = waist,
                        armCm = arm
                    )
                }
                showSheet = false
            }
        )
    }
}

@Composable
private fun BodyMetricsCard(
    latest: BodyMetricEntity?,
    history: List<BodyMetricEntity>,
    onRecord: () -> Unit,
    onSyncHealth: (() -> Unit)? = null
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
                    text = "最新一次",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppleTextPrimary
                )
                Text(
                    text = latest?.let {
                        "记录于 " + millisToDay(it.date)
                            .format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINESE))
                    } ?: "还没有记录",
                    fontSize = 11.sp,
                    color = AppleTextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onSyncHealth != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppleGray5)
                            .clickable { onSyncHealth() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = LucideIcons.Activity,
                                contentDescription = null,
                                tint = AppleFitnessGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "同步",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleFitnessGreen
                            )
                        }
                    }
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
                        text = "体重 / 体脂 / 身高 / 围度 · 一天一条",
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
                BioCard("体重", latest.weightKg?.let { formatMetric(it) } ?: "--", "kg", Modifier.weight(1f))
                BioCard("体脂率", latest.bodyFatPct?.let { formatMetric(it) } ?: "--", "%", Modifier.weight(1f))
                BioCard("身高", latest.heightCm?.let { formatMetric(it) } ?: "--", "cm", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BioCard("BMI", latest.bmiText() ?: "--", null, Modifier.weight(1f))
                BioCard("腰围", latest.waistCm?.let { formatMetric(it) } ?: "--", "cm", Modifier.weight(1f))
                BioCard("臂围", latest.armCm?.let { formatMetric(it) } ?: "--", "cm", Modifier.weight(1f))
            }
            latest.chestCm?.let {
                Text(
                    text = "胸围 ${formatMetric(it)} cm",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AppleTextSecondary
                )
            }
        }

        // 趋势图：至少两次记录才有意义
        val weightPoints = history.mapNotNull { m -> m.weightKg?.let { ChartPoint(toShortDate(m.date), it) } }
        val waistPoints = history.mapNotNull { m -> m.waistCm?.let { ChartPoint(toShortDate(m.date), it) } }
        val fatPoints = history.mapNotNull { m -> m.bodyFatPct?.let { ChartPoint(toShortDate(m.date), it) } }

        if (weightPoints.size >= 2) MetricTrend("体重趋势", "kg", weightPoints)
        if (fatPoints.size >= 2) MetricTrend("体脂趋势", "%", fatPoints)
        if (waistPoints.size >= 2) MetricTrend("腰围趋势", "cm", waistPoints)
    }
}

/** BMI = 体重 / 身高²，四舍五入到 1 位；缺任一返回 null（与 BodyProfile.bmi 同一口径）。 */
private fun BodyMetricEntity.bmiText(): String? {
    val w = weightKg ?: return null
    val h = heightCm?.takeIf { it > 0 } ?: return null
    val m = h / 100.0
    return formatMetric(kotlin.math.round(w / (m * m) * 10) / 10)
}

@Composable
private fun MetricTrend(title: String, unit: String, points: List<ChartPoint>) {
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
            zeroBased = false,
            showXLabels = true
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
            if (unit != null) {
                Text(
                    text = " $unit",
                    fontSize = 10.sp,
                    color = AppleTextSecondary,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }
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