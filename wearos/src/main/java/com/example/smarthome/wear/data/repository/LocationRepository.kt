package com.example.smarthome.wear.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
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
import kotlinx.coroutines.SupervisorJob
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)
    
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()
    
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()
    
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 
        10000 // 10 seconds
    ).build()
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                _location.value = location
                
                // Send location data to phone
                scope.launch {
                    wearableDataService.sendLocationData(
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }
                
                Log.d(TAG, "Location: ${location.latitude}, ${location.longitude}")
            }
        }
    }
    
    /**
     * Start tracking location
     */
    @SuppressLint("MissingPermission")
    fun startLocationTracking() {
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            _isTracking.value = true
            Log.d(TAG, "Started location tracking")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location tracking", e)
        }
    }
    
    /**
     * Stop tracking location
     */
    fun stopLocationTracking() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            _isTracking.value = false
            Log.d(TAG, "Stopped location tracking")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop location tracking", e)
        }
    }
    
    /**
     * Get last known location
     */
    @SuppressLint("MissingPermission")
    fun getLastLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    _location.value = it
                    
                    // Send location data to phone
                    scope.launch {
                        wearableDataService.sendLocationData(
                            latitude = it.latitude,
                            longitude = it.longitude
                        )
                    }
                    
                    Log.d(TAG, "Last location: ${it.latitude}, ${it.longitude}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last location", e)
        }
    }
}
