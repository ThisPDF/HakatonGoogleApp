package com.example.smarthome.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Create a DataStore instance at the top level
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    val useDarkTheme: Boolean? = null,
    val useDynamicColor: Boolean? = true,
    val notificationsEnabled: Boolean = true
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore
    
    companion object {
        val USE_DARK_THEME = booleanPreferencesKey("use_dark_theme")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }
    
    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data.map { preferences ->
        UserPreferences(
            useDarkTheme = preferences[USE_DARK_THEME],
            useDynamicColor = preferences[USE_DYNAMIC_COLOR],
            notificationsEnabled = preferences[NOTIFICATIONS_ENABLED] ?: true
        )
    }
    
    suspend fun updateDarkTheme(useDarkTheme: Boolean) {
        dataStore.edit { preferences ->
            preferences[USE_DARK_THEME] = useDarkTheme
        }
    }
    
    suspend fun updateDynamicColor(useDynamicColor: Boolean) {
        dataStore.edit { preferences ->
            preferences[USE_DYNAMIC_COLOR] = useDynamicColor
        }
    }
    
    suspend fun updateNotifications(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }
}
