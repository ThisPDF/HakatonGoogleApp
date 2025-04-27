package com.example.smarthome.wear.data.repository

import android.util.Log
import com.example.smarthome.wear.data.models.Device
import com.example.smarthome.wear.data.wearable.WearableService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val wearableService: WearableService
) {
    private val TAG = "DeviceRepository"
    
    // Use the devices flow from the WearableService
    val devices: StateFlow<List<Device>> = wearableService.devices
    
    // Use the temperature flow from the WearableService
    val temperature: StateFlow<Float?> = wearableService.temperature
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // Get all devices
    fun getAllDevices(): Flow<List<Device>> = devices
    
    // Get device by ID
    fun getDeviceById(id: String): Device? {
        return devices.value.find { it.id == id }
    }
    
    // Toggle device state
    suspend fun toggleDevice(deviceId: String) {
        try {
            wearableService.toggleDevice(deviceId)
            Log.d(TAG, "Toggle command sent for device $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling device", e)
        }
    }
    
    // Set device value
    suspend fun setDeviceValue(deviceId: String, value: String) {
        try {
            wearableService.setDeviceValue(deviceId, value)
            Log.d(TAG, "Set value command sent for device $deviceId: $value")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting device value", e)
        }
    }
}
