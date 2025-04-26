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
    val error: String? = null
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
    }

    fun connectToPhone() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }
            
            try {
                val success = deviceRepository.connectToPhone()
                if (!success) {
                    _uiState.update { it.copy(error = "Failed to connect to phone") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
