package com.example.smarthome.ui.esp32

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.data.bluetooth.BluetoothGattServerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ESP32ViewModel @Inject constructor(
    private val gattServerService: BluetoothGattServerService
) : ViewModel() {
    
    val isConnected: StateFlow<Boolean> = gattServerService.isConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    val temperatureData: StateFlow<Float?> = gattServerService.temperatureData
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    val ledState: StateFlow<Boolean> = gattServerService.ledState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()
    
    fun toggleServer() {
        if (_isServerRunning.value) {
            gattServerService.stopServer()
        } else {
            gattServerService.startServer()
        }
        _isServerRunning.value = !_isServerRunning.value
    }
    
    fun toggleLed() {
        gattServerService.setLedState(!ledState.value)
    }
    
    // Simulate temperature updates for testing
    fun simulateTemperatureUpdate(temperature: Float) {
        gattServerService.updateTemperature(temperature)
    }
    
    override fun onCleared() {
        super.onCleared()
        if (_isServerRunning.value) {
            gattServerService.stopServer()
        }
        gattServerService.cleanup()
    }
}
