package com.baoji.fitness.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 保存下来的私教分析报告。**一天一个范围一条**：`date`+`scope` 唯一索引，
 * 同天同范围重新生成是覆盖（REPLACE），不会攒出重复条目。
 *
 * `content` 存模型/本地给的 markdown（含 `## 小标题`），渲染时用
 * `CoachReport.splitSections` 还原成卡片。存原文而不是切好的卡，是为了以后
 * 改分区标题时旧报告仍能读。
 */
@Entity(
    tableName = "coach_reports",
    indices = [Index(value = ["date", "scope"], unique = true)]
)
data class CoachReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 生成当天本地 0 点 epoch millis —— 与 body_metrics/日历同一天语义。 */
    val date: Long,
    /** TODAY / THIS_WEEK / LAST_4_WEEKS */
    val scope: String,
    val content: String,
    /** LOCAL / LLM */
    val source: String,
    val generatedAt: Long
)