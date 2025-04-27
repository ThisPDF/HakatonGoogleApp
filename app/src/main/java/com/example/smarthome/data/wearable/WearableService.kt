package com.example.smarthome.data.wearable

import android.content.Context
import android.util.Log
import com.example.smarthome.data.Device
import com.example.smarthome.data.DeviceType
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearableService @Inject constructor(
    @ApplicationContext private val context: Context
) : DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {

    private val TAG = "WearableService"
    
    // Paths for data and messages
    companion object {
        const val DEVICE_LIST_PATH = "/device_list"
        const val DEVICE_CONTROL_PATH = "/device_control"
        const val TEMPERATURE_PATH = "/temperature"
        const val MESSAGE_PATH = "/message"
    }
    
    // Clients for communication
    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    
    // Coroutine scope
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    // JSON serialization
    private val gson = Gson()
    
    // State flows
    private val _connectedNodes = MutableStateFlow<List<String>>(emptyList())
    val connectedNodes: StateFlow<List<String>> = _connectedNodes.asStateFlow()
    
    private val _lastCommand = MutableStateFlow<DeviceCommand?>(null)
    val lastCommand: StateFlow<DeviceCommand?> = _lastCommand.asStateFlow()
    
    init {
        // Register listeners
        dataClient.addListener(this)
        messageClient.addListener(this)
        
        // Get connected nodes
        refreshConnectedNodes()
    }
    
    // Refresh the list of connected wearable nodes
    fun refreshConnectedNodes() {
        serviceScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                val nodeIds = nodes.map { it.id }
                _connectedNodes.value = nodeIds
                Log.d(TAG, "Connected nodes: $nodeIds")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting connected nodes", e)
            }
        }
    }
    
    // Send device list to wearable
    fun sendDeviceList(devices: List<Device>) {
        serviceScope.launch {
            try {
                // Convert devices to a simpler format for wearable
                val wearableDevices = devices.map { device ->
                    mapOf(
                        "id" to device.id,
                        "name" to device.name,
                        "type" to device.type.name,
                        "roomId" to device.roomId,
                        "isOn" to device.isOn,
                        "value" to device.value
                    )
                }
                
                // Convert to JSON
                val devicesJson = gson.toJson(wearableDevices)
                
                // Create data map
                val dataMap = PutDataMapRequest.create(DEVICE_LIST_PATH).apply {
                    dataMap.putString("devices", devicesJson)
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }
                
                // Send to wearable
                val request = dataMap.asPutDataRequest()
                val result = dataClient.putDataItem(request).await()
                Log.d(TAG, "Device list sent to wearable: ${result.uri}")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending device list to wearable", e)
            }
        }
    }
    
    // Send temperature to wearable
    fun sendTemperature(temperature: Float) {
        serviceScope.launch {
            try {
                // Create data map
                val dataMap = PutDataMapRequest.create(TEMPERATURE_PATH).apply {
                    dataMap.putFloat("temperature", temperature)
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }
                
                // Send to wearable
                val request = dataMap.asPutDataRequest()
                val result = dataClient.putDataItem(request).await()
                Log.d(TAG, "Temperature sent to wearable: ${result.uri}")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending temperature to wearable", e)
            }
        }
    }
    
    // Send a message to all connected wearables
    fun sendMessage(path: String, data: ByteArray) {
        serviceScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                for (node in nodes) {
                    messageClient.sendMessage(node.id, path, data).await()
                    Log.d(TAG, "Message sent to node ${node.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message to wearable", e)
            }
        }
    }
    
    // Handle data changed events
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                val path = uri.path ?: ""
                
                when (path) {
                    DEVICE_CONTROL_PATH -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        handleDeviceControl(dataMap)
                    }
                }
            }
        }
    }
    
    // Handle messages from wearable
    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val data = messageEvent.data
        
        when (path) {
            DEVICE_CONTROL_PATH -> {
                val json = String(data)
                handleDeviceControlMessage(json)
            }
            MESSAGE_PATH -> {
                val message = String(data)
                Log.d(TAG, "Message received: $message")
            }
        }
    }
    
    // Handle device control data
    private fun handleDeviceControl(dataMap: DataMap) {
        try {
            val deviceId = dataMap.getString("deviceId", "")
            val action = dataMap.getString("action", "")
            val value = dataMap.getString("value", null)
            
            if (deviceId.isNotEmpty() && action.isNotEmpty()) {
                _lastCommand.value = DeviceCommand(deviceId, action, value)
                Log.d(TAG, "Device control received: deviceId=$deviceId, action=$action, value=$value")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling device control", e)
        }
    }
    
    // Handle device control message
    private fun handleDeviceControlMessage(json: String) {
        try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val map: Map<String, String> = gson.fromJson(json, type)
            
            val deviceId = map["deviceId"] ?: ""
            val action = map["action"] ?: ""
            val value = map["value"]
            
            if (deviceId.isNotEmpty() && action.isNotEmpty()) {
                _lastCommand.value = DeviceCommand(deviceId, action, value)
                Log.d(TAG, "Device control message received: deviceId=$deviceId, action=$action, value=$value")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling device control message", e)
        }
    }
    
    // Clean up
    fun cleanup() {
        dataClient.removeListener(this)
        messageClient.removeListener(this)
    }
    
    // Data class for device commands
    data class DeviceCommand(
        val deviceId: String,
        val action: String,
        val value: String?
    )
}
