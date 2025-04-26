package com.example.smarthome.wear.data.models

data class HeartRateData(
    val heartRate: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class StepsData(
    val steps: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class SensorDataPackage(
    val heartRate: Double? = null,
    val steps: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)
