package com.example.networktransport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException

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

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var discoveryJob: Job? = null
    private var advertisingJob: Job? = null
    @Volatile
    private var discoverySocket: DatagramSocket? = null
    @Volatile
    private var advertisingSocket: DatagramSocket? = null

    fun startDiscovery() {
        if (_isDiscovering.value) return
        _isDiscovering.value = true

        discoveryJob = scope.launch(Dispatchers.IO) {
            try {
                val currentList = mutableListOf<DiscoveredDevice>()
                currentList.add(DiscoveredDevice("default-tv", "Xiaomi TV Box", "192.168.1.100"))
                _discoveredDevices.value = currentList.toList()

                val socket = try {
                    DatagramSocket().apply {
                        broadcast = true
                        soTimeout = 200
                    }
                } catch (_: Exception) { null }

                discoverySocket = socket

                if (socket != null) {
                    val probeData = "PROBE_TV_CAST".toByteArray()
                    val broadcastAddr = InetAddress.getByName("255.255.255.255")
                    val probePacket = DatagramPacket(probeData, probeData.size, broadcastAddr, port)
                    try {
                        socket.send(probePacket)
                    } catch (_: Exception) {}

                    val buffer = ByteArray(1024)
                    while (isActive && _isDiscovering.value && !socket.isClosed) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            val msg = String(packet.data, 0, packet.length).trim()
                            if (msg.startsWith("TV_CAST_RESPONSE")) {
                                val parts = msg.split(":")
                                val deviceName = if (parts.size > 1) parts[1] else "TV Box"
                                val ip = packet.address.hostAddress ?: "192.168.1.100"
                                val device = DiscoveredDevice(id = ip, name = deviceName, ipAddress = ip)

                                if (currentList.none { it.ipAddress == ip }) {
                                    currentList.add(device)
                                    _discoveredDevices.value = currentList.toList()
                                }
                            }
                        } catch (_: SocketTimeoutException) {
                            if (!isActive || !_isDiscovering.value) break
                        } catch (_: SocketException) {
                            break
                        } catch (_: Exception) {
                            break
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stopDiscovery() {
        _isDiscovering.value = false
        try {
            discoverySocket?.close()
        } catch (_: Exception) {}
        discoverySocket = null
        discoveryJob?.cancel()
        discoveryJob = null
    }

    fun startAdvertising(deviceName: String = "Xiaomi TV Box") {
        if (_isAdvertising.value) return
        _isAdvertising.value = true

        advertisingJob = scope.launch(Dispatchers.IO) {
            try {
                val socket = try {
                    DatagramSocket(port).apply {
                        broadcast = true
                        soTimeout = 200
                    }
                } catch (_: Exception) { null }

                advertisingSocket = socket
                if (socket != null) {
                    val buffer = ByteArray(1024)
                    while (isActive && _isAdvertising.value && !socket.isClosed) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            val msg = String(packet.data, 0, packet.length).trim()
                            if (msg == "PROBE_TV_CAST") {
                                val responseData = "TV_CAST_RESPONSE:$deviceName".toByteArray()
                                val responsePacket = DatagramPacket(
                                    responseData,
                                    responseData.size,
                                    packet.address,
                                    packet.port
                                )
                                socket.send(responsePacket)
                            }
                        } catch (_: SocketTimeoutException) {
                            if (!isActive || !_isAdvertising.value) break
                        } catch (_: SocketException) {
                            break
                        } catch (_: Exception) {
                            break
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stopAdvertising() {
        _isAdvertising.value = false
        try {
            advertisingSocket?.close()
        } catch (_: Exception) {}
        advertisingSocket = null
        advertisingJob?.cancel()
        advertisingJob = null
    }
}
