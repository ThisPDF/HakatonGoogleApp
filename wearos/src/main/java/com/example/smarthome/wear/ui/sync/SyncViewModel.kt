package com.example.smarthome.wear.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.wear.data.wearable.WearableService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val wearableService: WearableService
) : ViewModel() {

    enum class SyncState {
        CHECKING,
        CONNECTING,
        SYNCING,
        COMPLETE,
        ERROR
    }
    
    private val _syncState = MutableStateFlow(SyncState.CHECKING)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private val _phoneConnected = MutableStateFlow(false)
    val phoneConnected: StateFlow<Boolean> = _phoneConnected.asStateFlow()
    
    init {
        checkConnection()
    }
    
    private fun checkConnection() {
        viewModelScope.launch {
            try {
                _syncState.value = SyncState.CHECKING
                
                // Wait for phone node to be found
                delay(2000) // Give time for node discovery
                val phoneNodeId = wearableService.phoneNodeId.first()
                
                if (phoneNodeId != null) {
                    _phoneConnected.value = true
                    _syncState.value = SyncState.CONNECTING
                    
                    // Wait for initial data
                    delay(1000)
                    _syncState.value = SyncState.SYNCING
                    
                    // Wait for device list
                    val timeout = System.currentTimeMillis() + 5000 // 5 second timeout
                    while (wearableService.devices.value.isEmpty() && System.currentTimeMillis() < timeout) {
                        delay(500)
                    }
                    
                    // Success if we have devices or timeout reached
                    _syncState.value = SyncState.COMPLETE
                } else {
                    _phoneConnected.value = false
                    _syncState.value = SyncState.ERROR
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.ERROR
            }
        }
    }
    
    fun retry() {
        checkConnection()
    }
}
