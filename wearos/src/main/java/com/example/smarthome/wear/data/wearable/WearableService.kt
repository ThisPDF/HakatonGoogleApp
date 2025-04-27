package com.example.smarthome.wear.data.wearable

import android.content.Context
import android.util.Log
import com.example.smarthome.wear.data.models.Device
import com.google.android.gms.wearable.*
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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearableService @Inject constructor(
    @ApplicationContext private val context: Context
) : DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {
    
    private val TAG = "WearableService"
    
    // Paths for data and messages
    companion object {
        const val PATH_DEVICE_LIST = "/device_list"
        const val PATH_DEVICE_CONTROL = "/device_control"
        const val PATH_TEMPERATURE = "/temperature"
        const val PATH_CONNECTION_STATUS = "/connection_status"
    }
    
    // State flows for observing data
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    private val _temperature = MutableStateFlow<Float?>(null)
    val temperature: StateFlow<Float?> = _temperature.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Clients for Wearable API
    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    
    // JSON serialization
    private val gson = Gson()
    
    // Coroutine scope
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    init {
        // Register listeners
        dataClient.addListener(this)
        messageClient.addListener(this)
        
        // Initial data fetch
        coroutineScope.launch {
            fetchInitialData()
        }
    }
    
    // Fetch initial data from the phone
    private suspend fun fetchInitialData() {
        try {
            // Get connected nodes (phones)
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.d(TAG, "No connected nodes found")
                _connectionStatus.value = false
                return
            }
            
            _connectionStatus.value = true
            
            // Request device list
            val deviceListItems = dataClient.getDataItems(
                Uri.Builder()
                    .scheme(PutDataRequest.WEAR_URI_SCHEME)
                    .path(PATH_DEVICE_LIST)
                    .build()
            ).await()
            
            if (deviceListItems.count > 0) {
                val dataItem = deviceListItems.get(0)
                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                val jsonString = dataMap.getString("devices", "[]")
                processDeviceListData(jsonString)
            }
            
            // Request temperature
            val temperatureItems = dataClient.getDataItems(
                Uri.Builder()
                    .scheme(PutDataRequest.WEAR_URI_SCHEME)
                    .path(PATH_TEMPERATURE)
                    .build()
            ).await()
            
            if (temperatureItems.count > 0) {
                val dataItem = temperatureItems.get(0)
                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                val temperature = dataMap.getFloat("value", 0f)
                _temperature.value = temperature
            }
            
            deviceListItems.release()
            temperatureItems.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching initial data", e)
            _error.value = "Error fetching initial data: ${e.message}"
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
        try {
            // Get connected nodes (phones)
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.d(TAG, "No connected nodes found")
                _error.value = "No connected phone found"
                return@withContext false
            }
            
            // Format the command
            val command = if (value != null) {
                "$action:$deviceId:$value"
            } else {
                "$action:$deviceId:"
            }
            
            // Send message to all connected nodes
            var success = false
            for (node in nodes) {
                try {
                    messageClient.sendMessage(node.id, PATH_DEVICE_CONTROL, command.toByteArray()).await()
                    success = true
                    Log.d(TAG, "Sent device command to node ${node.displayName}: $command")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending message to node ${node.displayName}", e)
                }
            }
            
            if (!success) {
                _error.value = "Failed to send device command to any node"
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
    
    // Handle data changed events
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            when (event.type) {
                DataEvent.TYPE_CHANGED -> {
                    val uri = event.dataItem.uri
                    when (uri.path) {
                        PATH_DEVICE_LIST -> {
                            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                            val jsonString = dataMap.getString("devices", "[]")
                            processDeviceListData(jsonString)
                        }
                        PATH_TEMPERATURE -> {
                            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                            val temperature = dataMap.getFloat("value", 0f)
                            _temperature.value = temperature
                            Log.d(TAG, "Received temperature update: $temperature")
                        }
                        PATH_CONNECTION_STATUS -> {
                            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                            val status = dataMap.getBoolean("connected", false)
                            _connectionStatus.value = status
                            Log.d(TAG, "Received connection status update: $status")
                        }
                    }
                }
                DataEvent.TYPE_DELETED -> {
                    // Handle deleted data items if needed
                }
            }
        }
    }
    
    // Handle message received events
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_DEVICE_CONTROL -> {
                // Confirmation of device control, could update UI if needed
                Log.d(TAG, "Received device control confirmation")
            }
            PATH_CONNECTION_STATUS -> {
                val status = String(messageEvent.data).toBoolean()
                _connectionStatus.value = status
                Log.d(TAG, "Received connection status message: $status")
            }
        }
    }
    
    // Check connection to phone
    suspend fun checkPhoneConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodes = nodeClient.connectedNodes.await()
            val connected = nodes.isNotEmpty()
            _connectionStatus.value = connected
            return@withContext connected
        } catch (e: Exception) {
            Log.e(TAG, "Error checking phone connection", e)
            _connectionStatus.value = false
            return@withContext false
        }
    }
    
    // Clean up resources
    fun cleanup() {
        dataClient.removeListener(this)
        messageClient.removeListener(this)
    }
}
