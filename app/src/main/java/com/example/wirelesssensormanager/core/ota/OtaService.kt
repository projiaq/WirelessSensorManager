package com.example.wirelesssensormanager.core.ota

import android.content.ContentResolver
import android.net.Uri
import com.example.wirelesssensormanager.core.ble.BleTransport
import com.example.wirelesssensormanager.core.protocol.BleUuids
import com.example.wirelesssensormanager.core.protocol.OtaProtocol
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

data class OtaProgress(val sent: Long, val total: Long, val state: String, val error: String? = null) {
    val fraction get() = if (total == 0L) 0f else (sent.toFloat() / total).coerceIn(0f, 1f)
}

class OtaService(private val transport: BleTransport) {
    suspend fun update(contentResolver: ContentResolver, uri: Uri, onProgress: suspend (OtaProgress) -> Unit) {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取固件文件")
        require(bytes.isNotEmpty()) { "固件文件为空" }
        onProgress(OtaProgress(0, bytes.size.toLong(), "进入 DFU"))
        transport.write(BleUuids.OTA_SERVICE, BleUuids.OTA_CONTROL, byteArrayOf(OtaProtocol.BEGIN.toByte()), true)
        var offset = 0
        while (offset < bytes.size) {
            coroutineContext.ensureActive()
            val end = minOf(offset + OtaProtocol.CHUNK_SIZE, bytes.size)
            // Silicon Labs GBL transfer: a failed write is reported immediately; never replay a block.
            transport.write(BleUuids.OTA_SERVICE, BleUuids.OTA_DATA, bytes.copyOfRange(offset, end), false)
            offset = end
            onProgress(OtaProgress(offset.toLong(), bytes.size.toLong(), "传输固件"))
        }
        transport.write(BleUuids.OTA_SERVICE, BleUuids.OTA_CONTROL, byteArrayOf(OtaProtocol.FINISH.toByte()), true)
        onProgress(OtaProgress(bytes.size.toLong(), bytes.size.toLong(), "完成"))
    }
}
