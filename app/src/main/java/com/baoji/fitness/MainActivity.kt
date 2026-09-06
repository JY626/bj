package com.baoji.fitness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.baoji.fitness.core.theme.BaojiTheme
import com.baoji.fitness.ui.analytics.AnalyticsViewModel
import com.baoji.fitness.ui.coach.CoachViewModel
import com.baoji.fitness.ui.navigation.MainScreen
import com.baoji.fitness.ui.workout.WorkoutViewModel

class MainActivity : ComponentActivity() {

    private val workoutViewModel: WorkoutViewModel by viewModels {
        val app = application as BaojiApplication
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return WorkoutViewModel(app.workoutRepository) as T
            }
        }
    }

    private val analyticsViewModel: AnalyticsViewModel by viewModels {
        val app = application as BaojiApplication
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AnalyticsViewModel(app.workoutRepository) as T
            }
        }
    }

    private val coachViewModel: CoachViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BaojiApplication

        setContent {
            BaojiTheme {
                MainScreen(
                    workoutViewModel = workoutViewModel,
                    analyticsViewModel = analyticsViewModel,
                    coachViewModel = coachViewModel,
                    repository = app.workoutRepository
                )
            }
        }
    }
}
