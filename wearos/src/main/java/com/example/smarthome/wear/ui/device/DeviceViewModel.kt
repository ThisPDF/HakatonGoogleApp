package com.example.smarthome.wear.ui.device

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.wear.data.models.Device
import com.example.smarthome.wear.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val TAG = "DeviceViewModel"
    
    // Get deviceId from SavedStateHandle safely
    private val deviceId: String = savedStateHandle.get<String>("deviceId") ?: ""
    
    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    init {
        if (deviceId.isEmpty()) {
            _uiState.update { it.copy(error = "Invalid device ID", isLoading = false) }
        } else {
            loadDevice()
        }
    }

    private fun loadDevice() {
        viewModelScope.launch {
            try {
                deviceRepository.devices.collect { devices ->
                    val device = devices.find { it.id == deviceId }
                    if (device != null) {
                        _uiState.update { it.copy(device = device, isLoading = false) }
                    } else {
                        _uiState.update { it.copy(error = "Device not found", isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading device: ${e.message}", e)
                _uiState.update { it.copy(error = "Error loading device: ${e.message}", isLoading = false) }
            }
        }
    }

    fun toggleDevice() {
        val device = _uiState.value.device ?: return
        viewModelScope.launch {
            try {
                deviceRepository.toggleDevice(device.id, !device.isOn)
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling device: ${e.message}", e)
                _uiState.update { it.copy(error = "Error toggling device: ${e.message}") }
            }
        }
    }

    fun adjustTemperature(value: Int) {
        val device = _uiState.value.device ?: return
        viewModelScope.launch {
            try {
                if (device.type == "THERMOSTAT") {
                    deviceRepository.setDeviceValue(device.id, value)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adjusting temperature: ${e.message}", e)
                _uiState.update { it.copy(error = "Error adjusting temperature: ${e.message}") }
            }
        }
    }

    data class DeviceUiState(
        val device: Device? = null,
        val isLoading: Boolean = true,
        val error: String? = null
    )
}
