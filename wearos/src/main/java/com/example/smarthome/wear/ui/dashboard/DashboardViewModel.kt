package com.example.smarthome.wear.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.wear.data.bluetooth.BluetoothService
import com.example.smarthome.wear.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val devices: List<BluetoothService.Device> = emptyList(),
    val quickActions: List<QuickAction> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class QuickAction(
    val id: String,
    val name: String,
    val type: String
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val bluetoothService: BluetoothService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState(isLoading = true))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
        
        viewModelScope.launch {
            bluetoothService.devices.collect { devices ->
                _uiState.value = _uiState.value.copy(
                    devices = devices,
                    isLoading = false
                )
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val quickActions = listOf(
                    QuickAction("all_lights", "All Lights", "LIGHT"),
                    QuickAction("all_locks", "All Locks", "LOCK")
                )
                _uiState.value = _uiState.value.copy(
                    quickActions = quickActions,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
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
                        .forEach { bluetoothService.sendCommand(it.id, if (it.isOn) "OFF" else "ON") }
                }
                "all_locks" -> {
                    // Toggle all locks
                    _uiState.value.devices
                        .filter { it.type == "LOCK" }
                        .forEach { bluetoothService.sendCommand(it.id, if (it.isOn) "OFF" else "ON") }
                }
            }
            // Reload data to reflect changes
            bluetoothService.requestDevices()
        }
    }
}
