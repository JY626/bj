package com.baoji.fitness.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baoji.fitness.core.designsystem.icons.LucideIcons
import com.baoji.fitness.core.theme.AppleBlack
import com.baoji.fitness.core.theme.AppleFitnessGreen
import com.baoji.fitness.core.theme.AppleHairline
import com.baoji.fitness.core.theme.AppleTextSecondary
import com.baoji.fitness.data.repository.BodyMetricRepository
import com.baoji.fitness.data.repository.WorkoutRepository
import com.baoji.fitness.ui.analytics.AnalyticsScreen
import com.baoji.fitness.ui.analytics.AnalyticsViewModel
import com.baoji.fitness.ui.calendar.CalendarScreen
import com.baoji.fitness.ui.calendar.CalendarViewModel
import com.baoji.fitness.ui.coach.CoachScreen
import com.baoji.fitness.ui.coach.CoachViewModel
import com.baoji.fitness.ui.library.LibraryScreen
import com.baoji.fitness.ui.workout.WorkoutScreen
import com.baoji.fitness.ui.workout.WorkoutViewModel

enum class NavigationTab(val label: String, val icon: ImageVector) {
    WORKOUT("训练", LucideIcons.Dumbbell),
    ANALYTICS("负荷", LucideIcons.Activity),
    CALENDAR("日历", LucideIcons.Calendar),
    COACH("私教", LucideIcons.Sparkles),
    LIBRARY("动作库", LucideIcons.Layers)
}

@Composable
fun MainScreen(
    workoutViewModel: WorkoutViewModel,
    analyticsViewModel: AnalyticsViewModel,
    calendarViewModel: CalendarViewModel,
    coachViewModel: CoachViewModel,
    repository: WorkoutRepository,
    bodyMetricRepository: BodyMetricRepository
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.WORKOUT) }

    Scaffold(
        containerColor = AppleBlack,
        bottomBar = {
            AppleBottomNavBar(
                currentTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavigationTab.WORKOUT -> WorkoutScreen(
                    viewModel = workoutViewModel,
                    onNavigateToCoach = { selectedTab = NavigationTab.COACH }
                )
                NavigationTab.ANALYTICS -> AnalyticsScreen(
                    viewModel = analyticsViewModel
                )
                NavigationTab.CALENDAR -> CalendarScreen(
                    viewModel = calendarViewModel
                )
                NavigationTab.COACH -> CoachScreen(
                    viewModel = coachViewModel,
                    onApplyToWorkout = { selectedTab = NavigationTab.WORKOUT }
                )
                NavigationTab.LIBRARY -> LibraryScreen(
                    repository = repository,
                    bodyMetricRepository = bodyMetricRepository
                )
            }
        }
    }
}

@Composable
private fun AppleBottomNavBar(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.88f))
            .border(width = 0.5.dp, color = AppleHairline)
            .navigationBarsPadding()
            .height(58.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationTab.values().forEach { tab ->
                val isSelected = (tab == currentTab)
                val color = if (isSelected) AppleFitnessGreen else AppleTextSecondary

                Column(
                    modifier = Modifier
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                        // If Coach tab, display tiny green dot badge
                        if (tab == NavigationTab.COACH) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(AppleFitnessGreen)
                            )
                        }
                    }
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = color
                    )
                }
            }
        }
    }
}
