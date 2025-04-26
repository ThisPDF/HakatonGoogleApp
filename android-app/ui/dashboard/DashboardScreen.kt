package com.example.smarthome.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smarthome.data.Room
import com.example.smarthome.data.Device

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Smart Home",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Room selector
        RoomSelector(
            rooms = uiState.rooms,
            selectedRoom = uiState.selectedRoom,
            onRoomSelected = { viewModel.selectRoom(it) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Devices in the selected room
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.devicesInSelectedRoom) { device ->
                DeviceCard(
                    device = device,
                    onToggle = { viewModel.toggleDevice(device.id) }
                )
            }
        }
    }
}

@Composable
fun RoomSelector(
    rooms: List<Room>,
    selectedRoom: Room,
    onRoomSelected: (Room) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rooms.forEach { room ->
            FilterChip(
                selected = room.id == selectedRoom.id,
                onClick = { onRoomSelected(room) },
                label = { Text(room.name) }
            )
        }
    }
}

@Composable
fun DeviceCard(
    device: Device,
    onToggle: () -> Unit
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
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (device.isOn) "On" else "Off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = device.isOn,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
