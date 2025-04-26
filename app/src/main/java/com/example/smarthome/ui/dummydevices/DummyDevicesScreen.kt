package com.example.smarthome.ui.dummydevices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smarthome.data.Device
import com.example.smarthome.data.DeviceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DummyDevicesScreen(
    viewModel: DummyDevicesViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsState(initial = emptyList())
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Example Devices") },
                actions = {
                    IconButton(onClick = { showAddDeviceDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Device")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info card
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
                        text = "Example Devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add example devices to test your smart home app. These devices are for demonstration purposes only.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "The ESP32 Controller is the main device that connects to your ESP32 board via Bluetooth.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Device list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    DeviceCard(
                        device = device,
                        onToggle = { viewModel.toggleDevice(device.id) },
                        onDelete = { viewModel.removeDevice(device.id) }
                    )
                }
            }
        }
        
        if (showAddDeviceDialog) {
            AddDeviceDialog(
                onDismiss = { showAddDeviceDialog = false },
                onAddDevice = { device ->
                    viewModel.addDevice(device)
                    showAddDeviceDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCard(
    device: Device,
    onToggle: () -> Unit,
    onDelete: () -> Unit
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Type: ${device.type.name}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Room: ${device.roomId}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Status: ${if (device.isOn) "On" else "Off"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (device.value != null) {
                        Text(
                            text = "Value: ${device.value}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Only show toggle and delete for non-ESP32 devices
                    if (device.id != "esp32_controller") {
                        Switch(
                            checked = device.isOn,
                            onCheckedChange = { onToggle() }
                        )
                        
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        // For ESP32, show a special icon
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "ESP32 Controller",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onAddDevice: (Device) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(DeviceType.LIGHT) }
    var selectedRoom by remember { mutableStateOf("living") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Example Device") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Device Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("Device Type")
                Column {
                    DeviceType.values().forEach { type ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedType == type,
                                onClick = { selectedType = type }
                            )
                            Text(type.name)
                        }
                    }
                }
                
                Text("Room")
                val rooms = listOf("living", "kitchen", "bedroom", "bathroom", "controllers")
                Column {
                    rooms.forEach { room ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedRoom == room,
                                onClick = { selectedRoom = room }
                            )
                            Text(room.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val newDevice = Device(
                            id = "device_${System.currentTimeMillis()}",
                            name = name,
                            type = selectedType,
                            roomId = selectedRoom,
                            isOn = false
                        )
                        onAddDevice(newDevice)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
