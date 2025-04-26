package com.example.smarthome.di

import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.smarthome.data.bluetooth.BluetoothGattServerService
import com.example.smarthome.data.bluetooth.BluetoothService
import com.example.smarthome.data.preferences.UserPreferencesRepository
import com.example.smarthome.data.repository.DeviceRepository
import com.example.smarthome.data.repository.RoomRepository
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
    fun provideBluetoothManager(@ApplicationContext context: Context): BluetoothManager {
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    
    @Provides
    @Singleton
    fun provideBluetoothService(@ApplicationContext context: Context): BluetoothService {
        return BluetoothService(context)
    }

    @Provides
    @Singleton
    fun provideDeviceRepository(bluetoothService: BluetoothService): DeviceRepository {
        return DeviceRepository(bluetoothService)
    }

    @Provides
    @Singleton
    fun provideRoomRepository(): RoomRepository {
        return RoomRepository()
    }
    
    @Provides
    @Singleton
    fun provideBluetoothGattServerService(
        @ApplicationContext context: Context,
        bluetoothManager: BluetoothManager
    ): BluetoothGattServerService {
        return BluetoothGattServerService(context, bluetoothManager)
    }
    
    @Provides
    @Singleton
    fun provideUserPreferencesRepository(
        @ApplicationContext context: Context
    ): UserPreferencesRepository {
        return UserPreferencesRepository(context)
    }
}
