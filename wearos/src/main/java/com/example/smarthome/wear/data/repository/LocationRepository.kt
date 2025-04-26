package com.example.smarthome.wear.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.smarthome.wear.data.wearable.WearableDataService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wearableDataService: WearableDataService
) {
    private val TAG = "LocationRepository"
    
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    private val _locationData = MutableStateFlow<LocationData?>(null)
    val locationData: StateFlow<LocationData?> = _locationData.asStateFlow()
    
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    
    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()
    
    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000) // 10 seconds
        .setWaitForAccurateLocation(false)
        .setMinUpdateIntervalMillis(5000) // 5 seconds
        .setMaxUpdateDelayMillis(15000) // 15 seconds
        .build()
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val locationData = LocationData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    timestamp = location.time
                )
                
                _locationData.value = locationData
                Log.d(TAG, "Location update: $locationData")
                
                // Send to phone
                sendLocationToPhone(locationData)
            }
        }
    }
    
    init {
        checkLocationPermission()
    }
    
    private fun checkLocationPermission() {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        _hasPermission.value = fineLocationPermission || coarseLocationPermission
    }
    
    fun startLocationTracking() {
        if (!_hasPermission.value) {
            Log.e(TAG, "Cannot start location tracking: missing permissions")
            return
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            
            _isTracking.value = true
            Log.d(TAG, "Location tracking started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception when requesting location updates: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location tracking: ${e.message}", e)
        }
    }
    
    fun stopLocationTracking() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            _isTracking.value = false
            Log.d(TAG, "Location tracking stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location tracking: ${e.message}", e)
        }
    }
    
    private fun sendLocationToPhone(locationData: LocationData) {
        serviceScope.launch {
            try {
                wearableDataService.sendLocationData(locationData)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending location to phone: ${e.message}", e)
            }
        }
    }
    
    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val timestamp: Long
    )
}
