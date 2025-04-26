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
        loadDevices()
        loadQuickActions()
    }

    private fun loadDevices() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val devices = deviceRepository.getDevices()
                _uiState.update { it.copy(devices = devices, isLoading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadQuickActions() {
        viewModelScope.launch {
            try {
                val quickActions = deviceRepository.getQuickActions()
                _uiState.update { it.copy(quickActions = quickActions) }
            } catch (e: Exception) {
                // Just log the error, don't update UI state as we already have devices
                e.printStackTrace()
            }
        }
    }

    fun executeQuickAction(actionId: String) {
        viewModelScope.launch {
            try {
                deviceRepository.executeQuickAction(actionId)
                // Refresh devices after executing the action
                loadDevices()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun refreshData() {
        loadDevices()
        loadQuickActions()
    }
}
