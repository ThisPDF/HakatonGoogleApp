package com.example.smarthome.data.bluetooth

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.smarthome.data.Device
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothGattServerService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "BluetoothGattServer"
    
    // Define UUIDs for services and characteristics
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
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()
    
    private val _temperature = MutableStateFlow(22.0f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()
    
    // Bluetooth objects
    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    private val bluetoothAdapter by lazy {
        bluetoothManager.adapter
    }
    
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private val connectedDevices = mutableListOf<BluetoothDevice>()
    
    // Thread management
    private var bleThread: HandlerThread? = null
    private var bleHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val threadLock = Object()
    private var isThreadActive = false
    
    // JSON serialization
    private val gson = Gson()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    init {
        Log.d(TAG, "Initializing BluetoothGattServerService")
        initializeBluetoothThread()
    }
    
    private fun initializeBluetoothThread() {
        synchronized(threadLock) {
            Log.d(TAG, "Initializing Bluetooth thread")
            try {
                if (bleThread?.isAlive == true) {
                    Log.d(TAG, "BLE thread is already alive, joining...")
                    bleThread?.quitSafely()
                    bleThread?.join(1000)
                }
                
                val threadId = UUID.randomUUID().toString().substring(0, 8)
                bleThread = HandlerThread("BleThread-$threadId").apply { 
                    start()
                    Log.d(TAG, "BLE thread started: ${this.name}")
                }
                bleHandler = Handler(bleThread!!.looper)
                isThreadActive = true
                Log.d(TAG, "BLE thread initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Bluetooth thread", e)
                isThreadActive = false
                bleThread = null
                bleHandler = null
            }
        }
    }
    
    private fun postToHandler(runnable: Runnable) {
        synchronized(threadLock) {
            if (!isThreadActive || bleHandler == null) {
                Log.w(TAG, "BLE thread is not active, reinitializing...")
                initializeBluetoothThread()
            }
            
            try {
                bleHandler?.post(runnable) ?: mainHandler.post(runnable)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post to handler", e)
                mainHandler.post(runnable)
            }
        }
    }
    
    private fun postDelayedToHandler(runnable: Runnable, delayMillis: Long) {
        synchronized(threadLock) {
            if (!isThreadActive || bleHandler == null) {
                Log.w(TAG, "BLE thread is not active for delayed post, reinitializing...")
                initializeBluetoothThread()
            }
            
            try {
                bleHandler?.postDelayed(runnable, delayMillis) ?: mainHandler.postDelayed(runnable, delayMillis)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post delayed to handler", e)
                mainHandler.postDelayed(runnable, delayMillis)
            }
        }
    }
    
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
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
    
    // GATT Server Callback
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: device=${device.address}, status=$status, newState=$newState")
            
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Missing Bluetooth permissions")
                return
            }
            
            try {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    synchronized(connectedDevices) {
                        if (!connectedDevices.contains(device)) {
                            connectedDevices.add(device)
                        }
                    }
                    _isConnected.value = true
                    _connectedDeviceName.value = device.name ?: "Unknown Device"
                    Log.d(TAG, "Device connected: ${device.name ?: "Unknown"} (${device.address})")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    synchronized(connectedDevices) {
                        connectedDevices.remove(device)
                    }
                    if (connectedDevices.isEmpty()) {
                        _isConnected.value = false
                        _connectedDeviceName.value = null
                    }
                    Log.d(TAG, "Device disconnected: ${device.name ?: "Unknown"} (${device.address})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onConnectionStateChange", e)
            }
        }
        
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "onCharacteristicReadRequest: device=${device.address}, characteristic=${characteristic.uuid}")
            
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Missing Bluetooth permissions")
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                return
            }
            
            try {
                when (characteristic.uuid) {
                    DEVICE_LIST_CHARACTERISTIC_UUID -> {
                        // Send the device list as JSON
                        val deviceList = getDeviceList()
                        val jsonData = gson.toJson(deviceList)
                        val value = jsonData.toByteArray()
                        bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                        Log.d(TAG, "Sent device list: ${deviceList.size} devices")
                    }
                    TEMPERATURE_CHARACTERISTIC_UUID -> {
                        // Send the current temperature
                        val value = _temperature.value.toString().toByteArray()
                        bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                        Log.d(TAG, "Sent temperature: ${_temperature.value}")
                    }
                    else -> {
                        bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                        Log.w(TAG, "Read request for unknown characteristic: ${characteristic.uuid}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onCharacteristicReadRequest", e)
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }
        
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d(TAG, "onCharacteristicWriteRequest: device=${device.address}, characteristic=${characteristic.uuid}")
            
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Missing Bluetooth permissions")
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
                return
            }
            
            try {
                when (characteristic.uuid) {
                    DEVICE_CONTROL_CHARACTERISTIC_UUID -> {
                        // Process device control command
                        val command = String(value)
                        Log.d(TAG, "Received device control command: $command")
                        processDeviceCommand(command)
                        
                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                        }
                    }
                    else -> {
                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                        }
                        Log.w(TAG, "Write request for unknown characteristic: ${characteristic.uuid}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onCharacteristicWriteRequest", e)
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }
        
        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            Log.d(TAG, "onDescriptorReadRequest: device=${device.address}, descriptor=${descriptor.uuid}")
            
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Missing Bluetooth permissions")
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                return
            }
            
            try {
                if (descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR_UUID) {
                    val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
                } else {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onDescriptorReadRequest", e)
                bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }
        
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d(TAG, "onDescriptorWriteRequest: device=${device.address}, descriptor=${descriptor.uuid}")
            
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Missing Bluetooth permissions")
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
                return
            }
            
            try {
                if (descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR_UUID) {
                    // Client is subscribing to notifications
                    if (responseNeeded) {
                        bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                    Log.d(TAG, "Client subscribed to notifications")
                } else {
                    if (responseNeeded) {
                        bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onDescriptorWriteRequest", e)
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
            }
        }
    }
    
    // Advertising callback
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "BLE Advertising started successfully")
        }
        
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE Advertising failed to start with error code: $errorCode")
            
            // Try with simpler settings after a delay
            postDelayedToHandler({
                startAdvertisingWithSimpleSettings()
            }, 2000)
        }
    }
    
    // Start the GATT server
    fun startServer() {
        Log.d(TAG, "Starting GATT server")
        
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            return
        }
        
        postToHandler {
            try {
                // Initialize the GATT server
                bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
                
                if (bluetoothGattServer == null) {
                    Log.e(TAG, "Failed to open GATT server")
                    return@postToHandler
                }
                
                // Create the service
                val service = BluetoothGattService(SMART_HOME_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                
                // Create device list characteristic
                val deviceListCharacteristic = BluetoothGattCharacteristic(
                    DEVICE_LIST_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )
                
                // Create device control characteristic
                val deviceControlCharacteristic = BluetoothGattCharacteristic(
                    DEVICE_CONTROL_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE
                )
                
                // Create temperature characteristic
                val temperatureCharacteristic = BluetoothGattCharacteristic(
                    TEMPERATURE_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ
                )
                
                // Add client config descriptor to characteristics that support notifications
                val deviceListDescriptor = BluetoothGattDescriptor(
                    CLIENT_CONFIG_DESCRIPTOR_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
                deviceListCharacteristic.addDescriptor(deviceListDescriptor)
                
                val temperatureDescriptor = BluetoothGattDescriptor(
                    CLIENT_CONFIG_DESCRIPTOR_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
                temperatureCharacteristic.addDescriptor(temperatureDescriptor)
                
                // Add characteristics to service
                service.addCharacteristic(deviceListCharacteristic)
                service.addCharacteristic(deviceControlCharacteristic)
                service.addCharacteristic(temperatureCharacteristic)
                
                // Add service to GATT server
                val success = bluetoothGattServer?.addService(service) ?: false
                
                if (success) {
                    Log.d(TAG, "Service added successfully")
                    // Start advertising
                    startAdvertising()
                    
                    // Start simulating temperature changes
                    simulateTemperatureChanges()
                } else {
                    Log.e(TAG, "Failed to add service")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting GATT server", e)
            }
        }
    }
    
    // Start BLE advertising
    private fun startAdvertising() {
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            return
        }
        
        try {
            bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
            
            if (bluetoothLeAdvertiser == null) {
                Log.e(TAG, "Bluetooth LE advertising not supported")
                return
            }
            
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0) // No timeout
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()
            
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(SMART_HOME_SERVICE_UUID))
                .build()
            
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
            Log.d(TAG, "Started BLE advertising")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising", e)
        }
    }
    
    // Start advertising with simpler settings as a fallback
    private fun startAdvertisingWithSimpleSettings() {
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            return
        }
        
        try {
            bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
            
            if (bluetoothLeAdvertiser == null) {
                Log.e(TAG, "Bluetooth LE advertising not supported")
                return
            }
            
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .build()
            
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false) // Don't include device name to reduce packet size
                .addServiceUuid(ParcelUuid(SMART_HOME_SERVICE_UUID))
                .build()
            
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
            Log.d(TAG, "Started BLE advertising with simple settings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising with simple settings", e)
        }
    }
    
    // Stop advertising
    private fun stopAdvertising() {
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            bluetoothLeAdvertiser = null
            Log.d(TAG, "Stopped BLE advertising")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising", e)
        }
    }
    
    // Stop the GATT server
    fun stopServer() {
        Log.d(TAG, "Stopping GATT server")
        
        postToHandler {
            try {
                stopAdvertising()
                
                synchronized(connectedDevices) {
                    connectedDevices.clear()
                }
                _isConnected.value = false
                _connectedDeviceName.value = null
                
                bluetoothGattServer?.close()
                bluetoothGattServer = null
                
                Log.d(TAG, "GATT server stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping GATT server", e)
            }
        }
    }
    
    // Simulate temperature changes
    private fun simulateTemperatureChanges() {
        postDelayedToHandler({
            // Update temperature with a small random change
            val newTemp = _temperature.value + (-0.5f..0.5f).random()
            _temperature.value = newTemp.coerceIn(18f, 30f)
            
            // Notify connected devices
            notifyTemperatureChanged()
            
            // Schedule next update
            simulateTemperatureChanges()
        }, 5000) // Update every 5 seconds
    }
    
    // Notify connected devices about temperature changes
    private fun notifyTemperatureChanged() {
        if (!hasBluetoothPermissions() || bluetoothGattServer == null) {
            return
        }
        
        try {
            val service = bluetoothGattServer?.getService(SMART_HOME_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(TEMPERATURE_CHARACTERISTIC_UUID)
            
            if (characteristic != null) {
                characteristic.value = _temperature.value.toString().toByteArray()
                
                synchronized(connectedDevices) {
                    for (device in connectedDevices) {
                        bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, false)
                    }
                }
                
                Log.d(TAG, "Notified temperature change: ${_temperature.value}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying temperature change", e)
        }
    }
    
    // Notify connected devices about device list changes
    fun notifyDeviceListChanged() {
        if (!hasBluetoothPermissions() || bluetoothGattServer == null) {
            return
        }
        
        postToHandler {
            try {
                val service = bluetoothGattServer?.getService(SMART_HOME_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(DEVICE_LIST_CHARACTERISTIC_UUID)
                
                if (characteristic != null) {
                    val deviceList = getDeviceList()
                    val jsonData = gson.toJson(deviceList)
                    characteristic.value = jsonData.toByteArray()
                    
                    synchronized(connectedDevices) {
                        for (device in connectedDevices) {
                            bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, false)
                        }
                    }
                    
                    Log.d(TAG, "Notified device list change: ${deviceList.size} devices")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying device list change", e)
            }
        }
    }
    
    // Get the list of smart home devices
    private fun getDeviceList(): List<DeviceInfo> {
        // This would typically come from your repository
        // For now, we'll return a sample list
        return listOf(
            DeviceInfo("1", "Living Room Light", "LIGHT", "living_room", true, null),
            DeviceInfo("2", "Kitchen Light", "LIGHT", "kitchen", false, null),
            DeviceInfo("3", "Bedroom Light", "LIGHT", "bedroom", false, null),
            DeviceInfo("4", "Thermostat", "THERMOSTAT", "living_room", true, "22.5"),
            DeviceInfo("5", "Front Door", "LOCK", "entrance", false, null)
        )
    }
    
    // Process device control commands
    private fun processDeviceCommand(command: String) {
        try {
            // Parse the command
            // Format: "ACTION:deviceId:value"
            // Example: "TOGGLE:1:" or "SET:4:23.5"
            val parts = command.split(":")
            if (parts.size < 2) {
                Log.e(TAG, "Invalid command format: $command")
                return
            }
            
            val action = parts[0]
            val deviceId = parts[1]
            val value = if (parts.size > 2) parts[2] else null
            
            Log.d(TAG, "Processing command: action=$action, deviceId=$deviceId, value=$value")
            
            // Handle the command
            when (action) {
                "TOGGLE" -> {
                    // Toggle device state
                    Log.d(TAG, "Toggling device $deviceId")
                    // In a real app, you would update your repository
                    // For now, we'll just notify that the device list changed
                    notifyDeviceListChanged()
                }
                "SET" -> {
                    // Set device value
                    Log.d(TAG, "Setting device $deviceId to value $value")
                    // In a real app, you would update your repository
                    // For now, we'll just notify that the device list changed
                    notifyDeviceListChanged()
                }
                else -> {
                    Log.e(TAG, "Unknown action: $action")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing device command", e)
        }
    }
    
    // Update temperature
    fun updateTemperature(temp: Float) {
        _temperature.value = temp
        notifyTemperatureChanged()
    }
    
    // Clean up resources
    fun cleanup() {
        Log.d(TAG, "Cleaning up BluetoothGattServerService")
        
        coroutineScope.launch {
            stopServer()
            
            synchronized(threadLock) {
                try {
                    isThreadActive = false
                    bleHandler?.removeCallbacksAndMessages(null)
                    bleHandler = null
                    
                    bleThread?.quitSafely()
                    try {
                        bleThread?.join(1000)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "Error joining BLE thread", e)
                    }
                    bleThread = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error during cleanup", e)
                }
            }
        }
    }
    
    // Data class for device information
    data class DeviceInfo(
        val id: String,
        val name: String,
        val type: String,
        val roomId: String,
        val isOn: Boolean,
        val value: String?
    )
}
