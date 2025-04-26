package com.example.smarthome.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.smarthome.data.Device
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "BluetoothService"
    
    // Standard UUID for SPP (Serial Port Profile)
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    // Alternative UUIDs to try if the standard one fails
    private val ALTERNATIVE_UUIDS = listOf(
        UUID.fromString("0000110a-0000-1000-8000-00805f9b34fb"), // Health Device Profile
        UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb"), // Health Device Profile
        UUID.fromString("00001112-0000-1000-8000-00805f9b34fb"), // Headset Profile
        UUID.fromString("00001115-0000-1000-8000-00805f9b34fb"), // Personal Area Networking
        UUID.fromString("00001116-0000-1000-8000-00805f9b34fb")  // Network Access Point
    )
    
    private val bluetoothManager: BluetoothManager? by lazy {
        try {
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        } catch (e: Exception) {
            Log.e(TAG, "Error getting BluetoothManager: ${e.message}", e)
            null
        }
    }
    
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        try {
            bluetoothManager?.adapter
        } catch (e: Exception) {
            Log.e(TAG, "Error getting BluetoothAdapter: ${e.message}", e)
            null
        }
    }
    
    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var acceptJob: Job? = null
    private var listeningJob: Job? = null
    private var serverRecoveryJob: Job? = null
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow("Not connected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()
    
    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()
    
    private val gson = Gson()
    
    private var consecutiveErrors = 0
    private val MAX_CONSECUTIVE_ERRORS = 5
    private val MAX_ACCEPT_RETRIES = 3
    
    private var dataCallback: ((String) -> Unit)? = null
    private val isServerRunning = AtomicBoolean(false)
    
    fun setDataCallback(callback: (String) -> Unit) {
        dataCallback = callback
    }
    
    @SuppressLint("MissingPermission")
    fun startServer() {
        if (bluetoothAdapter == null) {
            _connectionStatus.value = "Bluetooth is not available on this device"
            return
        }
        
        if (bluetoothAdapter?.isEnabled == false) {
            _connectionStatus.value = "Bluetooth is not enabled"
            return
        }
        
        // Check if we're already connected
        if (_isConnected.value) {
            Log.d(TAG, "Already connected, no need to restart server")
            return
        }
        
        // Check if server is already running
        if (isServerRunning.getAndSet(true)) {
            Log.d(TAG, "Server is already running")
            return
        }
        
        // Cancel any existing accept job
        acceptJob?.cancel()
        
        acceptJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                _connectionStatus.value = "Waiting for connection..."
                _connectionState.value = ConnectionState.CONNECTING
                
                var retryCount = 0
                var serverStarted = false
                
                while (retryCount < MAX_ACCEPT_RETRIES && !serverStarted) {
                    try {
                        // Try to create a secure server socket
                        serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("SmartHomeServer", SPP_UUID)
                        serverStarted = true
                    } catch (e: IOException) {
                        Log.e(TAG, "Secure server socket failed, trying insecure: ${e.message}")
                        try {
                            // Fall back to insecure connection if secure fails
                            serverSocket = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord("SmartHomeServer", SPP_UUID)
                            serverStarted = true
                        } catch (e2: IOException) {
                            Log.e(TAG, "Insecure server socket also failed: ${e2.message}")
                            retryCount++
                            if (retryCount < MAX_ACCEPT_RETRIES) {
                                Log.d(TAG, "Retrying server socket creation (${retryCount}/${MAX_ACCEPT_RETRIES})")
                                delay(1000) // Wait before retrying
                            }
                        }
                    }
                }
                
                if (serverSocket == null) {
                    _connectionStatus.value = "Failed to create server socket after $MAX_ACCEPT_RETRIES attempts"
                    _connectionState.value = ConnectionState.DISCONNECTED
                    isServerRunning.set(false)
                    return@launch
                }
                
                var acceptRetryCount = 0
                var connectionAccepted = false
                
                while (acceptRetryCount < MAX_ACCEPT_RETRIES && !connectionAccepted) {
                    try {
                        Log.d(TAG, "Waiting for connection... (attempt ${acceptRetryCount + 1}/${MAX_ACCEPT_RETRIES})")
                        
                        // Set a timeout for the accept operation
                        val acceptedSocket = withTimeoutOrNull(30000) { // 30 seconds timeout
                            serverSocket?.accept()
                        }
                        
                        if (acceptedSocket != null) {
                            socket = acceptedSocket
                            connectionAccepted = true
                            
                            // Connection accepted
                            _connectedDeviceName.value = socket?.remoteDevice?.name ?: "Unknown Device"
                            _connectionStatus.value = "Connected to ${_connectedDeviceName.value}"
                            
                            // Close the server socket as we only need one connection
                            serverSocket?.close()
                            serverSocket = null
                            
                            // Set up communication
                            inputStream = socket?.inputStream
                            outputStream = socket?.outputStream
                            
                            if (inputStream != null && outputStream != null) {
                                _isConnected.value = true
                                _connectionState.value = ConnectionState.CONNECTED
                                consecutiveErrors = 0
                                startListening()
                            } else {
                                throw IOException("Failed to get input/output streams")
                            }
                        } else {
                            // Accept timed out
                            Log.e(TAG, "Accept operation timed out")
                            acceptRetryCount++
                            
                            if (acceptRetryCount < MAX_ACCEPT_RETRIES) {
                                // Try closing and reopening the server socket
                                serverSocket?.close()
                                delay(1000) // Wait before retrying
                                
                                // Try to create a new server socket
                                try {
                                    serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("SmartHomeServer", SPP_UUID)
                                } catch (e: IOException) {
                                    Log.e(TAG, "Failed to recreate server socket: ${e.message}")
                                    try {
                                        serverSocket = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord("SmartHomeServer", SPP_UUID)
                                    } catch (e2: IOException) {
                                        Log.e(TAG, "Failed to recreate insecure server socket: ${e2.message}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error accepting connection: ${e.message}")
                        _connectionStatus.value = "Connection error: ${e.message}"
                        
                        acceptRetryCount++
                        if (acceptRetryCount < MAX_ACCEPT_RETRIES) {
                            Log.d(TAG, "Retrying accept (${acceptRetryCount}/${MAX_ACCEPT_RETRIES})")
                            delay(2000) // Wait before retrying
                        }
                        
                        try {
                            socket?.close()
                        } catch (closeEx: Exception) {
                            Log.e(TAG, "Error closing socket: ${closeEx.message}")
                        }
                        socket = null
                    }
                }
                
                if (!connectionAccepted) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _connectionStatus.value = "Failed to accept connection after $MAX_ACCEPT_RETRIES attempts"
                    
                    // Clean up resources
                    try {
                        serverSocket?.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing server socket: ${e.message}")
                    }
                    serverSocket = null
                    
                    // Schedule recovery
                    scheduleServerRecovery()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
                _connectionStatus.value = "Server error: ${e.message}"
                _connectionState.value = ConnectionState.DISCONNECTED
                
                // Schedule recovery
                scheduleServerRecovery()
            } finally {
                isServerRunning.set(false)
            }
        }
    }
    
    private fun scheduleServerRecovery() {
        // Cancel any existing recovery job
        serverRecoveryJob?.cancel()
        
        serverRecoveryJob = CoroutineScope(Dispatchers.IO).launch {
            delay(5000) // Wait 5 seconds before restarting server
            Log.d(TAG, "Attempting to restart server...")
            startServer()
        }
    }
    
    private fun startListening() {
        // Cancel any existing listening job
        listeningJob?.cancel()
        
        listeningJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(1024)
            var bytes: Int
            
            try {
                while (_isConnected.value) {
                    try {
                        // Check if inputStream is null
                        val stream = inputStream ?: throw IOException("Input stream is null")
                        
                        // Set a read timeout to avoid blocking indefinitely
                        val readResult = withTimeoutOrNull(5000) { // 5 second timeout for read
                            try {
                                stream.read(buffer)
                            } catch (e: IOException) {
                                Log.e(TAG, "Read error: ${e.message}")
                                -1 // Return -1 on error, same as stream.read() would
                            }
                        }
                        
                        bytes = readResult ?: -1 // If timeout, treat as -1 (error)
                        
                        if (bytes > 0) {
                            val data = String(buffer, 0, bytes)
                            Log.d(TAG, "Received: $data")
                            
                            // Process the received data
                            dataCallback?.invoke(data)
                            
                            // Reset consecutive errors on successful read
                            consecutiveErrors = 0
                        } else if (bytes == -1) {
                            // End of stream or error
                            Log.e(TAG, "End of stream or read error")
                            consecutiveErrors++
                            
                            if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                                Log.e(TAG, "Too many consecutive errors, disconnecting")
                                break
                            }
                            
                            // Short delay before retrying
                            delay(1000)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error reading from input stream: ${e.message}")
                        consecutiveErrors++
                        
                        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                            Log.e(TAG, "Too many consecutive errors, disconnecting")
                            break
                        }
                        
                        // Short delay before retrying
                        delay(1000)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Listening thread error: ${e.message}")
            } finally {
                disconnect()
            }
        }
    }
    
    fun sendDevices(devices: List<Device>) {
        val message = "{\"devices\": ${gson.toJson(devices)}}"
        sendData(message)
    }
    
    fun sendData(data: String) {
        if (!_isConnected.value) {
            Log.e(TAG, "Cannot send data: not connected")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val stream = outputStream ?: throw IOException("Output stream is null")
                stream.write(data.toByteArray())
                stream.flush() // Ensure data is sent immediately
                Log.d(TAG, "Sent: $data")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending data: ${e.message}")
                disconnect()
                
                // Try to restart the server
                startServer()
            }
        }
    }
    
    fun disconnect() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                listeningJob?.cancel()
                
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
                
                // Then close sockets
                try {
                    socket?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing socket: ${e.message}")
                }
                
                try {
                    serverSocket?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing server socket: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during disconnect: ${e.message}")
            } finally {
                socket = null
                serverSocket = null
                inputStream = null
                outputStream = null
                _isConnected.value = false
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectedDeviceName.value = null
                _connectionStatus.value = "Disconnected"
                isServerRunning.set(false)
            }
        }
    }
    
    fun stopServer() {
        acceptJob?.cancel()
        serverRecoveryJob?.cancel()
        disconnect()
    }
    
    enum class ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTED
    }
}
