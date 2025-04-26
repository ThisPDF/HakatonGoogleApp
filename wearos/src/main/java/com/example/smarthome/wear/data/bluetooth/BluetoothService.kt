package com.example.smarthome.wear.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.smarthome.wear.data.Device
import com.example.smarthome.wear.data.repository.DeviceRepository
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class BluetoothService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "WearBluetoothService"
    // Standard SPP UUID - must match on both phone and watch
    private val SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val gson = Gson()
    
    enum class ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTED
    }
    
    // Check if a device is already connected at the system level
    fun isDeviceConnected(device: BluetoothDevice): Boolean {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Bluetooth connect permission not granted")
            return false
        }
        
        try {
            // Check if device is connected via any profile
            val profiles = listOf(
                BluetoothProfile.HEADSET,
                BluetoothProfile.A2DP,
                BluetoothProfile.GATT,
                BluetoothProfile.GATT_SERVER
            )
            
            for (profile in profiles) {
                val proxy = getProfileProxy(profile)
                if (proxy != null) {
                    val connectedDevices = proxy.connectedDevices
                    if (connectedDevices.any { it.address == device.address }) {
                        Log.d(TAG, "Device ${device.name} is already connected via profile $profile")
                        return true
                    }
                }
            }
            
            // Also check if we already have an active connection in our app
            if (_connectionState.value == ConnectionState.CONNECTED && 
                socket?.remoteDevice?.address == device.address) {
                return true
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device connection status: ${e.message}")
            return false
        }
    }
    
    // Get a BluetoothProfile proxy
    private fun getProfileProxy(profile: Int): BluetoothProfile? {
        if (bluetoothAdapter == null) return null
        
        var proxy: BluetoothProfile? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        
        try {
            bluetoothAdapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    this@BluetoothService.proxy = proxy
                    latch.countDown()
                }
                
                override fun onServiceDisconnected(profile: Int) {
                    this@BluetoothService.proxy = null
                }
            }, profile)
            
            // Wait for the proxy with a timeout
            latch.await(1, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting profile proxy: ${e.message}")
        }
        
        return proxy
    }
    
    private var proxy: BluetoothProfile? = null
    
    // Get all connected devices
    fun getConnectedDevices(): List<BluetoothDevice> {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Bluetooth connect permission not granted")
            return emptyList()
        }
        
        val connectedDevices = mutableListOf<BluetoothDevice>()
        
        try {
            // Check all profiles for connected devices
            val profiles = listOf(
                BluetoothProfile.HEADSET,
                BluetoothProfile.A2DP,
                BluetoothProfile.GATT,
                BluetoothProfile.GATT_SERVER
            )
            
            for (profile in profiles) {
                val proxy = getProfileProxy(profile)
                if (proxy != null) {
                    val devices = proxy.connectedDevices
                    connectedDevices.addAll(devices)
                    Log.d(TAG, "Found ${devices.size} devices connected via profile $profile")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connected devices: ${e.message}")
        }
        
        return connectedDevices.distinctBy { it.address }
    }
    
    // Check Bluetooth status
    fun checkBluetoothStatus(): DeviceRepository.BluetoothStatus {
        val isAvailable = bluetoothAdapter != null
        val isEnabled = bluetoothAdapter?.isEnabled == true
        
        var pairedDeviceCount = 0
        var connectedDeviceCount = 0
        
        if (isEnabled) {
            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    pairedDeviceCount = bluetoothAdapter?.bondedDevices?.size ?: 0
                    connectedDeviceCount = getConnectedDevices().size
                    
                    // Log paired and connected devices for debugging
                    Log.d(TAG, "Paired devices: $pairedDeviceCount")
                    Log.d(TAG, "Connected devices: $connectedDeviceCount")
                    
                    bluetoothAdapter?.bondedDevices?.forEach { device ->
                        val isConnected = isDeviceConnected(device)
                        Log.d(TAG, "Device: ${device.name} (${device.address}) - Connected: $isConnected")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking Bluetooth status: ${e.message}")
            }
        }
        
        return DeviceRepository.BluetoothStatus(isAvailable, isEnabled, pairedDeviceCount, connectedDeviceCount)
    }
    
    // Improve the connectToPhone method to check for existing connections
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
                
                // Check for permissions
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    _error.value = "Bluetooth connect permission not granted"
                    Log.e(TAG, "Bluetooth connect permission not granted")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@withContext false
                }
                
                // First check if any devices are already connected at system level
                val connectedDevices = getConnectedDevices()
                Log.d(TAG, "Found ${connectedDevices.size} devices already connected at system level")
                
                // Try to use an already connected device first
                if (connectedDevices.isNotEmpty()) {
                    for (device in connectedDevices) {
                        Log.d(TAG, "Trying to use existing connection to ${device.name} (${device.address})")
                        
                        try {
                            // Close any existing connection
                            disconnect()
                            
                            // Try to create socket using the existing connection
                            socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                            socket?.connect()
                            
                            inputStream = socket?.inputStream
                            outputStream = socket?.outputStream
                            
                            if (inputStream != null && outputStream != null) {
                                Log.d(TAG, "Successfully used existing connection to ${device.name}")
                                _connectionState.value = ConnectionState.CONNECTED
                                
                                // Start listening for incoming data
                                startListening()
                                
                                // Request devices from phone
                                requestDevices()
                                
                                return@withContext true
                            } else {
                                Log.e(TAG, "Failed to get streams from existing connection")
                                disconnect()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to use existing connection: ${e.message}")
                            disconnect()
                        }
                    }
                }
                
                // If no existing connection worked, fall back to normal connection process
                // Find the phone from paired devices
                val pairedDevices = bluetoothAdapter.bondedDevices
                
                if (pairedDevices.isEmpty()) {
                    _error.value = "No paired devices found"
                    Log.e(TAG, "No paired devices found")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@withContext false
                }
                
                // Log all paired devices for debugging
                Log.d(TAG, "Paired devices:")
                pairedDevices.forEach { device ->
                    Log.d(TAG, "Device: ${device.name} (${device.address})")
                }
                
                // Try to find a phone in the paired devices - more comprehensive search
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
                    deviceName.contains("huawei") ||
                    deviceName.contains("honor") ||
                    deviceName.contains("realme") ||
                    deviceName.contains("poco") ||
                    deviceName.contains("redmi") ||
                    deviceName.contains("samsung") ||
                    deviceName.contains("motorola") ||
                    deviceName.contains("sony") ||
                    deviceName.contains("lg") ||
                    deviceName.contains("htc")
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
                val maxAttempts = 5
                
                while (connectionAttempts < maxAttempts) {
                    connectionAttempts++
                    Log.d(TAG, "Connection attempt $connectionAttempts of $maxAttempts")
                    
                    try {
                        // Try to create and connect socket
                        socket = targetDevice.createRfcommSocketToServiceRecord(SERVICE_UUID)
                        
                        // Set connection timeout
                        socket?.connect()
                        Log.d(TAG, "Connected to ${targetDevice.name}")
                        break
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
                                socket = method.invoke(targetDevice, SERVICE_UUID) as BluetoothSocket
                                socket?.connect()
                                Log.d(TAG, "Connected using alternative method")
                                break
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
                        
                        if (connectionAttempts >= maxAttempts) {
                            _error.value = "Failed to connect after $maxAttempts attempts: ${e.message}"
                            Log.e(TAG, "Failed to connect after $maxAttempts attempts")
                            _connectionState.value = ConnectionState.DISCONNECTED
                            return@withContext false
                        }
                        
                        // Wait before next attempt
                        delay(1000)
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
    
    // Improve the startListening method to handle errors better
    private fun startListening() {
        Thread {
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
                        Thread.sleep(500)
                    } catch (interruptException: InterruptedException) {
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
        }.start()
    }
    
    private fun processMessage(message: String) {
        Log.d(TAG, "Received message: $message")
        
        try {
            when {
                message.startsWith("DEVICES:") -> {
                    val json = message.substring(8)
                    try {
                        val devices = gson.fromJson(json, Array<Device>::class.java).toList()
                        _devices.value = devices
                        Log.d(TAG, "Updated devices list with ${devices.size} devices")
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
}
