package com.example.smarthome.wear.data.repository

import android.util.Log
import com.example.smarthome.wear.data.bluetooth.BluetoothService
import com.example.smarthome.wear.data.models.Device
import com.example.smarthome.wear.data.models.QuickAction
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
    
    // Map BluetoothService.Device to our Device model
    val devices: Flow<List<Device>> = bluetoothService.devices.map { deviceList ->
        deviceList.map { device ->
            Device(
                id = device.id,
                name = device.name,
                type = device.type,
                roomId = device.roomId,
                isOn = device.isOn,
                value = device.value
            )
        }
    }
    
    private val _quickActions = MutableStateFlow<List<QuickAction>>(
        listOf(
            QuickAction("1", "All Lights", "bulb", "LIGHT"),
            QuickAction("2", "Lock Doors", "lock", "LOCK")
        )
    )
    val quickActions: StateFlow<List<QuickAction>> = _quickActions.asStateFlow()
    
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
        bluetoothService.toggleDevice(deviceId)
    }

    fun setDeviceValue(deviceId: String, value: Int) {
        bluetoothService.setDeviceValue(deviceId, value)
    }
    
    fun executeQuickAction(actionId: String): Boolean {
        return bluetoothService.executeQuickAction(actionId)
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}
