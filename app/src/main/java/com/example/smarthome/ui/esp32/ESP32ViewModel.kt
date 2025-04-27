package com.example.smarthome.ui.esp32

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.data.bluetooth.BluetoothGattServerService
import com.example.smarthome.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ESP32ViewModel @Inject constructor(
    private val bluetoothGattServerService: BluetoothGattServerService,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _temperature = MutableStateFlow(22.0f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()
    
    private val _ledState = MutableStateFlow(false)
    val ledState: StateFlow<Boolean> = _ledState.asStateFlow()
    
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()
    
    init {
        startServer()
    }
    
    fun startServer() {
        bluetoothGattServerService.startServer()
        _isServerRunning.value = true
    }
    
    fun stopServer() {
        bluetoothGattServerService.cleanup()
        _isServerRunning.value = false
    }
    
    fun toggleLed() {
        val newState = !_ledState.value
        _ledState.value = newState
        bluetoothGattServerService.setLedState(newState)
    }
    
    fun updateTemperature(temperature: Float) {
        _temperature.value = temperature
        bluetoothGattServerService.updateTemperature(temperature)
        
        // Also update the temperature in the device repository
        // This will trigger an update to the wearable
        viewModelScope.launch {
            deviceRepository.updateTemperature(temperature)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopServer()
    }
}
