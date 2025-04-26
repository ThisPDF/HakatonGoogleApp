package com.example.smarthome.wear.data.repository

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.example.smarthome.wear.data.bluetooth.BluetoothService
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val bluetoothService: BluetoothService
) {
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _bluetoothStatus = MutableStateFlow(BluetoothStatus(false, false, 0, 0))
    val bluetoothStatus: StateFlow<BluetoothStatus> = _bluetoothStatus.asStateFlow()

    private val gson = Gson()

    fun updateBluetoothStatus(status: BluetoothStatus) {
        _bluetoothStatus.value = status
    }

    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun updateDevices(deviceList: List<Device>) {
        _devices.value = deviceList
    }

    fun connectToPhone() {
        bluetoothService.connectToPhone()
    }

    fun disconnectFromPhone() {
        bluetoothService.disconnect()
    }

    fun toggleDevice(deviceId: String, isOn: Boolean) {
        val command = Command(
            type = "TOGGLE_DEVICE",
            deviceId = deviceId,
            value = if (isOn) "ON" else "OFF"
        )
        
        val jsonCommand = gson.toJson(command)
        bluetoothService.sendData(jsonCommand)
        
        // Optimistically update the device state
        val updatedDevices = _devices.value.map {
            if (it.id == deviceId) it.copy(isOn = isOn) else it
        }
        _devices.value = updatedDevices
    }

    fun setDeviceValue(deviceId: String, value: Int) {
        val command = Command(
            type = "SET_VALUE",
            deviceId = deviceId,
            value = value.toString()
        )
        
        val jsonCommand = gson.toJson(command)
        bluetoothService.sendData(jsonCommand)
        
        // Optimistically update the device state
        val updatedDevices = _devices.value.map {
            if (it.id == deviceId) it.copy(value = value) else it
        }
        _devices.value = updatedDevices
    }

    data class Device(
        val id: String,
        val name: String,
        val type: String,
        val roomId: String,
        val isOn: Boolean,
        val value: Int = 0
    )

    data class Command(
        val type: String,
        val deviceId: String,
        val value: String
    )

    data class BluetoothStatus(
        val isAvailable: Boolean,
        val isEnabled: Boolean,
        val pairedDevices: Int,
        val connectedDevices: Int
    )

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
}
