package com.example.smarthome.wear.data.repository

import com.example.smarthome.wear.data.Device
import com.example.smarthome.wear.data.bluetooth.BluetoothService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val bluetoothService: BluetoothService
) {
    // Get devices directly from BluetoothService
    val devices: Flow<List<Device>> = bluetoothService.devices
    
    suspend fun getDevices(): List<Device> {
        return bluetoothService.devices.value
    }
    
    suspend fun getDevice(deviceId: String): Device {
        return bluetoothService.devices.value.find { it.id == deviceId }
            ?: throw IllegalArgumentException("Device not found")
    }
    
    suspend fun toggleDevice(deviceId: String) {
        bluetoothService.sendCommand(deviceId, "TOGGLE")
    }
    
    suspend fun updateDeviceValue(deviceId: String, value: String) {
        bluetoothService.sendCommand(deviceId, "VALUE:$value")
    }
    
    suspend fun connectToPhone(): Boolean {
        return bluetoothService.connectToPhone()
    }
    
    suspend fun refreshDevices(): Boolean {
        return bluetoothService.requestDevices()
    }
}
