package com.cielfit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cielfit.app.data.FitViewModel
import com.cielfit.app.ui.screens.BodyScreen
import com.cielfit.app.ui.screens.LogScreen
import com.cielfit.app.ui.screens.PlanScreen
import com.cielfit.app.ui.screens.ScanScreen
import com.cielfit.app.ui.screens.TodayScreen
import com.cielfit.app.ui.screens.TrendsScreen
import com.cielfit.app.ui.screens.WorkoutScreen
import com.cielfit.app.ui.theme.Bg
import com.cielfit.app.ui.theme.CielFitTheme
import com.cielfit.app.ui.theme.SurfaceHigh

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CielFitTheme { CielFitApp() }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Default.Today),
    SCAN("Scan", Icons.Default.PhotoCamera),
    LOG("Log", Icons.AutoMirrored.Filled.ListAlt),
    TRENDS("Trends", Icons.Default.Insights),
    PLAN("Plan", Icons.Default.FitnessCenter),
    WORKOUT("Workout", Icons.Default.DirectionsRun),
    BODY("Body", Icons.Default.Straighten)
}

@Composable
private fun CielFitApp() {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    val vm: FitViewModel = viewModel()

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = SurfaceHigh) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(padding)
        ) {
            when (tab) {
                Tab.TODAY -> TodayScreen(vm)
                Tab.SCAN -> ScanScreen(vm)
                Tab.LOG -> LogScreen(vm)
                Tab.TRENDS -> TrendsScreen(vm)
                Tab.PLAN -> PlanScreen(vm)
                Tab.WORKOUT -> WorkoutScreen(vm)
                Tab.BODY -> BodyScreen(vm)
            }
        }
    }
}