package com.example.smarthome.wear.ui.device

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

    private val deviceId: String = checkNotNull(savedStateHandle["deviceId"])
    
    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    init {
        loadDevice()
    }

    private fun loadDevice() {
        viewModelScope.launch {
            deviceRepository.devices.collect { devices ->
                val device = devices.find { it.id == deviceId }
                if (device != null) {
                    _uiState.update { it.copy(device = device, isLoading = false) }
                } else {
                    _uiState.update { it.copy(error = "Device not found", isLoading = false) }
                }
            }
        }
    }

    fun toggleDevice() {
        val device = _uiState.value.device ?: return
        viewModelScope.launch {
            deviceRepository.toggleDevice(device.id, !device.isOn)
        }
    }

    fun adjustTemperature(value: Int) {
        val device = _uiState.value.device ?: return
        viewModelScope.launch {
            if (device.type == "THERMOSTAT") {
                deviceRepository.setDeviceValue(device.id, value)
            }
        }
    }

    data class DeviceUiState(
        val device: Device? = null,
        val isLoading: Boolean = true,
        val error: String? = null
    )
}
