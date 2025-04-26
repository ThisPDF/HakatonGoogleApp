package com.example.smarthome.wear.data.repository

import android.util.Log
import com.example.smarthome.wear.data.Device
import com.example.smarthome.wear.data.bluetooth.BluetoothService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val bluetoothService: BluetoothService
) {
    private val TAG = "DeviceRepository"
    
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
        try {
            val success = bluetoothService.sendCommand(deviceId, "TOGGLE")
            if (!success) {
                Log.e(TAG, "Failed to send toggle command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling device: ${e.message}")
        }
    }
    
    suspend fun updateDeviceValue(deviceId: String, value: String) {
        try {
            val success = bluetoothService.sendCommand(deviceId, "VALUE:$value")
            if (!success) {
                Log.e(TAG, "Failed to send value update command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating device value: ${e.message}")
        }
    }
    
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

    suspend fun checkBluetoothStatus(): BluetoothStatus {
        return withContext(Dispatchers.IO) {
            try {
                val status = bluetoothService.checkBluetoothStatus()
                Log.d(TAG, "Bluetooth status: available=${status.isAvailable}, enabled=${status.isEnabled}, paired=${status.pairedDevices}")
                status
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Bluetooth status: ${e.message}")
                BluetoothStatus(false, false, 0)
            }
        }
    }
}
