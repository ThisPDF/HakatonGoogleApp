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
import kotlinx.coroutines.SupervisorJob
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val healthClient = HealthServices.getClient(context)
    private val measureClient = healthClient.measureClient
    
    private val _heartRate = MutableStateFlow(0f)
    val heartRate: StateFlow<Float> = _heartRate.asStateFlow()
    
    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps.asStateFlow()
    
    private val _isMeasuring = MutableStateFlow(false)
    val isMeasuring: StateFlow<Boolean> = _isMeasuring.asStateFlow()
    
    private var heartRateCallback: MeasureCallback? = null
    private var stepsCallback: MeasureCallback? = null
    
    /**
     * Start measuring heart rate
     */
    suspend fun startHeartRateMeasurement() {
        try {
            if (heartRateCallback != null) return
            
            heartRateCallback = object : MeasureCallback {
                override fun onAvailabilityChanged(
                    dataType: DeltaDataType<*, *>,
                    availability: Availability
                ) {
                    Log.d(TAG, "Heart rate availability changed: $availability")
                }
                
                override fun onDataReceived(dataPoints: DataPointContainer) {
                    dataPoints.getData(DataType.HEART_RATE_BPM).firstOrNull()?.let { dataPoint ->
                        val heartRateBpm = (dataPoint as SampleDataPoint<Float>).value
                        _heartRate.value = heartRateBpm
                        
                        // Send heart rate data to phone
                        scope.launch {
                            wearableDataService.sendSensorData(heartRateBpm, _steps.value)
                        }
                        
                        Log.d(TAG, "Heart rate: $heartRateBpm BPM")
                    }
                }
            }
            
            measureClient.registerMeasureCallback(
                DataType.HEART_RATE_BPM,
                heartRateCallback!!
            )
            
            _isMeasuring.value = true
            Log.d(TAG, "Started measuring heart rate")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start heart rate measurement", e)
        }
    }
    
    /**
     * Start measuring steps
     */
    suspend fun startStepsMeasurement() {
        try {
            if (stepsCallback != null) return
            
            stepsCallback = object : MeasureCallback {
                override fun onAvailabilityChanged(
                    dataType: DeltaDataType<*, *>,
                    availability: Availability
                ) {
                    Log.d(TAG, "Steps availability changed: $availability")
                }
                
                override fun onDataReceived(dataPoints: DataPointContainer) {
                    dataPoints.getData(DataType.STEPS).firstOrNull()?.let { dataPoint ->
                        val stepsCount = (dataPoint as SampleDataPoint<Int>).value
                        _steps.value = stepsCount
                        
                        // Send steps data to phone
                        scope.launch {
                            wearableDataService.sendSensorData(_heartRate.value, stepsCount)
                        }
                        
                        Log.d(TAG, "Steps: $stepsCount")
                    }
                }
            }
            
            measureClient.registerMeasureCallback(
                DataType.STEPS,
                stepsCallback!!
            )
            
            _isMeasuring.value = true
            Log.d(TAG, "Started measuring steps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start steps measurement", e)
        }
    }
    
    /**
     * Stop all measurements
     */
    suspend fun stopMeasurements() {
        try {
            heartRateCallback?.let {
                measureClient.unregisterCallback(it)
                heartRateCallback = null
            }
            
            stepsCallback?.let {
                measureClient.unregisterCallback(it)
                stepsCallback = null
            }
            
            _isMeasuring.value = false
            Log.d(TAG, "Stopped all measurements")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop measurements", e)
        }
    }
    
    /**
     * Check if sensors are available
     */
    suspend fun checkSensorsAvailability(): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        
        try {
            val capabilities = measureClient.capabilities
            
            result["heartRate"] = DataType.HEART_RATE_BPM in capabilities.supportedDataTypes
            result["steps"] = DataType.STEPS in capabilities.supportedDataTypes
            
            Log.d(TAG, "Sensors availability: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check sensors availability", e)
        }
        
        return result
    }
}
