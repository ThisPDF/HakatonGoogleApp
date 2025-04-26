package com.example.smarthome.wear.ui.device

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DeviceDetailScreen(
    deviceId: String,
    viewModel: DeviceViewModel = viewModel(factory = DeviceViewModelFactory(deviceId))
) {
    val uiState by viewModel.uiState.collectAsState()
    val device = uiState.device
    
    if (device == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = device.name,
            style = MaterialTheme.typography.title2
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        when (device.type) {
            "LIGHT" -> {
                ToggleChip(
                    checked = device.isOn,
                    onCheckedChange = { viewModel.toggleDevice() },
                    label = { Text("Power") },
                    toggleControl = { Switch(checked = device.isOn) }
                )
            }
            "THERMOSTAT" -> {
                val temperature = device.value?.toFloatOrNull() ?: 70f
                
                Text(
                    text = "${temperature.toInt()}°",
                    style = MaterialTheme.typography.display1
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.adjustTemperature(-1f) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("-")
                    }
                    
                    Button(
                        onClick = { viewModel.adjustTemperature(1f) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text("+")
                    }
                }
            }
            "LOCK" -> {
                Button(
                    onClick = { viewModel.toggleDevice() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (device.isOn) "Unlock" else "Lock")
                }
            }
            else -> {
                ToggleChip(
                    checked = device.isOn,
                    onCheckedChange = { viewModel.toggleDevice() },
                    label = { Text("Power") },
                    toggleControl = { Switch(checked = device.isOn) }
                )
            }
        }
    }
}
