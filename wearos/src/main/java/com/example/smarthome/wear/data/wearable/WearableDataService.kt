package com.example.smarthome.wear.data.wearable

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearableDataService @Inject constructor(
    @ApplicationContext private val context: Context
) : DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {

    private val TAG = "WearableDataService"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)
    
    private val _connectedNodes = MutableStateFlow<Set<String>>(emptySet())
    val connectedNodes: StateFlow<Set<String>> = _connectedNodes.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _sensorData = MutableStateFlow<Map<String, Any>>(emptyMap())
    val sensorData: StateFlow<Map<String, Any>> = _sensorData.asStateFlow()
    
    private val _locationData = MutableStateFlow<LocationData?>(null)
    val locationData: StateFlow<LocationData?> = _locationData.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Path constants for data items
    companion object {
        const val SENSOR_DATA_PATH = "/sensor_data"
        const val LOCATION_DATA_PATH = "/location_data"
        const val COMMAND_PATH = "/command"
        
        // Keys
        const val KEY_HEART_RATE = "heart_rate"
        const val KEY_STEPS = "steps"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_COMMAND = "command"
        
        // Commands
        const val COMMAND_START_SENSORS = "start_sensors"
        const val COMMAND_STOP_SENSORS = "stop_sensors"
    }
    
    init {
        // Register listeners
        dataClient.addListener(this)
        messageClient.addListener(this)
        
        // Find connected nodes
        scope.launch {
            updateConnectedNodes()
        }
        
        // Set initial connection state
        checkConnectionState()
    }
    
    /**
     * Update the list of connected nodes
     */
    private suspend fun updateConnectedNodes() {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val nodeIds = nodes.map { it.id }.toSet()
            _connectedNodes.value = nodeIds
            _isConnected.value = nodeIds.isNotEmpty()
            
            Log.d(TAG, "Connected nodes: $nodeIds")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get connected nodes", e)
            _isConnected.value = false
        }
    }
    
    private fun checkConnectionState() {
        serviceScope.launch {
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                _connectionState.value = if (nodes.isNotEmpty()) {
                    ConnectionState.CONNECTED
                } else {
                    ConnectionState.DISCONNECTED
                }
                Log.d(TAG, "Connection state: ${_connectionState.value}, nodes: ${nodes.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connection state: ${e.message}", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                _error.value = "Connection error: ${e.message}"
            }
        }
    }
    
    suspend fun requestSensorData() {
        try {
            val request = PutDataMapRequest.create("/sensor/request").apply {
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest()
            
            val result = dataClient.putDataItem(request).await()
            Log.d(TAG, "Sensor data request sent: ${result.uri}")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting sensor data: ${e.message}", e)
            _error.value = "Error requesting sensor data: ${e.message}"
        }
    }
    
    suspend fun requestLocationData() {
        try {
            val request = PutDataMapRequest.create("/location/request").apply {
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest()
            
            val result = dataClient.putDataItem(request).await()
            Log.d(TAG, "Location data request sent: ${result.uri}")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting location data: ${e.message}", e)
            _error.value = "Error requesting location data: ${e.message}"
        }
    }

    /**
     * Send sensor data to the connected phone
     */
    suspend fun sendSensorData(heartRate: Float, steps: Int) {
        try {
            val request = PutDataMapRequest.create(SENSOR_DATA_PATH).apply {
                dataMap.putFloat(KEY_HEART_RATE, heartRate)
                dataMap.putInt(KEY_STEPS, steps)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }

            val result = dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
            Log.d(TAG, "Sensor data sent successfully: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send sensor data", e)
        }
    }

    /**
     * Send location data to the connected phone
     */
    suspend fun sendLocationData(latitude: Double, longitude: Double) {
        try {
            val request = PutDataMapRequest.create(LOCATION_DATA_PATH).apply {
                dataMap.putDouble(KEY_LATITUDE, latitude)
                dataMap.putDouble(KEY_LONGITUDE, longitude)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }

            val result = dataClient.putDataItem(request.asPutDataRequest().setUrgent()).await()
            Log.d(TAG, "Location data sent successfully: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send location data", e)
        }
    }
    
    /**
     * Send a command to all connected nodes
     */
    suspend fun sendCommand(command: String) {
        try {
            val nodes = _connectedNodes.value
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected nodes to send command to")
                return
            }
            
            nodes.forEach { nodeId ->
                val result = messageClient.sendMessage(nodeId, COMMAND_PATH, command.toByteArray()).await()
                Log.d(TAG, "Sent command $command to node $nodeId: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command", e)
        }
    }
    
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                Log.d(TAG, "Data changed: $uri")
                val path = uri.path ?: return@forEach
                
                when (path) {
                    "/sensor/data" -> processSensorData(event.dataItem)
                    "/location/data" -> processLocationData(event.dataItem)
                    COMMAND_PATH -> processCommand(event.dataItem)
                    // Add more paths as needed
                }
            }
        }
    }
    
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")
        val path = messageEvent.path
        val data = String(messageEvent.data)
        
        Log.d(TAG, "Message received: $path, data: $data")
        
        when (path) {
            "/connection/status" -> {
                val status = String(messageEvent.data)
                _connectionState.value = if (status == "connected") {
                    ConnectionState.CONNECTED
                } else {
                    ConnectionState.DISCONNECTED
                }
            }
            COMMAND_PATH -> {
                // Process command from phone
                when (data) {
                    COMMAND_START_SENSORS -> {
                        // Handle start sensors command
                        Log.d(TAG, "Received command to start sensors")
                    }
                    COMMAND_STOP_SENSORS -> {
                        // Handle stop sensors command
                        Log.d(TAG, "Received command to stop sensors")
                    }
                }
            }
        }
    }
    
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        scope.launch {
            updateConnectedNodes()
        }
    }
    
    /**
     * Process a command from the data item
     */
    private fun processCommand(dataItem: DataItem) {
        try {
            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
            val command = dataMap.getString(KEY_COMMAND)
            
            if (command != null) {
                Log.d(TAG, "Received command: $command")
                
                when (command) {
                    COMMAND_START_SENSORS -> {
                        // Handle start sensors command
                        Log.d(TAG, "Processing command to start sensors")
                    }
                    COMMAND_STOP_SENSORS -> {
                        // Handle stop sensors command
                        Log.d(TAG, "Processing command to stop sensors")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process command", e)
        }
    }
    
    private fun processSensorData(dataItem: DataItem) {
        try {
            val dataMapItem = DataMapItem.fromDataItem(dataItem)
            val dataMap = dataMapItem.dataMap
            val sensorMap = mutableMapOf<String, Any>()
            
            // Process heart rate if available
            if (dataMap.containsKey("heartRate")) {
                sensorMap["heartRate"] = dataMap.getFloat("heartRate")
            }
            
            // Process step count if available
            if (dataMap.containsKey("steps")) {
                sensorMap["steps"] = dataMap.getInt("steps")
            }
            
            // Process accelerometer data if available
            if (dataMap.containsKey("accelerometer")) {
                val accDataMap = dataMap.getDataMap("accelerometer")
                if (accDataMap != null) {
                    val accData = AccelerometerData(
                        accDataMap.getFloat("x"),
                        accDataMap.getFloat("y"),
                        accDataMap.getFloat("z")
                    )
                    sensorMap["accelerometer"] = accData
                }
            }
            
            // Update the sensor data flow
            _sensorData.value = sensorMap
            Log.d(TAG, "Processed sensor data: $sensorMap")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing sensor data: ${e.message}", e)
        }
    }
    
    private fun processLocationData(dataItem: DataItem) {
        try {
            val dataMapItem = DataMapItem.fromDataItem(dataItem)
            val dataMap = dataMapItem.dataMap
            
            if (dataMap.containsKey("latitude") && dataMap.containsKey("longitude")) {
                val locationData = LocationData(
                    dataMap.getDouble("latitude"),
                    dataMap.getDouble("longitude"),
                    dataMap.getFloat("accuracy"),
                    dataMap.getLong("timestamp")
                )
                
                _locationData.value = locationData
                Log.d(TAG, "Processed location data: $locationData")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing location data: ${e.message}", e)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        dataClient.removeListener(this)
        messageClient.removeListener(this)
        capabilityClient.removeListener(this)
    }
    
    enum class ConnectionState {
        CONNECTED, DISCONNECTED
    }
    
    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val timestamp: Long
    )
    
    data class AccelerometerData(
        val x: Float,
        val y: Float,
        val z: Float
    )
}
