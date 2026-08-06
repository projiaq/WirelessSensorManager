package com.example.wirelesssensormanager

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wirelesssensormanager.core.common.*
import com.example.wirelesssensormanager.core.database.*
import com.example.wirelesssensormanager.core.repository.DeviceRepository
import com.example.wirelesssensormanager.feature.binding.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*
import java.io.File
import android.net.Uri
import com.example.wirelesssensormanager.core.ota.*
import javax.inject.Inject

data class MainUiState(
    val connection: BleConnectionState = BleConnectionState.IDLE,
    val devices: List<DiscoveredDevice> = emptyList(),
    val selected: DiscoveredDevice? = null,
    val receiverStatus: ReceiverStatus? = null,
    val slots: List<BindingSlot> = List(8) { BindingSlot(it) },
    val sensorInfo: SensorInfo? = null,
    val deviceVersion: DeviceVersionInfo? = null,
    val sensorOffset: Int? = null,
    val sensorRate: Int? = null,
    val powerMode: Int? = null,
    val samples: Map<Int, SensorSample> = emptyMap(),
    val slotInfos: Map<Int, SensorInfo> = emptyMap(),
    val lastSampleAt: Long = 0L,
    val maintainerMode: Boolean = false,
    val mineMode: Boolean = false,
    val rssiThreshold: Int = -100,
    val scanQuery: String = "",
    val sortDescending: Boolean = true,
    val lastReceiver: String? = null,
    val lastSensor: String? = null,
    val zeroX: Int = 0,
    val zeroY: Int = 0,
    val sampleState: String = "等待数据",
    val stagedSensor: SensorIdentity? = null,
    val logs: List<DiagnosticLogEntity> = emptyList(),
    val operations: List<OperationEntity> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val bindingCoordinator: BindingCoordinator,
    private val settings: SettingsStore
    , private val otaService: OtaService
) : ViewModel() {
    private val local = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = combine(local, repository.connectionState, repository.devices, repository.logs, repository.operations) { s, connection, devices, logs, operations ->
        s.copy(connection = connection, devices = devices, logs = logs, operations = operations)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())
    private val _ota = MutableStateFlow<OtaProgress?>(null)
    val ota: StateFlow<OtaProgress?> = _ota.asStateFlow()
    private var otaJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var expectedDisconnectUntil = 0L
    private var watchdogRecovering = false
    init {
        viewModelScope.launch { settings.siteSettings.collect { site -> local.update { it.copy(maintainerMode = site.maintainer, mineMode = site.mineMode, rssiThreshold = site.rssiThreshold, lastReceiver = site.lastReceiver, lastSensor = site.lastSensor) } } }
        viewModelScope.launch { repository.connectionState.collect { connection ->
            if (connection == BleConnectionState.READY) reconnectAttempts = 0
            if (connection in listOf(BleConnectionState.DISCONNECTED, BleConnectionState.ERROR, BleConnectionState.TIMEOUT) && local.value.selected != null && otaJob?.isActive != true && System.currentTimeMillis() >= expectedDisconnectUntil && reconnectAttempts < 5 && reconnectJob?.isActive != true) {
                val device = local.value.selected ?: return@collect
                val delays = longArrayOf(1_000, 2_000, 4_000, 8_000, 15_000)
                reconnectJob = viewModelScope.launch { delay(delays[reconnectAttempts++]); runCatching { repository.connect(device); if (device.type == DeviceType.RECEIVER) refreshReceiver() else refreshSensor() } }
            }
        } }
        viewModelScope.launch {
            repository.packets.collect { packet ->
                runCatching {
                    val sample = when (packet.characteristic) {
                        com.example.wirelesssensormanager.core.protocol.BleUuids.RECEIVER_STREAM -> { val slot = packet.value.first().toInt() and 0xff; com.example.wirelesssensormanager.core.protocol.SensorProtocol.parseSample(packet.value, 1, slot, local.value.slotInfos[slot]?.protocolVersion) }
                        com.example.wirelesssensormanager.core.protocol.BleUuids.SENSOR_DATA -> com.example.wirelesssensormanager.core.protocol.SensorProtocol.parseSample(packet.value, protocolVersion = local.value.sensorInfo?.protocolVersion)
                        else -> null
                    }
                    if (sample != null) { watchdogRecovering = false; local.update { it.copy(samples = it.samples + ((sample.slot ?: -1) to sample), lastSampleAt = sample.receivedAt, sampleState = if (sample.readOk == false) "采样错误" else "正常") } }
                }
            }
        }
        viewModelScope.launch(Dispatchers.Default) { while (isActive) {
            delay(1_000)
            val snapshot = local.value
            val age = System.currentTimeMillis() - snapshot.lastSampleAt
            val timeout = if (snapshot.selected?.type == DeviceType.RECEIVER) { if ((snapshot.receiverStatus?.fastBitmap ?: 0) != 0) 10_000L else 25_000L } else if (snapshot.sensorRate == 1) 5_000L else 15_000L
            if (snapshot.lastSampleAt > 0 && age > timeout) local.update { it.copy(sampleState = "数据超时") }
            if (snapshot.lastSampleAt > 0 && age > timeout + 3_000 && repository.connectionState.value == BleConnectionState.READY && !watchdogRecovering && otaJob?.isActive != true) {
                watchdogRecovering = true
                runCatching { repository.disconnect() }
            }
        } }
    }
    fun startOta(context: Context, uri: Uri) = startOtaJob { otaService.readLocal(context.contentResolver, uri) }
    fun startOtaUrl(url: String) = startOtaJob { otaService.download(url) { _ota.value = it } }
    fun cancelOta() { otaJob?.cancel(); otaJob = null; _ota.update { it?.copy(state = "已取消") } }

    fun scan() = execute { repository.scan() }
    fun stopScan() = repository.stopScan()
    fun connect(device: DiscoveredDevice) = execute {
        expectedDisconnectUntil = 0L
        repository.connect(device)
        settings.saveLastDevice(device.type == DeviceType.RECEIVER, device.address, device.name)
        local.update { it.copy(selected = device) }
        if (device.type != DeviceType.RECEIVER) { val zero = settings.readZero(device.address); local.update { it.copy(zeroX = zero.first, zeroY = zero.second) } }
        if (device.type == DeviceType.RECEIVER) refreshReceiver() else refreshSensor()
    }
    fun disconnect() = execute { reconnectJob?.cancel(); local.update { it.copy(selected = null) }; repository.disconnect() }
    fun refreshReceiverAction() = execute { refreshReceiver() }
    fun refreshSensorAction() = execute { refreshSensor() }
    fun readSlotInfo(slot: Int) = execute { val info = repository.readReceiverSensorInfo(slot); local.update { it.copy(slotInfos = it.slotInfos + (slot to info)) } }
    fun setSlotRate(slot: Int, fast: Boolean) = execute { val response = repository.setReceiverSlotRate(slot, if (fast) 1 else 0); check(response.status == 0); refreshReceiver(); message("槽位 ${slot + 1} 已切换为${if (fast) "快速" else "慢速"}") }
    fun setReceiverId(id: Int) = restricted {
        val response = repository.setReceiverId(id)
        check(response.status == 0) { "设备拒绝设置：${response.status}" }
        refreshReceiver(); message("接收器编号已写入并回读")
    }
    fun writeOffset(value: Int) = restricted { repository.writeSensorOffset(value); refreshSensor(); message("偏移已写入并回读一致") }
    fun writeRate(value: Int) = execute { repository.writeSensorRate(value); refreshSensor(); message("速率已写入并回读一致") }
    fun writePower(value: Int) = restricted { repository.writePowerMode(value); refreshSensor(); message("功耗模式已写入并回读一致") }
    fun stageSensor(sensorId: Int) {
        val selected = local.value.selected ?: return message("请先连接传感器")
        val info = local.value.sensorInfo ?: return message("尚未读取传感器信息")
        val mac = selected.address.split(':').mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        if (sensorId !in 1..255 || mac.size != 6) return message("传感器编号或 MAC 无效")
        local.update { it.copy(stagedSensor = SensorIdentity(info.sensorType, sensorId, mac), message = "已暂存传感器身份，请连接接收器") }
    }
    fun stageManual(type: Int, sensorId: Int, source: String) {
        if (!local.value.maintainerMode) return message("请先进入维护模式")
        val macText = com.example.wirelesssensormanager.core.protocol.DeviceIdentityParser.extractMac(source) ?: return message("二维码或输入内容中没有有效 MAC")
        val mac = macText.split(':').map { it.toInt(16).toByte() }.toByteArray()
        if (type !in 1..2 || sensorId !in 1..255) return message("传感器类型或点位无效")
        local.update { it.copy(stagedSensor = SensorIdentity(type, sensorId, mac), message = "已暂存 ${SensorPositions.label(type, sensorId)} / $macText") }
    }
    fun bind(slot: Int) = restricted {
        val identity = local.value.stagedSensor ?: error("请先连接传感器并暂存身份")
        when (val result = bindingCoordinator.bind(slot, identity)) {
            is BindingResult.Success -> { refreshReceiver(); message("槽位 ${slot + 1} 绑定成功并已回读验证") }
            is BindingResult.Rejected -> error(result.reason)
        }
    }
    fun unbind(slot: Int) = restricted {
        when (val result = bindingCoordinator.unbind(slot)) {
            is BindingResult.Success -> { refreshReceiver(); message("槽位 ${slot + 1} 已解绑并回读验证") }
            is BindingResult.Rejected -> error(result.reason)
        }
    }
    fun applyBindingDraft(draft: List<SensorIdentity?>) = restricted { val actual = bindingCoordinator.synchronize(draft); local.update { it.copy(slots = actual) }; message("绑定编辑表已按差异下发并逐槽回读") }
    fun clearAllBindings() = restricted { val response = repository.clearAllBindings(); check(response.status == 0); refreshReceiver(); message("已清空全部绑定并回读验证") }
    fun writeSensorMac(value: String) = restricted { val mac = value.split(':').mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray(); require(mac.size == 6); expectedDisconnectUntil = System.currentTimeMillis() + 30_000; repository.writeSensorMac(mac); message("MAC 已写入，设备将复位；请重新扫描确认") }
    fun markZero() = execute { val device = local.value.selected ?: error("未连接传感器"); val sample = local.value.samples[-1] ?: error("尚未收到实时数据"); settings.saveZero(device.address, sample.xAngle, sample.yAngle); local.update { it.copy(zeroX = sample.xAngle, zeroY = sample.yAngle) }; message("当前位置已标记为零点") }
    fun enterEm4() = restricted { expectedDisconnectUntil = Long.MAX_VALUE; repository.enterSensorEm4(); message("已发送 EM4 休眠命令，设备将断开") }
    fun clearMessage() = local.update { it.copy(message = null) }
    fun setDiagnostics(enabled: Boolean) = viewModelScope.launch { settings.setDiagnosticsEnabled(enabled) }
    fun enterMaintainer(password: String) = execute { if (settings.enterMaintainer(password)) message("已进入维护模式") else error("维护密码错误") }
    fun exitMaintainer() = viewModelScope.launch { settings.exitMaintainer() }
    fun setMineMode(value: Boolean) = viewModelScope.launch { settings.setMineMode(value) }
    fun setRssiThreshold(value: Int) { local.update { it.copy(rssiThreshold = value) }; viewModelScope.launch { settings.setRssiThreshold(value) } }
    fun setScanQuery(value: String) = local.update { it.copy(scanQuery = value) }
    fun toggleScanSort() = local.update { it.copy(sortDescending = !it.sortDescending) }

    private fun startOtaJob(loader: suspend () -> ByteArray) {
        if (!local.value.maintainerMode) return message("请先进入维护模式")
        val device = local.value.selected ?: return message("请先连接需要升级的设备")
        if (otaJob?.isActive == true) return
        otaJob = execute {
            try { val firmware = loader(); val version = otaService.update(device, firmware) { _ota.value = it }; repository.recordOperation("OTA", "完成${version?.let { " / $it" }.orEmpty()}", true); message("OTA ${_ota.value?.state ?: "完成"}") }
            catch (error: Throwable) { repository.recordOperation("OTA", error.message ?: "失败", false); throw error }
        }
    }

    fun exportLogs(context: Context) {
        execute {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "ble-diagnostics-${System.currentTimeMillis()}.csv")
            file.bufferedWriter(Charsets.UTF_8).use { out ->
                out.appendLine("time,deviceType,address,state,command,direction,hex,durationMs,result,error")
                state.value.logs.reversed().forEach { l ->
                    val cells = listOf(l.timestamp, l.deviceType, l.deviceAddress, l.connectionState, l.commandName, l.direction, l.hexData, l.durationMs, l.result, l.error)
                    out.appendLine(cells.joinToString(",") { "\"${(it ?: "").toString().replace("\"", "\"\"")}\"" })
                }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "导出诊断日志"))
        }
    }

    private suspend fun refreshReceiver() {
        val status = repository.readReceiverStatus()
        val slots = repository.readBindingTable()
        val version = repository.readDeviceVersion()
        local.update { it.copy(receiverStatus = status, slots = slots, deviceVersion = version) }
    }
    private suspend fun refreshSensor() {
        val info = repository.readSensorInfo()
        val offset = repository.readSensorOffset()
        val rate = repository.readSensorRate()
        val power = repository.readPowerMode()
        val version = repository.readDeviceVersion()
        local.update { it.copy(sensorInfo = info, sensorOffset = offset, sensorRate = rate, powerMode = power, deviceVersion = version) }
    }
    private fun execute(block: suspend () -> Unit) = viewModelScope.launch {
        local.update { it.copy(busy = true, message = null) }
        runCatching { block() }.onFailure { message(it.message ?: "操作失败") }
        local.update { it.copy(busy = false) }
    }
    private fun restricted(block: suspend () -> Unit) = if (!local.value.maintainerMode) { message("请先进入维护模式") } else execute(block)
    private fun message(value: String) = local.update { it.copy(message = value) }
}
