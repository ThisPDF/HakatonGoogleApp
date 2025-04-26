package com.example.smarthome.data.repository

import com.example.smarthome.data.Device
import com.example.smarthome.data.DeviceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor() {
    // For now, we'll use mock data instead of actual API calls
    // This makes it easier to test without the ESP32 hardware
    fun getDevices(): Flow<List<Device>> = flow {
        val devices = listOf(
            Device("living_light", "Living Room Light", DeviceType.LIGHT, "living", false),
            Device("kitchen_light", "Kitchen Light", DeviceType.LIGHT, "kitchen", true),
            Device("thermostat", "Thermostat", DeviceType.THERMOSTAT, "living", true, "72"),
            Device("front_lock", "Front Door", DeviceType.LOCK, "entrance", false),
            Device("temp_sensor", "Temperature Sensor", DeviceType.SENSOR, "bedroom", true, "70.5")
        )
        emit(devices)
    }
    
    suspend fun toggleDevice(deviceId: String) {
        // In a real app, this would make an API call to toggle the device
        println("Toggling device: $deviceId")
    }
    
    suspend fun updateDeviceValue(deviceId: String, value: String) {
        // In a real app, this would make an API call to update the device value
        println("Updating device $deviceId to value: $value")
    }
}
