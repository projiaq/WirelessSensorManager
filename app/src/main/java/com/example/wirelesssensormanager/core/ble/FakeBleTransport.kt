package com.example.wirelesssensormanager.core.ble

import com.example.wirelesssensormanager.core.common.*
import com.example.wirelesssensormanager.core.protocol.BleUuids
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.util.UUID

class FakeBleTransport : BleTransport {
    private val _state = MutableStateFlow(BleConnectionState.IDLE)
    override val connectionState = _state.asStateFlow()
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices = _devices.asStateFlow()
    private val _packets = MutableSharedFlow<BlePacket>(extraBufferCapacity = 8)
    override val packets = _packets.asSharedFlow()
    val writes = mutableListOf<ByteArray>()
    var responseProvider: (UUID, ByteArray) -> ByteArray? = { _, _ -> null }

    override suspend fun startScan(timeoutMillis: Long) {
        _state.value = BleConnectionState.SCANNING
        _devices.value = listOf(
            DiscoveredDevice("02:00:00:00:00:01", "AIOT_0001_#001", DeviceType.RECEIVER, -48, setOf(BleUuids.RECEIVER_SERVICE)),
            DiscoveredDevice("02:00:00:00:00:02", "TILT-0002", DeviceType.SENSOR, -55, setOf(BleUuids.SENSOR_DATA_SERVICE))
        )
        _state.value = BleConnectionState.DEVICE_FOUND
    }
    override fun stopScan() { if (_state.value == BleConnectionState.SCANNING) _state.value = BleConnectionState.IDLE }
    override suspend fun connect(address: String, expectedType: DeviceType) { _state.value = BleConnectionState.CONNECTING; delay(10); _state.value = BleConnectionState.READY }
    override suspend fun disconnect() { _state.value = BleConnectionState.DISCONNECTED }
    override suspend fun read(service: UUID, characteristic: UUID) = responseProvider(characteristic, byteArrayOf()) ?: error("Fake 未配置读取值")
    override suspend fun write(service: UUID, characteristic: UUID, value: ByteArray, withResponse: Boolean) {
        writes += value.copyOf()
        responseProvider(characteristic, value)?.let { _packets.emit(BlePacket("fake", service, BleUuids.RECEIVER_RESPONSE, it)) }
    }
}
