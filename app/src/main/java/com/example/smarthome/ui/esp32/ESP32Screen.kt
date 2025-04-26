package com.example.smarthome.ui.esp32

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
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
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    
    // For temperature simulation
    var simulatedTemp by remember { mutableStateOf("25.0") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ESP32 Controller") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Connection status
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Connection Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            Column {
                                Text(
                                    text = if (isConnected) "Connected" else "Disconnected",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = connectionStatus,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        Button(onClick = { viewModel.toggleServer() }) {
                            Text(text = if (isServerRunning) "Stop Server" else "Start Server")
                        }
                    }
                }
            }
            
            // Device info
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Device Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Divider()
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Device Name")
                        Text("ESP32 Sparrow Rev 2", fontWeight = FontWeight.Bold)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Firmware Version")
                        Text("v1.2.3", fontWeight = FontWeight.Bold)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("MAC Address")
                        Text("AA:BB:CC:DD:EE:FF", fontWeight = FontWeight.Bold)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Status")
                        Text(
                            text = if (isConnected) "Online" else "Offline",
                            color = if (isConnected) Color.Green else Color.Red,
                            fontWeight = FontWeight.Bold
                        )
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            text = "Temperature Sensor",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Text(
                        text = temperature?.let { "$it °C" } ?: "No data",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Temperature simulation for testing
                    if (!isConnected) {
                        Divider()
                        Text(
                            text = "Simulation Mode",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                            .size(80.dp)
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
                        ),
                        enabled = isConnected
                    ) {
                        Text(text = if (ledState) "Turn OFF" else "Turn ON")
                    }
                }
            }
            
            // Connection logs
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Connection Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Divider()
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            val logs = viewModel.connectionLogs.collectAsState()
                            logs.value.forEach { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
