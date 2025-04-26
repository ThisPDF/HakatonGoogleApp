package com.example.smarthome.wear.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.wear.data.bluetooth.BluetoothService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceUiState(
    val device: BluetoothService.Device? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val bluetoothService: BluetoothService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceUiState(isLoading = true))
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()
    private var deviceId: String? = null

    fun loadDevice(deviceId: String) {
        this.deviceId = deviceId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Find the device in the current list
                val device = bluetoothService.devices.value.find { it.id == deviceId }
                if (device != null) {
                    _uiState.update { it.copy(device = device, isLoading = false) }
                } else {
                    _uiState.update { it.copy(error = "Device not found", isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
        
        // Listen for device updates
        viewModelScope.launch {
            bluetoothService.devices.collect { devices ->
                val updatedDevice = devices.find { it.id == deviceId }
                if (updatedDevice != null) {
                    _uiState.update { it.copy(device = updatedDevice) }
                }
            }
        }
    }

    fun toggleDevice() {
        val device = _uiState.value.device ?: return
        viewModelScope.launch {
            bluetoothService.sendCommand(device.id, "TOGGLE")
            // The device state will be updated via the flow collection
        }
    }

    fun adjustTemperature(change: Float) {
        val device = _uiState.value.device ?: return
        val currentTemp = device.value?.toFloatOrNull() ?: 70f
        val newTemp = currentTemp + change
        viewModelScope.launch {
            bluetoothService.sendCommand(device.id, "VALUE:${newTemp.toString()}")
            // The device state will be updated via the flow collection
        }
    }
}
