package com.example.smarthome.wear.ui.device

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smarthome.wear.ui.theme.IconFromDrawable
import com.example.smarthome.wear.ui.theme.WearIcons

@Composable
fun DeviceDetailScreen(
    viewModel: DeviceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator()
        } else if (uiState.error != null) {
            Text(
                text = uiState.error ?: "Unknown error",
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            val device = uiState.device
            if (device != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.title2
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    when (device.type) {
                        "LIGHT" -> {
                            Button(
                                onClick = { viewModel.toggleDevice() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (device.isOn) "Turn Off" else "Turn On")
                            }
                        }
                        "THERMOSTAT" -> {
                            val temperature = device.value?.toIntOrNull() ?: 70
                            
                            Text(
                                text = "$temperature°",
                                style = MaterialTheme.typography.display1
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = { viewModel.adjustTemperature(temperature - 1) }
                                ) {
                                    Text("-")
                                }
                                
                                Button(
                                    onClick = { viewModel.adjustTemperature(temperature + 1) }
                                ) {
                                    Text("+")
                                }
                            }
                            
                            Button(
                                onClick = { viewModel.toggleDevice() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (device.isOn) "Turn Off" else "Turn On")
                            }
                        }
                        "LOCK" -> {
                            Button(
                                onClick = { viewModel.toggleDevice() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (device.isOn) "Lock" else "Unlock")
                            }
                        }
                        else -> {
                            Button(
                                onClick = { viewModel.toggleDevice() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (device.isOn) "Turn Off" else "Turn On")
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Device not found",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
