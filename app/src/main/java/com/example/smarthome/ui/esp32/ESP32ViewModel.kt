package com.example.smarthome.ui.esp32

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.data.bluetooth.BluetoothGattServerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    
    private var isServerRunning = false
    
    fun toggleServer() {
        if (isServerRunning) {
            gattServerService.stopServer()
        } else {
            gattServerService.startServer()
        }
        isServerRunning = !isServerRunning
    }
    
    fun toggleLed() {
        gattServerService.setLedState(!ledState.value)
    }
    
    override fun onCleared() {
        super.onCleared()
        if (isServerRunning) {
            gattServerService.stopServer()
        }
    }
}
