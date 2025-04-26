package com.example.smarthome.wear.data.repository

import android.util.Log
import com.example.smarthome.wear.data.bluetooth.BluetoothService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val bluetoothService: BluetoothService
) {
    private val TAG = "DeviceRepository"
    
    // Get devices directly from BluetoothService
    val devices: Flow<List<BluetoothService.Device>> = bluetoothService.devices
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _bluetoothStatus = MutableStateFlow(BluetoothStatus(false, false, 0, 0))
    val bluetoothStatus: StateFlow<BluetoothStatus> = _bluetoothStatus.asStateFlow()

    suspend fun connectToPhone(): Boolean {
        return try {
            Log.d(TAG, "Attempting to connect to phone")
            bluetoothService.connectToPhone()
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to phone: ${e.message}")
            false
        }
    }
    
    suspend fun refreshDevices(): Boolean {
        return try {
            Log.d(TAG, "Requesting device refresh")
            bluetoothService.requestDevices()
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing devices: ${e.message}")
            false
        }
    }

    // Add a method to check Bluetooth status
    data class BluetoothStatus(
        val isAvailable: Boolean,
        val isEnabled: Boolean,
        val pairedDevices: Int,
        val connectedDevices: Int = 0
    )

    fun checkBluetoothStatus(): BluetoothService.BluetoothStatus {
        return bluetoothService.checkBluetoothStatus()
    }

    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun toggleDevice(deviceId: String, isOn: Boolean) {
        bluetoothService.toggleDevice(deviceId, isOn)
    }

    fun setDeviceValue(deviceId: String, value: Int) {
        bluetoothService.setDeviceValue(deviceId, value)
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}
