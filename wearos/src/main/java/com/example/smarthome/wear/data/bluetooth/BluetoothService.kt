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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
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
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    init {
        // Initialize with some mock devices for testing
        _devices.value = listOf(
            Device("1", "Living Room Light", "LIGHT", "living_room", true, null),
            Device("2", "Bedroom Light", "LIGHT", "bedroom", false, null),
            Device("3", "Kitchen Light", "LIGHT", "kitchen", false, null),
            Device("4", "Thermostat", "THERMOSTAT", "living_room", true, "72")
        )
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
    
    fun checkBluetoothStatus(): BluetoothStatus {
        // First check if the device has Bluetooth hardware
        val packageManager = context.packageManager
        val hasBluetooth = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        
        if (!hasBluetooth) {
            return BluetoothStatus(
                isAvailable = false,
                isEnabled = false,
                pairedDevices = 0,
                connectedDevices = 0
            )
        }
        
        if (!hasBluetoothPermissions()) {
            return BluetoothStatus(
                isAvailable = true,
                isEnabled = false,
                pairedDevices = 0,
                connectedDevices = 0
            )
        }
        
        val isAvailable = bluetoothAdapter != null
        val isEnabled = isAvailable && (bluetoothAdapter?.isEnabled == true)
        
        val pairedDevices = if (isEnabled) {
            try {
                bluetoothAdapter?.bondedDevices?.size ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "Error getting paired devices: ${e.message}", e)
                0
            }
        } else 0
        
        val connectedDevices = if (isEnabled) {
            try {
                bluetoothAdapter?.bondedDevices?.count { device ->
                    device.bondState == BluetoothDevice.BOND_BONDED
                } ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connected devices: ${e.message}", e)
                0
            }
        } else 0
        
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
            // Get paired devices
            val pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
            
            if (pairedDevices.isEmpty()) {
                _error.value = "No paired devices found"
                _connectionState.value = ConnectionState.DISCONNECTED
                return@withContext false
            }
            
            // Find the phone device - in a real app, you'd have a way to identify the specific device
            val phoneDevice = pairedDevices.firstOrNull { device ->
                // This is a simplified example - in a real app, you'd have a better way to identify the phone
                device.name?.contains("Phone", ignoreCase = true) == true ||
                device.name?.contains("Pixel", ignoreCase = true) == true ||
                device.name?.contains("Galaxy", ignoreCase = true) == true ||
                device.name?.contains("iPhone", ignoreCase = true) == true
            }
            
            if (phoneDevice == null) {
                _error.value = "Phone not found among paired devices"
                _connectionState.value = ConnectionState.DISCONNECTED
                return@withContext false
            }
            
            // For now, let's simulate a successful connection
            // In a real app, you'd actually connect to the device
            _connectionState.value = ConnectionState.CONNECTED
            _error.value = null
            
            // Start listening for data
            startListening()
            
            // Request device list
            requestDevices()
            
            return@withContext true
            
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
    
    private fun startListening() {
        // In a real app, you'd start a coroutine to listen for incoming data
        // For this example, we'll just simulate receiving data
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
            // In a real app, you'd send a command to the phone to request the device list
            // For this example, we'll just use our mock data
            
            // Simulate a delay for network request
            kotlinx.coroutines.delay(500)
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting devices: ${e.message}", e)
            _error.value = "Error requesting devices: ${e.message}"
            return@withContext false
        }
    }
    
    fun disconnect() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: ${e.message}", e)
        } finally {
            socket = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }
    
    suspend fun sendCommand(deviceId: String, command: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            _error.value = "Bluetooth permissions not granted"
            return@withContext false
        }
        
        if (_connectionState.value != ConnectionState.CONNECTED) {
            _error.value = "Not connected to phone"
            return@withContext false
        }
        
        try {
            // In a real app, you'd send the command to the phone
            // For this example, we'll just update our mock data
            
            when (command) {
                "TOGGLE" -> {
                    toggleDevice(deviceId)
                }
                else -> {
                    if (command.startsWith("VALUE:")) {
                        val value = command.substringAfter("VALUE:")
                        setDeviceValue(deviceId, value.toIntOrNull() ?: 0)
                    }
                }
            }
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command: ${e.message}", e)
            _error.value = "Error sending command: ${e.message}"
            return@withContext false
        }
    }
    
    fun toggleDevice(deviceId: String) {
        val devices = _devices.value.toMutableList()
        val deviceIndex = devices.indexOfFirst { it.id == deviceId }
        
        if (deviceIndex != -1) {
            val device = devices[deviceIndex]
            devices[deviceIndex] = device.copy(isOn = !device.isOn)
            _devices.value = devices
        }
    }
    
    fun setDeviceValue(deviceId: String, value: Int) {
        val devices = _devices.value.toMutableList()
        val deviceIndex = devices.indexOfFirst { it.id == deviceId }
        
        if (deviceIndex != -1) {
            val device = devices[deviceIndex]
            devices[deviceIndex] = device.copy(value = value.toString())
            _devices.value = devices
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
