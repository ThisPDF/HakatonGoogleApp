package com.example.smarthome.wear.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.wear.data.models.Device
import com.example.smarthome.wear.data.models.QuickAction
import com.example.smarthome.wear.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDevices()
        loadQuickActions()
    }

    private fun loadDevices() {
        viewModelScope.launch {
            deviceRepository.devices.collect { devices ->
                _uiState.update { it.copy(devices = devices) }
            }
        }
    }

    private fun loadQuickActions() {
        // In a real app, these would come from a repository
        val quickActions = listOf(
            QuickAction("qa1", "All Lights", "LIGHT"),
            QuickAction("qa2", "Lock Home", "LOCK"),
            QuickAction("qa3", "Night Mode", "SCENE")
        )
        _uiState.update { it.copy(quickActions = quickActions) }
    }

    fun executeQuickAction(actionId: String) {
        viewModelScope.launch {
            when (actionId) {
                "qa1" -> {
                    // Toggle all lights
                    uiState.value.devices
                        .filter { it.type == "LIGHT" }
                        .forEach { device ->
                            deviceRepository.toggleDevice(device.id, !device.isOn)
                        }
                }
                "qa2" -> {
                    // Lock all doors
                    uiState.value.devices
                        .filter { it.type == "LOCK" }
                        .forEach { device ->
                            deviceRepository.toggleDevice(device.id, true)
                        }
                }
                "qa3" -> {
                    // Night mode - turn off lights, lock doors
                    uiState.value.devices
                        .filter { it.type == "LIGHT" }
                        .forEach { device ->
                            deviceRepository.toggleDevice(device.id, false)
                        }
                    uiState.value.devices
                        .filter { it.type == "LOCK" }
                        .forEach { device ->
                            deviceRepository.toggleDevice(device.id, true)
                        }
                }
            }
        }
    }

    data class DashboardUiState(
        val devices: List<Device> = emptyList(),
        val quickActions: List<QuickAction> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )
}
