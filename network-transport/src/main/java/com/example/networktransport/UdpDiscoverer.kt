package com.example.networktransport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val ipAddress: String
)

class UdpDiscoverer(
    private val port: Int = 8888,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var discoveryJob: Job? = null

    fun startDiscovery() {
        if (_isDiscovering.value) return
        _isDiscovering.value = true

        discoveryJob = scope.launch {
            try {
                val current = _discoveredDevices.value.toMutableList()
                if (current.none { it.id == "default-tv" }) {
                    current.add(DiscoveredDevice("default-tv", "Xiaomi TV Box", "192.168.1.100"))
                    _discoveredDevices.value = current
                }
            } catch (_: Exception) {}
        }
    }

    fun stopDiscovery() {
        _isDiscovering.value = false
        discoveryJob?.cancel()
        discoveryJob = null
    }
}
