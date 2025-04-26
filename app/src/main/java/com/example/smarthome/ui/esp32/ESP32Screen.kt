package com.example.smarthome.ui.esp32

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ESP32Screen(
    viewModel: ESP32ViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val temperature by viewModel.temperatureData.collectAsState()
    val ledState by viewModel.ledState.collectAsState()
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    
    // For temperature simulation
    var simulatedTemp by remember { mutableStateOf("25.0") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ESP32 Control") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Connection status
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                            contentDescription = "Connection status",
                            tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = if (isConnected) "Connected" else "Disconnected",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Button(onClick = { viewModel.toggleServer() }) {
                        Text(text = if (isServerRunning) "Stop Server" else "Start Server")
                    }
                }
            }
            
            // Temperature display
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Thermostat,
                            contentDescription = "Temperature",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Temperature",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Text(
                        text = temperature?.let { "$it °C" } ?: "No data",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Temperature simulation for testing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = simulatedTemp,
                            onValueChange = { simulatedTemp = it },
                            label = { Text("Simulate Temperature") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        
                        Button(
                            onClick = {
                                simulatedTemp.toFloatOrNull()?.let { temp ->
                                    viewModel.simulateTemperatureUpdate(temp)
                                }
                            }
                        ) {
                            Text("Send")
                        }
                    }
                }
            }
            
            // LED control
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (ledState) Icons.Default.Lightbulb else Icons.Outlined.Lightbulb,
                            contentDescription = "LED status",
                            tint = if (ledState) Color.Yellow else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "LED Control",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(
                                color = if (ledState) Color.Yellow else Color.Gray,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (ledState) "ON" else "OFF",
                            color = if (ledState) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Button(
                        onClick = { viewModel.toggleLed() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ledState) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(text = if (ledState) "Turn OFF" else "Turn ON")
                    }
                }
            }
        }
    }
}
