package com.baoji.fitness.ui.calendar

import java.time.LocalDate
import java.time.YearMonth

/** 月历格子（周一为首列），末尾补 null 填满整数周。 */
internal fun monthGridCells(month: YearMonth): List<LocalDate?> {
    val days: List<LocalDate?> =
        List(month.atDay(1).dayOfWeek.value - 1) { null } +
            (1..month.lengthOfMonth()).map { month.atDay(it) }
    return days + List((7 - days.size % 7) % 7) { null }
}