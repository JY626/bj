package com.baoji.fitness.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.designsystem.icons.LucideIcons
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleGray4
import com.baoji.fitness.core.theme.AppleGray5
import com.baoji.fitness.core.theme.AppleGray6
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleTextPrimary
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.core.theme.AppleTextTertiary
import com.baoji.fitness.data.repository.AiShareContext
import com.baoji.fitness.domain.algorithm.AiShareTextFormatter

/**
 * 分享给 AI（如 Muse）的预览 BottomSheet。
 *
 * 遵循隐私门设计原则：
 * 1. 明确展示所有将导出的 Markdown 文本；
 * 2. 支持勾选/取消备注字段；
 * 3. 明确告知纯本地运行、不经过第三方服务器；
 * 4. 确认后调起系统 Sharesheet 或复制剪贴板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSharePreviewBottomSheet(
    shareContext: AiShareContext?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var includeNotes by remember { mutableStateOf(true) }

    val formattedText = remember(shareContext, includeNotes) {
        if (shareContext == null) ""
        else AiShareTextFormatter.buildAiShareText(
            detail = shareContext.detail,
            includeNotes = includeNotes,
            previousSameTypeVolume = shareContext.previousSameTypeVolume,
            weeklySessionCount = shareContext.weeklySessionCount,
            weeklyTotalVolumeKg = shareContext.weeklyTotalVolumeKg,
            weeklyVolumeDeltaPct = shareContext.weeklyVolumeDeltaPct,
            weeklyMuscles = shareContext.weeklyMuscles,
            latestMetric = shareContext.latestMetric,
            previousMetric = shareContext.previousMetric
        )
    }

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
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppleFitnessGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = LucideIcons.Sparkles,
                        contentDescription = null,
                        tint = AppleFitnessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "分享给 AI 教练",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleTextPrimary
                    )
                    Text(
                        text = "生成格式化 Markdown 卡片，方便 Muse 解析",
                        fontSize = 11.sp,
                        color = AppleTextSecondary
                    )
                }
            }

            // 隐私安全提示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppleGray5)
                    .border(0.5.dp, AppleHairline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "🔒 隐私保障：纯本地生成，零服务器中转。通过系统分享直接转发给 Muse。",
                    fontSize = 11.sp,
                    color = AppleTextSecondary,
                    lineHeight = 16.sp
                )
            }

            if (shareContext == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AppleFitnessGreen,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            } else {
                // 选项控制：是否包含自述备注
                if (shareContext.detail.notes.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { includeNotes = !includeNotes }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeNotes,
                            onCheckedChange = { includeNotes = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AppleFitnessGreen,
                                checkmarkColor = Color.Black,
                                uncheckedColor = AppleTextTertiary
                            )
                        )
                        Text(
                            text = "包含自述状态与训练备注",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppleTextPrimary
                        )
                    }
                }

                // 预览框
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppleGray5)
                        .border(0.5.dp, AppleHairline, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = formattedText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AppleTextPrimary,
                        lineHeight = 18.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 复制到剪贴板
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppleGray5)
                            .clickable {
                                copyToClipboard(context, formattedText)
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "复制文本",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleTextPrimary
                        )
                    }

                    // 调起系统分享
                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppleFitnessGreen)
                            .clickable {
                                shareTextViaSystem(context, formattedText)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = LucideIcons.ArrowUp,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "发送给 AI (Muse)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Workout Log", text)
    clipboard.setPrimaryClip(clip)
}

private fun shareTextViaSystem(context: Context, text: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(sendIntent, "分享训练记录给 AI")
    context.startActivity(chooser)
}
