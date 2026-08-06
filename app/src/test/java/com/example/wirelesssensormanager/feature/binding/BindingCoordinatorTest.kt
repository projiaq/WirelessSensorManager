package com.example.wirelesssensormanager.feature.binding

import com.example.wirelesssensormanager.core.common.*
import com.example.wirelesssensormanager.core.database.DiagnosticLogEntity
import com.example.wirelesssensormanager.core.database.OperationEntity
import com.example.wirelesssensormanager.core.repository.DeviceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import com.example.wirelesssensormanager.core.protocol.ReceiverProtocol

class BindingCoordinatorTest {
    private val identity = SensorIdentity(2, 7, byteArrayOf(1,2,3,4,5,6))

    @Test fun `binding succeeds only after matching readback`() = runTest {
        val repo = StubRepository()
        val result = BindingCoordinator(repo).bind(2, identity)
        assertTrue(result is BindingResult.Success)
        assertArrayEquals(identity.mac, repo.slots[2].mac)
    }

    @Test fun `duplicate binding is rejected without write`() = runTest {
        val repo = StubRepository().apply { slots[1] = BindingSlot(1, 2, 7, identity.mac, true) }
        val result = BindingCoordinator(repo).bind(2, identity)
        assertTrue((result as BindingResult.Rejected).reason.contains("已绑定"))
        assertEquals(0, repo.writeCount)
    }

    @Test fun `full table is rejected`() = runTest {
        val repo = StubRepository().apply { repeat(8) { slots[it] = BindingSlot(it, 1, it + 1, byteArrayOf(9,9,9,9,9,it.toByte())) } }
        assertTrue(BindingCoordinator(repo).bind(2, identity) is BindingResult.Rejected)
    }

    @Test fun `unbind clears slot and verifies readback`() = runTest {
        val repo = StubRepository().apply { slots[3] = BindingSlot(3, 2, 7, identity.mac) }
        val result = BindingCoordinator(repo).unbind(3)
        assertTrue(result is BindingResult.Success)
        assertFalse(repo.slots[3].occupied)
    }

    @Test fun `binding timeout is surfaced`() = runTest {
        val repo = StubRepository().apply { writeDelayMs = 100 }
        assertFailsWith<TimeoutCancellationException> { withTimeout(10) { BindingCoordinator(repo).bind(0, identity) } }
    }
}

class StubRepository : DeviceRepository {
    override val connectionState = MutableStateFlow(BleConnectionState.READY)
    override val devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val logs = flowOf<List<DiagnosticLogEntity>>(emptyList())
    override val operations = flowOf<List<OperationEntity>>(emptyList())
    val slots = MutableList(8) { BindingSlot(it) }
    var writeCount = 0
    var writeDelayMs = 0L
    override suspend fun scan() { devices.value = listOf(DiscoveredDevice("AA:BB:CC:DD:EE:FF", "AIOT_0011_#001", DeviceType.RECEIVER, -40, emptySet())) }
    override fun stopScan() = Unit
    override suspend fun connect(device: DiscoveredDevice) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun readReceiverStatus() = ReceiverStatus(1,0,0,0,8,0)
    override suspend fun readDeviceVersion() = DeviceVersionInfo(null,null,null)
    override suspend fun readBindingTable() = slots.toList()
    override suspend fun setBinding(slot: Int, identity: SensorIdentity): ReceiverResponse {
        writeCount++; if (writeDelayMs > 0) delay(writeDelayMs)
        slots[slot] = BindingSlot(slot, identity.type, identity.sensorId, identity.mac)
        return response(ReceiverProtocol.SET_SLOT, slot)
    }
    override suspend fun clearBinding(slot: Int): ReceiverResponse { slots[slot] = BindingSlot(slot); return response(ReceiverProtocol.CLEAR_SLOT, slot) }
    override suspend fun setReceiverId(id: Int) = response(ReceiverProtocol.SET_ID, 0xff)
    override suspend fun readSensorInfo() = SensorInfo(0,0,0,true,0,0,2,2,1,0)
    override suspend fun readSensorOffset() = 0
    override suspend fun writeSensorOffset(value: Int) = Unit
    override suspend fun readSensorRate() = 0
    override suspend fun writeSensorRate(value: Int) = Unit
    override suspend fun readPowerMode() = 0
    override suspend fun writePowerMode(value: Int) = Unit
    private fun response(opcode: Int, slot: Int) = ReceiverResponse(1, opcode, 0, slot, 1, 0, 0, ByteArray(6), false)
}
