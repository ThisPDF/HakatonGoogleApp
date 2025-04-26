package com.example.smarthome.data.repository

import com.example.smarthome.data.Device
import com.example.smarthome.data.network.SmartHomeApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val api: SmartHomeApi
) {
    fun getDevices(): Flow<List<Device>> = flow {
        val devices = api.getDevices()
        emit(devices)
    }
    
    suspend fun toggleDevice(deviceId: String) {
        api.toggleDevice(deviceId)
    }
    
    suspend fun updateDeviceValue(deviceId: String, value: String) {
        api.updateDeviceValue(deviceId, value)
    }
}
