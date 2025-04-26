package com.example.smarthome.wear.data.repository

import com.example.smarthome.wear.data.Device
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor() {
    // Mock data for devices
    private val devices = listOf(
        Device("living_light", "Living Room Light", "LIGHT", "living", false),
        Device("kitchen_light", "Kitchen Light", "LIGHT", "kitchen", true),
        Device("thermostat", "Thermostat", "THERMOSTAT", "living", true, "72"),
        Device("front_lock", "Front Door", "LOCK", "entrance", false),
        Device("temp_sensor", "Temperature Sensor", "SENSOR", "bedroom", true, "70.5")
    )
    
    suspend fun getDevices(): List<Device> {
        delay(500) // Simulate network delay
        return devices
    }
    
    suspend fun getDevice(deviceId: String): Device {
        delay(300) // Simulate network delay
        return devices.find { it.id == deviceId } 
            ?: throw IllegalArgumentException("Device not found")
    }
    
    suspend fun toggleDevice(deviceId: String) {
        delay(300) // Simulate network delay
        // In a real app, this would make an API call to toggle the device
        println("Toggling device: $deviceId")
    }
    
    suspend fun updateDeviceValue(deviceId: String, value: String) {
        delay(300) // Simulate network delay
        // In a real app, this would make an API call to update the device value
        println("Updating device $deviceId to value: $value")
    }
}
