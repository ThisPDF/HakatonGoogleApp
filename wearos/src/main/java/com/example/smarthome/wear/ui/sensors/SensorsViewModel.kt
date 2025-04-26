package com.example.smarthome.wear.ui.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.wear.data.repository.LocationRepository
import com.example.smarthome.wear.data.repository.SensorDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorsViewModel @Inject constructor(
    private val sensorDataRepository: SensorDataRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {
    
    val heartRate = sensorDataRepository.heartRate
    val steps = sensorDataRepository.steps
    val location = locationRepository.location
    
    private val _isSensorMeasuring = sensorDataRepository.isMeasuring
    private val _isLocationTracking = locationRepository.isTracking
    
    private val _isDataCollectionActive = MutableStateFlow(false)
    val isDataCollectionActive: StateFlow<Boolean> = _isDataCollectionActive.asStateFlow()
    
    init {
        viewModelScope.launch {
            combine(_isSensorMeasuring, _isLocationTracking) { sensorActive, locationActive ->
                sensorActive || locationActive
            }.collect {
                _isDataCollectionActive.value = it
            }
        }
    }
    
    fun startDataCollection() {
        viewModelScope.launch {
            sensorDataRepository.startMeasurement()
            locationRepository.startLocationTracking()
        }
    }
    
    fun stopDataCollection() {
        viewModelScope.launch {
            sensorDataRepository.stopMeasurement()
            locationRepository.stopLocationTracking()
        }
    }
    
    fun getLastLocation() {
        locationRepository.getLastLocation()
    }
    
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            sensorDataRepository.stopMeasurement()
            locationRepository.stopLocationTracking()
        }
    }
}
