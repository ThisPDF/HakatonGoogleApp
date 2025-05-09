package com.example.smarthome.ui.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smarthome.data.bluetooth.BluetoothService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScreen(
    viewModel: BluetoothViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Check for Bluetooth permissions
    val bluetoothPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
    }
    
    var permissionsGranted by remember {
        mutableStateOf(
            bluetoothPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.values.all { it }
        if (permissionsGranted) {
            viewModel.refreshPairedDevices()
        }
    }
    
    // Request permissions if not granted
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(bluetoothPermissions)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Connection") },
                actions = {
                    IconButton(onClick = { 
                        if (permissionsGranted) {
                            viewModel.refreshPairedDevices()
                        } else {
                            permissionLauncher.launch(bluetoothPermissions)
                        }
                    }) {
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
            if (!permissionsGranted) {
                PermissionRequiredCard {
                    permissionLauncher.launch(bluetoothPermissions)
                }
            } else {
                ConnectionStatus(
                    connectionState = uiState.connectionState,
                    connectedDevice = uiState.connectedDevice
                )
                
                Text(
                    text = "Bluetooth Server Mode",
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (uiState.error != null) {
                    ErrorCard(error = uiState.error!!)
                }
                
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
                            text = "This app acts as a Bluetooth server.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            text = "To connect your WearOS device:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "1. Make sure your WearOS device is paired with this phone\n" +
                                  "2. Open the Smart Home app on your WearOS device\n" +
                                  "3. Tap 'Connect' on your WearOS device",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Button(
                            onClick = { viewModel.refreshPairedDevices() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Bluetooth Server")
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
}

@Composable
fun PermissionRequiredCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            
            Text(
                text = "Bluetooth permissions are required",
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = "This app needs Bluetooth permissions to connect to your WearOS device.",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Button(onClick = onRequestPermission) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium
            )
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
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
                                Text(
                                    text = it.address,
                                    style = MaterialTheme.typography.bodySmall
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
            
            // Add connection tips when disconnected
            if (connectionState == BluetoothService.ConnectionState.DISCONNECTED) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Connection Tips:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "• Make sure your WearOS device is paired with this phone\n" +
                          "• Keep devices close to each other\n" +
                          "• Ensure Bluetooth is enabled on both devices",
                    style = MaterialTheme.typography.bodySmall
                )
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
