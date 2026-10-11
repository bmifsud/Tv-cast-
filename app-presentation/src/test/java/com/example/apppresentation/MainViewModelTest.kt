package com.example.apppresentation

import com.example.apppresentation.network.DiscoveredDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var stubDiscoverer: TestDiscoverer
    private lateinit var stubSender: TestSender
    private lateinit var stubCaptureEmitter: TestCaptureEmitter
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        stubDiscoverer = TestDiscoverer()
        stubSender = TestSender()
        stubCaptureEmitter = TestCaptureEmitter()
        viewModel = MainViewModel(
            discoverer = stubDiscoverer,
            sender = stubSender,
            captureEmitter = stubCaptureEmitter
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testStartDiscovery() {
        assertFalse(viewModel.isDiscovering.value)
        viewModel.startDiscovery()
        assertTrue(viewModel.isDiscovering.value)
        assertEquals(1, viewModel.discoveredDevices.value.size)
        assertEquals("Xiaomi TV Box", viewModel.discoveredDevices.value.first().name)
    }

    @Test
    fun testStartAndStopCasting() {
        val testDevice = DiscoveredDevice("id-123", "Test TV", "10.0.0.1")
        viewModel.selectDevice(testDevice)

        assertFalse(viewModel.isCasting.value)
        assertFalse(stubCaptureEmitter.isCapturing.value)

        viewModel.startCasting(null)

        assertTrue(viewModel.isCasting.value)
        assertTrue(stubCaptureEmitter.isCapturing.value)
        assertEquals(testDevice, viewModel.selectedDevice.value)

        viewModel.stopCasting()

        assertFalse(viewModel.isCasting.value)
        assertFalse(stubCaptureEmitter.isCapturing.value)
    }

    @Test
    fun testFramePropagation() {
        viewModel.startDiscovery()
        viewModel.startCasting(null)

        testDispatcher.scheduler.advanceUntilIdle()

        val sampleNalUnit = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65)
        stubCaptureEmitter.emitFrame(sampleNalUnit)

        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(stubSender.lastSentNalUnit)
        assertEquals(sampleNalUnit[4], stubSender.lastSentNalUnit!![4])
    }
}
