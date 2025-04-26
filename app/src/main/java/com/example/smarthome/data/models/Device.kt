package com.example.smarthome.data.models

data class Device(
    val id: String,
    val name: String,
    val type: String,
    val roomId: String,
    val isOn: Boolean = false,
    val brightness: Int? = null,
    val temperature: Int? = null,
    val isLocked: Boolean? = null
)

data class QuickAction(
    val id: String,
    val name: String,
    val actionType: String,
    val deviceId: String? = null
)
