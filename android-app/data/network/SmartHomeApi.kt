package com.example.smarthome.data.network

import com.example.smarthome.data.Device
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SmartHomeApi {
    @GET("devices")
    suspend fun getDevices(): List<Device>
    
    @POST("devices/{id}/toggle")
    suspend fun toggleDevice(@Path("id") deviceId: String)
    
    @POST("devices/{id}/value")
    suspend fun updateDeviceValue(
        @Path("id") deviceId: String,
        @Body value: String
    )
}
