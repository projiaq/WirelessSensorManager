package com.example.wirelesssensormanager.core.database

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val diagnosticsKey = booleanPreferencesKey("diagnostics_enabled")
    val diagnosticsEnabled = context.dataStore.data.map { it[diagnosticsKey] ?: true }
    suspend fun setDiagnosticsEnabled(value: Boolean) { context.dataStore.edit { it[diagnosticsKey] = value } }
}
