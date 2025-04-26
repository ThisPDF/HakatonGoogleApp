package com.example.smarthome.wear.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.wear.data.Device
import com.example.smarthome.wear.data.QuickAction
import com.example.smarthome.wear.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val devices: List<Device> = emptyList(),
    val quickActions: List<QuickAction> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val devices = deviceRepository.getDevices()
                val quickActions = listOf(
                    QuickAction("all_lights", "All Lights", "LIGHT"),
                    QuickAction("all_locks", "All Locks", "LOCK")
                )
                _uiState.value = DashboardUiState(
                    devices = devices,
                    quickActions = quickActions,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = DashboardUiState(
                    error = e.message,
                    isLoading = false
                )
            }
        }
    }

    fun executeQuickAction(actionId: String) {
        viewModelScope.launch {
            when (actionId) {
                "all_lights" -> {
                    // Toggle all lights
                    _uiState.value.devices
                        .filter { it.type == "LIGHT" }
                        .forEach { deviceRepository.toggleDevice(it.id) }
                }
                "all_locks" -> {
                    // Toggle all locks
                    _uiState.value.devices
                        .filter { it.type == "LOCK" }
                        .forEach { deviceRepository.toggleDevice(it.id) }
                }
            }
            // Reload data to reflect changes
            loadData()
        }
    }
}
