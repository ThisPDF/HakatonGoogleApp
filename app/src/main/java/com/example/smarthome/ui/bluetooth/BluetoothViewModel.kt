package com.example.smarthome.ui.bluetooth

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.data.bluetooth.BluetoothService
import com.example.smarthome.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BluetoothUiState(
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val connectionState: BluetoothService.ConnectionState = BluetoothService.ConnectionState.DISCONNECTED,
    val connectedDevice: BluetoothDevice? = null,
    val error: String? = null
)

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BluetoothUiState())
    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()

    init {
        refreshPairedDevices()
        
        viewModelScope.launch {
            bluetoothService.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
    }

    fun refreshPairedDevices() {
        val devices = bluetoothService.getPairedDevices()
        _uiState.update { it.copy(pairedDevices = devices) }
    }

    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            try {
                val success = bluetoothService.connectToDevice(device.address)
                if (success) {
                    _uiState.update { it.copy(connectedDevice = device, error = null) }
                } else {
                    _uiState.update { it.copy(error = "Failed to connect to device") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun syncDevices() {
        viewModelScope.launch {
            deviceRepository.syncDevicesWithWatch()
        }
    }
}
