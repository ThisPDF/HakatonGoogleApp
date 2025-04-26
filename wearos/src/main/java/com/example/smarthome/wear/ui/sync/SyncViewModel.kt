package com.example.smarthome.wear.ui.sync

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.wear.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {
    private val TAG = "SyncViewModel"

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        // Auto-start sync when the screen is opened
        startSync()
    }

    fun startSync() {
        _uiState.update { it.copy(isSyncing = true, error = null, syncComplete = false) }
        
        viewModelScope.launch {
            try {
                // First check Bluetooth status
                val status = deviceRepository.checkBluetoothStatus()
                
                if (!status.isAvailable) {
                    _uiState.update { it.copy(isSyncing = false, error = "Bluetooth is not available") }
                    return@launch
                }
                
                if (!status.isEnabled) {
                    _uiState.update { it.copy(isSyncing = false, error = "Bluetooth is not enabled") }
                    return@launch
                }
                
                // Connect to phone
                val connected = deviceRepository.connectToPhone()
                
                if (!connected) {
                    _uiState.update { it.copy(isSyncing = false, error = "Failed to connect to phone") }
                    return@launch
                }
                
                // Refresh devices
                val refreshed = deviceRepository.refreshDevices()
                
                if (!refreshed) {
                    _uiState.update { it.copy(isSyncing = false, error = "Failed to refresh devices") }
                    return@launch
                }
                
                // Short delay to ensure data is loaded
                delay(1000)
                
                // Success
                _uiState.update { it.copy(isSyncing = false, syncComplete = true) }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during sync: ${e.message}", e)
                _uiState.update { it.copy(isSyncing = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    data class SyncUiState(
        val isSyncing: Boolean = false,
        val error: String? = null,
        val syncComplete: Boolean = false
    )
}
