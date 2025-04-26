package com.example.smarthome.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.data.Device
import com.example.smarthome.data.Room
import com.example.smarthome.data.repository.DeviceRepository
import com.example.smarthome.data.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val rooms: List<Room> = emptyList(),
    val selectedRoom: Room = Room("", ""),
    val devicesInSelectedRoom: List<Device> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val roomRepository: RoomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                roomRepository.getRooms(),
                deviceRepository.getDevices()
            ) { rooms, devices ->
                val selectedRoom = rooms.firstOrNull() ?: Room("", "")
                DashboardUiState(
                    rooms = rooms,
                    selectedRoom = selectedRoom,
                    devicesInSelectedRoom = devices.filter { it.roomId == selectedRoom.id },
                    isLoading = false
                )
            }.catch { e ->
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectRoom(room: Room) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    selectedRoom = room,
                    devicesInSelectedRoom = deviceRepository.getDevices().first()
                        .filter { it.roomId == room.id }
                )
            }
        }
    }

    fun toggleDevice(deviceId: String) {
        viewModelScope.launch {
            deviceRepository.toggleDevice(deviceId)
        }
    }
}
