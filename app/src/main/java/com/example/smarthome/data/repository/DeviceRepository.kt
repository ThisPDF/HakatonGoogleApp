package com.example.smarthome.data.repository

import android.util.Log
import com.example.smarthome.data.Device
import com.example.smarthome.data.DeviceType
import com.example.smarthome.data.bluetooth.BluetoothService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val bluetoothService: BluetoothService
) {
    private val TAG = "DeviceRepository"
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: Flow<List<Device>> = _devices.asStateFlow()
    
    // Create a coroutine scope for repository operations
    private val repositoryScope = CoroutineScope(Dispatchers.Main)

    init {
        // Initialize with mock data
        _devices.value = listOf(
            Device("living_light", "Living Room Light", DeviceType.LIGHT, "living", false),
            Device("kitchen_light", "Kitchen Light", DeviceType.LIGHT, "kitchen", true),
            Device("thermostat", "Thermostat", DeviceType.THERMOSTAT, "living", true, "72"),
            Device("front_lock", "Front Door", DeviceType.LOCK, "entrance", false),
            Device("temp_sensor", "Temperature Sensor", DeviceType.SENSOR, "bedroom", true, "70.5")
        )
        
        // Listen for device requests from the watch
        listenForDeviceRequests()
    }
    
    private fun listenForDeviceRequests() {
        // This would be implemented with a callback from the BluetoothService
        // For now, we'll just sync devices whenever the connection state changes
        repositoryScope.launch {
            bluetoothService.connectionState.collect { state ->
                if (state == BluetoothService.ConnectionState.CONNECTED) {
                    // Automatically sync devices when connected
                    syncDevicesWithWatch()
                }
            }
        }
    }
    
    suspend fun toggleDevice(deviceId: String) {
        val currentDevices = _devices.value.toMutableList()
        val deviceIndex = currentDevices.indexOfFirst { it.id == deviceId }
        
        if (deviceIndex != -1) {
            val device = currentDevices[deviceIndex]
            val updatedDevice = device.copy(isOn = !device.isOn)
            currentDevices[deviceIndex] = updatedDevice
            _devices.value = currentDevices
            
            // Sync with watch if connected
            try {
                bluetoothService.sendData("{\"command\":\"toggle\",\"deviceId\":\"$deviceId\"}")
            } catch (e: Exception) {
                // Log error but don't fail the toggle operation
                Log.e(TAG, "Failed to sync toggle: ${e.message}")
            }
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
            
            // Sync with watch if connected
            try {
                bluetoothService.sendData("{\"command\":\"setValue\",\"deviceId\":\"$deviceId\",\"value\":\"$value\"}")
            } catch (e: Exception) {
                // Log error but don't fail the update operation
                Log.e(TAG, "Failed to sync value update: ${e.message}")
            }
        }
    }
    
    suspend fun syncDevicesWithWatch(): Boolean {
        return try {
            Log.d(TAG, "Syncing devices with watch")
            bluetoothService.sendDevices(_devices.value)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync devices: ${e.message}")
            false
        }
    }
    
    fun addDevice(device: Device) {
        val currentDevices = _devices.value.toMutableList()
        currentDevices.add(device)
        _devices.value = currentDevices
        
        // Sync with watch
        repositoryScope.launch {
            syncDevicesWithWatch()
        }
    }
    
    fun removeDevice(deviceId: String) {
        val currentDevices = _devices.value.toMutableList()
        currentDevices.removeIf { it.id == deviceId }
        _devices.value = currentDevices
        
        // Sync with watch
        repositoryScope.launch {
            syncDevicesWithWatch()
        }
    }
}
