package com.example.smarthome.wear.data.models

data class Device(
    val id: String,
    val name: String,
    val type: String,
    val roomId: String,
    val isOn: Boolean,
    val value: String? = null
)

data class QuickAction(
    val id: String,
    val name: String,
    val type: String
)
