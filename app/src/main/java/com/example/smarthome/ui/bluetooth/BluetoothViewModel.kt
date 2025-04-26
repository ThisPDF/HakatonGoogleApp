package com.example.smarthome.ui.bluetooth

import android.bluetooth.BluetoothDevice
import android.util.Log
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
    val error: String? = null,
    val isConnecting: Boolean = false
)

@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothService: BluetoothService,
    private val deviceRepository: DeviceRepository
) : ViewModel() {

    private val TAG = "BluetoothViewModel"
    private val _uiState = MutableStateFlow(BluetoothUiState())
    val uiState: StateFlow<BluetoothUiState> = _uiState.asStateFlow()

    init {
        refreshPairedDevices()
        
        viewModelScope.launch {
            bluetoothService.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                
                // If disconnected, clear the connected device
                if (state == BluetoothService.ConnectionState.DISCONNECTED) {
                    _uiState.update { it.copy(connectedDevice = null) }
                }
            }
        }
    }

    fun refreshPairedDevices() {
        viewModelScope.launch {
            try {
                // Start the Bluetooth server which will handle connections
                bluetoothService.startServer()
                
                // For now, we'll just update the UI state with an empty list
                // since we're not actually getting paired devices directly
                _uiState.update { it.copy(pairedDevices = emptyList(), error = null) }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = "Failed to get paired devices: ${e.message}",
                        pairedDevices = emptyList()
                    ) 
                }
            }
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null, isConnecting = true) }
                
                // Since our BluetoothService doesn't have a connectToDevice method,
                // we'll just start the server which will accept incoming connections
                bluetoothService.startServer()
                
                // Update the UI to show we're attempting to connect
                _uiState.update { 
                    it.copy(
                        connectedDevice = device,
                        isConnecting = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = "Error: ${e.message}",
                        isConnecting = false
                    ) 
                }
            }
        }
    }

    fun syncDevices() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                val success = deviceRepository.syncDevicesWithWatch()
                if (!success) {
                    _uiState.update { it.copy(error = "Failed to sync devices") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Error syncing devices: ${e.message}") }
            }
        }
    }

    companion object {
        private const val TAG = "BluetoothViewModel"
    }
}
