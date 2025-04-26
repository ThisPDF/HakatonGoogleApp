package com.example.smarthome.ui.dummydevices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.data.Device
import com.example.smarthome.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DummyDevicesViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    val devices = deviceRepository.devices

    fun addDevice(device: Device) {
        deviceRepository.addDevice(device)
    }

    fun removeDevice(deviceId: String) {
        deviceRepository.removeDevice(deviceId)
    }

    fun toggleDevice(deviceId: String) {
        viewModelScope.launch {
            deviceRepository.toggleDevice(deviceId)
        }
    }
}
