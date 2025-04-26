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
    private lateinit var bleHandler: Handler
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    init {
        // Start the BLE handler thread
        bleHandlerThread.start()
        bleHandler = Handler(bleHandlerThread.looper)
    }

    // First, add a method to check for Bluetooth permissions
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
    }
    
    // Callback for GATT server events
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
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
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Bluetooth permissions not granted")
                bluetoothGattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                )
                return
            }
            
            when (characteristic.uuid) {
                LED_CONTROL_CHARACTERISTIC_UUID -> {
                    val value = byteArrayOf(if (_ledState.value) 1 else 0)
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value
                    )
                    Log.i(TAG, "LED state read: ${_ledState.value}")
                }
                TEMPERATURE_CHARACTERISTIC_UUID -> {
                    val temperature = _temperatureData.value ?: 0f
                    val value = temperature.toString().toByteArray()
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value
                    )
                    Log.i(TAG, "Temperature read: $temperature")
                }
                else -> {
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                    )
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
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Bluetooth permissions not granted")
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                    )
                }
                return
            }
            
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
                    if (responseNeeded) {
                        bluetoothGattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                        )
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
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Bluetooth permissions not granted")
                bluetoothGattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                )
                return
            }
            
            if (descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR_UUID) {
                val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                bluetoothGattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value
                )
            } else {
                bluetoothGattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                )
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
            if (!hasBluetoothPermissions()) {
                Log.e(TAG, "Bluetooth permissions not granted")
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                    )
                }
                return
            }
            
            if (descriptor.uuid == CLIENT_CONFIG_DESCRIPTOR_UUID) {
                // Handle notification/indication subscription
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(
                        device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null
                    )
                }
            } else if (responseNeeded) {
                bluetoothGattServer?.sendResponse(
                    device, requestId, BluetoothGatt.GATT_FAILURE, 0, null
                )
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
            bleHandler.postDelayed({
                startAdvertisingWithSimpleSettings()
            }, 2000)
        }
    }
    
    // Start the GATT server and begin advertising
    fun startServer() {
        // Run on background thread
        bleHandler.post {
            try {
                // Check for permissions first
                if (!hasBluetoothPermissions()) {
                    Log.e(TAG, "Cannot start GATT server: Bluetooth permissions not granted")
                    return@post
                }
            
                val bluetoothAdapter = bluetoothManager.adapter
                
                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                    Log.e(TAG, "Bluetooth is not enabled")
                    return@post
                }
                
                // Initialize the GATT server
                bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)
                
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
                bluetoothGattServer?.addService(service)
                
                // Start advertising
                startAdvertising()
                
                Log.i(TAG, "GATT server started")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting GATT server", e)
            }
        }
    }
    
    // Stop the GATT server and advertising
    fun stopServer() {
        bleHandler.post {
            try {
                stopAdvertising()
                
                synchronized(connectedDevices) {
                    connectedDevices.clear()
                }
                _isConnected.value = false
                
                bluetoothGattServer?.close()
                bluetoothGattServer = null
                
                Log.i(TAG, "GATT server stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping GATT server", e)
            }
        }
    }
    
    // Start BLE advertising
    private fun startAdvertising() {
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
            
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising", e)
        }
    }
    
    // Start advertising with even simpler settings as a fallback
    private fun startAdvertisingWithSimpleSettings() {
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
            
            bluetoothLeAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising with simple settings", e)
        }
    }
    
    // Stop BLE advertising
    private fun stopAdvertising() {
        try {
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            bluetoothLeAdvertiser = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising", e)
        }
    }
    
    // Update LED state and notify connected devices
    fun setLedState(on: Boolean) {
        _ledState.value = on
        
        // Check permissions before notifying connected devices
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Cannot notify LED state change: Bluetooth permissions not granted")
            return
        }
        
        // Notify connected devices about the LED state change
        bleHandler.post {
            try {
                val service = bluetoothGattServer?.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(LED_CONTROL_CHARACTERISTIC_UUID)
                
                if (characteristic != null) {
                    characteristic.value = byteArrayOf(if (on) 1 else 0)
                    synchronized(connectedDevices) {
                        for (device in connectedDevices) {
                            bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying LED state change", e)
            }
        }
    }
    
    // Update temperature data
    fun updateTemperature(temperature: Float) {
        _temperatureData.value = temperature
        
        // Check permissions before notifying connected devices
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Cannot notify temperature change: Bluetooth permissions not granted")
            return
        }
        
        // Notify connected devices about temperature change
        bleHandler.post {
            try {
                val service = bluetoothGattServer?.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(TEMPERATURE_CHARACTERISTIC_UUID)
                
                if (characteristic != null) {
                    characteristic.value = temperature.toString().toByteArray()
                    synchronized(connectedDevices) {
                        for (device in connectedDevices) {
                            bluetoothGattServer?.notifyCharacteristicChanged(device, characteristic, false)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying temperature change", e)
            }
        }
    }
    
    // Clean up resources
    fun cleanup() {
        stopServer()
        bleHandlerThread.quitSafely()
    }
}
