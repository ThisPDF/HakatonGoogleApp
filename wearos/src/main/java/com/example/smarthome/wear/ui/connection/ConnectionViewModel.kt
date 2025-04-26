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

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val bluetoothService: BluetoothService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        observeConnectionState()
        observeBluetoothError()
        checkBluetoothStatus()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            bluetoothService.connectionState.collect { state ->
                _uiState.update { 
                    when (state) {
                        BluetoothService.ConnectionState.CONNECTED -> 
                            it.copy(connectionState = ConnectionState.CONNECTED, isConnecting = false)
                        BluetoothService.ConnectionState.CONNECTING -> 
                            it.copy(connectionState = ConnectionState.CONNECTING, isConnecting = true)
                        BluetoothService.ConnectionState.DISCONNECTED -> 
                            it.copy(connectionState = ConnectionState.DISCONNECTED, isConnecting = false)
                    }
                }
            }
        }
    }

    private fun observeBluetoothError() {
        viewModelScope.launch {
            bluetoothService.error.collect { error ->
                if (error != null) {
                    _uiState.update { it.copy(error = error) }
                } else {
                    _uiState.update { it.copy(error = null) }
                }
            }
        }
    }

    fun checkBluetoothStatus() {
        val status = bluetoothService.checkBluetoothStatus()
        _uiState.update { 
            it.copy(
                isBluetoothAvailable = status.isAvailable,
                isBluetoothEnabled = status.isEnabled,
                pairedDevices = status.pairedDevices
            )
        }
    }

    fun connectToPhone() {
        if (_uiState.value.isConnecting) return
        
        _uiState.update { it.copy(isConnecting = true, error = null) }
        viewModelScope.launch {
            val success = deviceRepository.connectToPhone()
            if (!success) {
                _uiState.update { it.copy(isConnecting = false) }
            }
        }
    }

    fun disconnect() {
        bluetoothService.disconnect()
    }

    data class ConnectionUiState(
        val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
        val isConnecting: Boolean = false,
        val isBluetoothAvailable: Boolean = false,
        val isBluetoothEnabled: Boolean = false,
        val pairedDevices: Int = 0,
        val error: String? = null
    )

    enum class ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTED
    }
}
