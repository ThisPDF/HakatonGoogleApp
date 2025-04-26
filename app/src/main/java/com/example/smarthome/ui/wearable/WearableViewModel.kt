package com.example.smarthome.ui.wearable

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.data.wearable.WearableDataService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WearableViewModel @Inject constructor(
    private val wearableDataService: WearableDataService
) : ViewModel() {
    private val TAG = "WearableViewModel"
    
    private val _uiState = MutableStateFlow(WearableUiState())
    val uiState: StateFlow<WearableUiState> = _uiState.asStateFlow()
    
    init {
        observeConnectionState()
        observeSensorData()
        observeLocationData()
        observeErrors()
    }
    
    private fun observeConnectionState() {
        viewModelScope.launch {
            wearableDataService.connectionState.collect { state ->
                _uiState.update { 
                    it.copy(
                        isConnected = state == WearableDataService.ConnectionState.CONNECTED,
                        connectionStatus = when (state) {
                            WearableDataService.ConnectionState.CONNECTED -> "Connected"
                            WearableDataService.ConnectionState.DISCONNECTED -> "Disconnected"
                        }
                    )
                }
            }
        }
    }
    
    private fun observeSensorData() {
        viewModelScope.launch {
            wearableDataService.sensorData.collect { sensorData ->
                val heartRate = sensorData["heartRate"] as? Float
                val steps = sensorData["steps"] as? Int
                val accelerometer = sensorData["accelerometer"] as? WearableDataService.AccelerometerData
                
                _uiState.update { 
                    it.copy(
                        heartRate = heartRate,
                        steps = steps,
                        accelerometerData = accelerometer?.let { acc ->
                            AccelerometerData(acc.x, acc.y, acc.z)
                        }
                    )
                }
            }
        }
    }
    
    private fun observeLocationData() {
        viewModelScope.launch {
            wearableDataService.locationData.collect { locationData ->
                _uiState.update { 
                    it.copy(
                        locationData = locationData?.let { loc ->
                            LocationData(
                                loc.latitude,
                                loc.longitude,
                                loc.accuracy,
                                loc.timestamp
                            )
                        }
                    )
                }
            }
        }
    }
    
    private fun observeErrors() {
        viewModelScope.launch {
            wearableDataService.error.collect { error ->
                if (error != null) {
                    _uiState.update { it.copy(error = error) }
                    Log.e(TAG, "Wearable error: $error")
                } else {
                    _uiState.update { it.copy(error = null) }
                }
            }
        }
    }
    
    fun requestSensorData() {
        viewModelScope.launch {
            try {
                wearableDataService.requestSensorData()
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting sensor data: ${e.message}", e)
                _uiState.update { it.copy(error = "Error requesting sensor data: ${e.message}") }
            }
        }
    }
    
    fun requestLocationData() {
        viewModelScope.launch {
            try {
                wearableDataService.requestLocationData()
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting location data: ${e.message}", e)
                _uiState.update { it.copy(error = "Error requesting location data: ${e.message}") }
            }
        }
    }
    
    data class WearableUiState(
        val isConnected: Boolean = false,
        val connectionStatus: String = "Disconnected",
        val heartRate: Float? = null,
        val steps: Int? = null,
        val accelerometerData: AccelerometerData? = null,
        val locationData: LocationData? = null,
        val error: String? = null
    )
    
    data class AccelerometerData(
        val x: Float,
        val y: Float,
        val z: Float
    )
    
    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val timestamp: Long
    )
}
