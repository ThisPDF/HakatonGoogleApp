package com.example.smarthome.wear.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.smarthome.wear.data.Device
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
    private val TAG = "WearBluetoothService"
    private val SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SPP UUID
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    private val gson = Gson()
    
    enum class ConnectionState {
        CONNECTED, CONNECTING, DISCONNECTED
    }
    
    suspend fun connectToPhone(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not available or not enabled")
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                
                // Find the phone from paired devices
                val pairedDevices = bluetoothAdapter.bondedDevices
                val phoneDevice = pairedDevices.find { it.name.contains("Phone", ignoreCase = true) }
                    ?: return@withContext false
                
                socket = phoneDevice.createRfcommSocketToServiceRecord(SERVICE_UUID)
                socket?.connect()
                
                inputStream = socket?.inputStream
                outputStream = socket?.outputStream
                
                _connectionState.value = ConnectionState.CONNECTED
                
                // Start listening for incoming data
                startListening()
                
                // Request devices from phone
                requestDevices()
                
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
    
    suspend fun requestDevices(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    Log.e(TAG, "Not connected to phone")
                    return@withContext false
                }
                
                val message = "REQUEST:DEVICES"
                outputStream?.write(message.toByteArray())
                true
            } catch (e: IOException) {
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
                    _devices.value = devices
                    Log.d(TAG, "Updated devices list with ${devices.size} devices")
                } catch (e: Exception) {
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
