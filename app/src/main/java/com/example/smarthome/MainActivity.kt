package com.example.smarthome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.smarthome.ui.dashboard.DashboardScreen
import com.example.smarthome.ui.devices.DevicesScreen
import com.example.smarthome.ui.dummydevices.DummyDevicesScreen
import com.example.smarthome.ui.esp32.ESP32Screen
import com.example.smarthome.ui.settings.SettingsScreen
import com.example.smarthome.ui.theme.SmartHomeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartHomeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }
}

object SmartHomeIcons {
    val Dashboard = Icons.Default.Dashboard
    val Devices = Icons.Default.Devices
    val Settings = Icons.Default.Settings
    val ESP32 = Icons.Default.Memory
    val Examples = Icons.Default.Lightbulb
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    var selectedItem by remember { mutableStateOf(0) }
    val items = listOf("Dashboard", "ESP32", "Examples", "Settings")
    val icons = listOf(
        SmartHomeIcons.Dashboard,
        SmartHomeIcons.ESP32,
        SmartHomeIcons.Examples,
        SmartHomeIcons.Settings
    )
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = item) },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = {
                            selectedItem = index
                            navController.navigate(item.lowercase()) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("dashboard") {
                DashboardScreen()
            }
            composable("esp32") {
                ESP32Screen()
            }
            composable("examples") {
                DummyDevicesScreen()
            }
            composable("settings") {
                SettingsScreen()
            }
        }
    }
}
