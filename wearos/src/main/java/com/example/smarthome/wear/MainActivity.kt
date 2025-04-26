package com.example.smarthome.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.smarthome.wear.ui.connection.ConnectionScreen
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
        startDestination = "connection"
    ) {
        composable("connection") {
            ConnectionScreen(
                onConnected = {
                    navController.navigate("dashboard") {
                        popUpTo("connection") { inclusive = true }
                    }
                }
            )
        }
        
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
