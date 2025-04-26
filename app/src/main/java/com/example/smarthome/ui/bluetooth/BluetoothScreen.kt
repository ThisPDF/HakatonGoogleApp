package com.example.smarthome.ui.bluetooth

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smarthome.data.bluetooth.BluetoothService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(
    viewModel: BluetoothViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Connection") },
                actions = {
                    IconButton(onClick = { viewModel.refreshPairedDevices() }) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionStatus(
                connectionState = uiState.connectionState,
                connectedDevice = uiState.connectedDevice
            )
            
            Text(
                text = "Paired Devices",
                style = MaterialTheme.typography.titleMedium
            )
            
            if (uiState.pairedDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No paired devices found")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.pairedDevices) { device ->
                        DeviceItem(
                            device = device,
                            isConnected = uiState.connectedDevice?.address == device.address,
                            onConnect = { viewModel.connectToDevice(device) }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { viewModel.syncDevices() },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.connectionState == BluetoothService.ConnectionState.CONNECTED
            ) {
                Text("Sync Devices with Watch")
            }
        }
    }
}

@Composable
fun ConnectionStatus(
    connectionState: BluetoothService.ConnectionState,
    connectedDevice: BluetoothDevice?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (connectionState) {
                BluetoothService.ConnectionState.CONNECTED -> {
                    Icon(
                        imageVector = Icons.Default.BluetoothConnected,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Connected",
                            style = MaterialTheme.typography.titleMedium
                        )
                        connectedDevice?.let {
                            Text(
                                text = it.name ?: "Unknown Device",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                BluetoothService.ConnectionState.CONNECTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Connecting...",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                BluetoothService.ConnectionState.DISCONNECTED -> {
                    Icon(
                        imageVector = Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Disconnected",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: BluetoothDevice,
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Button(
                onClick = onConnect,
                enabled = !isConnected
            ) {
                Text(if (isConnected) "Connected" else "Connect")
            }
        }
    }
}
