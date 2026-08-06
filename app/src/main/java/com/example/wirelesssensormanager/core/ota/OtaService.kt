package com.example.wirelesssensormanager.core.ota

import android.content.ContentResolver
import android.net.Uri
import com.example.wirelesssensormanager.core.ble.BleTransport
import com.example.wirelesssensormanager.core.common.DeviceType
import com.example.wirelesssensormanager.core.common.DiscoveredDevice
import com.example.wirelesssensormanager.core.protocol.BleUuids
import com.example.wirelesssensormanager.core.protocol.OtaProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class OtaProgress(
    val sent: Long, val total: Long, val state: String, val error: String? = null,
    val firmwareVersion: String? = null, val logs: List<String> = emptyList()
) { val fraction get() = if (total == 0L) 0f else (sent.toFloat() / total).coerceIn(0f, 1f) }

class OtaService(private val transport: BleTransport) {
    suspend fun readLocal(resolver: ContentResolver, uri: Uri): ByteArray =
        resolver.openInputStream(uri)?.use { it.readBytes() }?.also { require(it.isNotEmpty()) { "固件文件为空" } }
            ?: error("无法读取固件文件")

    suspend fun download(url: String, onProgress: suspend (OtaProgress) -> Unit): ByteArray = withContext(Dispatchers.IO) {
        require(url.startsWith("https://") || url.startsWith("http://")) { "固件 URL 必须以 http:// 或 https:// 开头" }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000; connection.readTimeout = 60_000; connection.instanceFollowRedirects = true
        try {
            require(connection.responseCode in 200..299) { "固件下载失败 HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong.coerceAtLeast(0)
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer); if (count < 0) break
                    output.write(buffer, 0, count)
                    onProgress(OtaProgress(output.size().toLong(), total, "下载固件"))
                }
            }
            output.toByteArray().also { require(it.isNotEmpty()) { "下载的固件为空" } }
        } finally { connection.disconnect() }
    }

    suspend fun update(original: DiscoveredDevice, firmware: ByteArray, onProgress: suspend (OtaProgress) -> Unit): String? = try {
        performUpdate(original, firmware, onProgress)
    } catch (error: Throwable) {
        runCatching { transport.disconnect() }
        throw error
    }

    private suspend fun performUpdate(original: DiscoveredDevice, firmware: ByteArray, onProgress: suspend (OtaProgress) -> Unit): String? {
        require(firmware.isNotEmpty()) { "固件文件为空" }
        val logs = mutableListOf<String>()
        suspend fun report(sent: Long, state: String, version: String? = null) {
            logs += state
            onProgress(OtaProgress(sent, firmware.size.toLong(), state, firmwareVersion = version, logs = logs.takeLast(200)))
        }
        report(0, "请求设备进入 DFU")
        runCatching { transport.write(BleUuids.OTA_SERVICE, BleUuids.OTA_CONTROL, byteArrayOf(OtaProtocol.ENTER_DFU.toByte()), true) }
        delay(800)
        transport.disconnect()

        report(0, "扫描 DFU 设备")
        val dfu = awaitDevice(30_000) { device -> isMatchingDfu(device, original.address) }
        report(0, "连接 DFU：${dfu.address}")
        transport.connect(dfu.address, DeviceType.OTA)
        transport.write(BleUuids.OTA_SERVICE, BleUuids.OTA_CONTROL, byteArrayOf(OtaProtocol.BEGIN.toByte()), true)
        delay(200)

        var offset = 0
        while (offset < firmware.size) {
            coroutineContext.ensureActive()
            val end = minOf(offset + OtaProtocol.CHUNK_SIZE, firmware.size)
            try {
                transport.write(BleUuids.OTA_SERVICE, BleUuids.OTA_DATA, firmware.copyOfRange(offset, end), false)
            } catch (error: Throwable) {
                throw IllegalStateException("固件块写入失败 offset=$offset；已终止且不会重发", error)
            }
            offset = end
            report(offset.toLong(), "传输固件 $offset/${firmware.size}")
        }
        runCatching { transport.write(BleUuids.OTA_SERVICE, BleUuids.OTA_CONTROL, byteArrayOf(OtaProtocol.FINISH.toByte()), true) }
        transport.disconnect(); delay(2_000)

        report(firmware.size.toLong(), "等待设备恢复正常模式")
        val normal = awaitDevice(30_000) { it.address.equals(original.address, true) && it.type != DeviceType.OTA }
        transport.connect(normal.address, original.type)
        val version = runCatching { transport.read(BleUuids.DEVICE_INFO_SERVICE, BleUuids.FIRMWARE_REVISION).toString(Charsets.UTF_8).trimEnd('\u0000') }.getOrNull()
        report(firmware.size.toLong(), "升级完成${version?.let { "，版本 $it" }.orEmpty()}", version)
        return version
    }

    private suspend fun awaitDevice(timeout: Long, predicate: (DiscoveredDevice) -> Boolean): DiscoveredDevice = coroutineScope {
        val scan = launch { transport.startScan(timeout) }
        try { withTimeout(timeout) { transport.discoveredDevices.first { list -> list.any(predicate) }.first(predicate) } }
        finally { transport.stopScan(); scan.cancelAndJoin() }
    }

    private fun isMatchingDfu(device: DiscoveredDevice, originalAddress: String): Boolean {
        if (device.type != DeviceType.OTA) return false
        val normalized = originalAddress.filter(Char::isLetterOrDigit).uppercase()
        return device.address.equals(originalAddress, true) || normalized in device.advertisedAddresses
    }
}
