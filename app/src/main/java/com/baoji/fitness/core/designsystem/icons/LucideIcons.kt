package com.baoji.fitness.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object LucideIcons {
    // 1. Dumbbell (Workout)
    val Dumbbell: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideDumbbell",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6.5f, 6.5f)
                lineTo(17.5f, 17.5f)
                moveTo(14.5f, 9.5f)
                lineTo(19.5f, 14.5f)
                moveTo(4.5f, 19.5f)
                lineTo(9.5f, 14.5f)
                moveTo(18f, 22f)
                lineTo(22f, 18f)
                moveTo(2f, 6f)
                lineTo(6f, 2f)
            }
        }.build()
    }

    // 2. Activity / Trending (Overload Analytics)
    val Activity: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideActivity",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22f, 12f)
                lineTo(18f, 12f)
                lineTo(15f, 21f)
                lineTo(9f, 3f)
                lineTo(6f, 12f)
                lineTo(2f, 12f)
            }
        }.build()
    }

    // 3. Sparkles (AI Coach)
    val Sparkles: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideSparkles",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 3f)
                lineTo(9.5f, 9.5f)
                lineTo(3f, 12f)
                lineTo(9.5f, 14.5f)
                lineTo(12f, 21f)
                lineTo(14.5f, 14.5f)
                lineTo(21f, 12f)
                lineTo(14.5f, 9.5f)
                close()
                moveTo(19f, 5f)
                lineTo(19f, 9f)
                moveTo(17f, 7f)
                lineTo(21f, 7f)
            }
        }.build()
    }

    // 4. Layers (Library)
    val Layers: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideLayers",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 2f)
                lineTo(2f, 7f)
                lineTo(12f, 12f)
                lineTo(22f, 7f)
                close()
                moveTo(2f, 12f)
                lineTo(12f, 17f)
                lineTo(22f, 12f)
                moveTo(2f, 17f)
                lineTo(12f, 22f)
                lineTo(22f, 17f)
            }
        }.build()
    }

    // 5. Check
    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20f, 6f)
                lineTo(9f, 17f)
                lineTo(4f, 12f)
            }
        }.build()
    }

    // 6. Timer
    val Timer: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideTimer",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 2f)
                lineTo(14f, 2f)
                moveTo(12f, 14f)
                lineTo(15f, 11f)
                // Circle approximation
                moveTo(12f, 6f)
                arcTo(8f, 8f, 0f, true, true, 11.99f, 6f)
            }
        }.build()
    }

    // 7. RefreshCw (Replace Exercise)
    val RefreshCw: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideRefreshCw",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(21f, 3f)
                lineTo(21f, 8f)
                lineTo(16f, 8f)
                moveTo(3f, 21f)
                lineTo(3f, 16f)
                lineTo(8f, 16f)
                moveTo(21f, 8f)
                arcTo(9f, 9f, 0f, false, false, 5.64f, 5.64f)
                lineTo(3f, 8f)
                moveTo(3f, 16f)
                arcTo(9f, 9f, 0f, false, false, 18.36f, 18.36f)
                lineTo(21f, 16f)
            }
        }.build()
    }

    // 8. Plus
    val Plus: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucidePlus",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 12f)
                lineTo(19f, 12f)
                moveTo(12f, 5f)
                lineTo(12f, 19f)
            }
        }.build()
    }

    // 9. Camera
    val Camera: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideCamera",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(14.5f, 4f)
                lineTo(9.5f, 4f)
                lineTo(7f, 7f)
                lineTo(4f, 7f)
                arcTo(2f, 2f, 0f, false, false, 2f, 9f)
                lineTo(2f, 19f)
                arcTo(2f, 2f, 0f, false, false, 4f, 21f)
                lineTo(20f, 21f)
                arcTo(2f, 2f, 0f, false, false, 22f, 19f)
                lineTo(22f, 9f)
                arcTo(2f, 2f, 0f, false, false, 20f, 7f)
                lineTo(17f, 7f)
                close()
                moveTo(12f, 10f)
                arcTo(3f, 3f, 0f, true, true, 11.99f, 10f)
            }
        }.build()
    }

    // 10. ArrowUp (Send)
    val ArrowUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideArrowUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 19f)
                lineTo(12f, 5f)
                moveTo(5f, 12f)
                lineTo(12f, 5f)
                lineTo(19f, 12f)
            }
        }.build()
    }

    // 11. ChevronRight
    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(9f, 18f)
                lineTo(15f, 12f)
                lineTo(9f, 6f)
            }
        }.build()
    }

    // 12. Calendar
    val Calendar: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideCalendar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8f, 2f)
                lineTo(8f, 6f)
                moveTo(16f, 2f)
                lineTo(16f, 6f)
                moveTo(3f, 10f)
                lineTo(21f, 10f)
                moveTo(5f, 4f)
                lineTo(19f, 4f)
                arcTo(2f, 2f, 0f, false, true, 21f, 6f)
                lineTo(21f, 20f)
                arcTo(2f, 2f, 0f, false, true, 19f, 22f)
                lineTo(5f, 22f)
                arcTo(2f, 2f, 0f, false, true, 3f, 20f)
                lineTo(3f, 6f)
                arcTo(2f, 2f, 0f, false, true, 5f, 4f)
            }
        }.build()
    }

    // User (Body metrics tab) —— Lucide "user": 头部圆 + 肩部弧线
    val User: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideUser",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 头
                moveTo(16f, 7f)
                arcTo(4f, 4f, 0f, true, true, 8f, 7f)
                arcTo(4f, 4f, 0f, true, true, 16f, 7f)
                // 肩
                moveTo(4f, 21f)
                arcTo(8f, 8f, 0f, false, true, 20f, 21f)
            }
        }.build()
    }

    // Share2 (Export / Share) —— Lucide "share-2"
    val Share2: ImageVector by lazy {
        ImageVector.Builder(
            name = "LucideShare2",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // 3 circles
                moveTo(18f, 2f)
                arcTo(3f, 3f, 0f, true, true, 17.99f, 2f)
                moveTo(6f, 9f)
                arcTo(3f, 3f, 0f, true, true, 5.99f, 9f)
                moveTo(18f, 16f)
                arcTo(3f, 3f, 0f, true, true, 17.99f, 16f)
                // 2 connecting lines
                moveTo(8.59f, 13.51f)
                lineTo(15.42f, 17.49f)
                moveTo(15.41f, 6.51f)
                lineTo(8.59f, 10.49f)
            }
        }.build()
    }
}

