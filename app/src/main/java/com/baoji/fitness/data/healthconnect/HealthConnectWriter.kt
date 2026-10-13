package com.baoji.fitness.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import com.baoji.fitness.core.database.entity.BodyMetricEntity
import com.baoji.fitness.core.database.entity.ExerciseEntity
import com.baoji.fitness.core.database.entity.WorkoutSessionEntity
import com.baoji.fitness.core.database.entity.WorkoutSetEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Health Connect 数据写入器。
 *
 * 负责把 App 内部的训练记录与身体数据写入 Android 系统的 Health Connect，
 * 供 AI 教练（如 Muse）在后台直接读取。
 *
 * 核心保证：
 * 1. **口径红线**：排除热身组；写入的累计容量与训练页显示的数字逐字对齐；
 * 2. **幂等写入**：通过固定格式 clientRecordId 去重，重复归档/更新不会产生脏数据；
 * 3. **降级与防崩溃**：设备未安装 Health Connect 或权限未就绪时静默跳过，绝不阻断正常归档流程。
 */
object HealthConnectWriter {

    /** 写入所需的全部权限列表。 */
    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class)
    )

    /** 检查系统 Health Connect SDK 是否可用。 */
    fun isAvailable(context: Context): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    /** 检查是否已获得全部写入权限。 */
    suspend fun hasPermissions(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(PERMISSIONS)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 写入训练会话记录。
     *
     * @param session 会话实体
     * @param sets 会话的所有组
     * @param exercises 动作库信息（用于解析动作名及器械）
     */
    suspend fun writeWorkout(
        context: Context,
        session: WorkoutSessionEntity,
        sets: List<WorkoutSetEntity>,
        exercises: List<ExerciseEntity>
    ): Result<Unit> = runCatching {
        if (!isAvailable(context)) return Result.success(Unit)

        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val permExercise = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        if (!granted.contains(permExercise)) return Result.success(Unit)

        // 口径红线：严格排除热身组，只保留完成组
        val effectiveSets = sets.filter { it.isCompleted && it.setType != "WARMUP" }
        if (effectiveSets.isEmpty()) return Result.success(Unit)

        val startTime = Instant.ofEpochMilli(session.startTime)
        val lastCompleted = effectiveSets.mapNotNull { it.completedAt }.maxOrNull()
        val endEpoch = lastCompleted ?: session.endTime ?: (session.startTime + 60_000L)
        val endTime = if (endEpoch > session.startTime) {
            Instant.ofEpochMilli(endEpoch)
        } else {
            startTime.plusSeconds(60)
        }
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(startTime)

        val totalVolumeKg = effectiveSets.sumOf { it.weightKg * it.reps }
        val exerciseMap = exercises.associateBy { it.id }

        val segments = effectiveSets.mapNotNull { set ->
            val completedAt = set.completedAt ?: return@mapNotNull null
            val segEnd = Instant.ofEpochMilli(completedAt)
            val segStart = segEnd.minusSeconds(30)
            val exName = exerciseMap[set.exerciseId]?.name.orEmpty()
            ExerciseSegment(
                startTime = segStart,
                endTime = segEnd,
                segmentType = mapSegmentType(exName),
                repetitions = set.reps.coerceAtLeast(1)
            )
        }

        val notesText = buildString {
            append("累计容量: ${totalVolumeKg.toInt()} kg · ${effectiveSets.size}组 (不含热身)")
            if (session.notes.isNotBlank()) {
                append(" · 备注: ${session.notes.trim()}")
            }
        }

        val record = ExerciseSessionRecord(
            startTime = startTime,
            startZoneOffset = zoneOffset,
            endTime = endTime,
            endZoneOffset = zoneOffset,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = session.title.ifBlank { "力量训练" },
            notes = notesText,
            metadata = Metadata(
                clientRecordId = "baoji:session:${session.id}",
                recordingMethod = Metadata.RECORDING_METHOD_ACTIVELY_RECORDED
            ),
            segments = segments
        )

        insertOrUpdate(client, listOf(record))
    }

    /**
     * 写入身体数据记录（体重、体脂率）。
     */
    suspend fun writeBodyMetrics(
        context: Context,
        metric: BodyMetricEntity
    ): Result<Unit> = runCatching {
        if (!isAvailable(context)) return Result.success(Unit)

        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()

        val zoneId = ZoneId.systemDefault()
        val localDate = if (metric.date > 100_000_000L) {
            Instant.ofEpochMilli(metric.date).atZone(zoneId).toLocalDate()
        } else {
            LocalDate.ofEpochDay(metric.date)
        }
        val zonedDateTime = localDate.atTime(8, 0).atZone(zoneId)
        val instant = zonedDateTime.toInstant()
        val zoneOffset = zonedDateTime.offset

        val records = mutableListOf<Record>()

        val permWeight = HealthPermission.getWritePermission(WeightRecord::class)
        if (granted.contains(permWeight) && metric.weightKg != null && metric.weightKg > 0.0) {
            records.add(
                WeightRecord(
                    time = instant,
                    zoneOffset = zoneOffset,
                    weight = Mass.kilograms(metric.weightKg),
                    metadata = Metadata(
                        clientRecordId = "baoji:weight:$localDate",
                        recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY
                    )
                )
            )
        }

        val permBodyFat = HealthPermission.getWritePermission(BodyFatRecord::class)
        if (granted.contains(permBodyFat) && metric.bodyFatPct != null && metric.bodyFatPct > 0.0) {
            records.add(
                BodyFatRecord(
                    time = instant,
                    zoneOffset = zoneOffset,
                    percentage = Percentage(metric.bodyFatPct),
                    metadata = Metadata(
                        clientRecordId = "baoji:bodyfat:$localDate",
                        recordingMethod = Metadata.RECORDING_METHOD_MANUAL_ENTRY
                    )
                )
            )
        }

        if (records.isNotEmpty()) {
            insertOrUpdate(client, records)
        }
    }

    /**
     * 幂等写入辅助方法：优先 insert，若已有 clientRecordId 则降级 update。
     */
    private suspend fun insertOrUpdate(client: HealthConnectClient, records: List<Record>) {
        try {
            client.insertRecords(records)
        } catch (_: Exception) {
            try {
                client.updateRecords(records)
            } catch (_: Exception) {
                // 忽略非致命写入异常，保持主流程畅通
            }
        }
    }

    private fun mapSegmentType(name: String): Int {
        return when {
            name.contains("卧推") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS
            name.contains("深蹲") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT
            name.contains("硬拉") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT
            name.contains("引体") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP
            name.contains("举腿") || name.contains("卷腹") || name.contains("平板") -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH
            else -> ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING
        }
    }
}
