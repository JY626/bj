package com.baoji.fitness

import com.baoji.fitness.ui.workout.formatElapsed
import org.junit.Assert.assertEquals
import org.junit.Test

/** 训练计时显示：秒/分/时三种量级与边界（59:59 → 1:00:00 是经典翻车点）。 */
class ElapsedFormatTest {

    @Test
    fun underOneMinuteShowsZeroPaddedSeconds() {
        assertEquals("00:00", formatElapsed(0))
        assertEquals("00:05", formatElapsed(5))
        assertEquals("00:59", formatElapsed(59))
    }

    @Test
    fun minutesAreZeroPadded() {
        assertEquals("01:00", formatElapsed(60))
        assertEquals("09:05", formatElapsed(545))
        assertEquals("32:15", formatElapsed(32 * 60 + 15))
    }

    /** 边界：59:59 的下一秒必须变成 1:00:00，不能出现在 60:00 这种读不出的格式。 */
    @Test
    fun hourBoundaryRollsOverCleanly() {
        assertEquals("59:59", formatElapsed(3599))
        assertEquals("1:00:00", formatElapsed(3600))
        assertEquals("1:05:30", formatElapsed(3600 + 5 * 60 + 30))
    }

    /** 超长训练（马拉松式 3 小时）也要可读。 */
    @Test
    fun longSessionsStayReadable() {
        assertEquals("3:00:00", formatElapsed(3 * 3600))
    }

    /** 负数（时钟回拨等异常）不能崩，钳到 0。 */
    @Test
    fun negativeClampsToZero() {
        assertEquals("00:00", formatElapsed(-5))
    }
}
