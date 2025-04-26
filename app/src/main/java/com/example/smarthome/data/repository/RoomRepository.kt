package com.example.smarthome.data.repository

import com.example.smarthome.data.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRepository @Inject constructor() {
    // Mock data for rooms
    fun getRooms(): Flow<List<Room>> = flow {
        val rooms = listOf(
            Room("living", "Living Room"),
            Room("kitchen", "Kitchen"),
            Room("bedroom", "Bedroom"),
            Room("entrance", "Entrance")
        )
        emit(rooms)
    }
}
