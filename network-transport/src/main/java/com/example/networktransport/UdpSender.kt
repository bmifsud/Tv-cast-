package com.example.networktransport

import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpSender(
    private val port: Int = 8889,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    @Volatile
    var lastSentFrame: ByteArray? = null
        private set

    private var socket: DatagramSocket? = null

    fun startCasting(targetIp: String, projectionData: Intent? = null) {
        if (_isCasting.value) return
        _isCasting.value = true
        scope.launch {
            try {
                socket = DatagramSocket()
            } catch (_: Exception) {}
        }
    }

    fun sendFrame(nalUnit: ByteArray, targetIp: String = "192.168.1.100") {
        if (!_isCasting.value) return
        lastSentFrame = nalUnit
        scope.launch {
            try {
                val address = InetAddress.getByName(targetIp)
                val packet = DatagramPacket(nalUnit, nalUnit.size, address, port)
                socket?.send(packet)
            } catch (_: Exception) {}
        }
    }

    fun stopCasting() {
        _isCasting.value = false
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
    }
}
