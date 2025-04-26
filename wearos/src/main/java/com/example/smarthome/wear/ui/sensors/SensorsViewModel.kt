package com.example.smarthome.wear.ui.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.wear.data.repository.SensorDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorsViewModel @Inject constructor(
    private val sensorDataRepository: SensorDataRepository
) : ViewModel() {
    
    private val _heartRate = MutableStateFlow(0f)
    val heartRate: StateFlow<Float> = _heartRate.asStateFlow()
    
    private val _steps = MutableStateFlow(0)
    val steps: StateFlow<Int> = _steps.asStateFlow()
    
    private val _isMeasuring = MutableStateFlow(false)
    val isMeasuring: StateFlow<Boolean> = _isMeasuring.asStateFlow()
    
    private val _sensorsAvailable = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val sensorsAvailable: StateFlow<Map<String, Boolean>> = _sensorsAvailable.asStateFlow()
    
    init {
        viewModelScope.launch {
            sensorDataRepository.heartRate.collectLatest { heartRate ->
                _heartRate.value = heartRate
            }
        }
        
        viewModelScope.launch {
            sensorDataRepository.steps.collectLatest { steps ->
                _steps.value = steps
            }
        }
        
        viewModelScope.launch {
            sensorDataRepository.isMeasuring.collectLatest { isMeasuring ->
                _isMeasuring.value = isMeasuring
            }
        }
    }
    
    fun checkSensorsAvailability() {
        viewModelScope.launch {
            _sensorsAvailable.value = sensorDataRepository.checkSensorsAvailability()
        }
    }
    
    fun startMeasurements() {
        viewModelScope.launch {
            sensorDataRepository.startHeartRateMeasurement()
            sensorDataRepository.startStepsMeasurement()
        }
    }
    
    fun stopMeasurements() {
        viewModelScope.launch {
            sensorDataRepository.stopMeasurements()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            sensorDataRepository.stopMeasurements()
        }
    }
}
