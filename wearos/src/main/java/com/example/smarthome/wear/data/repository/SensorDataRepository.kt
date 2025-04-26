package com.example.smarthome.wear.data.repository

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.SampleDataPoint
import com.example.smarthome.wear.data.wearable.WearableDataService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wearableDataService: WearableDataService
) {
    private val TAG = "SensorDataRepository"
    
    private val healthClient = HealthServices.getClient(context)
    private val measureClient = healthClient.measureClient
    
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    private val _heartRate = MutableStateFlow<Float?>(null)
    val heartRate: StateFlow<Float?> = _heartRate.asStateFlow()
    
    private val _steps = MutableStateFlow<Int?>(null)
    val steps: StateFlow<Int?> = _steps.asStateFlow()
    
    private val _accelerometerData = MutableStateFlow<AccelerometerData?>(null)
    val accelerometerData: StateFlow<AccelerometerData?> = _accelerometerData.asStateFlow()
    
    private val _sensorAvailability = MutableStateFlow<Map<DataType<*>, Boolean>>(emptyMap())
    val sensorAvailability: StateFlow<Map<DataType<*>, Boolean>> = _sensorAvailability.asStateFlow()
    
    private var heartRateCallback: MeasureCallback? = null
    private var stepsCallback: MeasureCallback? = null
    private var accelerometerCallback: MeasureCallback? = null
    
    init {
        checkSensorAvailability()
    }
    
    private fun checkSensorAvailability() {
        serviceScope.launch {
            try {
                val capabilities = measureClient.getCapabilities()
                val availabilityMap = mutableMapOf<DataType<*>, Boolean>()
                
                // Check heart rate availability
                val heartRateAvailable = capabilities.supportedDataTypeAvailability.contains(DataType.HEART_RATE_BPM)
                availabilityMap[DataType.HEART_RATE_BPM] = heartRateAvailable
                
                // Check steps availability
                val stepsAvailable = capabilities.supportedDataTypeAvailability.contains(DeltaDataType.STEPS)
                availabilityMap[DeltaDataType.STEPS] = stepsAvailable
                
                // Check accelerometer availability
                val accelerometerAvailable = capabilities.supportedDataTypeAvailability.contains(DataType.ACCELEROMETER_X)
                availabilityMap[DataType.ACCELEROMETER_X] = accelerometerAvailable
                
                _sensorAvailability.value = availabilityMap
                
                Log.d(TAG, "Sensor availability: $availabilityMap")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking sensor availability: ${e.message}", e)
            }
        }
    }
    
    fun startHeartRateMonitoring() {
        if (heartRateCallback != null) return
        
        try {
            heartRateCallback = object : MeasureCallback {
                override fun onAvailabilityChanged(dataType: DataType<*>, availability: Availability) {
                    if (dataType == DataType.HEART_RATE_BPM) {
                        val available = availability is DataTypeAvailability.Available
                        _sensorAvailability.value = _sensorAvailability.value.toMutableMap().apply {
                            put(DataType.HEART_RATE_BPM, available)
                        }
                    }
                }
                
                override fun onDataReceived(data: DataPointContainer) {
                    data.getData(DataType.HEART_RATE_BPM).firstOrNull()?.let { dataPoint ->
                        val heartRate = (dataPoint as SampleDataPoint).value
                        _heartRate.value = heartRate
                        Log.d(TAG, "Heart rate: $heartRate BPM")
                        
                        // Send to phone
                        sendSensorDataToPhone()
                    }
                }
            }
            
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback!!)
            Log.d(TAG, "Heart rate monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting heart rate monitoring: ${e.message}", e)
        }
    }
    
    fun startStepCounting() {
        if (stepsCallback != null) return
        
        try {
            stepsCallback = object : MeasureCallback {
                override fun onAvailabilityChanged(dataType: DataType<*>, availability: Availability) {
                    if (dataType == DeltaDataType.STEPS) {
                        val available = availability is DataTypeAvailability.Available
                        _sensorAvailability.value = _sensorAvailability.value.toMutableMap().apply {
                            put(DeltaDataType.STEPS, available)
                        }
                    }
                }
                
                override fun onDataReceived(data: DataPointContainer) {
                    data.getData(DeltaDataType.STEPS).firstOrNull()?.let { dataPoint ->
                        val steps = dataPoint.value.toInt()
                        _steps.value = steps
                        Log.d(TAG, "Steps: $steps")
                        
                        // Send to phone
                        sendSensorDataToPhone()
                    }
                }
            }
            
            measureClient.registerMeasureCallback(DeltaDataType.STEPS, stepsCallback!!)
            Log.d(TAG, "Step counting started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting step counting: ${e.message}", e)
        }
    }
    
    fun startAccelerometerMonitoring() {
        if (accelerometerCallback != null) return
        
        try {
            accelerometerCallback = object : MeasureCallback {
                override fun onAvailabilityChanged(dataType: DataType<*>, availability: Availability) {
                    if (dataType == DataType.ACCELEROMETER_X) {
                        val available = availability is DataTypeAvailability.Available
                        _sensorAvailability.value = _sensorAvailability.value.toMutableMap().apply {
                            put(DataType.ACCELEROMETER_X, available)
                        }
                    }
                }
                
                override fun onDataReceived(data: DataPointContainer) {
                    val x = data.getData(DataType.ACCELEROMETER_X).firstOrNull()?.value ?: return
                    val y = data.getData(DataType.ACCELEROMETER_Y).firstOrNull()?.value ?: return
                    val z = data.getData(DataType.ACCELEROMETER_Z).firstOrNull()?.value ?: return
                    
                    val accelerometerData = AccelerometerData(x, y, z)
                    _accelerometerData.value = accelerometerData
                    Log.d(TAG, "Accelerometer: $accelerometerData")
                    
                    // Send to phone
                    sendSensorDataToPhone()
                }
            }
            
            measureClient.registerMeasureCallback(DataType.ACCELEROMETER_X, accelerometerCallback!!)
            Log.d(TAG, "Accelerometer monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting accelerometer monitoring: ${e.message}", e)
        }
    }
    
    private fun sendSensorDataToPhone() {
        serviceScope.launch {
            try {
                wearableDataService.sendSensorData(
                    heartRate = _heartRate.value,
                    steps = _steps.value,
                    accelerometerData = _accelerometerData.value
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error sending sensor data to phone: ${e.message}", e)
            }
        }
    }
    
    fun stopMonitoring() {
        try {
            heartRateCallback?.let {
                measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, it)
                heartRateCallback = null
            }
            
            stepsCallback?.let {
                measureClient.unregisterMeasureCallback(DeltaDataType.STEPS, it)
                stepsCallback = null
            }
            
            accelerometerCallback?.let {
                measureClient.unregisterMeasureCallback(DataType.ACCELEROMETER_X, it)
                accelerometerCallback = null
            }
            
            Log.d(TAG, "All sensor monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping monitoring: ${e.message}", e)
        }
    }
    
    data class AccelerometerData(
        val x: Float,
        val y: Float,
        val z: Float
    )
}
