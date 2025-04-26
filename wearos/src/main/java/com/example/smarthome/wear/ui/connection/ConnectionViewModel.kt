package com.example.smarthome.wear.ui.connection

import android.util.Log
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
    private val TAG = "ConnectionViewModel"

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        try {
            observeConnectionState()
            observeBluetoothError()
            checkBluetoothStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ConnectionViewModel: ${e.message}", e)
            _uiState.update { 
                it.copy(
                    error = "Initialization error: ${e.message}",
                    isBluetoothAvailable = false,
                    isBluetoothEnabled = false
                ) 
            }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                Log.e(TAG, "Error observing connection state: ${e.message}", e)
                _uiState.update { it.copy(error = "Connection error: ${e.message}") }
            }
        }
    }

    private fun observeBluetoothError() {
        viewModelScope.launch {
            try {
                bluetoothService.error.collect { error ->
                    if (error != null) {
                        _uiState.update { it.copy(error = error) }
                    } else {
                        _uiState.update { it.copy(error = null) }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing bluetooth errors: ${e.message}", e)
            }
        }
    }

    fun checkBluetoothStatus() {
        try {
            val status = bluetoothService.checkBluetoothStatus()
            Log.d(TAG, "Bluetooth status: available=${status.isAvailable}, enabled=${status.isEnabled}, paired=${status.pairedDevices}")
            _uiState.update { 
                it.copy(
                    isBluetoothAvailable = status.isAvailable,
                    isBluetoothEnabled = status.isEnabled,
                    pairedDevices = status.pairedDevices,
                    error = if (!status.isAvailable) "Bluetooth is not available on this device" else null
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth status: ${e.message}", e)
            _uiState.update { 
                it.copy(
                    error = "Bluetooth error: ${e.message}",
                    isBluetoothAvailable = false,
                    isBluetoothEnabled = false
                ) 
            }
        }
    }

    fun connectToPhone() {
        if (_uiState.value.isConnecting) return
        
        _uiState.update { it.copy(isConnecting = true, error = null) }
        viewModelScope.launch {
            try {
                val success = deviceRepository.connectToPhone()
                if (!success) {
                    _uiState.update { it.copy(isConnecting = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to phone: ${e.message}", e)
                _uiState.update { 
                    it.copy(
                        isConnecting = false, 
                        error = "Connection error: ${e.message}"
                    ) 
                }
            }
        }
    }

    fun disconnect() {
        try {
            bluetoothService.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}", e)
            _uiState.update { it.copy(error = "Disconnect error: ${e.message}") }
        }
    }

    data class ConnectionUiState(
        val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
        val isConnecting: Boolean = false,
        val isBluetoothAvailable: Boolean = true,  // Default to true until we check
        val isBluetoothEnabled: Boolean = false,
        val pairedDevices: Int = 0,
        val error: String? = null
    )

    enum class ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTED
    }
}
