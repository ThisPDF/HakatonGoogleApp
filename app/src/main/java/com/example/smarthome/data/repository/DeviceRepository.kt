package com.example.smarthome.data.repository

import com.example.smarthome.data.Device
import com.example.smarthome.data.DeviceType
import com.example.smarthome.data.bluetooth.BluetoothService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val bluetoothService: BluetoothService
) {
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: Flow<List<Device>> = _devices.asStateFlow()

    init {
        // Initialize with mock data
        _devices.value = listOf(
            Device("living_light", "Living Room Light", DeviceType.LIGHT, "living", false),
            Device("kitchen_light", "Kitchen Light", DeviceType.LIGHT, "kitchen", true),
            Device("thermostat", "Thermostat", DeviceType.THERMOSTAT, "living", true, "72"),
            Device("front_lock", "Front Door", DeviceType.LOCK, "entrance", false),
            Device("temp_sensor", "Temperature Sensor", DeviceType.SENSOR, "bedroom", true, "70.5")
        )
    }
    
    suspend fun toggleDevice(deviceId: String) {
        val currentDevices = _devices.value.toMutableList()
        val deviceIndex = currentDevices.indexOfFirst { it.id == deviceId }
        
        if (deviceIndex != -1) {
            val device = currentDevices[deviceIndex]
            val updatedDevice = device.copy(isOn = !device.isOn)
            currentDevices[deviceIndex] = updatedDevice
            _devices.value = currentDevices
            
            // Sync with watch
            bluetoothService.sendCommand(deviceId, "TOGGLE")
        }
    }
    
    suspend fun updateDeviceValue(deviceId: String, value: String) {
        val currentDevices = _devices.value.toMutableList()
        val deviceIndex = currentDevices.indexOfFirst { it.id == deviceId }
        
        if (deviceIndex != -1) {
            val device = currentDevices[deviceIndex]
            val updatedDevice = device.copy(value = value)
            currentDevices[deviceIndex] = updatedDevice
            _devices.value = currentDevices
            
            // Sync with watch
            bluetoothService.sendCommand(deviceId, "VALUE:$value")
        }
    }
    
    suspend fun syncDevicesWithWatch() {
        bluetoothService.sendDevices(_devices.value)
    }
    
    fun addDevice(device: Device) {
        val currentDevices = _devices.value.toMutableList()
        currentDevices.add(device)
        _devices.value = currentDevices
    }
    
    fun removeDevice(deviceId: String) {
        val currentDevices = _devices.value.toMutableList()
        currentDevices.removeIf { it.id == deviceId }
        _devices.value = currentDevices
    }
}
