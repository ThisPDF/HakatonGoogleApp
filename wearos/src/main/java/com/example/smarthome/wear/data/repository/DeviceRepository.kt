package com.example.smarthome.wear.data.repository

import com.example.smarthome.wear.data.bluetooth.BluetoothService
import com.example.smarthome.wear.data.models.Device
import com.example.smarthome.wear.data.models.QuickAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val bluetoothService: BluetoothService
) {
    suspend fun getDevices(): List<Device> = withContext(Dispatchers.IO) {
        try {
            val bluetoothDevices = bluetoothService.getDevices()
            bluetoothDevices.map { bluetoothDevice ->
                Device(
                    id = bluetoothDevice.id,
                    name = bluetoothDevice.name,
                    type = bluetoothDevice.type,
                    roomId = bluetoothDevice.roomId,
                    isOn = bluetoothDevice.isOn,
                    brightness = bluetoothDevice.brightness,
                    temperature = bluetoothDevice.temperature,
                    isLocked = bluetoothDevice.isLocked
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getDevice(deviceId: String): Device? = withContext(Dispatchers.IO) {
        try {
            val bluetoothDevice = bluetoothService.getDevice(deviceId)
            bluetoothDevice?.let {
                Device(
                    id = it.id,
                    name = it.name,
                    type = it.type,
                    roomId = it.roomId,
                    isOn = it.isOn,
                    brightness = it.brightness,
                    temperature = it.temperature,
                    isLocked = it.isLocked
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getQuickActions(): List<QuickAction> = withContext(Dispatchers.IO) {
        try {
            val bluetoothQuickActions = bluetoothService.getQuickActions()
            bluetoothQuickActions.map { bluetoothQuickAction ->
                QuickAction(
                    id = bluetoothQuickAction.id,
                    name = bluetoothQuickAction.name,
                    actionType = bluetoothQuickAction.type, // Map from 'type' to 'actionType'
                    deviceId = bluetoothQuickAction.deviceId
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun toggleDevice(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            bluetoothService.toggleDevice(deviceId)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun setDeviceValue(deviceId: String, value: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            bluetoothService.setDeviceValue(deviceId, value)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun executeQuickAction(actionId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            bluetoothService.executeQuickAction(actionId)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
