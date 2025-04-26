package com.example.smarthome.data.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserPreferencesViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = userPreferencesRepository.userPreferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    fun toggleDarkTheme() {
        viewModelScope.launch {
            val currentValue = userPreferences.value.useDarkTheme ?: false
            userPreferencesRepository.updateDarkTheme(!currentValue)
        }
    }

    fun toggleDynamicColor() {
        viewModelScope.launch {
            val currentValue = userPreferences.value.useDynamicColor ?: true
            userPreferencesRepository.updateDynamicColor(!currentValue)
        }
    }

    fun toggleNotifications() {
        viewModelScope.launch {
            val currentValue = userPreferences.value.notificationsEnabled
            userPreferencesRepository.updateNotifications(!currentValue)
        }
    }
}
