package com.example.wirelesssensormanager.core.repository

import com.example.wirelesssensormanager.core.ble.BleTransport
import com.example.wirelesssensormanager.core.common.*
import com.example.wirelesssensormanager.core.database.*
import com.example.wirelesssensormanager.core.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceRepository {
    val packets: Flow<BlePacket>
    val connectionState: StateFlow<BleConnectionState>
    val devices: StateFlow<List<DiscoveredDevice>>
    val logs: Flow<List<DiagnosticLogEntity>>
    val operations: Flow<List<OperationEntity>>
    suspend fun scan()
    fun stopScan()
    suspend fun connect(device: DiscoveredDevice)
    suspend fun disconnect()
    suspend fun readReceiverStatus(): ReceiverStatus
    suspend fun readDeviceVersion(): DeviceVersionInfo
    suspend fun readBindingTable(): List<BindingSlot>
    suspend fun setBinding(slot: Int, identity: SensorIdentity): ReceiverResponse
    suspend fun clearBinding(slot: Int): ReceiverResponse
    suspend fun clearAllBindings(): ReceiverResponse
    suspend fun setReceiverId(id: Int): ReceiverResponse
    suspend fun setReceiverSlotRate(slot: Int, rate: Int): ReceiverResponse
    suspend fun readReceiverSensorInfo(slot: Int): SensorInfo
    suspend fun readSensorInfo(): SensorInfo
    suspend fun readSensorOffset(): Int
    suspend fun writeSensorOffset(value: Int)
    suspend fun readSensorRate(): Int
    suspend fun writeSensorRate(value: Int)
    suspend fun readPowerMode(): Int
    suspend fun writePowerMode(value: Int)
    suspend fun writeSensorMac(mac: ByteArray)
    suspend fun enterSensorEm4()
    suspend fun recordOperation(action: String, detail: String, success: Boolean)
}

