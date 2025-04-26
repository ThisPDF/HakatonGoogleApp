package com.example.smarthome.wear.di

import android.content.Context
import com.example.smarthome.wear.data.repository.LocationRepository
import com.example.smarthome.wear.data.repository.SensorDataRepository
import com.example.smarthome.wear.data.wearable.WearableDataService
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
    fun provideWearableDataService(@ApplicationContext context: Context): WearableDataService {
        return WearableDataService(context)
    }
    
    @Provides
    @Singleton
    fun provideSensorDataRepository(
        @ApplicationContext context: Context,
        wearableDataService: WearableDataService
    ): SensorDataRepository {
        return SensorDataRepository(context, wearableDataService)
    }
    
    @Provides
    @Singleton
    fun provideLocationRepository(
        @ApplicationContext context: Context,
        wearableDataService: WearableDataService
    ): LocationRepository {
        return LocationRepository(context, wearableDataService)
    }
}
