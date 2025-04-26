package com.example.smarthome.wear.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.smarthome.wear.data.Device
import com.example.smarthome.wear.data.repository.DeviceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "BluetoothService"
    
    // Standard UUID for SPP (Serial Port Profile)
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    enum class ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTED
    }
    
    data class Device(
        val id: String,
        val name: String,
        val type: String,
        val roomId: String,
        val isOn: Boolean,
        val value: String? = null
    )
    
    @SuppressLint("MissingPermission")
    suspend fun connectToPhone(): Boolean {
        if (bluetoothAdapter == null) {
            _error.value = "Bluetooth is not available"
            Log.e(TAG, "Bluetooth is not available")
            return false
        }
        
        if (!bluetoothAdapter.isEnabled) {
            _error.value = "Bluetooth is not enabled"
            Log.e(TAG, "Bluetooth is not enabled")
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                _error.value = null
                
                // Find the phone from paired devices
                val pairedDevices = bluetoothAdapter.bondedDevices
                
                if (pairedDevices.isEmpty()) {
                    _error.value = "No paired devices found"
                    Log.e(TAG, "No paired devices found")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@withContext false
                }
                
                // Try to find a phone in the paired devices
                val phoneDevice = pairedDevices.find { device -> 
                    val deviceName = device.name?.lowercase() ?: ""
                    deviceName.contains("phone") || 
                    deviceName.contains("galaxy") ||
                    deviceName.contains("pixel") ||
                    deviceName.contains("iphone") ||
                    deviceName.contains("oneplus") ||
                    deviceName.contains("xiaomi") ||
                    deviceName.contains("oppo") ||
                    deviceName.contains("vivo") ||
                    deviceName.contains("huawei")
                }
                
                // If no phone found by name, try the first paired device
                val targetDevice = phoneDevice ?: pairedDevices.firstOrNull()
                
                if (targetDevice == null) {
                    _error.value = "No suitable device found in paired devices"
                    Log.e(TAG, "No suitable device found in paired devices")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@withContext false
                }
                
                Log.d(TAG, "Attempting to connect to ${targetDevice.name} (${targetDevice.address})")
                
                // Close any existing connection
                disconnect()
                
                // Try multiple times to establish connection
                var connectionAttempts = 0
                val maxAttempts = 3
                var connected = false
                
                while (connectionAttempts < maxAttempts && !connected) {
                    connectionAttempts++
                    Log.d(TAG, "Connection attempt $connectionAttempts of $maxAttempts")
                    
                    try {
                        // Try to create and connect socket
                        socket = targetDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                        
                        // Set connection timeout
                        socket?.connect()
                        Log.d(TAG, "Connected to ${targetDevice.name}")
                        connected = true
                    } catch (e: IOException) {
                        Log.e(TAG, "Connection attempt $connectionAttempts failed: ${e.message}")
                        
                        try {
                            socket?.close()
                        } catch (closeException: IOException) {
                            Log.e(TAG, "Could not close the client socket", closeException)
                        }
                        
                        socket = null
                        
                        // Try alternative connection method if standard method fails
                        if (connectionAttempts == 2) {
                            try {
                                Log.d(TAG, "Trying alternative connection method...")
                                // Use reflection to get a different socket type
                                val method = targetDevice.javaClass.getMethod("createInsecureRfcommSocketToServiceRecord", UUID::class.java)
                                socket = method.invoke(targetDevice, SPP_UUID) as BluetoothSocket
                                socket?.connect()
                                Log.d(TAG, "Connected using alternative method")
                                connected = true
                            } catch (e2: Exception) {
                                Log.e(TAG, "Alternative connection method failed: ${e2.message}")
                                try {
                                    socket?.close()
                                } catch (closeException: IOException) {
                                    Log.e(TAG, "Could not close the client socket", closeException)
                                }
                                socket = null
                            }
                        }
                        
                        if (connectionAttempts >= maxAttempts && !connected) {
                            _error.value = "Failed to connect after $maxAttempts attempts: ${e.message}"
                            Log.e(TAG, "Failed to connect after $maxAttempts attempts")
                            _connectionState.value = ConnectionState.DISCONNECTED
                            return@withContext false
                        }
                        
                        // Wait before next attempt
                        if (!connected && connectionAttempts < maxAttempts) {
                            delay(1000)
                        }
                    }
                }
                
                try {
                    inputStream = socket?.inputStream
                    outputStream = socket?.outputStream
                    
                    if (inputStream == null || outputStream == null) {
                        throw IOException("Failed to get valid streams")
                    }
                } catch (e: IOException) {
                    _error.value = "Failed to get streams: ${e.message}"
                    Log.e(TAG, "Failed to get streams: ${e.message}")
                    disconnect()
                    return@withContext false
                }
                
                _connectionState.value = ConnectionState.CONNECTED
                
                // Start listening for incoming data
                startListening()
                
                // Request devices from phone
                requestDevices()
                
                true
            } catch (e: Exception) {
                _error.value = "Unexpected error: ${e.message}"
                Log.e(TAG, "Unexpected error: ${e.message}")
                disconnect()
                false
            }
        }
    }
    
    fun disconnect() {
        try {
            inputStream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing input stream: ${e.message}")
        }
        
        try {
            outputStream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing output stream: ${e.message}")
        }
        
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
        
        inputStream = null
        outputStream = null
        socket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
    
    suspend fun requestDevices(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    Log.e(TAG, "Not connected to phone")
                    return@withContext false
                }
                
                val message = "REQUEST:DEVICES"
                Log.d(TAG, "Requesting devices from phone")
                outputStream?.write(message.toByteArray())
                true
            } catch (e: IOException) {
                _error.value = "Error requesting devices: ${e.message}"
                Log.e(TAG, "Error requesting devices: ${e.message}")
                disconnect()
                false
            }
        }
    }
    
    suspend fun sendCommand(deviceId: String, command: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    Log.e(TAG, "Not connected to phone")
                    return@withContext false
                }
                
                val message = "COMMAND:$deviceId:$command"
                outputStream?.write(message.toByteArray())
                true
            } catch (e: IOException) {
                _error.value = "Error sending command: ${e.message}"
                Log.e(TAG, "Error sending command: ${e.message}")
                disconnect()
                false
            }
        }
    }
    
    private fun startListening() {
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            var bytes: Int
            var consecutiveErrors = 0
            val maxConsecutiveErrors = 5
            
            while (_connectionState.value == ConnectionState.CONNECTED) {
                try {
                    if (inputStream == null) {
                        Log.e(TAG, "Input stream is null, stopping listener")
                        break
                    }
                    
                    bytes = inputStream?.read(buffer) ?: -1
                    
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes)
                        Log.d(TAG, "Received data: $message")
                        processMessage(message)
                        consecutiveErrors = 0
                    } else if (bytes < 0) {
                        // End of stream reached
                        Log.e(TAG, "End of stream reached")
                        break
                    }
                } catch (e: IOException) {
                    consecutiveErrors++
                    Log.e(TAG, "Error reading from stream ($consecutiveErrors/$maxConsecutiveErrors): ${e.message}")
                    
                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        Log.e(TAG, "Too many consecutive errors, disconnecting")
                        break
                    }
                    
                    // Add a small delay to avoid tight loop on errors
                    try {
                        delay(500)
                    } catch (interruptException: Exception) {
                        Log.e(TAG, "Listener thread sleep interrupted", interruptException)
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    Log.e(TAG, "Unexpected error ($consecutiveErrors/$maxConsecutiveErrors): ${e.message}")
                    
                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        Log.e(TAG, "Too many consecutive errors, disconnecting")
                        break
                    }
                }
            }
            
            disconnect()
        }
    }
    
    private fun processMessage(message: String) {
        Log.d(TAG, "Received message: $message")
        
        try {
            when {
                message.startsWith("DEVICES:") -> {
                    val json = message.substring(8)
                    try {
                        // Simple parsing for now
                        val devicesList = mutableListOf<Device>()
                        // Parse the JSON and populate devicesList
                        _devices.value = devicesList
                        Log.d(TAG, "Updated devices list with ${devicesList.size} devices")
                    } catch (e: Exception) {
                        _error.value = "Error parsing devices: ${e.message}"
                        Log.e(TAG, "Error parsing devices: ${e.message}")
                    }
                }
                message.startsWith("COMMAND:") -> {
                    val parts = message.split(":")
                    if (parts.size >= 3) {
                        val deviceId = parts[1]
                        val command = parts[2]
                        // Update local device state
                        updateDeviceState(deviceId, command)
                    }
                }
            }
        } catch (e: Exception) {
            _error.value = "Error processing message: ${e.message}"
            Log.e(TAG, "Error processing message: ${e.message}")
        }
    }
    
    private fun updateDeviceState(deviceId: String, command: String) {
        val currentDevices = _devices.value.toMutableList()
        val deviceIndex = currentDevices.indexOfFirst { it.id == deviceId }
        
        if (deviceIndex != -1) {
            val device = currentDevices[deviceIndex]
            val updatedDevice = when (command) {
                "TOGGLE" -> device.copy(isOn = !device.isOn)
                "ON" -> device.copy(isOn = true)
                "OFF" -> device.copy(isOn = false)
                else -> {
                    if (command.startsWith("VALUE:")) {
                        val value = command.substring(6)
                        device.copy(value = value)
                    } else {
                        device
                    }
                }
            }
            
            currentDevices[deviceIndex] = updatedDevice
            _devices.value = currentDevices
        }
    }
    
    fun checkBluetoothStatus(): BluetoothStatus {
        val isAvailable = bluetoothAdapter != null
        val isEnabled = bluetoothAdapter?.isEnabled == true
        val pairedDevices = if (isEnabled) {
            @SuppressLint("MissingPermission")
            bluetoothAdapter?.bondedDevices?.size ?: 0
        } else {
            0
        }
        
        return BluetoothStatus(isAvailable, isEnabled, pairedDevices, 0)
    }
    
    data class BluetoothStatus(
        val isAvailable: Boolean,
        val isEnabled: Boolean,
        val pairedDevices: Int,
        val connectedDevices: Int
    )
}
