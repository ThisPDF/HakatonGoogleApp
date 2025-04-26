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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    
    private val _connectionStatus = MutableStateFlow("Server not started")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()
    
    private val _connectionLogs = MutableStateFlow<List<String>>(emptyList())
    val connectionLogs: StateFlow<List<String>> = _connectionLogs.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    init {
        observeConnectionState()
    }
    
    private fun observeConnectionState() {
        viewModelScope.launch {
            gattServerService.isConnected.collect { connected ->
                if (connected) {
                    _connectionStatus.value = "Connected to ESP32"
                    addLog("Connected to ESP32")
                } else {
                    _connectionStatus.value = if (_isServerRunning.value) "Waiting for connection..." else "Server not started"
                    if (_isServerRunning.value) {
                        addLog("Disconnected from ESP32")
                    }
                }
            }
        }
    }
    
    fun toggleServer() {
        if (_isServerRunning.value) {
            gattServerService.stopServer()
            _isServerRunning.value = false
            _connectionStatus.value = "Server stopped"
            addLog("GATT Server stopped")
        } else {
            gattServerService.startServer()
            _isServerRunning.value = true
            _connectionStatus.value = "Waiting for connection..."
            addLog("GATT Server started")
        }
    }
    
    fun toggleLed() {
        gattServerService.setLedState(!ledState.value)
        addLog("LED state changed to: ${!ledState.value}")
    }
    
    // Simulate temperature updates for testing
    fun simulateTemperatureUpdate(temperature: Float) {
        gattServerService.updateTemperature(temperature)
        addLog("Temperature updated to: $temperature °C")
    }
    
    private fun addLog(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message"
        _connectionLogs.value = _connectionLogs.value.toMutableList().apply {
            add(0, logEntry)
            if (size > 100) {
                removeAt(size - 1)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        if (_isServerRunning.value) {
            gattServerService.stopServer()
        }
        gattServerService.cleanup()
    }
}