@Singleton
class DefaultDeviceRepository @Inject constructor(
    private val transport: BleTransport,
    private val dao: AppDao,
    settings: SettingsStore
) : DeviceRepository {
    override val packets: Flow<BlePacket> = transport.packets
    override val connectionState = transport.connectionState
    override val devices = transport.discoveredDevices
    override val logs = dao.logs()
    override val operations = dao.operations()
    private var current: DiscoveredDevice? = null
    private var diagnosticsEnabled = true
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        scope.launch { settings.diagnosticsEnabled.collect { diagnosticsEnabled = it } }
        scope.launch {
            transport.packets.collect { packet ->
                log(packet.characteristic.toString(), "RX", packet.value, null, "通知", null)
            }
        }
    }

    override suspend fun scan() = transport.startScan()
    override fun stopScan() = transport.stopScan()
    override suspend fun connect(device: DiscoveredDevice) {
        transport.connect(device.address, device.type)
        current = device
        dao.upsertDevice(DeviceEntity(device.address, device.name, device.type.name, null, System.currentTimeMillis()))
        operation("连接", device.name ?: device.address, true)
    }
    override suspend fun disconnect() { transport.disconnect(); operation("断开", current?.name.orEmpty(), true); current = null }

    override suspend fun readReceiverStatus(): ReceiverStatus = logged("读取接收器状态", "RX_STATUS") {
        ReceiverProtocol.parseStatus(transport.read(BleUuids.RECEIVER_SERVICE, BleUuids.RECEIVER_STATUS))
    }
    override suspend fun readDeviceVersion(): DeviceVersionInfo {
        suspend fun text(uuid: UUID) = runCatching { transport.read(BleUuids.DEVICE_INFO_SERVICE, uuid).toString(Charsets.UTF_8).trimEnd('\u0000') }.getOrNull()
        val value = DeviceVersionInfo(text(BleUuids.MODEL_NUMBER), text(BleUuids.HARDWARE_REVISION), text(BleUuids.FIRMWARE_REVISION))
        current?.let { dao.insertVersion(DeviceVersionEntity(deviceAddress = it.address, hardware = value.hardware, firmware = value.firmware, protocol = null, readAt = System.currentTimeMillis())) }
        return value
    }

    override suspend fun readBindingTable(): List<BindingSlot> {
        val slots = (0 until ReceiverProtocol.SLOT_COUNT).map { slot ->
            val response = receiverCommand(ReceiverProtocol.getSlot(slot), ReceiverProtocol.GET_SLOT, slot)
            check(response.status == 0) { ReceiverProtocol.statusMessage(response.status) }
            BindingSlot(slot, response.sensorType, response.sensorId, response.mac, response.online)
        }
        current?.let { device ->
            dao.clearBindings(device.address)
            dao.upsertBindings(slots.filter { it.occupied }.map { BindingEntity(device.address, it.index, it.sensorType, it.sensorId, it.mac.joinToString(":") { b -> "%02X".format(b) }, it.online, System.currentTimeMillis()) })
        }
        return slots
    }

    override suspend fun setBinding(slot: Int, identity: SensorIdentity) = receiverCommand(ReceiverProtocol.setSlot(slot, identity), ReceiverProtocol.SET_SLOT, slot).also {
        operation("绑定", "槽位 ${slot + 1} / ${identity.displayMac}", it.status == 0)
    }
    override suspend fun clearBinding(slot: Int) = receiverCommand(ReceiverProtocol.clearSlot(slot), ReceiverProtocol.CLEAR_SLOT, slot).also {
        operation("解绑", "槽位 ${slot + 1}", it.status == 0)
    }
    override suspend fun clearAllBindings() = receiverCommand(ReceiverProtocol.clearAll(), ReceiverProtocol.CLEAR_ALL, 0xff).also { operation("清空全部绑定", "8 个槽位", it.status == 0) }
    override suspend fun setReceiverId(id: Int) = receiverCommand(ReceiverProtocol.setId(id), ReceiverProtocol.SET_ID, 0xff).also {
        operation("设置接收器编号", id.toString(), it.status == 0)
    }
    override suspend fun setReceiverSlotRate(slot: Int, rate: Int) = receiverCommand(ReceiverProtocol.setRate(slot, rate), ReceiverProtocol.SET_RATE, slot).also { operation("设置槽位速率", "槽位 ${slot + 1} / $rate", it.status == 0) }
    override suspend fun readReceiverSensorInfo(slot: Int): SensorInfo {
        val response = receiverCommand(ReceiverProtocol.getSensorInfo(slot), ReceiverProtocol.GET_SENSOR_INFO, slot)
        check(response.status == 0) { ReceiverProtocol.statusMessage(response.status) }
        return requireNotNull(response.sensorInfo) { "设备未返回槽位 ${slot + 1} 的传感器信息" }
    }

    override suspend fun readSensorInfo() = logged("读取传感器信息", "SN_INFO") {
        SensorProtocol.parseInfo(transport.read(BleUuids.SENSOR_CONFIG_SERVICE, BleUuids.SENSOR_INFO))
    }
    override suspend fun readSensorOffset() = logged("读取偏移", "SN_OFFSET") { SensorProtocol.decodeOffset(transport.read(BleUuids.SENSOR_CONFIG_SERVICE, BleUuids.SENSOR_OFFSET)) }
    override suspend fun writeSensorOffset(value: Int) = writeAndVerify("偏移", BleUuids.SENSOR_OFFSET, SensorProtocol.encodeOffset(value))
    override suspend fun readSensorRate() = logged("读取速率", "SN_RATE") { transport.read(BleUuids.SENSOR_CONFIG_SERVICE, BleUuids.SENSOR_RATE).single().toInt() and 0xff }
    override suspend fun writeSensorRate(value: Int) = writeAndVerify("速率", BleUuids.SENSOR_RATE, SensorProtocol.encodeRate(value))
    override suspend fun readPowerMode() = logged("读取功耗模式", "SN_POWER") { transport.read(BleUuids.SENSOR_CONFIG_SERVICE, BleUuids.SENSOR_POWER).single().toInt() and 0xff }
    override suspend fun writePowerMode(value: Int) = writeAndVerify("功耗模式", BleUuids.SENSOR_POWER, SensorProtocol.encodePower(value))
    override suspend fun writeSensorMac(mac: ByteArray) {
        require(mac.size == 6)
        logged("设置传感器 MAC", "SN_MAC", mac) { transport.write(BleUuids.SENSOR_CONFIG_SERVICE, BleUuids.SENSOR_MAC, mac, true) }
        operation("设置传感器 MAC", mac.toHex(), true)
    }
    override suspend fun enterSensorEm4() {
        logged("进入 EM4", "SN_EM4", byteArrayOf(2)) { transport.write(BleUuids.SENSOR_CONFIG_SERVICE, BleUuids.SENSOR_RATE, byteArrayOf(2), true) }
        operation("进入 EM4", "设备预期断开", true)
    }
    override suspend fun recordOperation(action: String, detail: String, success: Boolean) = operation(action, detail, success)

    private suspend fun receiverCommand(bytes: ByteArray, opcode: Int, slot: Int): ReceiverResponse = coroutineScope {
        logged("接收器命令 $opcode", "RX_CMD", bytes) {
            val responsePacket = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(10_000) {
                    transport.packets.filter { it.characteristic == BleUuids.RECEIVER_RESPONSE }
                        .first { runCatching { ReceiverProtocol.matches(ReceiverProtocol.parseResponse(it.value), opcode, slot) }.getOrDefault(false) }
                }
            }
            transport.write(BleUuids.RECEIVER_SERVICE, BleUuids.RECEIVER_COMMAND, bytes, true)
            val packet = responsePacket.await()
            log("RX_RESP", "RX", packet.value, null, "接收", null)
            ReceiverProtocol.parseResponse(packet.value)
        }
    }

    private suspend fun writeAndVerify(name: String, characteristic: UUID, value: ByteArray) {
        logged("设置$name", characteristic.toString(), value) {
            transport.write(BleUuids.SENSOR_CONFIG_SERVICE, characteristic, value, true)
            val actual = transport.read(BleUuids.SENSOR_CONFIG_SERVICE, characteristic)
            ReadbackVerifier.verify(name, value, actual)
            current?.let { dao.insertSnapshot(ConfigSnapshotEntity(deviceAddress = it.address, key = name, value = value.toHex(), capturedAt = System.currentTimeMillis())) }
        }
        operation("设置$name", value.toHex(), true)
    }

    private suspend fun <T> logged(command: String, commandName: String, tx: ByteArray = byteArrayOf(), block: suspend () -> T): T {
        val start = System.currentTimeMillis()
        if (tx.isNotEmpty()) log(commandName, "TX", tx, null, "发送", null)
        return try {
            block().also { log(commandName, "RX", byteArrayOf(), System.currentTimeMillis() - start, "成功", null) }
        } catch (error: Throwable) {
            log(commandName, "-", byteArrayOf(), System.currentTimeMillis() - start, "失败", error.message)
            dao.insertError(CommunicationErrorEntity(timestamp = System.currentTimeMillis(), deviceAddress = current?.address, state = connectionState.value.name, reason = error.message ?: error::class.java.simpleName))
            throw error
        }
    }
    private suspend fun log(name: String, direction: String, data: ByteArray, duration: Long?, result: String, error: String?) {
        if (!diagnosticsEnabled) return
        dao.insertLog(DiagnosticLogEntity(timestamp = System.currentTimeMillis(), deviceType = current?.type?.name ?: "UNKNOWN", deviceAddress = current?.address, deviceId = null, connectionState = connectionState.value.name, commandName = name, direction = direction, hexData = data.toHex(), durationMs = duration, result = result, error = error))
    }
    private suspend fun operation(action: String, detail: String, success: Boolean) = dao.insertOperation(OperationEntity(timestamp = System.currentTimeMillis(), deviceAddress = current?.address, action = action, detail = detail, success = success))
}
