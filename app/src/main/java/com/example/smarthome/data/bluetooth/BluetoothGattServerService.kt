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

@Singleton
class BluetoothGattServerService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothManager: BluetoothManager
) {
    private val TAG = "GattServerService"
    
    // Service and characteristic UUIDs
    companion object {
        // Service UUIDs
        val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        
        // Characteristic UUIDs
        val LED_CONTROL_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val TEMPERATURE_CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9")
        
        // Descriptor UUIDs
        val CLIENT_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
    
    // State flows for temperature and connection status
    private val _temperatureData = MutableStateFlow<Float?>(null)
    val temperatureData: StateFlow<Float?> = _temperatureData.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _ledState = MutableStateFlow(false)
    val ledState: StateFlow<Boolean> = _ledState.asStateFlow()
    
    // Bluetooth objects
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private val connectedDevices = mutableListOf<BluetoothDevice>()
    
    // Background thread for BLE operations
    private val bleHandlerThread = HandlerThread("BLEThread")
    private var bleHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // Thread state tracking
    private var isThreadActive = false
    private val threadLock = Object()
    
    init {
        Log.d(TAG, "Initializing BluetoothGattServerService")
        initializeHandlerThread()
    }
    
    private fun initializeHandlerThread() {
        synchronized(threadLock) {
            try {
                Log.d(TAG, "Starting BLE handler thread")
                bleHandlerThread.start()
                bleHandler = Handler(bleHandlerThread.looper)
                isThreadActive = true
                Log.d(TAG, "BLE handler thread started successfully: ${bleHandlerThread.name}, state: ${bleHandlerThread.state}")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing BLE handler thread", e)
                isThreadActive = false
            }
        }
    }

    // First, add a method to check for Bluetooth permissions
    private fun hasBluetoothPermissions(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        Log.d(TAG, "Bluetooth permissions check: $hasPermission")
        return hasPermission
    }
    
    // Safe way to post to the handler
    private fun postToHandler(runnable: Runnable): Boolean {
        synchronized(threadLock) {
            val handler = bleHandler
            if (handler != null && isThreadActive && bleHandlerThread.isAlive) {
                Log.d(TAG, "Posting to BLE handler thread: ${bleHandlerThread.name}, state: ${bleHandlerThread.state}")
                return handler.post(runnable)
            } else {
                Log.e(TAG, "Cannot post to BLE handler thread - thread is not active or handler is null. Thread alive: ${bleHandlerThread.isAlive}, Handler null: ${handler == null}")
                return false
            }
        }
    }
    
    // Safe way to post delayed to the handler
    private fun postDelayedToHandler(runnable: Runnable, delayMillis: Long): Boolean {
        synchronized(threadLock) {
            val handler = bleHandler
            if (handler != null && isThreadActive && bleHandlerThread.isAlive) {
                Log.d(TAG, "Posting delayed task to BLE handler thread")
                return handler.postDelayed(runnable, delayMillis)
            } else {
                Log.e(TAG, "Cannot post delayed task to BLE handler thread - thread is not active or handler is null")
                return false
            }
        }
    }
    
    // Callback for GATT server events
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: device=${device.address}, status=$status, newState=$newState")
            
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Bluetooth permissions not granted")
                return
            }
            
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Device connected: ${device.address}")
                synchronized(connectedDevices) {
                    connectedDevices.add(device)
                }
                _isConnected.value = true
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Device disconnected: ${device.address}")
                synchronized(connectedDevices) {
                    connectedDevices.remove(device)
                }
                _isConnected.value = connectedDevices.isNotEmpty()
            }
        }
        
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "onCharacteristicReadRequest: device=${device.address}, requestId=$requestId, characteristic=${characteristic.uuid}")
            
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Bluetooth permissions not granted")
                try {
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending failure response", e)
                }
                return
            }
            
            try {
                when (characteristic.uuid) {
                    LED_CONTROL_CHARACTERISTIC_UUID -> {
                        val value = byteArrayOf(if (_ledState.value) 1 else 0)
                        Log.d(TAG, "Sending LED state response: ${_ledState.value}")
                        bluetoothGattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value
                        )
                        Log.i(TAG, "LED state read: ${_ledState.value}")
                    }
                    TEMPERATURE_CHARACTERISTIC_UUID -> {
                        val temperature = _temperatureData.value ?: 0f
                        val value = temperature.toString().toByteArray()
                        Log.d(TAG, "Sending temperature response: $temperature")
                        bluetoothGattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value
                        )
                        Log.i(TAG, "Temperature read: $temperature")
                    }
                    else -> {
                        Log.d(TAG, "Unknown characteristic read request: ${characteristic.uuid}")
                        bluetoothGattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing characteristic read request", e)
                try {
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "Error sending failure response", e2)
                }
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
            Log.d(TAG, "onCharacteristicWriteRequest: device=${device.address}, requestId=$requestId, characteristic=${characteristic.uuid}")
            
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Bluetooth permissions not granted")
                if (responseNeeded) {
                    try {
                        bluetoothGattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending failure response", e)
                    }
                }
                return
            }
            
            try {
                when (characteristic.uuid) {
                    LED_CONTROL_CHARACTERISTIC_UUID -> {
                        if (value.isNotEmpty()) {
                            val ledOn = value[0] != 0.toByte()
                            _ledState.value = ledOn
                            Log.i(TAG, "LED state changed to: $ledOn")
                        }
                        
                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(
                                device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                            )
                        }
                    }
                    TEMPERATURE_CHARACTERISTIC_UUID -> {
                        if (value.isNotEmpty()) {
                            try {
                                val temperatureString = String(value)
                                val temperature = temperatureString.toFloat()
                                _temperatureData.value = temperature
                                Log.i(TAG, "Temperature updated: $temperature")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing temperature data", e)
                            }
                        }
                        
                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(
                                device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                            )
                        }
                    }
                    else -> {
                        Log.d(TAG, "Unknown characteristic write request: ${characteristic.uuid}")
                        if (responseNeeded) {
                            bluetoothGattServer?.sendResponse(
                                device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing characteristic write request", e)
                if (responseNeeded) {
                    try {
                        bluetoothGattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                        )
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error sending failure response", e2)
                    }
                }
            }
        }
        
        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            Log.d(TAG, "onDescriptorReadRequest: device=${device.address}, requestId=$requestId, descriptor=${descriptor.uuid}")
            
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Bluetooth permissions not granted")
                try {
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending failure response", e)
                }
                return
            }
            
            try {
                if (descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR_UUID) {
                    val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value
                    )
                } else {
                    Log.d(TAG, "Unknown descriptor read request: ${descriptor.uuid}")
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing descriptor read request", e)
                try {
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "Error sending failure response", e2)
                }
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
            Log.d(TAG, "onDescriptorWriteRequest: device=${device.address}, requestId=$requestId, descriptor=${descriptor.uuid}")
            
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Bluetooth permissions not granted")
                if (responseNeeded) {
                    try {
                        bluetoothGattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending failure response", e)
                    }
                }
                return
            }
            
            try {
                if (descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR_UUID) {
                    // Handle notification/indication subscription
                    if (responseNeeded) {
                        bluetoothGattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                        )
                    }
                } else if (responseNeeded) {
                    Log.d(TAG, "Unknown descriptor write request: ${descriptor.uuid}")
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing descriptor write request", e)
                if (responseNeeded) {
                    try {
                        bluetoothGattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                        )
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error sending failure response", e2)
                    }
                }
            }
        }
    }
    
    // Callback for BLE advertising
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "BLE advertising started successfully")
        }
        
        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed with error code: $errorCode")
            // Try to restart advertising with simpler settings after a delay
            val runnable = Runnable {
                startAdvertisingWithSimpleSettings()
            }
            
            if (!postDelayedToHandler(runnable, 2000)) {
                // If posting to handler failed, try on main thread
                Log.d(TAG, "Posting to main thread instead")
                mainHandler.postDelayed(runnable, 2000)
            }
        }
    }
    
    // Start the GATT server and begin advertising
    fun startServer() {
        Log.d(TAG, "startServer() called")
        
        // Check if thread is active, if not reinitialize
        synchronized(threadLock) {
            if (!isThreadActive || !bleHandlerThread.isAlive) {
                Log.d(TAG, "BLE thread is not active, reinitializing")
                if (bleHandlerThread.state == Thread.State.TERMINATED) {
                    Log.d(TAG, "BLE thread was terminated, creating a new one")
                    val newThread = HandlerThread("BLEThread-${UUID.randomUUID()}")
                    bleHandlerThread.quitSafely()
                    bleHandlerThread.join(1000) // Wait for old thread to finish
                    bleHandlerThread = newThread
                }
                initializeHandlerThread()
            }
        }
        
        // Run on background thread
        val runnable = Runnable {
            try {
                // Check for permissions first
                if (!hasBluetoothPermissions()) {
                    Log.e(TAG, "Cannot start GATT server: Bluetooth permissions not granted")
                    return@Runnable
                }
            
                val bluetoothAdapter = bluetoothManager.adapter
                
                if (bluetoothAdapter == null) {
                    Log.e(TAG, "Bluetooth adapter is null")
                    return@Runnable
                }
                
                if (!bluetoothAdapter.isEnabled) {
                    Log.e(TAG, "Bluetooth is not enabled")
                    return@Runnable
                }
                
                Log.d(TAG, "Opening GATT server")
                // Initialize the GATT server
                bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
                
                if (bluetoothGattServer == null) {
                    Log.e(TAG, "Failed to open GATT server")
                    return@Runnable
                }
                
                // Create the service
                val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                
                // Create LED control characteristic
                val ledControlCharacteristic = BluetoothGattCharacteristic(
                    LED_CONTROL_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
                )
                
                // Create temperature characteristic
                val temperatureCharacteristic = BluetoothGattCharacteristic(
                    TEMPERATURE_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or
                            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
                )
                
                // Add client config descriptor to temperature characteristic
                val clientConfigDescriptor = BluetoothGattDescriptor(
                    CLIENT_CONFIG_DESCRIPTOR_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
                temperatureCharacteristic.addDescriptor(clientConfigDescriptor)
                
                // Add characteristics to service
                service.addCharacteristic(ledControlCharacteristic)
                service.addCharacteristic(temperatureCharacteristic)
                
                // Add service to GATT server
                val success = bluetoothGattServer?.addService(service)
                Log.d(TAG, "Add service result: $success")
                
                // Start advertising
                startAdvertising()
                
                Log.i(TAG, "GATT server started")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting GATT server", e)
            }
        }
        
        if (!postToHandler(runnable)) {
            // If posting to handler failed, try on main thread
            Log.d(TAG, "Posting startServer to main thread instead")
            mainHandler.post(runnable)
        }
    }
    
    // Stop the GATT server and advertising
    fun stopServer() {
        Log.d(TAG, "stopServer() called")
        
        val runnable = Runnable {
            try {
                Log.d(TAG, "Stopping advertising")
                stopAdvertising()
                
                Log.d(TAG, "Clearing connected devices")
                synchronized(connectedDevices) {
                    connectedDevices.clear()
                }
                _isConnected.value = false
                
                Log.d(TAG, "Closing GATT server")
                bluetoothGattServer?.close()
                bluetoothGattServer = null
                
                Log.i(TAG, "GATT server stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping GATT server", e)
            }
        }
        
        if (!postToHandler(runnable)) {
            // If posting to handler failed, try on main thread
            Log.d(TAG, "Posting stopServer to main thread instead")
            mainHandler.post(runnable)
        }
    }
    
    // Start BLE advertising
    private fun startAdvertising() {
        Log.d(TAG, "startAdvertising() called")
        try {
            val bluetoothAdapter = bluetoothManager.adapter
            bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
            
            if (bluetoothLeAdvertiser == null) {
                Log.e(TAG, "BLE advertising not supported")
                return
            }
            
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()
            
            // Simplified advertising data - minimal data to reduce chances of failure
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false) // Don't include device name to reduce packet size
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
            
            Log.d(TAG, "Starting BLE advertising")
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising", e)
        }
    }
    
    // Start advertising with even simpler settings as a fallback
    private fun startAdvertisingWithSimpleSettings() {
        Log.d(TAG, "startAdvertisingWithSimpleSettings() called")
        try {
            val bluetoothAdapter = bluetoothManager.adapter
            bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
            
            if (bluetoothLeAdvertiser == null) {
                Log.e(TAG, "BLE advertising not supported")
                return
            }
            
            // Use the most basic settings
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER) // Use low power mode
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW) // Use low power
                .build()
            
            // Minimal advertising data
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
            
            Log.d(TAG, "Starting BLE advertising with simple settings")
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising with simple settings", e)
        }
    }
    
    // Stop BLE advertising
    private fun stopAdvertising() {
        Log.d(TAG, "stopAdvertising() called")
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            bluetoothLeAdvertiser = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising", e)
        }
    }
    
    // Update LED state and notify connected devices
    fun setLedState(on: Boolean) {
        Log.d(TAG, "setLedState() called with: on=$on")
        _ledState.value = on
        
        // Check permissions before notifying connected devices
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Cannot notify LED state change: Bluetooth permissions not granted")
            return
        }
        
        // Notify connected devices about the LED state change
        val runnable = Runnable {
            try {
                val service = bluetoothGattServer?.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(LED_CONTROL_CHARACTERISTIC_UUID)
                
                if (characteristic != null) {
                    characteristic.value = byteArrayOf(if (on) 1 else 0)
                    synchronized(connectedDevices) {
                        Log.d(TAG, "Notifying ${connectedDevices.size} devices about LED state change")
                        for (device in connectedDevices) {
                            try {
                                bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, false)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error notifying device ${device.address}", e)
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "LED characteristic not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying LED state change", e)
            }
        }
        
        if (!postToHandler(runnable)) {
            // If posting to handler failed, try on main thread
            Log.d(TAG, "Posting setLedState to main thread instead")
            mainHandler.post(runnable)
        }
    }
    
    // Update temperature data
    fun updateTemperature(temperature: Float) {
        Log.d(TAG, "updateTemperature() called with: temperature=$temperature")
        _temperatureData.value = temperature
        
        // Check permissions before notifying connected devices
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Cannot notify temperature change: Bluetooth permissions not granted")
            return
        }
        
        // Notify connected devices about temperature change
        val runnable = Runnable {
            try {
                val service = bluetoothGattServer?.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(TEMPERATURE_CHARACTERISTIC_UUID)
                
                if (characteristic != null) {
                    characteristic.value = temperature.toString().toByteArray()
                    synchronized(connectedDevices) {
                        Log.d(TAG, "Notifying ${connectedDevices.size} devices about temperature change")
                        for (device in connectedDevices) {
                            try {
                                bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, false)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error notifying device ${device.address}", e)
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Temperature characteristic not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying temperature change", e)
            }
        }
        
        if (!postToHandler(runnable)) {
            // If posting to handler failed, try on main thread
            Log.d(TAG, "Posting updateTemperature to main thread instead")
            mainHandler.post(runnable)
        }
    }
    
    // Clean up resources
    fun cleanup() {
        Log.d(TAG, "cleanup() called")
        
        // First stop the server on the main thread to ensure it completes
        mainHandler.post {
            try {
                Log.d(TAG, "Stopping server from cleanup")
                stopAdvertising()
                
                synchronized(connectedDevices) {
                    connectedDevices.clear()
                }
                _isConnected.value = false
                
                bluetoothGattServer?.close()
                bluetoothGattServer = null
            } catch (e: Exception) {
                Log.e(TAG, "Error in cleanup stopping server", e)
            }
            
            // Then quit the handler thread
            synchronized(threadLock) {
                try {
                    Log.d(TAG, "Quitting BLE handler thread")
                    isThreadActive = false
                    bleHandler = null
                    bleHandlerThread.quitSafely()
                } catch (e: Exception) {
                    Log.e(TAG, "Error quitting BLE handler thread", e)
                }
            }
        }
    }
}
