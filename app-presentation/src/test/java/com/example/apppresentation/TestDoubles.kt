package com.example.apppresentation

import android.content.Context
import android.content.Intent
import com.example.apppresentation.capture.CaptureEmitter
import com.example.apppresentation.network.DiscoveredDevice
import com.example.apppresentation.network.Discoverer
import com.example.apppresentation.network.Receiver
import com.example.apppresentation.network.Sender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class TestDiscoverer : Discoverer {
    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    override fun startDiscovery() {
        _isDiscovering.value = true
        _discoveredDevices.value = listOf(
            DiscoveredDevice(id = "tv-box-1", name = "Xiaomi TV Box", ipAddress = "192.168.1.100")
        )
    }

    override fun stopDiscovery() {
        _isDiscovering.value = false
    }

    fun setDiscoveredDevices(devices: List<DiscoveredDevice>) {
        _discoveredDevices.value = devices
    }
}

class TestSender : Sender {
    private val _isCasting = MutableStateFlow(false)
    override val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    var lastSentNalUnit: ByteArray? = null
        private set

    override fun startCasting(targetDevice: DiscoveredDevice, projectionData: Intent?) {
        _isCasting.value = true
    }

    override fun sendFrame(nalUnit: ByteArray) {
        lastSentNalUnit = nalUnit
    }

    override fun stopCasting() {
        _isCasting.value = false
    }
}

class TestReceiver : Receiver {
    private val _isReceiving = MutableStateFlow(false)
    override val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

    private val _incomingStream = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incomingStream: Flow<ByteArray> = _incomingStream.asSharedFlow()

    override fun startReceiving() {
        _isReceiving.value = true
    }

    override fun stopReceiving() {
        _isReceiving.value = false
    }

    fun emitNalUnit(nalUnit: ByteArray) {
        _incomingStream.tryEmit(nalUnit)
    }
}

class TestCaptureEmitter : CaptureEmitter {
    private val _isCapturing = MutableStateFlow(false)
    override val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _frameStream = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val frameStream: Flow<ByteArray> = _frameStream.asSharedFlow()

    override fun startCapture(projectionData: Intent?, context: Context?) {
        _isCapturing.value = true
    }

    override fun stopCapture() {
        _isCapturing.value = false
    }

    fun emitFrame(nalUnit: ByteArray) {
        _frameStream.tryEmit(nalUnit)
    }
}
