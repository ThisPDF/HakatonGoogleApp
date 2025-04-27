package com.example.smarthome.wear.data.bluetooth

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import java.io.InputStream
import java.io.OutputStream

/**
 * Data class representing a device
 */
data class Device(
    val id: String,
    val name: String,
    val type: String,
    val status: Boolean,
    val value: String? = null
)

/**
 * Data class representing Bluetooth status
 */
data class BluetoothStatus(
    val isAvailable: Boolean,
    val isEnabled: Boolean,
    val pairedDevices: Int,
    val connectedDevices: Int
)

/**
 * Enum representing connection states
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

/**
 * Service for managing Bluetooth LE connections to the smart home system
 */
@Singleton
class BluetoothService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "BluetoothService"
    
    // UUIDs for services and characteristics - must match the server
    companion object {
        // Service UUIDs
        val SMART_HOME_SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        
        // Characteristic UUIDs
        val DEVICE_LIST_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val DEVICE_CONTROL_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9")
        val TEMPERATURE_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26aa")
        
        // Descriptor UUIDs
        val CLIENT_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    
    // State flows for observing data
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    private val _temperature = MutableStateFlow<Float?>(null)
    val temperature: StateFlow<Float?> = _temperature.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _scanResults = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scanResults: StateFlow<List<BluetoothDevice>> = _scanResults.asStateFlow()
    
    // Bluetooth objects
    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }
    
    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    
    // Handler for timeouts
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // JSON serialization
    private val gson = Gson()
    
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val MAX_CONNECTION_RETRIES = 3
    private val CONNECTION_TIMEOUT = 10000L // 10 seconds
    
    // Create a coroutine scope for this service
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
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
    
    // Check Bluetooth status
    fun checkBluetoothStatus(): BluetoothStatus {
        val isAvailable = bluetoothAdapter != null
        val isEnabled = isAvailable && (bluetoothAdapter?.isEnabled == true)
        
        val pairedDevices = if (isEnabled && hasBluetoothPermissions()) {
            try {
                bluetoothAdapter?.bondedDevices?.size ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "Error getting paired devices", e)
                0
            }
        } else 0
        
        return BluetoothStatus(isAvailable, isEnabled, pairedDevices, 0)
    }
    
    // Start scanning for BLE devices
    fun startScan() {
        if (!hasBluetoothPermissions()) {
            _error.value = "Bluetooth permissions not granted"
            return
        }
        
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            _error.value = "Bluetooth is not enabled"
            return
        }
        
        if (isScanning) {
            return
        }
        
        // Clear previous results
        _scanResults.value = emptyList()
        
        coroutineScope.launch {
            try {
                bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
                
                if (bluetoothLeScanner == null) {
                    _error.value = "Bluetooth LE scanner not available"
                    return@launch
                }
                
                // Set up scan filters to only find devices with our service UUID
                val filters = listOf(
                    ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(SMART_HOME_SERVICE_UUID))
                        .build()
                )
                
                // Set up scan settings
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                
                isScanning = true
                
                // Start scanning
                bluetoothLeScanner?.startScan(filters, settings, scanCallback)
                Log.d(TAG, "Started BLE scan")
                
                // Stop scanning after a timeout
                handler.postDelayed({
                    stopScan()
                }, 10000) // 10 seconds
            } catch (e: Exception) {
                Log.e(TAG, "Error starting scan", e)
                _error.value = "Error starting scan: ${e.message}"
                isScanning = false
            }
        }
    }
    
    // Stop scanning for BLE devices
    fun stopScan() {
        if (!isScanning) {
            return
        }
        
        coroutineScope.launch {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
                isScanning = false
                Log.d(TAG, "Stopped BLE scan")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
            }
        }
    }
    
    // Scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!hasBluetoothPermissions()) {
                return
            }
            
            try {
                val device = result.device
                val deviceName = device.name ?: "Unknown Device"
                
                Log.d(TAG, "Found device: $deviceName (${device.address})")
                
                // Add to scan results if not already present
                val currentResults = _scanResults.value.toMutableList()
                if (!currentResults.any { it.address == device.address }) {
                    currentResults.add(device)
                    _scanResults.value = currentResults
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing scan result", e)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _error.value = "Scan failed with error code: $errorCode"
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
        }
    }
    
    // Connect to a device
    suspend fun connectToDevice(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            _error.value = "Bluetooth permissions not granted"
            return@withContext false
        }
        
        try {
            // Disconnect from any existing connection
            disconnect()
            
            _connectionState.value = ConnectionState.CONNECTING
            
            // Connect to the device
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            
            if (bluetoothGatt == null) {
                _connectionState.value = ConnectionState.DISCONNECTED
                _error.value = "Failed to connect to device"
                return@withContext false
            }
            
            Log.d(TAG, "Connecting to device: ${device.name ?: "Unknown"} (${device.address})")
            
            // Wait for connection to complete (handled in callback)
            // Return true to indicate connection attempt started
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device", e)
            _connectionState.value = ConnectionState.DISCONNECTED
            _error.value = "Error connecting to device: ${e.message}"
            return@withContext false
        }
    }
    
    // GATT callback
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!hasBluetoothPermissions()) {
                return
            }
            
            try {
                val deviceAddress = gatt.device.address
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "Connected to device: $deviceAddress")
                        _connectionState.value = ConnectionState.CONNECTED
                        
                        // Discover services
                        handler.post {
                            try {
                                gatt.discoverServices()
                                Log.d(TAG, "Discovering services...")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error discovering services", e)
                                disconnect()
                            }
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "Disconnected from device: $deviceAddress")
                        _connectionState.value = ConnectionState.DISCONNECTED
                        disconnect()
                    }
                } else {
                    Log.e(TAG, "Connection state change failed with status: $status")
                    _error.value = "Connection failed with status: $status"
                    _connectionState.value = ConnectionState.DISCONNECTED
                    disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onConnectionStateChange", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                disconnect()
            }
        }
        
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!hasBluetoothPermissions()) {
                return
            }
            
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Services discovered")
                    
                    // Find our service
                    val service = gatt.getService(SMART_HOME_SERVICE_UUID)
                    
                    if (service == null) {
                        Log.e(TAG, "Smart Home service not found")
                        _error.value = "Smart Home service not found"
                        disconnect()
                        return
                    }
                    
                    // Enable notifications for temperature and device list
                    enableNotifications(gatt, service)
                    
                    // Read initial device list
                    readDeviceList(gatt, service)
                    
                    // Read initial temperature
                    readTemperature(gatt, service)
                } else {
                    Log.e(TAG, "Service discovery failed with status: $status")
                    _error.value = "Service discovery failed"
                    disconnect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onServicesDiscovered", e)
                disconnect()
            }
        }
        
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (!hasBluetoothPermissions()) {
                return
            }
            
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    when (characteristic.uuid) {
                        DEVICE_LIST_CHARACTERISTIC_UUID -> {
                            val value = characteristic.value
                            val jsonString = String(value)
                            processDeviceListData(jsonString)
                        }
                        TEMPERATURE_CHARACTERISTIC_UUID -> {
                            val value = characteristic.value
                            val tempString = String(value)
                            try {
                                _temperature.value = tempString.toFloat()
                                Log.d(TAG, "Temperature: ${_temperature.value}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing temperature", e)
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Characteristic read failed with status: $status")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onCharacteristicRead", e)
            }
        }
        
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (!hasBluetoothPermissions()) {
                return
            }
            
            try {
                when (characteristic.uuid) {
                    DEVICE_LIST_CHARACTERISTIC_UUID -> {
                        val value = characteristic.value
                        val jsonString = String(value)
                        processDeviceListData(jsonString)
                    }
                    TEMPERATURE_CHARACTERISTIC_UUID -> {
                        val value = characteristic.value
                        val tempString = String(value)
                        try {
                            _temperature.value = tempString.toFloat()
                            Log.d(TAG, "Temperature updated: ${_temperature.value}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing temperature update", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onCharacteristicChanged", e)
            }
        }
        
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (!hasBluetoothPermissions()) {
                return
            }
            
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Descriptor write successful: ${descriptor.uuid}")
                } else {
                    Log.e(TAG, "Descriptor write failed with status: $status")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onDescriptorWrite", e)
            }
        }
    }
    
    // Enable notifications for characteristics
    private fun enableNotifications(gatt: BluetoothGatt, service: BluetoothGattService) {
        try {
            // Enable notifications for device list
            val deviceListChar = service.getCharacteristic(DEVICE_LIST_CHARACTERISTIC_UUID)
            if (deviceListChar != null) {
                val success = gatt.setCharacteristicNotification(deviceListChar, true)
                if (success) {
                    val descriptor = deviceListChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        Log.d(TAG, "Enabled notifications for device list")
                    }
                }
            }
            
            // Enable notifications for temperature
            val temperatureChar = service.getCharacteristic(TEMPERATURE_CHARACTERISTIC_UUID)
            if (temperatureChar != null) {
                val success = gatt.setCharacteristicNotification(temperatureChar, true)
                if (success) {
                    val descriptor = temperatureChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                        Log.d(TAG, "Enabled notifications for temperature")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling notifications", e)
        }
    }
    
    // Read device list
    private fun readDeviceList(gatt: BluetoothGatt, service: BluetoothGattService) {
        try {
            val characteristic = service.getCharacteristic(DEVICE_LIST_CHARACTERISTIC_UUID)
            if (characteristic != null) {
                gatt.readCharacteristic(characteristic)
                Log.d(TAG, "Reading device list")
            } else {
                Log.e(TAG, "Device list characteristic not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading device list", e)
        }
    }
    
    // Read temperature
    private fun readTemperature(gatt: BluetoothGatt, service: BluetoothGattService) {
        try {
            val characteristic = service.getCharacteristic(TEMPERATURE_CHARACTERISTIC_UUID)
            if (characteristic != null) {
                gatt.readCharacteristic(characteristic)
                Log.d(TAG, "Reading temperature")
            } else {
                Log.e(TAG, "Temperature characteristic not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading temperature", e)
        }
    }
    
    // Process device list data
    private fun processDeviceListData(jsonString: String) {
        try {
            val type = object : TypeToken<List<Device>>() {}.type
            val deviceList: List<Device> = gson.fromJson(jsonString, type)
            _devices.value = deviceList
            Log.d(TAG, "Received device list: ${deviceList.size} devices")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing device list data", e)
        }
    }
    
    // Send device control command
    suspend fun sendDeviceCommand(deviceId: String, action: String, value: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            _error.value = "Bluetooth permissions not granted"
            return@withContext false
        }
        
        if (_connectionState.value != ConnectionState.CONNECTED || bluetoothGatt == null) {
            _error.value = "Not connected to device"
            return@withContext false
        }
        
        try {
            val service = bluetoothGatt?.getService(SMART_HOME_SERVICE_UUID)
            if (service == null) {
                _error.value = "Smart Home service not found"
                return@withContext false
            }
            
            val characteristic = service.getCharacteristic(DEVICE_CONTROL_CHARACTERISTIC_UUID)
            if (characteristic == null) {
                _error.value = "Device control characteristic not found"
                return@withContext false
            }
            
            // Format the command
            val command = if (value != null) {
                "$action:$deviceId:$value"
            } else {
                "$action:$deviceId:"
            }
            
            characteristic.value = command.toByteArray()
            val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
            
            if (success) {
                Log.d(TAG, "Sent device command: $command")
            } else {
                Log.e(TAG, "Failed to send device command")
                _error.value = "Failed to send device command"
            }
            
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "Error sending device command", e)
            _error.value = "Error sending device command: ${e.message}"
            return@withContext false
        }
    }
    
    // Toggle device
    suspend fun toggleDevice(deviceId: String): Boolean {
        return sendDeviceCommand(deviceId, "TOGGLE")
    }
    
    // Set device value
    suspend fun setDeviceValue(deviceId: String, value: String): Boolean {
        return sendDeviceCommand(deviceId, "SET", value)
    }
    
    fun disconnect() {
        if (!hasBluetoothPermissions()) {
            return
        }
        
        coroutineScope.launch {
            try {
                bluetoothGatt?.close()
                bluetoothGatt = null
                _connectionState.value = ConnectionState.DISCONNECTED
                Log.d(TAG, "Disconnected from device")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting", e)
            }
        }
    }
    
    // Clean up resources
    fun cleanup() {
        stopScan()
        disconnect()
        handler.removeCallbacksAndMessages(null)
    }
}
