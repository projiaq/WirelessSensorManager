package com.example.wirelesssensormanager.core.ble

import com.example.wirelesssensormanager.core.common.BleConnectionState
import com.example.wirelesssensormanager.core.common.DeviceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FakeBleTransportTest {
    @Test fun `fake scan exposes only firmware identities`() = runTest {
        val fake = FakeBleTransport()
        fake.startScan(100)
        assertEquals(2, fake.discoveredDevices.value.size)
        assertEquals(DeviceType.RECEIVER, fake.discoveredDevices.value.first().type)
        assertEquals(BleConnectionState.DEVICE_FOUND, fake.connectionState.value)
    }

    @Test fun `fake connection state reaches ready and releases`() = runTest {
        val fake = FakeBleTransport()
        fake.connect("02:00:00:00:00:01", DeviceType.RECEIVER)
        assertEquals(BleConnectionState.READY, fake.connectionState.value)
        fake.disconnect()
        assertEquals(BleConnectionState.DISCONNECTED, fake.connectionState.value)
    }
}
