package com.example.smarthome.data.wearable

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
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
class WearableDataService @Inject constructor(
    @ApplicationContext private val context: Context
) : DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {

    private val TAG = "WearableDataService"
    
    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _sensorData = MutableStateFlow<Map<String, Any>>(emptyMap())
    val sensorData: StateFlow<Map<String, Any>> = _sensorData.asStateFlow()
    
    private val _locationData = MutableStateFlow<LocationData?>(null)
    val locationData: StateFlow<LocationData?> = _locationData.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    init {
        // Register listeners
        dataClient.addListener(this)
        messageClient.addListener(this)
        
        // Set initial connection state
        checkConnectionState()
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
    
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                Log.d(TAG, "Data changed: $uri")
                
                when (uri.path) {
                    "/sensor/data" -> processSensorData(event.dataItem)
                    "/location/data" -> processLocationData(event.dataItem)
                }
            }
        }
    }
    
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")
    }
    
    private fun processSensorData(dataItem: DataItem) {
        try {
            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
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
                val accData = AccelerometerData(
                    accDataMap.getFloat("x"),
                    accDataMap.getFloat("y"),
                    accDataMap.getFloat("z")
                )
                sensorMap["accelerometer"] = accData
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
            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
            
            if (dataMap.containsKey("latitude") && dataMap.containsKey("longitude")) {
                val locationData = LocationData(
                    dataMap.getDouble("latitude"),
                    dataMap.getDouble("longitude"),
                    dataMap.getFloat("accuracy"),
                    dataMap.getLong("locationTimestamp")
                )
                
                _locationData.value = locationData
                Log.d(TAG, "Processed location data: $locationData")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing location data: ${e.message}", e)
        }
    }
    
    fun cleanup() {
        dataClient.removeListener(this)
        messageClient.removeListener(this)
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
