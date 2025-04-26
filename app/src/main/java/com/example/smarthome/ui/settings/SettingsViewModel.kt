package com.example.smarthome.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthome.data.preferences.UserPreferencesViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val controllerIp: String = "192.168.1.100",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesViewModel: UserPreferencesViewModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    val userPreferences = userPreferencesViewModel.userPreferences

    fun toggleDarkMode() {
        userPreferencesViewModel.toggleDarkTheme()
    }

    fun toggleNotifications() {
        userPreferencesViewModel.toggleNotifications()
    }

    fun updateControllerIp(ip: String) {
        _uiState.update { it.copy(controllerIp = ip) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, saveSuccess = false) }
            
            try {
                // In a real app, save settings to SharedPreferences or DataStore
                // For now, just simulate a delay
                kotlinx.coroutines.delay(1000)
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }
}
