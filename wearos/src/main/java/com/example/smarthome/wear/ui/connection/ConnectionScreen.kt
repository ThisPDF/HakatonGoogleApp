package com.example.smarthome.wear.ui.connection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smarthome.wear.ui.connection.ConnectionViewModel.ConnectionState

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = hiltViewModel(),
    onConnected: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Define required Bluetooth permissions based on Android version
    val bluetoothPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }
    
    // Check if permissions are granted
    var permissionsGranted by remember {
        mutableStateOf(
            bluetoothPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    
    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.values.all { it }
        if (permissionsGranted) {
            viewModel.checkBluetoothStatus()
        }
    }
    
    // Request permissions if not granted
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(bluetoothPermissions)
        }
    }
    
    // If connected, navigate to dashboard
    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState == ConnectionState.CONNECTED || uiState.demoMode) {
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
            
            if (!permissionsGranted) {
                Text(
                    text = "Bluetooth permissions are required",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = { permissionLauncher.launch(bluetoothPermissions) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Permissions")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Add demo mode button
                Button(
                    onClick = { viewModel.enableDemoMode() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
                ) {
                    Text("Use Demo Mode")
                }
            } else {
                when {
                    !uiState.isBluetoothAvailable -> {
                        Text(
                            text = "Bluetooth is not available on this device",
                            style = MaterialTheme.typography.body1,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Add demo mode button
                        Button(
                            onClick = { viewModel.enableDemoMode() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use Demo Mode")
                        }
                    }
                    !uiState.isBluetoothEnabled -> {
                        Text(
                            text = "Please enable Bluetooth to continue",
                            style = MaterialTheme.typography.body1,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = { 
                                try {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                } catch (e: Exception) {
                                    // Ignore if settings can't be opened
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable Bluetooth")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = { viewModel.checkBluetoothStatus() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Check Again")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Add demo mode button
                        Button(
                            onClick = { viewModel.enableDemoMode() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
                        ) {
                            Text("Use Demo Mode")
                        }
                    }
                    uiState.pairedDevicesCount == 0 -> {
                        Text(
                            text = "No paired devices found. Please pair your phone in Bluetooth settings",
                            style = MaterialTheme.typography.body1,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = { 
                                try {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                } catch (e: Exception) {
                                    // Ignore if settings can't be opened
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Bluetooth Settings")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = { viewModel.checkBluetoothStatus() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Check Again")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Add demo mode button
                        Button(
                            onClick = { viewModel.enableDemoMode() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
                        ) {
                            Text("Use Demo Mode")
                        }
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
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Add demo mode button
                        Button(
                            onClick = { viewModel.enableDemoMode() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
                        ) {
                            Text("Use Demo Mode")
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
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Add demo mode button
                        Button(
                            onClick = { viewModel.enableDemoMode() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
                        ) {
                            Text("Use Demo Mode")
                        }
                    }
                }
            }
        }
    }
}
