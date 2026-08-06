package com.example.wirelesssensormanager.core.protocol

import com.example.wirelesssensormanager.core.common.SensorIdentity
import org.junit.Assert.*
import org.junit.Test

class BleProtocolTest {
    @Test fun `sensor v2 sample decodes signed values and flags`() {
        val raw = byteArrayOf(1,0,0,0,25, 0x9c.toByte(),0xff.toByte(), 20,0, 10,0, 0xf6.toByte(),0xff.toByte(), 0xff.toByte(), 7,0, (37 shl 2 or 2).toByte())
        val sample = SensorProtocol.parseSample(raw)
        assertEquals(-100, sample.xAngle)
        assertEquals(20, sample.yAngle)
        assertTrue(sample.readOk == true)
        assertEquals(3.7f, sample.voltageVolts ?: 0f, 0.01f)
    }

    @Test fun `qr identity parser extracts compact and separated mac`() {
        assertEquals("AA:BB:CC:DD:EE:FF", DeviceIdentityParser.extractMac("sensor=AABBCCDDEEFF"))
        assertEquals("01:02:03:04:05:06", DeviceIdentityParser.extractMac("01-02-03-04-05-06"))
    }

    @Test fun `sensor v1 voltage uses legacy flag layout`() {
        val raw = ByteArray(17).also { it[16] = (37 shl 1).toByte() }
        val sample = SensorProtocol.parseSample(raw, protocolVersion = 1)
        assertEquals(3.7f, sample.voltageVolts ?: 0f, 0.01f)
        assertEquals(null, sample.readOk)
    }
    @Test fun `receiver commands match firmware byte layout`() {
        assertArrayEquals(byteArrayOf(1, 1, 0x34, 0x12), ReceiverProtocol.setId(0x1234))
        assertArrayEquals(byteArrayOf(1, 2, 3, 2, 9, 1, 2, 3, 4, 5, 6), ReceiverProtocol.setSlot(3, SensorIdentity(2, 9, byteArrayOf(1,2,3,4,5,6))))
        assertArrayEquals(byteArrayOf(1, 3, 7), ReceiverProtocol.clearSlot(7))
        assertArrayEquals(byteArrayOf(1, 5, 0), ReceiverProtocol.getSlot(0))
    }

    @Test fun `receiver response is strictly parsed`() {
        val bytes = byteArrayOf(1, 5, 0, 2, 0x34, 0x12, 1, 7, 0x11,0x22,0x33,0x44,0x55,0x66,1)
        val value = ReceiverProtocol.parseResponse(bytes)
        assertEquals(0x1234, value.receiverId)
        assertEquals(2, value.slot)
        assertEquals("11:22:33:44:55:66", value.mac.joinToString(":") { "%02X".format(it) })
        assertTrue(value.online)
        assertTrue(ReceiverProtocol.matches(value, ReceiverProtocol.GET_SLOT, 2))
        assertFalse(ReceiverProtocol.matches(value, ReceiverProtocol.GET_SLOT, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `short response is rejected`() { ReceiverProtocol.parseResponse(byteArrayOf(1, 5, 0)) }

    @Test fun `signed offset uses little endian`() {
        val encoded = SensorProtocol.encodeOffset(-123456)
        assertEquals(-123456, SensorProtocol.decodeOffset(encoded))
        assertArrayEquals(byteArrayOf(0xC0.toByte(), 0x1D, 0xFE.toByte(), 0xFF.toByte()), encoded)
    }

    @Test fun `sensor info follows firmware v2 layout`() {
        val raw = byteArrayOf(1,0,0,0, 2,0,0,0, 0xE4.toByte(),0x0C, 1,1, 3,0, 2,2, 1,1)
        val info = SensorProtocol.parseInfo(raw)
        assertEquals(1, info.workSeconds)
        assertEquals(3300, info.voltageMv)
        assertEquals(2, info.sensorType)
        assertEquals(1, info.powerMode)
    }

    @Test fun `parameter write readback accepts exact bytes and rejects mismatch`() {
        ReadbackVerifier.verify("偏移", byteArrayOf(1,2,3,4), byteArrayOf(1,2,3,4))
        assertThrows(IllegalStateException::class.java) { ReadbackVerifier.verify("偏移", byteArrayOf(1,2,3,4), byteArrayOf(1,2,3,5)) }
    }

    @Test fun `crc and frame match firmware published vector`() {
        val body = byteArrayOf(0x10, 0x01, 0x00, 0x20, 0x02)
        assertArrayEquals(byteArrayOf(0xAA.toByte(),0xBB.toByte(),0x05,0x10,0x01,0x00,0x20,0x02,0x7C,0x3E), Rs485Protocol.frame(body))
    }

    @Test fun `stream decoder handles half and multiple frames`() {
        val one = Rs485Protocol.frame(byteArrayOf(0x10,1,0,0x20,1))
        val two = Rs485Protocol.frame(byteArrayOf(0x10,1,0,0x20,2))
        val decoder = Rs485StreamDecoder()
        assertTrue(decoder.feed(one.copyOfRange(0, 4)).isEmpty())
        assertEquals(2, decoder.feed(one.copyOfRange(4, one.size) + two).size)
    }

    @Test fun `stream decoder skips corrupt frame and resynchronizes`() {
        val bad = Rs485Protocol.frame(byteArrayOf(0x10,1,0,0x20,1)).also { it[it.lastIndex] = 0 }
        val goodBody = byteArrayOf(0x10,1,0,0x20,2)
        val result = Rs485StreamDecoder().feed(byteArrayOf(9,8,7) + bad + Rs485Protocol.frame(goodBody))
        assertEquals(1, result.size)
        assertArrayEquals(goodBody, result.single())
    }
}
