package com.example.apppresentation.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StubDiscoverer : Discoverer {
    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    override fun startDiscovery() {
        _isDiscovering.value = true
        // Simulate discovering a Xiaomi TV Box
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
