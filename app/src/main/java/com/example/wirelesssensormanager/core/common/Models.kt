package com.example.wirelesssensormanager.core.common

import java.util.UUID

enum class DeviceType { RECEIVER, PRESSURE_SENSOR, TILT_SENSOR, SENSOR, UNKNOWN }

enum class BleConnectionState {
    BLUETOOTH_UNAVAILABLE, PERMISSION_REQUIRED, IDLE, SCANNING, DEVICE_FOUND,
    CONNECTING, CONNECTED, DISCOVERING_SERVICES, NEGOTIATING_MTU,
    SUBSCRIBING_NOTIFICATIONS, PROTOCOL_HANDSHAKE, READY, EXECUTING,
    DISCONNECTING, DISCONNECTED, TIMEOUT, ERROR
}

data class DiscoveredDevice(
    val address: String,
    val name: String?,
    val type: DeviceType,
    val rssi: Int,
    val serviceUuids: Set<UUID>,
    val lastSeenMillis: Long = System.currentTimeMillis()
)

data class BlePacket(
    val address: String,
    val service: UUID,
    val characteristic: UUID,
    val value: ByteArray,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class SensorIdentity(val type: Int, val sensorId: Int, val mac: ByteArray) {
    val displayMac: String get() = mac.joinToString(":") { "%02X".format(it) }
}

data class BindingSlot(
    val index: Int,
    val sensorType: Int = 0,
    val sensorId: Int = 0,
    val mac: ByteArray = ByteArray(6),
    val online: Boolean = false
) { val occupied: Boolean get() = sensorType != 0 && sensorId != 0 && mac.any { it.toInt() != 0 } }

data class ReceiverStatus(
    val receiverId: Int,
    val boundBitmap: Int,
    val onlineBitmap: Int,
    val validBitmap: Int,
    val slotCount: Int,
    val fastBitmap: Int
)

data class ReceiverResponse(
    val version: Int, val opcode: Int, val status: Int, val slot: Int,
    val receiverId: Int, val sensorType: Int, val sensorId: Int,
    val mac: ByteArray, val online: Boolean, val sensorInfo: SensorInfo? = null
)

data class SensorInfo(
    val workSeconds: Long, val bootSeconds: Long, val voltageMv: Int,
    val online: Boolean, val dataRate: Int, val errors: Int, val protocolVersion: Int,
    val sensorType: Int, val pa5: Int, val powerMode: Int,
    val rssi: Int? = null, val receiverFlags: Int? = null,
    val invalidSamples: Int = 0, val missedNotifications: Int = 0
)

data class DeviceVersionInfo(val model: String?, val hardware: String?, val firmware: String?)

data class SensorSample(
    val slot: Int? = null, val uptimeSeconds: Long, val temperature: Int,
    val pressure: Int, val raw: Long, val xAngle: Int, val yAngle: Int,
    val xRaw: Int, val yRaw: Int, val zRaw: Int, val sequence: Int,
    val voltageVolts: Float? = null, val readOk: Boolean? = null,
    val receivedAt: Long = System.currentTimeMillis()
)

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
}
