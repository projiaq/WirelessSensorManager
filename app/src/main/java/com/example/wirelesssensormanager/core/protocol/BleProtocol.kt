package com.example.wirelesssensormanager.core.protocol

import com.example.wirelesssensormanager.core.common.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object BleUuids {
    val OTA_SERVICE: UUID = UUID.fromString("1d14d6ee-fd63-4fa1-bfa4-8f47b42119f0")
    val OTA_CONTROL: UUID = UUID.fromString("f7bf3564-fb6d-4e53-88a4-5e37e0326063")
    val OTA_DATA: UUID = UUID.fromString("984227f3-34fc-4045-a5d0-2c581f81a153")
    val DEVICE_INFO_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val MODEL_NUMBER: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val FIRMWARE_REVISION: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    val HARDWARE_REVISION: UUID = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
    val RECEIVER_SERVICE: UUID = UUID.fromString("6f8a0000-7d4b-4a8e-9b21-3c5d7e9f1000")
    val RECEIVER_COMMAND: UUID = UUID.fromString("6f8a0001-7d4b-4a8e-9b21-3c5d7e9f1000")
    val RECEIVER_RESPONSE: UUID = UUID.fromString("6f8a0002-7d4b-4a8e-9b21-3c5d7e9f1000")
    val RECEIVER_STREAM: UUID = UUID.fromString("6f8a0003-7d4b-4a8e-9b21-3c5d7e9f1000")
    val RECEIVER_STATUS: UUID = UUID.fromString("6f8a0004-7d4b-4a8e-9b21-3c5d7e9f1000")
    val SENSOR_DATA_SERVICE: UUID = UUID.fromString("c8e21e04-2d3a-4c65-b9d6-8e1a4b0f2c7d")
    val SENSOR_DATA: UUID = UUID.fromString("c8e21e05-2d3a-4c65-b9d6-8e1a4b0f2c7d")
    val SENSOR_CONFIG_SERVICE: UUID = UUID.fromString("d6e1f204-3a1b-4c72-b9a5-5e1f6c308d2a")
    val SENSOR_OFFSET: UUID = UUID.fromString("d6e1f205-3a1b-4c72-b9a5-5e1f6c308d2a")
    val SENSOR_RATE: UUID = UUID.fromString("d6e1f206-3a1b-4c72-b9a5-5e1f6c308d2a")
    val SENSOR_MAC: UUID = UUID.fromString("d6e1f207-3a1b-4c72-b9a5-5e1f6c308d2a")
    val SENSOR_INFO: UUID = UUID.fromString("d6e1f208-3a1b-4c72-b9a5-5e1f6c308d2a")
    val SENSOR_POWER: UUID = UUID.fromString("d6e1f209-3a1b-4c72-b9a5-5e1f6c308d2a")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

object OtaProtocol {
    const val BEGIN = 0xEE
    const val FINISH = 0x00
    const val CHUNK_SIZE = 128
}

object ReceiverProtocol {
    const val VERSION = 1
    const val SLOT_COUNT = 8
    const val SET_ID = 1
    const val SET_SLOT = 2
    const val CLEAR_SLOT = 3
    const val CLEAR_ALL = 4
    const val GET_SLOT = 5
    const val SET_RATE = 6
    const val GET_SENSOR_INFO = 7

    fun setId(id: Int) = byteArrayOf(VERSION.b(), SET_ID.b(), id.b(), (id ushr 8).b())
    fun setSlot(slot: Int, identity: SensorIdentity) = byteArrayOf(
        VERSION.b(), SET_SLOT.b(), slot.b(), identity.type.b(), identity.sensorId.b(), *identity.mac
    ).also { require(slot in 0 until SLOT_COUNT && identity.type != 0 && identity.sensorId != 0 && identity.mac.size == 6) }
    fun clearSlot(slot: Int) = byteArrayOf(VERSION.b(), CLEAR_SLOT.b(), slot.b()).also { require(slot in 0 until SLOT_COUNT) }
    fun clearAll() = byteArrayOf(VERSION.b(), CLEAR_ALL.b())
    fun getSlot(slot: Int) = byteArrayOf(VERSION.b(), GET_SLOT.b(), slot.b()).also { require(slot in 0 until SLOT_COUNT) }
    fun setRate(slot: Int, rate: Int) = byteArrayOf(VERSION.b(), SET_RATE.b(), slot.b(), rate.b()).also { require(slot in 0 until SLOT_COUNT && rate in 0..2) }
    fun getSensorInfo(slot: Int) = byteArrayOf(VERSION.b(), GET_SENSOR_INFO.b(), slot.b()).also { require(slot in 0 until SLOT_COUNT) }

    fun parseStatus(data: ByteArray): ReceiverStatus {
        require(data.size == 8) { "接收器状态长度应为 8，实际 ${data.size}" }
        require(data.u(0) == VERSION) { "不支持的协议版本 ${data.u(0)}" }
        return ReceiverStatus(data.le16(1), data.u(3), data.u(4), data.u(5), data.u(6), data.u(7))
    }

    fun parseResponse(data: ByteArray): ReceiverResponse {
        require(data.size == 15 || data.size == 39) { "接收器响应长度应为 15 或 39，实际 ${data.size}" }
        require(data.u(0) == VERSION) { "不支持的协议版本 ${data.u(0)}" }
        val opcode = data.u(1)
        require(opcode in SET_ID..GET_SENSOR_INFO) { "未知命令响应 $opcode" }
        val info = if (opcode == GET_SENSOR_INFO && data.u(2) == 0) parseSensorInfo(data.copyOfRange(15, 39), true) else null
        return ReceiverResponse(data.u(0), opcode, data.u(2), data.u(3), data.le16(4), data.u(6), data.u(7), data.copyOfRange(8, 14), data.u(14) != 0, info)
    }

    fun parseSensorInfo(data: ByteArray, viaReceiver: Boolean = false): SensorInfo {
        val minimum = if (viaReceiver) 24 else 18
        require(data.size >= minimum) { "传感器信息长度不足" }
        return SensorInfo(
            data.le32(0), data.le32(4), data.le16(8), data.u(10) != 0,
            data.u(11), data.le16(12), data.u(14), data.u(15), data.u(16), data.u(17),
            if (viaReceiver) data[18].toInt() else null,
            if (viaReceiver) data.u(19) else null,
            if (viaReceiver) data.le16(20) else 0,
            if (viaReceiver) data.le16(22) else 0
        )
    }

    fun statusMessage(code: Int) = when (code) { 0 -> "成功"; 1 -> "请求非法"; 2 -> "保存失败"; 3 -> "设备或操作不可用"; else -> "未知状态 $code" }
    fun matches(response: ReceiverResponse, opcode: Int, slot: Int) = response.opcode == opcode && (slot == 0xff || response.slot == slot)
}

object SensorProtocol {
    fun encodeOffset(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    fun decodeOffset(data: ByteArray): Int { require(data.size == 4); return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int }
    fun encodeRate(rate: Int) = byteArrayOf(rate.b()).also { require(rate in 0..2) }
    fun encodePower(mode: Int) = byteArrayOf(mode.b()).also { require(mode in 0..1) }
    fun parseInfo(data: ByteArray) = ReceiverProtocol.parseSensorInfo(data)
}

object ReadbackVerifier {
    fun verify(name: String, expected: ByteArray, actual: ByteArray) {
        check(actual.contentEquals(expected)) { "$name 回读不一致：期望 ${expected.toHex()}，实际 ${actual.toHex()}" }
    }
}

object Rs485Protocol {
    fun crc16Modbus(body: ByteArray): Int {
        var crc = 0xffff
        body.forEach { byte ->
            crc = crc xor (byte.toInt() and 0xff)
            repeat(8) { crc = if ((crc and 1) != 0) (crc ushr 1) xor 0xa001 else crc ushr 1 }
        }
        return crc and 0xffff
    }
    fun frame(body: ByteArray): ByteArray {
        require(body.size in 1..123)
        val crc = crc16Modbus(body)
        return byteArrayOf(0xaa.toByte(), 0xbb.toByte(), body.size.b(), *body, crc.b(), (crc ushr 8).b())
    }
}

class Rs485StreamDecoder {
    private val buffer = mutableListOf<Byte>()
    fun feed(chunk: ByteArray): List<ByteArray> {
        buffer.addAll(chunk.toList())
        val frames = mutableListOf<ByteArray>()
        while (true) {
            while (buffer.size >= 2 && !(buffer[0] == 0xaa.toByte() && buffer[1] == 0xbb.toByte())) buffer.removeAt(0)
            if (buffer.size < 3) break
            val bodyLength = buffer[2].toInt() and 0xff
            if (bodyLength !in 1..123) { buffer.removeAt(0); continue }
            val total = bodyLength + 5
            if (buffer.size < total) break
            val candidate = buffer.take(total).toByteArray()
            val body = candidate.copyOfRange(3, 3 + bodyLength)
            val expected = (candidate[3 + bodyLength].toInt() and 0xff) or ((candidate[4 + bodyLength].toInt() and 0xff) shl 8)
            if (Rs485Protocol.crc16Modbus(body) == expected) {
                frames += body
                repeat(total) { buffer.removeAt(0) }
            } else buffer.removeAt(0)
        }
        return frames
    }
}

fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }
private fun Int.b() = toByte()
private fun ByteArray.u(i: Int) = this[i].toInt() and 0xff
private fun ByteArray.le16(i: Int) = u(i) or (u(i + 1) shl 8)
private fun ByteArray.le32(i: Int) = (u(i).toLong() or (u(i + 1).toLong() shl 8) or (u(i + 2).toLong() shl 16) or (u(i + 3).toLong() shl 24)) and 0xffffffffL
