package com.example.smarthome.di

import android.content.Context
import com.example.smarthome.data.bluetooth.BluetoothGattServerService
import com.example.smarthome.data.preferences.UserPreferences
import com.example.smarthome.data.repository.DeviceRepository
import com.example.smarthome.data.repository.RoomRepository
import com.example.smarthome.data.wearable.WearableService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }
    
    @Provides
    @Singleton
    fun provideBluetoothGattServerService(@ApplicationContext context: Context): BluetoothGattServerService {
        return BluetoothGattServerService(context)
    }
    
    @Provides
    @Singleton
    fun provideWearableService(@ApplicationContext context: Context): WearableService {
        return WearableService(context)
    }
    
    @Provides
    @Singleton
    fun provideDeviceRepository(wearableService: WearableService): DeviceRepository {
        return DeviceRepository(wearableService)
    }
    
    @Provides
    @Singleton
    fun provideRoomRepository(): RoomRepository {
        return RoomRepository()
    }
}
