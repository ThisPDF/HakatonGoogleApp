package com.example.smarthome.wear.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.smarthome.wear.data.repository.DeviceRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import java.lang.reflect.Method
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepository: DeviceRepository
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
    private var connectionJob: Job? = null
    private var listeningJob: Job? = null
    private var reconnectJob: Job? = null
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val gson = Gson()
    private val deviceListType = object : TypeToken<List<DeviceRepository.Device>>() {}.type
    
    private var consecutiveErrors = 0
    private val MAX_CONSECUTIVE_ERRORS = 5
    private var connectionAttempts = 0
    private val MAX_CONNECTION_ATTEMPTS = 3
    
    init {
        updateBluetoothStatus()
    }
    
    @SuppressLint("MissingPermission")
    fun updateBluetoothStatus() {
        val isAvailable = bluetoothAdapter != null
        val isEnabled = bluetoothAdapter?.isEnabled == true
        val pairedDevices = bluetoothAdapter?.bondedDevices?.size ?: 0
        
        // Check for already connected devices
        val connectedDevices = if (isEnabled) {
            bluetoothAdapter?.bondedDevices?.count { device ->
                try {
                    val method = device.javaClass.getMethod("isConnected")
                    method.invoke(device) as Boolean
                } catch (e: Exception) {
                    false
                }
            } ?: 0
        } else {
            0
        }
        
        deviceRepository.updateBluetoothStatus(
            DeviceRepository.BluetoothStatus(
                isAvailable = isAvailable,
                isEnabled = isEnabled,
                pairedDevices = pairedDevices,
                connectedDevices = connectedDevices
            )
        )
        
        // If we have connected devices but our app doesn't show connected,
        // try to use the existing connection
        if (connectedDevices > 0 && !_isConnected.value) {
            Log.d(TAG, "Found $connectedDevices already connected devices, attempting to use existing connection")
            tryUseExistingConnection()
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun tryUseExistingConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                try {
                    val method = device.javaClass.getMethod("isConnected")
                    val isConnected = method.invoke(device) as Boolean
                    
                    if (isConnected) {
                        Log.d(TAG, "Found already connected device: ${device.name}")
                        // Try to use this connection
                        connectToDevice(device)
                        return@forEach
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking if device is connected: ${e.message}")
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    fun connectToPhone() {
        if (bluetoothAdapter == null) {
            deviceRepository.updateConnectionState(
                DeviceRepository.ConnectionState.Error("Bluetooth is not available on this device")
            )
            return
        }
        
        if (bluetoothAdapter?.isEnabled == false) {
            deviceRepository.updateConnectionState(
                DeviceRepository.ConnectionState.Error("Bluetooth is not enabled")
            )
            return
        }
        
        // Check if we're already connected
        if (_isConnected.value) {
            Log.d(TAG, "Already connected, no need to reconnect")
            return
        }
        
        // Reset connection attempts
        connectionAttempts = 0
        
        // Cancel any existing connection job
        connectionJob?.cancel()
        
        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            deviceRepository.updateConnectionState(DeviceRepository.ConnectionState.Connecting)
            
            // First check if any device is already connected at system level
            val alreadyConnectedDevice = findAlreadyConnectedPhone()
            if (alreadyConnectedDevice != null) {
                Log.d(TAG, "Found already connected phone: ${alreadyConnectedDevice.name}")
                connectToDevice(alreadyConnectedDevice)
                return@launch
            }
            
            // If no already connected device, try to find and connect to the phone
            val phoneDevice = findPhone()
            if (phoneDevice != null) {
                connectToDevice(phoneDevice)
            } else {
                deviceRepository.updateConnectionState(
                    DeviceRepository.ConnectionState.Error("Could not find paired phone")
                )
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun findAlreadyConnectedPhone(): BluetoothDevice? {
        return withContext(Dispatchers.IO) {
            try {
                bluetoothAdapter?.bondedDevices?.find { device ->
                    try {
                        val method = device.javaClass.getMethod("isConnected")
                        val isConnected = method.invoke(device) as Boolean
                        val isPhone = isPhoneDevice(device)
                        isConnected && isPhone
                    } catch (e: Exception) {
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finding already connected phone: ${e.message}")
                null
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun findPhone(): BluetoothDevice? {
        return withContext(Dispatchers.IO) {
            try {
                // First try to find a device that matches known phone patterns
                var device = bluetoothAdapter?.bondedDevices?.find { isPhoneDevice(it) }
                
                // If no matching device found, fall back to the first paired device
                if (device == null && bluetoothAdapter?.bondedDevices?.isNotEmpty() == true) {
                    device = bluetoothAdapter?.bondedDevices?.first()
                    Log.d(TAG, "No phone found, falling back to first paired device: ${device?.name}")
                }
                
                device
            } catch (e: Exception) {
                Log.e(TAG, "Error finding phone: ${e.message}")
                null
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun isPhoneDevice(device: BluetoothDevice): Boolean {
        val name = device.name?.lowercase() ?: return false
        val phonePatterns = listOf(
            "phone", "smartphone", "pixel", "galaxy", "iphone", 
            "android", "mobile", "oneplus", "xiaomi", "redmi", 
            "poco", "oppo", "vivo", "huawei", "honor", "realme"
        )
        
        return phonePatterns.any { pattern -> name.contains(pattern) }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun connectToDevice(device: BluetoothDevice) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to connect to ${device.name}")
                
                // Try multiple connection attempts with delays
                while (connectionAttempts < MAX_CONNECTION_ATTEMPTS) {
                    connectionAttempts++
                    
                    try {
                        // Try standard connection method first
                        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                        
                        // Set connection timeout
                        withContext(Dispatchers.IO) {
                            try {
                                socket?.connect()
                                break // Connection successful, exit the loop
                            } catch (e: IOException) {
                                Log.e(TAG, "Standard connection failed, trying fallback: ${e.message}")
                                socket?.close()
                                
                                // Try fallback method using reflection
                                try {
                                    val method: Method = device.javaClass.getMethod(
                                        "createRfcommSocket", Int::class.javaPrimitiveType
                                    )
                                    socket = method.invoke(device, 1) as BluetoothSocket
                                    socket?.connect()
                                    break // Connection successful, exit the loop
                                } catch (e2: Exception) {
                                    Log.e(TAG, "Fallback connection failed: ${e2.message}")
                                    socket?.close()
                                    throw e2
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Connection attempt $connectionAttempts failed: ${e.message}")
                        socket?.close()
                        
                        if (connectionAttempts < MAX_CONNECTION_ATTEMPTS) {
                            val delayTime = 1000L * connectionAttempts
                            Log.d(TAG, "Retrying in ${delayTime}ms...")
                            delay(delayTime)
                        } else {
                            throw e
                        }
                    }
                }
                
                // If we got here without an exception, we're connected
                inputStream = socket?.inputStream
                outputStream = socket?.outputStream
                
                if (inputStream != null && outputStream != null) {
                    _isConnected.value = true
                    deviceRepository.updateConnectionState(DeviceRepository.ConnectionState.Connected)
                    consecutiveErrors = 0
                    startListening()
                    
                    // Request initial device list
                    sendData("{\"type\":\"GET_DEVICES\"}")
                } else {
                    throw IOException("Failed to get input/output streams")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect after $MAX_CONNECTION_ATTEMPTS attempts: ${e.message}")
                socket?.close()
                socket = null
                inputStream = null
                outputStream = null
                _isConnected.value = false
                deviceRepository.updateConnectionState(
                    DeviceRepository.ConnectionState.Error("Failed to connect: ${e.message}")
                )
            }
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
                            processReceivedData(data)
                            
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
    
    private fun processReceivedData(data: String) {
        try {
            if (data.contains("\"devices\":")) {
                // This is a device list response
                val startIndex = data.indexOf("[")
                val endIndex = data.lastIndexOf("]") + 1
                
                if (startIndex >= 0 && endIndex > startIndex) {
                    val devicesJson = data.substring(startIndex, endIndex)
                    val devices = gson.fromJson<List<DeviceRepository.Device>>(devicesJson, deviceListType)
                    deviceRepository.updateDevices(devices)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing received data: ${e.message}")
        }
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
                
                // Try to reconnect
                scheduleReconnect()
            }
        }
    }
    
    private fun scheduleReconnect() {
        // Cancel any existing reconnect job
        reconnectJob?.cancel()
        
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            delay(5000) // Wait 5 seconds before reconnecting
            Log.d(TAG, "Attempting to reconnect...")
            connectToPhone()
        }
    }
    
    fun disconnect() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                listeningJob?.cancel()
                inputStream?.close()
                outputStream?.close()
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error during disconnect: ${e.message}")
            } finally {
                socket = null
                inputStream = null
                outputStream = null
                _isConnected.value = false
                deviceRepository.updateConnectionState(DeviceRepository.ConnectionState.Disconnected)
            }
        }
    }
}
