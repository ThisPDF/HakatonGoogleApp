package com.example.smarthome.wear.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.wear.data.Device
import com.example.smarthome.wear.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceUiState(
    val device: Device? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceUiState(isLoading = true))
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    fun loadDevice(deviceId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val device = deviceRepository.getDevice(deviceId)
                _uiState.update { it.copy(device = device, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun toggleDevice() {
        val device = _uiState.value.device ?: return
        viewModelScope.launch {
            deviceRepository.toggleDevice(device.id)
            _uiState.update { 
                it.copy(device = it.device?.copy(isOn = !it.device.isOn)) 
            }
        }
    }

    fun adjustTemperature(change: Float) {
        val device = _uiState.value.device ?: return
        val currentTemp = device.value?.toFloatOrNull() ?: 70f
        val newTemp = currentTemp + change
        viewModelScope.launch {
            deviceRepository.updateDeviceValue(device.id, newTemp.toString())
            _uiState.update { 
                it.copy(device = it.device?.copy(value = newTemp.toString())) 
            }
        }
    }
}
