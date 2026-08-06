package com.example.wirelesssensormanager.core.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.example.wirelesssensormanager.core.common.*
import com.example.wirelesssensormanager.core.protocol.BleUuids
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@SuppressLint("MissingPermission")
class AndroidBleTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : BleTransport {
    private val adapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val _state = MutableStateFlow(BleConnectionState.IDLE)
    override val connectionState = _state.asStateFlow()
    private val devices = linkedMapOf<String, DiscoveredDevice>()
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices = _devices.asStateFlow()
    private val _packets = MutableSharedFlow<BlePacket>(extraBufferCapacity = 64)
    override val packets = _packets.asSharedFlow()

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var address: String = ""
    private var expectedType: DeviceType = DeviceType.UNKNOWN
    private var pendingRead: CompletableDeferred<Result<ByteArray>>? = null
    private var pendingWrite: CompletableDeferred<Result<Unit>>? = null
    private var pendingConnect: CompletableDeferred<Result<Unit>>? = null
    private val subscribeQueue = ArrayDeque<BluetoothGattCharacteristic>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = accept(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::accept)
        override fun onScanFailed(errorCode: Int) { _state.value = BleConnectionState.ERROR }
    }

    private fun accept(result: ScanResult) {
        val record = result.scanRecord
        val uuids = record?.serviceUuids.orEmpty().map(ParcelUuid::getUuid).toSet()
        val name = record?.deviceName ?: runCatching { result.device.name }.getOrNull()
        val type = when {
            BleUuids.OTA_SERVICE in uuids || name.equals("OTA", true) || name?.contains("DFU", true) == true || name?.contains("APPL", true) == true -> DeviceType.OTA
            BleUuids.RECEIVER_SERVICE in uuids -> DeviceType.RECEIVER
            BleUuids.SENSOR_DATA_SERVICE in uuids -> DeviceType.SENSOR
            name?.startsWith("AIOT_") == true -> DeviceType.RECEIVER
            name?.startsWith("PRES-") == true -> DeviceType.PRESSURE_SENSOR
            name?.startsWith("TILT-") == true -> DeviceType.TILT_SENSOR
            else -> DeviceType.UNKNOWN
        }
        if (type == DeviceType.UNKNOWN) return
        devices[result.device.address] = DiscoveredDevice(result.device.address, name, type, result.rssi, uuids, parseAdvertisedAddresses(record?.bytes))
        _devices.value = devices.values.sortedByDescending { it.rssi }
        _state.value = BleConnectionState.DEVICE_FOUND
    }

    override suspend fun startScan(timeoutMillis: Long) {
        if (!hasPermissions()) { _state.value = BleConnectionState.PERMISSION_REQUIRED; return }
        val bt = adapter
        if (bt == null || !bt.isEnabled) { _state.value = BleConnectionState.BLUETOOTH_UNAVAILABLE; return }
        stopScan()
        devices.clear(); _devices.value = emptyList()
        scanner = bt.bluetoothLeScanner
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        _state.value = BleConnectionState.SCANNING
        // Receiver firmware replaces generated advertising data with flags + name,
        // so a Service UUID scan filter would hide real receivers.
        scanner?.startScan(null, settings, scanCallback)
        delay(timeoutMillis.coerceIn(1_000, 60_000))
        stopScan()
    }

    override fun stopScan() {
        if (hasPermissions()) runCatching { scanner?.stopScan(scanCallback) }
        scanner = null
        if (_state.value == BleConnectionState.SCANNING) _state.value = BleConnectionState.IDLE
    }

    override suspend fun connect(address: String, expectedType: DeviceType) {
        if (!hasPermissions()) { _state.value = BleConnectionState.PERMISSION_REQUIRED; error("蓝牙权限未授权") }
        val bt = adapter ?: error("设备不支持蓝牙")
        if (!bt.isEnabled) { _state.value = BleConnectionState.BLUETOOTH_UNAVAILABLE; error("蓝牙未开启") }
        disconnect()
        this.address = address
        this.expectedType = expectedType
        _state.value = BleConnectionState.CONNECTING
        val completion = CompletableDeferred<Result<Unit>>()
        pendingConnect = completion
        gatt = bt.getRemoteDevice(address).connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        try {
            withTimeout(15_000) { completion.await().getOrThrow() }
        } catch (error: TimeoutCancellationException) {
            _state.value = BleConnectionState.TIMEOUT
            runCatching { gatt?.disconnect() }; runCatching { gatt?.close() }; gatt = null
            throw error
        }
    }

    override suspend fun disconnect() {
        stopScan()
        val current = gatt ?: return
        _state.value = BleConnectionState.DISCONNECTING
        runCatching { current.disconnect() }
        delay(100)
        runCatching { current.close() }
        gatt = null
        pendingRead?.cancel(); pendingWrite?.cancel(); pendingConnect?.cancel()
        _state.value = BleConnectionState.DISCONNECTED
    }

    override suspend fun read(service: UUID, characteristic: UUID): ByteArray = operationMutex.withLock {
        val current = requireReady()
        val target = current.getService(service)?.getCharacteristic(characteristic) ?: error("缺少特征 $characteristic")
        val deferred = CompletableDeferred<Result<ByteArray>>()
        pendingRead = deferred
        _state.value = BleConnectionState.EXECUTING
        if (!current.readCharacteristic(target)) error("无法启动 GATT 读取")
        try { withTimeout(10_000) { deferred.await().getOrThrow() } }
        finally { pendingRead = null; if (gatt != null) _state.value = BleConnectionState.READY }
    }

    override suspend fun write(service: UUID, characteristic: UUID, value: ByteArray, withResponse: Boolean) = operationMutex.withLock {
        val current = requireReady()
        val target = current.getService(service)?.getCharacteristic(characteristic) ?: error("缺少特征 $characteristic")
        val deferred = CompletableDeferred<Result<Unit>>()
        pendingWrite = deferred
        _state.value = BleConnectionState.EXECUTING
        val started = if (Build.VERSION.SDK_INT >= 33) {
            current.writeCharacteristic(target, value, if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run { target.writeType = if (withResponse) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; target.value = value; current.writeCharacteristic(target) }
        }
        if (!started) error("无法启动 GATT 写入")
        try {
            if (withResponse) withTimeout(10_000) { deferred.await().getOrThrow() } else delay(80)
        } finally { pendingWrite = null; if (gatt != null) _state.value = BleConnectionState.READY }
    }

    private fun requireReady(): BluetoothGatt {
        check(_state.value == BleConnectionState.READY) { "设备尚未就绪：${_state.value}" }
        return gatt ?: error("没有活动连接")
    }

    private fun hasPermissions(): Boolean = if (Build.VERSION.SDK_INT >= 31) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { failConnect("连接错误 GATT=$status"); close(g); return }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> { _state.value = BleConnectionState.CONNECTED; _state.value = BleConnectionState.DISCOVERING_SERVICES; if (!g.discoverServices()) failConnect("无法启动服务发现") }
                BluetoothProfile.STATE_DISCONNECTED -> { close(g); _state.value = BleConnectionState.DISCONNECTED; pendingConnect?.complete(Result.failure(IllegalStateException("设备已断开"))) }
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || !validateServices(g)) { failConnect("所需 Service/Characteristic 不完整"); return }
            _state.value = BleConnectionState.NEGOTIATING_MTU
            if (!g.requestMtu(247)) {
                if (expectedType == DeviceType.RECEIVER) failConnect("接收器 39 字节响应要求 MTU 至少 42") else beginSubscriptions(g)
            }
        }
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (expectedType == DeviceType.RECEIVER && (status != BluetoothGatt.GATT_SUCCESS || mtu < 42)) failConnect("MTU=$mtu，无法承载接收器 39 字节响应")
            else beginSubscriptions(g)
        }
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) { failConnect("CCCD 写入失败 GATT=$status"); return }
            subscribeNext(g)
        }
        @Deprecated("API 33 callback")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) = finishRead(status, c.value ?: byteArrayOf())
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) = finishRead(status, value)
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingWrite?.complete(if (status == BluetoothGatt.GATT_SUCCESS) Result.success(Unit) else Result.failure(IllegalStateException("GATT 写失败 $status")))
        }
        @Deprecated("API 33 callback")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) = emit(c, c.value ?: byteArrayOf())
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) = emit(c, value)
    }

    private fun validateServices(g: BluetoothGatt): Boolean {
        val receiver = g.getService(BleUuids.RECEIVER_SERVICE)
        val sensorData = g.getService(BleUuids.SENSOR_DATA_SERVICE)
        val sensorConfig = g.getService(BleUuids.SENSOR_CONFIG_SERVICE)
        return when (expectedType) {
            DeviceType.RECEIVER -> receiver != null && listOf(BleUuids.RECEIVER_COMMAND, BleUuids.RECEIVER_RESPONSE, BleUuids.RECEIVER_STREAM, BleUuids.RECEIVER_STATUS).all { receiver.getCharacteristic(it) != null }
            DeviceType.OTA -> g.getService(BleUuids.OTA_SERVICE)?.let { it.getCharacteristic(BleUuids.OTA_CONTROL) != null && it.getCharacteristic(BleUuids.OTA_DATA) != null } == true
            else -> sensorData?.getCharacteristic(BleUuids.SENSOR_DATA) != null && sensorConfig != null && listOf(BleUuids.SENSOR_OFFSET, BleUuids.SENSOR_RATE, BleUuids.SENSOR_MAC, BleUuids.SENSOR_INFO, BleUuids.SENSOR_POWER).all { sensorConfig.getCharacteristic(it) != null }
        }
    }

    private fun beginSubscriptions(g: BluetoothGatt) {
        _state.value = BleConnectionState.SUBSCRIBING_NOTIFICATIONS
        subscribeQueue.clear()
        if (expectedType == DeviceType.RECEIVER) {
            val s = g.getService(BleUuids.RECEIVER_SERVICE)
            listOf(BleUuids.RECEIVER_RESPONSE, BleUuids.RECEIVER_STATUS, BleUuids.RECEIVER_STREAM).mapNotNullTo(subscribeQueue) { s?.getCharacteristic(it) }
        } else if (expectedType != DeviceType.OTA) g.getService(BleUuids.SENSOR_DATA_SERVICE)?.getCharacteristic(BleUuids.SENSOR_DATA)?.let(subscribeQueue::add)
        subscribeNext(g)
    }

    private fun subscribeNext(g: BluetoothGatt) {
        val c = subscribeQueue.removeFirstOrNull()
        if (c == null) { _state.value = BleConnectionState.PROTOCOL_HANDSHAKE; _state.value = BleConnectionState.READY; pendingConnect?.complete(Result.success(Unit)); return }
        if (!g.setCharacteristicNotification(c, true)) { failConnect("无法启用通知 ${c.uuid}"); return }
        val d = c.getDescriptor(BleUuids.CCCD) ?: run { failConnect("通知特征缺少 CCCD"); return }
        val started = if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        else { @Suppress("DEPRECATION") run { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) } }
        if (!started) failConnect("无法写入 CCCD")
    }

    private fun finishRead(status: Int, value: ByteArray) { pendingRead?.complete(if (status == BluetoothGatt.GATT_SUCCESS) Result.success(value.copyOf()) else Result.failure(IllegalStateException("GATT 读失败 $status"))) }
    private fun emit(c: BluetoothGattCharacteristic, value: ByteArray) { _packets.tryEmit(BlePacket(address, c.service.uuid, c.uuid, value.copyOf())) }
    private fun failConnect(message: String) { _state.value = BleConnectionState.ERROR; pendingConnect?.complete(Result.failure(IllegalStateException(message))) }
    private fun close(g: BluetoothGatt) { runCatching { g.close() }; if (gatt === g) gatt = null }

    private fun parseAdvertisedAddresses(bytes: ByteArray?): Set<String> {
        if (bytes == null) return emptySet()
        val result = linkedSetOf<String>()
        var offset = 0
        while (offset < bytes.size) {
            val length = bytes[offset].toInt() and 0xff
            if (length == 0 || offset + length >= bytes.size) break
            if ((bytes[offset + 1].toInt() and 0xff) == 0x1b && length >= 8) {
                val address = bytes.copyOfRange(offset + 2, offset + 8)
                result += address.joinToString("") { "%02X".format(it) }
                result += address.reversedArray().joinToString("") { "%02X".format(it) }
            }
            offset += length + 1
        }
        return result
    }
}
