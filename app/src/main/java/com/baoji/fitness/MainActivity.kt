package com.baoji.fitness

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.baoji.fitness.core.theme.BaojiTheme
import com.baoji.fitness.ui.analytics.AnalyticsViewModel
import com.baoji.fitness.ui.calendar.CalendarViewModel
import com.baoji.fitness.ui.coach.CoachViewModel
import com.baoji.fitness.ui.library.LibraryViewModel
import com.baoji.fitness.ui.navigation.MainScreen
import com.baoji.fitness.ui.workout.WorkoutViewModel

class MainActivity : ComponentActivity() {

    private val workoutViewModel: WorkoutViewModel by viewModels {
        val app = application as BaojiApplication
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return WorkoutViewModel(
                    repository = app.workoutRepository,
                    reminderSettings = app.reminderSettings
                ) as T
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

    private val coachViewModel: CoachViewModel by viewModels {
        val app = application as BaojiApplication
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CoachViewModel(
                    repository = app.workoutRepository,
                    bodyMetricRepository = app.bodyMetricRepository,
                    reminderSettings = app.reminderSettings,
                    coachReportRepository = app.coachReportRepository,
                    llmSettings = app.llmSettings
                ) as T
            }
        }
    }

    private val calendarViewModel: CalendarViewModel by viewModels {
        val app = application as BaojiApplication
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CalendarViewModel(
                    repository = app.workoutRepository,
                    bodyMetricRepository = app.bodyMetricRepository,
                    reminderSettings = app.reminderSettings,
                    coachReportRepository = app.coachReportRepository
                ) as T
            }
        }
    }

    private val libraryViewModel: LibraryViewModel by viewModels {
        val app = application as BaojiApplication
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LibraryViewModel(
                    repository = app.workoutRepository,
                    llmSettings = app.llmSettings
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as BaojiApplication

        setContent {
            BaojiTheme {
                // App 常在后台活过午夜：回到前台时检查是否已跨天（推进"今天"并归档旧会话）
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            workoutViewModel.rollOverIfNewDay()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                // 只在用户真的打开提醒开关时才申请通知权限（不在启动时打扰）
                val wantsPermission by calendarViewModel.permissionRequest.collectAsState()
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { calendarViewModel.onPermissionRequestHandled() }

                LaunchedEffect(wantsPermission) {
                    if (wantsPermission &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (wantsPermission) {
                        // 13 以下无需运行时权限，直接收尾
                        calendarViewModel.onPermissionRequestHandled()
                    }
                }

                MainScreen(
                    workoutViewModel = workoutViewModel,
                    analyticsViewModel = analyticsViewModel,
                    calendarViewModel = calendarViewModel,
                    coachViewModel = coachViewModel,
                    libraryViewModel = libraryViewModel,
                    repository = app.workoutRepository,
                    bodyMetricRepository = app.bodyMetricRepository
                )
            }
        }
    }
}
