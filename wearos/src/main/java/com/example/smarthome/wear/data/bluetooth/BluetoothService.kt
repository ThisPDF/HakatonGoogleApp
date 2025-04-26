package com.example.smarthome.wear.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    
    // Use a well-known UUID for WearOS to phone communication
    // This is the UUID for Serial Port Profile (SPP)
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    // Alternative UUIDs to try if the standard one fails
    private val ALTERNATIVE_UUIDS = listOf(
        UUID.fromString("0000110a-0000-1000-8000-00805f9b34fb"), // Health Device Profile
        UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb"), // Health Device Profile
        UUID.fromString("00001112-0000-1000-8000-00805f9b34fb"), // Headset Profile
        UUID.fromString("00001115-0000-1000-8000-00805f9b34fb"), // Personal Area Networking
        UUID.fromString("00001116-0000-1000-8000-00805f9b34fb")  // Network Access Point
    )
    
    private val bluetoothManager by lazy {
        try {
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        } catch (e: Exception) {
            Log.e(TAG, "Error getting BluetoothManager: ${e.message}", e)
            null
        }
    }
    
    private val bluetoothAdapter by lazy {
        try {
            bluetoothManager?.adapter
        } catch (e: Exception) {
            Log.e(TAG, "Error getting BluetoothAdapter: ${e.message}", e)
            null
        }
    }
    
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val MAX_CONNECTION_RETRIES = 3
    private val CONNECTION_TIMEOUT = 10000L // 10 seconds
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()
    
    init {
        // Initialize with some mock devices for testing
        _devices.value = listOf(
            Device("1", "Living Room Light", "LIGHT", "living_room", true, null),
            Device("2", "Bedroom Light", "LIGHT", "bedroom", false, null),
            Device("3", "Kitchen Light", "LIGHT", "kitchen", false, null),
            Device("4", "Thermostat", "THERMOSTAT", "living_room", true, "72")
        )
        
        // Try to get paired devices at initialization
        refreshPairedDevices()
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun refreshPairedDevices() {
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Cannot refresh paired devices: missing permissions")
            return
        }
        
        try {
            val adapter = bluetoothAdapter ?: return
            if (!adapter.isEnabled) return
            
            val devices = adapter.bondedDevices?.toList() ?: emptyList()
            Log.d(TAG, "Found ${devices.size} paired devices")
            devices.forEach { device ->
                Log.d(TAG, "Paired device: ${device.name ?: "Unknown"} (${device.address})")
            }
            _pairedDevices.value = devices
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing paired devices: ${e.message}", e)
        }
    }
    
    fun checkBluetoothStatus(): BluetoothStatus {
        // First check if the device has Bluetooth hardware
        val packageManager = context.packageManager
        val hasBluetooth = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        
        Log.d(TAG, "Device has Bluetooth hardware: $hasBluetooth")
        
        if (!hasBluetooth) {
            return BluetoothStatus(
                isAvailable = false,
                isEnabled = false,
                pairedDevices = 0,
                connectedDevices = 0
            )
        }
        
        if (!hasBluetoothPermissions()) {
            Log.d(TAG, "Bluetooth permissions not granted")
            return BluetoothStatus(
                isAvailable = true,
                isEnabled = false,
                pairedDevices = 0,
                connectedDevices = 0
            )
        }
        
        val isAvailable = bluetoothAdapter != null
        val isEnabled = isAvailable && (bluetoothAdapter?.isEnabled == true)
        
        Log.d(TAG, "Bluetooth adapter available: $isAvailable, enabled: $isEnabled")
        
        // Refresh paired devices
        refreshPairedDevices()
        
        val pairedDevices = _pairedDevices.value.size
        
        val connectedDevices = if (isEnabled) {
            try {
                _pairedDevices.value.count { device ->
                    device.bondState == BluetoothDevice.BOND_BONDED
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connected devices: ${e.message}", e)
                0
            }
        } else 0
        
        Log.d(TAG, "Paired devices: $pairedDevices, connected: $connectedDevices")
        
        return BluetoothStatus(isAvailable, isEnabled, pairedDevices, connectedDevices)
    }
    
    suspend fun connectToPhone(): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            _error.value = "Bluetooth permissions not granted"
            return@withContext false
        }
        
        // Check if the device has Bluetooth hardware
        val packageManager = context.packageManager
        val hasBluetooth = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        
        if (!hasBluetooth) {
            _error.value = "This device does not have Bluetooth hardware"
            return@withContext false
        }
        
        if (bluetoothAdapter == null) {
            _error.value = "Bluetooth is not available on this device"
            return@withContext false
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            _error.value = "Bluetooth is not enabled"
            return@withContext false
        }
        
        _connectionState.value = ConnectionState.CONNECTING
        
        try {
            // Close any existing socket
            disconnect()
            
            // Refresh paired devices list
            refreshPairedDevices()
            
            // Get paired devices
            val pairedDevices = _pairedDevices.value
            
            if (pairedDevices.isEmpty()) {
                _error.value = "No paired devices found"
                _connectionState.value = ConnectionState.DISCONNECTED
                return@withContext false
            }
            
            // Log all paired devices for debugging
            pairedDevices.forEach { device ->
                Log.d(TAG, "Paired device: ${device.name ?: "Unknown"} (${device.address})")
            }
            
            // Try to connect to any paired device
            // In a real app, you'd have a way to identify the specific device
            for (device in pairedDevices) {
                Log.d(TAG, "Attempting to connect to device: ${device.name ?: "Unknown"} (${device.address})")
                
                // Try multiple connection methods with retries
                var retryCount = 0
                var connected = false
                
                while (retryCount < MAX_CONNECTION_RETRIES && !connected) {
                    // Try the standard UUID first
                    if (tryConnectToDevice(device, SPP_UUID)) {
                        connected = true
                        break
                    }
                    
                    // If that fails, try alternative UUIDs
                    for (uuid in ALTERNATIVE_UUIDS) {
                        if (tryConnectToDevice(device, uuid)) {
                            connected = true
                            break
                        }
                    }
                    
                    // If all UUIDs fail, try createInsecureRfcommSocketToServiceRecord
                    if (!connected && tryInsecureConnection(device)) {
                        connected = true
                        break
                    }
                    
                    // If still not connected, increment retry count and try again
                    if (!connected) {
                        retryCount++
                        if (retryCount < MAX_CONNECTION_RETRIES) {
                            Log.d(TAG, "Retrying connection (${retryCount}/${MAX_CONNECTION_RETRIES})")
                            delay(2000) // Wait 2 seconds before retrying
                        }
                    }
                }
                
                if (connected) {
                    return@withContext true
                }
            }
            
            // If we get here, all connection attempts failed
            _error.value = "Failed to connect to any paired device"
            _connectionState.value = ConnectionState.DISCONNECTED
            return@withContext false
            
        } catch (e: IOException) {
            Log.e(TAG, "Connection error: ${e.message}", e)
            _error.value = "Connection error: ${e.message}"
            _connectionState.value = ConnectionState.DISCONNECTED
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}", e)
            _error.value = "Unexpected error: ${e.message}"
            _connectionState.value = ConnectionState.DISCONNECTED
            return@withContext false
        }
    }
    
    private suspend fun tryConnectToDevice(device: BluetoothDevice, uuid: UUID): Boolean {
        return try {
            Log.d(TAG, "Trying to connect to ${device.name ?: "Unknown"} with UUID $uuid")
            
            // Create socket with timeout
            val socket = withTimeoutOrNull(CONNECTION_TIMEOUT) {
                try {
                    device.createRfcommSocketToServiceRecord(uuid)
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating socket: ${e.message}", e)
                    null
                }
            } ?: return false
            
            // Connect with timeout
            val connected = withTimeoutOrNull(CONNECTION_TIMEOUT) {
                try {
                    // Cancel discovery before connecting
                    bluetoothAdapter?.cancelDiscovery()
                    
                    socket.connect()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error connecting socket: ${e.message}", e)
                    try {
                        socket.close()
                    } catch (closeException: Exception) {
                        Log.e(TAG, "Error closing socket: ${closeException.message}", closeException)
                    }
                    false
                }
            } ?: false
            
            if (connected) {
                this.socket = socket
                
                // Set up input and output  {
                this.socket = socket
                
                // Set up input and output streams
                try {
                    inputStream = socket.inputStream
                    outputStream = socket.outputStream
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting streams: ${e.message}", e)
                    try {
                        socket.close()
                    } catch (closeException: Exception) {
                        Log.e(TAG, "Error closing socket: ${closeException.message}", closeException)
                    }
                    return false
                }
                
                _connectionState.value = ConnectionState.CONNECTED
                _error.value = null
                
                // Start listening for data
                startListening()
                
                // Request device list
                requestDevices()
                
                Log.d(TAG, "Successfully connected to ${device.name ?: "Unknown"}")
                return true
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error in tryConnectToDevice: ${e.message}", e)
            false
        }
    }
    
    private suspend fun tryInsecureConnection(device: BluetoothDevice): Boolean {
        return try {
            Log.d(TAG, "Trying insecure connection to ${device.name ?: "Unknown"}")
            
            // Create insecure socket with timeout
            val socket = withTimeoutOrNull(CONNECTION_TIMEOUT) {
                try {
                    device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating insecure socket: ${e.message}", e)
                    null
                }
            } ?: return false
            
            // Connect with timeout
            val connected = withTimeoutOrNull(CONNECTION_TIMEOUT) {
                try {
                    // Cancel discovery before connecting
                    bluetoothAdapter?.cancelDiscovery()
                    
                    socket.connect()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error connecting insecure socket: ${e.message}", e)
                    try {
                        socket.close()
                    } catch (closeException: Exception) {
                        Log.e(TAG, "Error closing insecure socket: ${closeException.message}", closeException)
                    }
                    false
                }
            } ?: false
            
            if (connected) {
                this.socket = socket
                
                // Set up input and output streams
                try {
                    inputStream = socket.inputStream
                    outputStream = socket.outputStream
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting streams: ${e.message}", e)
                    try {
                        socket.close()
                    } catch (closeException: Exception) {
                        Log.e(TAG, "Error closing socket: ${closeException.message}", closeException)
                    }
                    return false
                }
                
                _connectionState.value = ConnectionState.CONNECTED
                _error.value = null
                
                // Start listening for data
                startListening()
                
                // Request device list
                requestDevices()
                
                Log.d(TAG, "Successfully connected to ${device.name ?: "Unknown"} using insecure connection")
                return true
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error in tryInsecureConnection: ${e.message}", e)
            false
        }
    }
    
    private fun startListening() {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            
            try {
                while (_connectionState.value == ConnectionState.CONNECTED) {
                    try {
                        val stream = inputStream ?: throw IOException("Input stream is null")
                        
                        // Read with timeout
                        val bytesRead = withTimeoutOrNull(5000) { // 5 second timeout
                            try {
                                if (stream.available() > 0) {
                                    stream.read(buffer)
                                } else {
                                    0 // No data available
                                }
                            } catch (e: IOException) {
                                Log.e(TAG, "Error reading from stream: ${e.message}")
                                -1 // Error
                            }
                        } ?: -1 // Timeout
                        
                        if (bytesRead > 0) {
                            val data = String(buffer, 0, bytesRead)
                            Log.d(TAG, "Received data: $data")
                            
                            // Process received data
                            processReceivedData(data)
                        } else if (bytesRead == -1) {
                            // Error or end of stream
                            Log.e(TAG, "End of stream or read error")
                            break
                        }
                        
                        // Small delay to prevent tight loop
                        delay(100)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in listening loop: ${e.message}")
                        delay(1000) // Wait before retrying
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in listening thread: ${e.message}")
            } finally {
                // If we exit the loop, make sure we're disconnected
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    disconnect()
                }
            }
        }
    }
    
    private fun processReceivedData(data: String) {
        // In a real app, parse the JSON data and update the device list
        // For this example, we'll just log it
        Log.d(TAG, "Processing data: $data")
    }
    
    suspend fun requestDevices(): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            _error.value = "Bluetooth permissions not granted"
            return@withContext false
        }
        
        if (_connectionState.value != ConnectionState.CONNECTED) {
            _error.value = "Not connected to phone"
            return@withContext false
        }
        
        try {
            // Send a command to request device list
            val command = "{\"command\":\"getDevices\"}"
            sendData(command)
            
            // Simulate a delay for network request
            delay(500)
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting devices: ${e.message}", e)
            _error.value = "Error requesting devices: ${e.message}"
            return@withContext false
        }
    }
    
    private suspend fun sendData(data: String): Boolean = withContext(Dispatchers.IO) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            return@withContext false
        }
        
        try {
            val stream = outputStream ?: throw IOException("Output stream is null")
            stream.write(data.toByteArray())
            stream.flush() // Ensure data is sent immediately
            Log.d(TAG, "Sent: $data")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending data: ${e.message}")
            disconnect()
            return@withContext false
        }
    }
    
    fun disconnect() {
        try {
            // Close streams first
            try {
                inputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing input stream: ${e.message}")
            }
            
            try {
                outputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing output stream: ${e.message}")
            }
            
            // Then close socket
            try {
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing socket: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect: ${e.message}")
        } finally {
            socket = null
            inputStream = null
            outputStream = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }
    
    suspend fun sendCommand(deviceId: String, command: String): Boolean = withContext(Dispatchers.IO) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            _error.value = "Not connected to phone"
            return@withContext false
        }
        
        try {
            // Format the command as JSON
            val jsonCommand = "{\"command\":\"$command\",\"deviceId\":\"$deviceId\"}"
            return@withContext sendData(jsonCommand)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command: ${e.message}", e)
            _error.value = "Error sending command: ${e.message}"
            return@withContext false
        }
    }
    
    fun toggleDevice(deviceId: String) {
        // Update local state immediately for responsive UI
        val devices = _devices.value.toMutableList()
        val deviceIndex = devices.indexOfFirst { it.id == deviceId }
        
        if (deviceIndex != -1) {
            val device = devices[deviceIndex]
            devices[deviceIndex] = device.copy(isOn = !device.isOn)
            _devices.value = devices
            
            // Send command to phone
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                sendCommand(deviceId, "TOGGLE")
            }
        }
    }
    
    fun setDeviceValue(deviceId: String, value: Int) {
        // Update local state immediately for responsive UI
        val devices = _devices.value.toMutableList()
        val deviceIndex = devices.indexOfFirst { it.id == deviceId }
        
        if (deviceIndex != -1) {
            val device = devices[deviceIndex]
            devices[deviceIndex] = device.copy(value = value.toString())
            _devices.value = devices
            
            // Send command to phone
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                sendCommand(deviceId, "VALUE:$value")
            }
        }
    }
    
    fun executeQuickAction(actionId: String): Boolean {
        // In a real app, you'd have logic to execute the quick action
        // For this example, we'll just return true
        return true
    }
    
    enum class ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTED
    }
    
    data class BluetoothStatus(
        val isAvailable: Boolean,
        val isEnabled: Boolean,
        val pairedDevices: Int,
        val connectedDevices: Int
    )
    
    data class Device(
        val id: String,
        val name: String,
        val type: String,
        val roomId: String,
        val isOn: Boolean,
        val value: String?
    )
}
