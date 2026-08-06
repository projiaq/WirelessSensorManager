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
import kotlinx.coroutines.launch
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
    fun startOta(context: Context, uri: Uri) = execute { otaService.update(context.contentResolver, uri) { _ota.value = it }; message("OTA ${_ota.value?.state ?: "完成"}") }

    fun scan() = execute { repository.scan() }
    fun stopScan() = repository.stopScan()
    fun connect(device: DiscoveredDevice) = execute {
        repository.connect(device)
        local.update { it.copy(selected = device) }
        if (device.type == DeviceType.RECEIVER) refreshReceiver() else refreshSensor()
    }
    fun disconnect() = execute { repository.disconnect(); local.update { it.copy(selected = null) } }
    fun refreshReceiverAction() = execute { refreshReceiver() }
    fun refreshSensorAction() = execute { refreshSensor() }
    fun setReceiverId(id: Int) = execute {
        val response = repository.setReceiverId(id)
        check(response.status == 0) { "设备拒绝设置：${response.status}" }
        refreshReceiver(); message("接收器编号已写入并回读")
    }
    fun writeOffset(value: Int) = execute { repository.writeSensorOffset(value); refreshSensor(); message("偏移已写入并回读一致") }
    fun writeRate(value: Int) = execute { repository.writeSensorRate(value); refreshSensor(); message("速率已写入并回读一致") }
    fun writePower(value: Int) = execute { repository.writePowerMode(value); refreshSensor(); message("功耗模式已写入并回读一致") }
    fun stageSensor(sensorId: Int) {
        val selected = local.value.selected ?: return message("请先连接传感器")
        val info = local.value.sensorInfo ?: return message("尚未读取传感器信息")
        val mac = selected.address.split(':').mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
        if (sensorId !in 1..255 || mac.size != 6) return message("传感器编号或 MAC 无效")
        local.update { it.copy(stagedSensor = SensorIdentity(info.sensorType, sensorId, mac), message = "已暂存传感器身份，请连接接收器") }
    }
    fun bind(slot: Int) = execute {
        val identity = local.value.stagedSensor ?: error("请先连接传感器并暂存身份")
        when (val result = bindingCoordinator.bind(slot, identity)) {
            is BindingResult.Success -> { refreshReceiver(); message("槽位 ${slot + 1} 绑定成功并已回读验证") }
            is BindingResult.Rejected -> error(result.reason)
        }
    }
    fun unbind(slot: Int) = execute {
        when (val result = bindingCoordinator.unbind(slot)) {
            is BindingResult.Success -> { refreshReceiver(); message("槽位 ${slot + 1} 已解绑并回读验证") }
            is BindingResult.Rejected -> error(result.reason)
        }
    }
    fun clearMessage() = local.update { it.copy(message = null) }
    fun setDiagnostics(enabled: Boolean) = viewModelScope.launch { settings.setDiagnosticsEnabled(enabled) }

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
    private fun message(value: String) = local.update { it.copy(message = value) }
}
