package com.example.smarthome.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.smarthome.wear.ui.dashboard.DashboardScreen
import com.example.smarthome.wear.ui.device.DeviceDetailScreen
import com.example.smarthome.wear.ui.theme.SmartHomeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartHomeTheme {
                WearApp()
            }
        }
    }
}

@Composable
fun WearApp() {
    val navController = rememberSwipeDismissableNavController()
    
    SwipeDismissableNavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            DashboardScreen(
                onDeviceClick = { deviceId ->
                    navController.navigate("device/$deviceId")
                }
            )
        }
        composable("device/{deviceId}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            DeviceDetailScreen(deviceId = deviceId)
        }
    }
}
