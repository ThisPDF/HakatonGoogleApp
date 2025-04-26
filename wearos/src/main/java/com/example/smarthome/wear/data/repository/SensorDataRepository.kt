package com.example.smarthome.wear.data.repository

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.HeartRateAccuracy
import androidx.health.services.client.data.HeartRateData
import androidx.health.services.client.data.StepsData
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
    
    private val measureCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DataType, availability: DataTypeAvailability) {
            Log.d(TAG, "Availability changed for $dataType: $availability")
        }

        override fun onDataReceived(data: DataPointContainer) {
            // Process heart rate data
            data.getData(DataType.HEART_RATE_BPM).firstOrNull()?.let { dataPoint ->
                val heartRateData = dataPoint.value as HeartRateData
                val heartRateBpm = heartRateData.bpm
                val accuracy = heartRateData.accuracy
                
                if (accuracy != HeartRateAccuracy.UNKNOWN) {
                    _heartRate.value = heartRateBpm
                    
                    // Send heart rate data to phone
                    scope.launch {
                        wearableDataService.sendSensorData(heartRateBpm, _steps.value)
                    }
                    
                    Log.d(TAG, "Heart rate: $heartRateBpm BPM, accuracy: $accuracy")
                }
            }
            
            // Process steps data
            data.getData(DataType.STEPS).firstOrNull()?.let { dataPoint ->
                val stepsData = dataPoint.value as StepsData
                val stepsCount = stepsData.count
                
                _steps.value = stepsCount
                
                // Send steps data to phone
                scope.launch {
                    wearableDataService.sendSensorData(_heartRate.value, stepsCount)
                }
                
                Log.d(TAG, "Steps: $stepsCount")
            }
        }
    }
    
    /**
     * Start measuring heart rate and steps
     */
    suspend fun startMeasurement() {
        try {
            val capabilities = measureClient.getCapabilities()
            
            if (DataType.HEART_RATE_BPM in capabilities.supportedDataTypes &&
                DataType.STEPS in capabilities.supportedDataTypes) {
                
                val request = androidx.health.services.client.MeasureRequest.Builder()
                    .addDataType(DataType.HEART_RATE_BPM)
                    .addDataType(DataType.STEPS)
                    .build()
                
                measureClient.registerMeasureCallback(request, measureCallback)
                _isMeasuring.value = true
                Log.d(TAG, "Started measuring heart rate and steps")
            } else {
                Log.w(TAG, "Heart rate or steps measurement not supported on this device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start measurement", e)
        }
    }
    
    /**
     * Stop measuring heart rate and steps
     */
    suspend fun stopMeasurement() {
        try {
            measureClient.unregisterMeasureCallback(measureCallback)
            _isMeasuring.value = false
            Log.d(TAG, "Stopped measuring heart rate and steps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop measurement", e)
        }
    }
}
