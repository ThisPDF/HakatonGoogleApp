package com.example.smarthome.data.repository

import android.util.Log
import com.example.smarthome.data.Device
import com.example.smarthome.data.DeviceType
import com.example.smarthome.data.Room
import com.example.smarthome.data.wearable.WearableService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val wearableService: WearableService
) {
    private val TAG = "DeviceRepository"
    
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    init {
        // Initialize with sample devices
        _devices.value = listOf(
            Device("1", "Living Room Light", DeviceType.LIGHT, "living_room", true),
            Device("2", "Kitchen Light", DeviceType.LIGHT, "kitchen", false),
            Device("3", "Bedroom Light", DeviceType.LIGHT, "bedroom", false),
            Device("4", "Thermostat", DeviceType.THERMOSTAT, "living_room", true, "22.5"),
            Device("5", "Front Door", DeviceType.LOCK, "entrance", false)
        )
        
        // Send initial device list to wearable
        wearableService.sendDeviceList(_devices.value)
        
        // Listen for device commands from wearable
        wearableService.lastCommand.onEach { command ->
            command?.let { handleDeviceCommand(it) }
        }.launchIn(coroutineScope)
    }
    
    // Handle device commands from wearable
    private fun handleDeviceCommand(command: WearableService.DeviceCommand) {
        when (command.action) {
            "TOGGLE" -> toggleDevice(command.deviceId)
            "SET" -> {
                if (command.value != null) {
                    setDeviceValue(command.deviceId, command.value)
                }
            }
        }
    }
    
    // Get all devices
    fun getAllDevices(): Flow<List<Device>> = devices
    
    // Get device by ID
    fun getDeviceById(id: String): Device? {
        return _devices.value.find { it.id == id }
    }
    
    // Get devices by room
    fun getDevicesByRoom(roomId: String): List<Device> {
        return _devices.value.filter { it.roomId == roomId }
    }
    
    // Toggle device state
    fun toggleDevice(deviceId: String) {
        val currentDevices = _devices.value.toMutableList()
        val deviceIndex = currentDevices.indexOfFirst { it.id == deviceId }
        
        if (deviceIndex != -1) {
            val device = currentDevices[deviceIndex]
            val updatedDevice = device.copy(isOn = !device.isOn)
            currentDevices[deviceIndex] = updatedDevice
            _devices.value = currentDevices
            
            // Notify wearable of device list change
            wearableService.sendDeviceList(_devices.value)
            
            Log.d(TAG, "Device ${device.name} toggled to ${updatedDevice.isOn}")
        }
    }
    
    // Set device value
    fun setDeviceValue(deviceId: String, value: String) {
        val currentDevices = _devices.value.toMutableList()
        val deviceIndex = currentDevices.indexOfFirst { it.id == deviceId }
        
        if (deviceIndex != -1) {
            val device = currentDevices[deviceIndex]
            val updatedDevice = device.copy(value = value)
            currentDevices[deviceIndex] = updatedDevice
            _devices.value = currentDevices
            
            // Notify wearable of device list change
            wearableService.sendDeviceList(_devices.value)
            
            Log.d(TAG, "Device ${device.name} value set to $value")
        }
    }
    
    // Add a new device
    fun addDevice(device: Device) {
        val currentDevices = _devices.value.toMutableList()
        currentDevices.add(device)
        _devices.value = currentDevices
        
        // Notify wearable of device list change
        wearableService.sendDeviceList(_devices.value)
        
        Log.d(TAG, "Device ${device.name} added")
    }
    
    // Update a device
    fun updateDevice(device: Device) {
        val currentDevices = _devices.value.toMutableList()
        val deviceIndex = currentDevices.indexOfFirst { it.id == device.id }
        
        if (deviceIndex != -1) {
            currentDevices[deviceIndex] = device
            _devices.value = currentDevices
            
            // Notify wearable of device list change
            wearableService.sendDeviceList(_devices.value)
            
            Log.d(TAG, "Device ${device.name} updated")
        }
    }
    
    // Delete a device
    fun deleteDevice(deviceId: String) {
        val currentDevices = _devices.value.toMutableList()
        val deviceIndex = currentDevices.indexOfFirst { it.id == deviceId }
        
        if (deviceIndex != -1) {
            val device = currentDevices[deviceIndex]
            currentDevices.removeAt(deviceIndex)
            _devices.value = currentDevices
            
            // Notify wearable of device list change
            wearableService.sendDeviceList(_devices.value)
            
            Log.d(TAG, "Device ${device.name} deleted")
        }
    }
    
    // Update temperature
    fun updateTemperature(temperature: Float) {
        // Find thermostat device
        val currentDevices = _devices.value.toMutableList()
        val thermostatIndex = currentDevices.indexOfFirst { it.type == DeviceType.THERMOSTAT }
        
        if (thermostatIndex != -1) {
            val thermostat = currentDevices[thermostatIndex]
            val updatedThermostat = thermostat.copy(value = temperature.toString())
            currentDevices[thermostatIndex] = updatedThermostat
            _devices.value = currentDevices
            
            // Send temperature to wearable
            wearableService.sendTemperature(temperature)
            
            Log.d(TAG, "Temperature updated to $temperature")
        }
    }
}
