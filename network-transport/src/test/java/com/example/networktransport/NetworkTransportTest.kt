package com.example.networktransport

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkTransportTest {

    @Test
    fun testUdpDiscoverer() = runTest {
        val discoverer = UdpDiscoverer(scope = this)
        assertFalse(discoverer.isDiscovering.value)
        discoverer.startDiscovery()
        testScheduler.advanceUntilIdle()

        assertTrue(discoverer.isDiscovering.value)
        assertEquals(1, discoverer.discoveredDevices.value.size)
        assertEquals("Xiaomi TV Box", discoverer.discoveredDevices.value.first().name)
        discoverer.stopDiscovery()
        assertFalse(discoverer.isDiscovering.value)
    }

    @Test
    fun testUdpSender() = runTest {
        val sender = UdpSender(scope = this)
        assertFalse(sender.isCasting.value)
        sender.startCasting("127.0.0.1")
        testScheduler.advanceUntilIdle()

        assertTrue(sender.isCasting.value)
        val sampleData = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65)
        sender.sendFrame(sampleData, "127.0.0.1")
        testScheduler.advanceUntilIdle()

        sender.stopCasting()
        assertFalse(sender.isCasting.value)
    }
}
