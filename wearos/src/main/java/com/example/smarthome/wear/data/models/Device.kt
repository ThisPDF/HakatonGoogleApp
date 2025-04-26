package com.example.smarthome.wear.data.models

data class Device(
    val id: String,
    val name: String,
    val type: String,
    val roomId: String,
    val isOn: Boolean = false,
    val value: String? = null,
    val quickActions: List<QuickAction> = emptyList()
)

data class QuickAction(
    val id: String,
    val name: String,
    val icon: String,
    val command: String
)
