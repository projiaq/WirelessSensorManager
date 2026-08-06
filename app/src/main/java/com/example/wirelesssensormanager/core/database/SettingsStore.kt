package com.example.wirelesssensormanager.core.database

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val diagnosticsKey = booleanPreferencesKey("diagnostics_enabled")
    private val maintainerKey = booleanPreferencesKey("site_maintainer")
    private val mineModeKey = booleanPreferencesKey("site_mine_mode")
    private val rssiKey = intPreferencesKey("scan_rssi_threshold")
    private val lastReceiverKey = stringPreferencesKey("last_receiver")
    private val lastSensorKey = stringPreferencesKey("last_sensor")
    val diagnosticsEnabled = context.dataStore.data.map { it[diagnosticsKey] ?: true }
    val siteSettings = context.dataStore.data.map { SiteSettings(it[maintainerKey] ?: false, it[mineModeKey] ?: false, it[rssiKey] ?: -100, it[lastReceiverKey], it[lastSensorKey]) }
    suspend fun setDiagnosticsEnabled(value: Boolean) { context.dataStore.edit { it[diagnosticsKey] = value } }
    suspend fun enterMaintainer(password: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256").digest(password.trim().toByteArray()).joinToString("") { "%02x".format(it) }
        val valid = digest == MAINTAINER_PASSWORD_SHA256
        if (valid) context.dataStore.edit { it[maintainerKey] = true }
        return valid
    }
    suspend fun exitMaintainer() { context.dataStore.edit { it[maintainerKey] = false } }
    suspend fun setMineMode(value: Boolean) { context.dataStore.edit { it[mineModeKey] = value } }
    suspend fun setRssiThreshold(value: Int) { context.dataStore.edit { it[rssiKey] = value.coerceIn(-120, -20) } }
    suspend fun saveLastDevice(receiver: Boolean, address: String, name: String?) { context.dataStore.edit { it[if (receiver) lastReceiverKey else lastSensorKey] = "$address|${name.orEmpty()}" } }
    suspend fun saveZero(address: String, x: Int, y: Int) { context.dataStore.edit { it[stringPreferencesKey("zero_${address.filter(Char::isLetterOrDigit)}")] = "$x|$y" } }
    suspend fun readZero(address: String): Pair<Int, Int> = context.dataStore.data.first()[stringPreferencesKey("zero_${address.filter(Char::isLetterOrDigit)}")]?.split('|')?.let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) } ?: (0 to 0)
    companion object { private const val MAINTAINER_PASSWORD_SHA256 = "f214f185f5690ee02464c3918280cb840be7a08bed00a078bca70d4180ece023" }
}

data class SiteSettings(val maintainer: Boolean, val mineMode: Boolean, val rssiThreshold: Int, val lastReceiver: String?, val lastSensor: String?)
