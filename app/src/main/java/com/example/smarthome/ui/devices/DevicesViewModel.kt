package com.example.smarthome.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.data.Device
import com.example.smarthome.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevicesUiState(
    val devices: List<Device> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState(isLoading = true))
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            deviceRepository.devices.collect { devices ->
                _uiState.update { it.copy(devices = devices, isLoading = false) }
            }
        }
    }

    fun addDevice(device: Device) {
        viewModelScope.launch {
            deviceRepository.addDevice(device)
            // Sync with watch
            deviceRepository.syncDevicesWithWatch()
        }
    }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            deviceRepository.removeDevice(deviceId)
            // Sync with watch
            deviceRepository.syncDevicesWithWatch()
        }
    }
}
