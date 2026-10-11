package com.example.networktransport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTransportTest {

    @Test
    fun testUdpDiscoverer() {
        val discoverer = UdpDiscoverer()
        assertFalse(discoverer.isDiscovering.value)
        discoverer.startDiscovery()
        Thread.sleep(50)

        assertTrue(discoverer.isDiscovering.value)
        assertEquals(1, discoverer.discoveredDevices.value.size)
        assertEquals("Xiaomi TV Box", discoverer.discoveredDevices.value.first().name)

        discoverer.stopDiscovery()
        assertFalse(discoverer.isDiscovering.value)
    }

    @Test
    fun testUdpSender() {
        val sender = UdpSender()
        assertFalse(sender.isCasting.value)
        sender.startCasting("127.0.0.1")
        Thread.sleep(50)

        assertTrue(sender.isCasting.value)
        val sampleData = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65)
        sender.sendFrame(sampleData, "127.0.0.1")

        sender.stopCasting()
        assertFalse(sender.isCasting.value)
    }

    @Test
    fun testUdpReceiverState() {
        val receiver = UdpReceiver()
        assertFalse(receiver.isReceiving.value)
        receiver.startReceiving()
        Thread.sleep(50)

        assertTrue(receiver.isReceiving.value)
        receiver.stopReceiving()
        assertFalse(receiver.isReceiving.value)
    }
}
