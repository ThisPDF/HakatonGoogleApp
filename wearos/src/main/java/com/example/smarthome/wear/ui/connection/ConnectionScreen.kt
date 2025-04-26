package com.example.smarthome.wear.ui.connection

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smarthome.wear.ui.connection.ConnectionViewModel.ConnectionState

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = hiltViewModel(),
    onConnected: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // If connected, navigate to dashboard
    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState == ConnectionState.CONNECTED) {
            onConnected()
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Smart Home",
                style = MaterialTheme.typography.title1,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            when {
                !uiState.isBluetoothAvailable -> {
                    Text(
                        text = "Bluetooth is not available on this device",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center
                    )
                }
                !uiState.isBluetoothEnabled -> {
                    Text(
                        text = "Please enable Bluetooth to continue",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center
                    )
                }
                uiState.pairedDevices == 0 -> {
                    Text(
                        text = "No paired devices found. Please pair your phone in Bluetooth settings",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center
                    )
                }
                uiState.isConnecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = "Connecting to phone...",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center
                    )
                }
                uiState.error != null -> {
                    Text(
                        text = "Error: ${uiState.error}",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.connectToPhone() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry")
                    }
                }
                else -> {
                    Text(
                        text = "Connect to your phone to control your smart home devices",
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.connectToPhone() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}
