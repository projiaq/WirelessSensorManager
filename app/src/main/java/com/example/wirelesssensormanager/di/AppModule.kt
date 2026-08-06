package com.example.wirelesssensormanager.di

import android.content.Context
import androidx.room.Room
import com.example.wirelesssensormanager.core.ble.*
import com.example.wirelesssensormanager.core.database.*
import com.example.wirelesssensormanager.core.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
abstract class BindModule {
    @Binds @Singleton abstract fun bindTransport(value: AndroidBleTransport): BleTransport
    @Binds @Singleton abstract fun bindRepository(value: DefaultDeviceRepository): DeviceRepository
}

@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "wireless-sensors.db").build()
    @Provides fun dao(database: AppDatabase): AppDao = database.dao()
}
