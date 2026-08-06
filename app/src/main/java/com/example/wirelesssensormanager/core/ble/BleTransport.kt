package com.example.wirelesssensormanager.core.ble

import com.example.wirelesssensormanager.core.common.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

interface BleTransport {
    val connectionState: StateFlow<BleConnectionState>
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>
    val packets: Flow<BlePacket>
    suspend fun startScan(timeoutMillis: Long = 12_000)
    fun stopScan()
    suspend fun connect(address: String, expectedType: DeviceType)
    suspend fun disconnect()
    suspend fun read(service: UUID, characteristic: UUID): ByteArray
    suspend fun write(service: UUID, characteristic: UUID, value: ByteArray, withResponse: Boolean = true)
}
