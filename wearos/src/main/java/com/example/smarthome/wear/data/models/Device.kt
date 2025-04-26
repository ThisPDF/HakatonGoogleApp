package com.example.smarthome.wear.data.models

data class Device(
    val id: String,
    val name: String,
    val type: String,
    val roomId: String,
    val isOn: Boolean = false,
    val value: String? = null
)
