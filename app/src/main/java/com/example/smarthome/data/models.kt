package com.example.smarthome.data

data class Room(
    val id: String,
    val name: String
)

data class Device(
    val id: String,
    val name: String,
    val type: DeviceType,
    val roomId: String,
    val isOn: Boolean,
    val value: String? = null
)

enum class DeviceType {
    LIGHT,
    THERMOSTAT,
    LOCK,
    SWITCH,
    SENSOR
}
