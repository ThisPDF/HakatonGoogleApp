package com.example.smarthome.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
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

@Singleton
class BluetoothService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "BluetoothService"
    private val SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SPP UUID
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val gson = Gson()
    
    enum class ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTED
    }
    
    suspend fun connectToDevice(deviceAddress: String): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not available or not enabled")
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                
                val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
                socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket?.connect()
                
                inputStream = socket?.inputStream
                outputStream = socket?.outputStream
                
                _connectionState.value = ConnectionState.CONNECTED
                
                // Start listening for incoming data
                startListening()
                
                true
            } catch (e: IOException) {
                Log.e(TAG, "Failed to connect: ${e.message}")
                disconnect()
                false
            }
        }
    }
    
    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing connection: ${e.message}")
        } finally {
            inputStream = null
            outputStream = null
            socket = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
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
                outputStream?.write(message.toByteArray())
                true
            } catch (e: IOException) {
                Log.e(TAG, "Error sending devices: ${e.message}")
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
                outputStream?.write(message.toByteArray())
                true
            } catch (e: IOException) {
                Log.e(TAG, "Error sending command: ${e.message}")
                disconnect()
                false
            }
        }
    }
    
    private fun startListening() {
        Thread {
            val buffer = ByteArray(1024)
            var bytes: Int
            
            while (_connectionState.value == ConnectionState.CONNECTED) {
                try {
                    bytes = inputStream?.read(buffer) ?: -1
                    
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes)
                        processMessage(message)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Disconnected: ${e.message}")
                    break
                }
            }
            
            disconnect()
        }.start()
    }
    
    private fun processMessage(message: String) {
        Log.d(TAG, "Received message: $message")
        
        when {
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
    }
    
    fun getPairedDevices(): List<BluetoothDevice> {
        return if (bluetoothAdapter?.isEnabled == true) {
            bluetoothAdapter.bondedDevices.toList()
        } else {
            emptyList()
        }
    }
}
