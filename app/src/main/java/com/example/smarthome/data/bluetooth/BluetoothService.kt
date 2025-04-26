package com.example.smarthome.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.smarthome.data.models.Device
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
    
    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var acceptJob: Job? = null
    private var listeningJob: Job? = null
    private var serverRecoveryJob: Job? = null
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow("Not connected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()
    
    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()
    
    private val gson = Gson()
    
    private var consecutiveErrors = 0
    private val MAX_CONSECUTIVE_ERRORS = 5
    
    private var dataCallback: ((String) -> Unit)? = null
    
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
        
        // Check if any device is already connected at system level
        checkForExistingConnections()
        
        // Cancel any existing accept job
        acceptJob?.cancel()
        
        acceptJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                _connectionStatus.value = "Waiting for connection..."
                
                // Try to create a secure server socket
                try {
                    serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("SmartHomeServer", SPP_UUID)
                } catch (e: IOException) {
                    Log.e(TAG, "Secure server socket failed, trying insecure: ${e.message}")
                    // Fall back to insecure connection if secure fails
                    serverSocket = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord("SmartHomeServer", SPP_UUID)
                }
                
                if (serverSocket == null) {
                    _connectionStatus.value = "Failed to create server socket"
                    return@launch
                }
                
                var shouldContinue = true
                while (shouldContinue) {
                    try {
                        socket = serverSocket?.accept()
                        
                        if (socket != null) {
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
                                consecutiveErrors = 0
                                startListening()
                                shouldContinue = false
                            } else {
                                throw IOException("Failed to get input/output streams")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error accepting connection: ${e.message}")
                        _connectionStatus.value = "Connection error: ${e.message}"
                        socket?.close()
                        socket = null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
                _connectionStatus.value = "Server error: ${e.message}"
            } finally {
                if (!_isConnected.value) {
                    // Only schedule recovery if we didn't successfully connect
                    scheduleServerRecovery()
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun checkForExistingConnections() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectedDevices = bluetoothAdapter?.bondedDevices?.filter { device ->
                    try {
                        val method = device.javaClass.getMethod("isConnected")
                        method.invoke(device) as Boolean
                    } catch (e: Exception) {
                        false
                    }
                } ?: emptyList()
                
                if (connectedDevices.isNotEmpty()) {
                    Log.d(TAG, "Found ${connectedDevices.size} already connected devices")
                    
                    // Try to use the first connected device
                    val device = connectedDevices.first()
                    Log.d(TAG, "Using existing connection to ${device.name}")
                    
                    // Try to establish communication with the already connected device
                    try {
                        // Use reflection to get the existing connection
                        val socketField = device.javaClass.getDeclaredField("mSocket")
                        socketField.isAccessible = true
                        socket = socketField.get(device) as? BluetoothSocket
                        
                        if (socket != null && socket?.isConnected == true) {
                            _connectedDeviceName.value = device.name
                            _connectionStatus.value = "Using existing connection to ${device.name}"
                            
                            // Set up communication
                            inputStream = socket?.inputStream
                            outputStream = socket?.outputStream
                            
                            if (inputStream != null && outputStream != null) {
                                _isConnected.value = true
                                consecutiveErrors = 0
                                startListening()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to use existing connection: ${e.message}")
                        // Continue with normal server startup
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for existing connections: ${e.message}")
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
                        
                        bytes = stream.read(buffer)
                        if (bytes > 0) {
                            val data = String(buffer, 0, bytes)
                            Log.d(TAG, "Received: $data")
                            
                            // Process the received data
                            dataCallback?.invoke(data)
                            
                            // Reset consecutive errors on successful read
                            consecutiveErrors = 0
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
    
    fun sendDeviceList(devices: List<Device>) {
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
                inputStream?.close()
                outputStream?.close()
                socket?.close()
                serverSocket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error during disconnect: ${e.message}")
            } finally {
                socket = null
                serverSocket = null
                inputStream = null
                outputStream = null
                _isConnected.value = false
                _connectedDeviceName.value = null
                _connectionStatus.value = "Disconnected"
            }
        }
    }
    
    fun stopServer() {
        acceptJob?.cancel()
        serverRecoveryJob?.cancel()
        disconnect()
    }
}
