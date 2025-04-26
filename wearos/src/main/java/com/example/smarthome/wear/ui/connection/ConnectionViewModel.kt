package com.example.smarthome.wear.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.wear.data.bluetooth.BluetoothService
import com.example.smarthome.wear.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionUiState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null,
    val connectionAttempt: Int = 0,
    val bluetoothEnabled: Boolean = false
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bluetoothService.connectionState.collect { state ->
                _uiState.update { 
                    it.copy(
                        isConnected = state == BluetoothService.ConnectionState.CONNECTED,
                        isConnecting = state == BluetoothService.ConnectionState.CONNECTING
                    )
                }
            }
        }
        
        viewModelScope.launch {
            bluetoothService.error.collect { errorMsg ->
                if (errorMsg != null) {
                    _uiState.update { it.copy(error = errorMsg) }
                }
            }
        }
    }

    fun checkBluetoothStatus() {
        viewModelScope.launch {
            try {
                val status = bluetoothService.checkBluetoothStatus()
                _uiState.update {
                    it.copy(
                        bluetoothEnabled = status.isEnabled,
                        error = if (!status.isEnabled) "Bluetooth is not enabled" else null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error checking Bluetooth: ${e.message}") }
            }
        }
    }

    fun connectToPhone() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null, connectionAttempt = it.connectionAttempt + 1) }

            try {
                val success = bluetoothService.connectToPhone()
                if (!success) {
                    _uiState.update { it.copy(error = "Failed to connect to phone") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isConnecting = false) }
            }
        }
    }
}
