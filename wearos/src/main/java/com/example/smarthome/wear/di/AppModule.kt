package com.example.smarthome.wear.di

import android.content.Context
import com.example.smarthome.wear.data.bluetooth.BluetoothService
import com.example.smarthome.wear.data.repository.DeviceRepository
import com.example.smarthome.wear.data.wearable.WearableService
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
    fun provideBluetoothService(@ApplicationContext context: Context): BluetoothService {
        return BluetoothService(context)
    }
    
    @Provides
    @Singleton
    fun provideWearableService(@ApplicationContext context: Context): WearableService {
        return WearableService(context)
    }

    @Provides
    @Singleton
    fun provideDeviceRepository(
        wearableService: WearableService
    ): DeviceRepository {
        return DeviceRepository(wearableService)
    }
}
