package com.example.smarthome.wear.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smarthome.wear.data.Device

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onDeviceClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Smart Home",
                style = MaterialTheme.typography.title1,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
        }
        
        items(uiState.quickActions) { action ->
            Chip(
                onClick = { viewModel.executeQuickAction(action.id) },
                label = { Text(action.name) },
                icon = {
                    when (action.type) {
                        "LIGHT" -> Icon(WearIcons.Light, contentDescription = null)
                        "LOCK" -> Icon(WearIcons.Lock, contentDescription = null)
                        else -> Icon(WearIcons.Device, contentDescription = null)
                    }
                }
            )
        }
        
        item {
            Text(
                text = "Devices",
                style = MaterialTheme.typography.title2,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }
        
        items(uiState.devices) { device ->
            DeviceChip(
                device = device,
                onClick = { onDeviceClick(device.id) }
            )
        }
    }
}

@Composable
fun DeviceChip(
    device: Device,
    onClick: () -> Unit
) {
    Chip(
        onClick = onClick,
        label = { Text(device.name) },
        secondaryLabel = { Text(if (device.isOn) "On" else "Off") },
        icon = {
            when (device.type) {
                "LIGHT" -> Icon(WearIcons.Light, contentDescription = null)
                "THERMOSTAT" -> Icon(WearIcons.Thermostat, contentDescription = null)
                "LOCK" -> Icon(WearIcons.Lock, contentDescription = null)
                else -> Icon(WearIcons.Device, contentDescription = null)
            }
        }
    )
}
