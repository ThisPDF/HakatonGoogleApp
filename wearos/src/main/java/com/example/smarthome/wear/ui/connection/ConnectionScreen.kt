package com.example.smarthome.wear.ui.connection

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smarthome.wear.data.bluetooth.BluetoothService

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = hiltViewModel(),
    onConnected: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) {
            onConnected()
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            uiState.isConnecting -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Connecting to phone...",
                        textAlign = TextAlign.Center
                    )
                    
                    // Show connection attempt count if available
                    if (uiState.connectionAttempt > 0) {
                        Text(
                            text = "Attempt ${uiState.connectionAttempt}",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.body2
                        )
                    }
                }
            }
            uiState.error != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Connection failed",
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error ?: "Unknown error",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.body2
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Add troubleshooting tips
                    Text(
                        text = "Troubleshooting tips:",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.body1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Make sure Bluetooth is enabled on both devices\n" +
                              "• Ensure devices are paired\n" +
                              "• Keep devices close to each other\n" +
                              "• Restart Bluetooth on both devices",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.body2
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { viewModel.connectToPhone() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry Connection")
                    }
                }
            }
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Connect to Phone",
                        style = MaterialTheme.typography.title2,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Make sure Bluetooth is enabled on both devices and they are paired.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.body2
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.connectToPhone() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Add check Bluetooth status button
                    Button(
                        onClick = { viewModel.checkBluetoothStatus() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.surface
                        )
                    ) {
                        Text("Check Bluetooth Status")
                    }
                }
            }
        }
    }
}
