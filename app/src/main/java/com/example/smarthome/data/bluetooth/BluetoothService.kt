package com.example.smarthome.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.smarthome.data.Device
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
// Add import for delay
import kotlinx.coroutines.delay

@Singleton
class BluetoothService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "BluetoothService"
    // Standard SPP UUID - must match on both phone and watch
    private val SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val SERVICE_NAME = "SmartHomeBluetoothService"
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    
    private var socket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var acceptThread: AcceptThread? = null
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()
    
    private val gson = Gson()
    
    enum class ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTED
    }
    
    init {
        // Start listening for incoming connections when service is created
        startAcceptingConnections()
    }
    
    fun startAcceptingConnections() {
        if (acceptThread == null || !acceptThread!!.isAlive) {
            acceptThread = AcceptThread()
            acceptThread?.start()
        }
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
                _connectedDevice.value?.address == device.address) {
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
    
    // Update the connectToDevice method to check for existing connections
    suspend fun connectToDevice(deviceAddress: String): Boolean {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth is not available")
            return false
        }
        
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                
                // Check for permissions
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "Bluetooth connect permission not granted")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@withContext false
                }
                
                val device = try {
                    bluetoothAdapter.getRemoteDevice(deviceAddress)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Invalid Bluetooth address: $deviceAddress")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@withContext false
                }
                
                // Check if device is already connected at system level
                val isAlreadyConnected = isDeviceConnected(device)
                Log.d(TAG, "Device ${device.name} is already connected: $isAlreadyConnected")
                
                // If already connected at system level, try to create socket directly
                if (isAlreadyConnected) {
                    try {
                        Log.d(TAG, "Using existing system connection for ${device.name}")
                        socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                        socket?.connect()
                        
                        inputStream = socket?.inputStream
                        outputStream = socket?.outputStream
                        
                        if (inputStream == null || outputStream == null) {
                            throw IOException("Failed to get valid streams")
                        }
                        
                        _connectionState.value = ConnectionState.CONNECTED
                        _connectedDevice.value = device
                        
                        // Start listening for incoming data
                        startListening()
                        
                        return@withContext true
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to use existing connection: ${e.message}")
                        // Fall back to normal connection process
                    }
                }
                
                // Close any existing connection
                disconnect()
                
                // Try multiple times to establish connection
                var connectionAttempts = 0
                val maxAttempts = 3
                
                while (connectionAttempts < maxAttempts) {
                    connectionAttempts++
                    Log.d(TAG, "Connection attempt $connectionAttempts of $maxAttempts")
                    
                    try {
                        Log.d(TAG, "Attempting to connect to ${device.name} (${device.address})")
                        socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                        
                        // Set socket timeout
                        socket?.connect()
                        Log.d(TAG, "Connected to ${device.name}")
                        break
                    } catch (e: IOException) {
                        Log.e(TAG, "Connection attempt $connectionAttempts failed: ${e.message}")
                        
                        try {
                            socket?.close()
                        } catch (closeException: IOException) {
                            Log.e(TAG, "Could not close the client socket", closeException)
                        }
                        
                        socket = null
                        
                        if (connectionAttempts >= maxAttempts) {
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
                    Log.e(TAG, "Failed to get streams: ${e.message}")
                    disconnect()
                    return@withContext false
                }
                
                _connectionState.value = ConnectionState.CONNECTED
                _connectedDevice.value = device
                
                // Start listening for incoming data
                startListening()
                
                true
            } catch (e: Exception) {
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
        _connectedDevice.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        
        // Restart accepting connections
        startAcceptingConnections()
    }
    
    suspend fun sendDevices(devices: List<Device>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    Log.e(TAG, "Not connected to a device")
                    return@withContext false
                }
                
                val json = gson.toJson(devices)
                val message = "DEVICES:$json"
                
                try {
                    Log.d(TAG, "Sending devices to watch")
                    outputStream?.write(message.toByteArray())
                    true
                } catch (e: IOException) {
                    Log.e(TAG, "Error sending devices: ${e.message}")
                    disconnect()
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error sending devices: ${e.message}")
                disconnect()
                false
            }
        }
    }
    
    suspend fun sendCommand(deviceId: String, command: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    Log.e(TAG, "Not connected to a device")
                    return@withContext false
                }
                
                val message = "COMMAND:$deviceId:$command"
                
                try {
                    outputStream?.write(message.toByteArray())
                    true
                } catch (e: IOException) {
                    Log.e(TAG, "Error sending command: ${e.message}")
                    disconnect()
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error sending command: ${e.message}")
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
                message.startsWith("REQUEST:DEVICES") -> {
                    // Watch is requesting device list
                    Log.d(TAG, "Watch requested device list")
                    // This will be handled by the repository
                }
                message.startsWith("DEVICES:") -> {
                    val json = message.substring(8)
                    try {
                        val devices = gson.fromJson(json, Array<Device>::class.java).toList()
                        // Handle received devices
                        Log.d(TAG, "Received ${devices.size} devices")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing devices: ${e.message}")
                    }
                }
                message.startsWith("COMMAND:") -> {
                    val parts = message.split(":")
                    if (parts.size >= 3) {
                        val deviceId = parts[1]
                        val command = parts[2]
                        // Handle received command
                        Log.d(TAG, "Received command for device $deviceId: $command")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message: ${e.message}")
        }
    }
    
    fun getPairedDevices(): List<BluetoothDevice> {
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth adapter is null")
            return emptyList()
        }
        
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            return emptyList()
        }
        
        return try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Bluetooth connect permission not granted")
                return emptyList()
            }
            
            bluetoothAdapter.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired devices: ${e.message}")
            emptyList()
        }
    }
    
    // Check Bluetooth status
    fun checkBluetoothStatus(): BluetoothStatus {
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
        
        return BluetoothStatus(isAvailable, isEnabled, pairedDeviceCount, connectedDeviceCount)
    }
    
    data class BluetoothStatus(
        val isAvailable: Boolean,
        val isEnabled: Boolean,
        val pairedDevices: Int,
        val connectedDevices: Int
    )
    
    // Improve the AcceptThread to be more robust
    private inner class AcceptThread : Thread() {
        init {
            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Try to create server socket with different strategies
                    try {
                        serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                            SERVICE_NAME,
                            SERVICE_UUID
                        )
                        Log.d(TAG, "Server socket created, listening for connections")
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to create server socket with RfcommWithServiceRecord, trying alternative", e)
                        try {
                            // Try alternative method if available
                            serverSocket = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(
                                SERVICE_NAME,
                                SERVICE_UUID
                            )
                            Log.d(TAG, "Server socket created with insecure method")
                        } catch (e2: IOException) {
                            Log.e(TAG, "Failed to create server socket with alternative method", e2)
                            serverSocket = null
                        }
                    }
                } else {
                    Log.e(TAG, "Bluetooth connect permission not granted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error creating server socket", e)
                serverSocket = null
            }
        }
        
        override fun run() {
            var shouldLoop = true
            var consecutiveFailures = 0
            val maxConsecutiveFailures = 3
            
            while (shouldLoop) {
                try {
                    if (serverSocket == null) {
                        Log.e(TAG, "Server socket is null, stopping accept thread")
                        break
                    }
                    
                    Log.d(TAG, "Waiting for connection...")
                    val socket = serverSocket?.accept()
                    
                    // Reset failure counter on successful accept
                    consecutiveFailures = 0
                    
                    socket?.let {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val device = it.remoteDevice
                            Log.d(TAG, "Connection accepted from ${device.name} (${device.address})")
                            
                            // If we're already connected, close the new connection
                            if (_connectionState.value == ConnectionState.CONNECTED) {
                                Log.d(TAG, "Already connected, closing new connection")
                                try {
                                    it.close()
                                } catch (e: IOException) {
                                    Log.e(TAG, "Could not close unwanted socket", e)
                                }
                            } else {
                                // We're not connected, so accept this connection
                                synchronized(this@BluetoothService) {
                                    // Close any existing connection
                                    disconnect()
                                    
                                    // Set up the new connection
                                    this@BluetoothService.socket = it
                                    this@BluetoothService.inputStream = it.inputStream
                                    this@BluetoothService.outputStream = it.outputStream
                                    this@BluetoothService._connectedDevice.value = device
                                    this@BluetoothService._connectionState.value = ConnectionState.CONNECTED
                                    
                                    // Start listening for messages
                                    startListening()
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    consecutiveFailures++
                    Log.e(TAG, "Socket accept() failed ($consecutiveFailures/$maxConsecutiveFailures): ${e.message}")
                    
                    if (consecutiveFailures >= maxConsecutiveFailures) {
                        Log.e(TAG, "Too many consecutive failures, restarting server socket")
                        try {
                            serverSocket?.close()
                        } catch (closeException: IOException) {
                            Log.e(TAG, "Could not close the server socket", closeException)
                        }
                        
                        // Try to recreate the server socket
                        try {
                            if (ActivityCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                                    SERVICE_NAME,
                                    SERVICE_UUID
                                )
                                Log.d(TAG, "Server socket recreated")
                                consecutiveFailures = 0
                            }
                        } catch (recreateException: IOException) {
                            Log.e(TAG, "Failed to recreate server socket", recreateException)
                            shouldLoop = false
                        }
                    }
                    
                    // Add a small delay to avoid tight loop
                    try {
                        sleep(1000)
                    } catch (interruptException: InterruptedException) {
                        Log.e(TAG, "Accept thread sleep interrupted", interruptException)
                    }
                }
            }
            
            Log.d(TAG, "AcceptThread ending")
        }
        
        fun cancel() {
            try {
                serverSocket?.close()
                serverSocket = null
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the server socket", e)
            }
        }
    }
    
    fun cleanup() {
        disconnect()
        acceptThread?.cancel()
        acceptThread = null
    }
}
