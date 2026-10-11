package com.example.apppresentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apppresentation.capture.CaptureEmitter
import com.example.apppresentation.capture.RealCaptureEmitter
import com.example.apppresentation.network.DiscoveredDevice
import com.example.apppresentation.network.Discoverer
import com.example.apppresentation.network.RealDiscoverer
import com.example.apppresentation.network.RealSender
import com.example.apppresentation.network.Sender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    val discoverer: Discoverer = RealDiscoverer(),
    val sender: Sender = RealSender(),
    val captureEmitter: CaptureEmitter = RealCaptureEmitter()
) : ViewModel() {

    private val _selectedDevice = MutableStateFlow<DiscoveredDevice?>(null)
    val selectedDevice: StateFlow<DiscoveredDevice?> = _selectedDevice.asStateFlow()

    val isDiscovering = discoverer.isDiscovering
    val discoveredDevices = discoverer.discoveredDevices
    val isCasting = sender.isCasting

    init {
        // Automatically listen to frames from capture emitter when casting
        viewModelScope.launch {
            captureEmitter.frameStream.collect { nalUnit ->
                if (isCasting.value) {
                    sender.sendFrame(nalUnit)
                }
            }
        }
    }

    fun startDiscovery() {
        discoverer.startDiscovery()
    }

    fun stopDiscovery() {
        discoverer.stopDiscovery()
    }

    fun selectDevice(device: DiscoveredDevice) {
        _selectedDevice.value = device
    }

    fun startCasting(projectionIntent: Intent?) {
        val target = _selectedDevice.value ?: discoveredDevices.value.firstOrNull()
        target?.let { device ->
            _selectedDevice.value = device
            sender.startCasting(device, projectionIntent)
            captureEmitter.startCapture(projectionIntent)
        }
    }

    fun stopCasting() {
        captureEmitter.stopCapture()
        sender.stopCasting()
    }
}
