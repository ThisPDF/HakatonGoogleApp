package com.example.smarthome.wear.data.models

data class QuickAction(
    val id: String,
    val name: String,
    val actionType: String, // Changed from 'type' to 'actionType' to avoid confusion with Device.type
    val deviceId: String? = null
)
