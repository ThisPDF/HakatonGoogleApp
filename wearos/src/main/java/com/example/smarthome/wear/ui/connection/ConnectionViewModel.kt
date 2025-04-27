package com.example.smarthome.wear.ui.connection

import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.wear.data.bluetooth.BluetoothService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val wearableService: com.example.smarthome.wear.data.wearable.WearableService
) : ViewModel() {
    private val TAG = "ConnectionViewModel"

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting.asStateFlow()

    val connectionStatus = wearableService.connectionStatus
    val error = wearableService.error

    init {
        checkConnection()
    }

    fun checkConnection() {
        viewModelScope.launch {
            _isConnecting.value = true
            wearableService.checkPhoneConnection()
            _isConnecting.value = false
        }
    }

    data class ConnectionUiState(
        val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
        val isConnecting: Boolean = false,
        val isScanning: Boolean = false,
        val isBluetoothAvailable: Boolean = true,
        val isBluetoothEnabled: Boolean = false,
        val pairedDevicesCount: Int = 0,
        val availableDevices: List<BluetoothDevice> = emptyList(),
        val error: String? = null,
        val demoMode: Boolean = false
    )

    enum class ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTED
    }
}
