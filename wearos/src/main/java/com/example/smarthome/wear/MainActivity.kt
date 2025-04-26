package com.example.smarthome.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
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
    
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // All permissions granted, proceed with Bluetooth operations
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check and request permissions
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        }
        
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
            DeviceDetailScreen(
                deviceId = deviceId
            )
        }
    }
}
