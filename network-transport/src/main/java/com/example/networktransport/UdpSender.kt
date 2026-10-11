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

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var targetInetAddress: InetAddress? = null

    fun startCasting(targetIp: String, projectionData: Intent? = null) {
        if (_isCasting.value) return
        _isCasting.value = true
        scope.launch {
            try {
                targetInetAddress = InetAddress.getByName(targetIp)
                socket = DatagramSocket()
            } catch (_: Exception) {}
        }
    }

    fun sendFrame(nalUnit: ByteArray, targetIp: String? = null) {
        if (!_isCasting.value) return
        lastSentFrame = nalUnit
        scope.launch {
            try {
                val address = if (!targetIp.isNullOrBlank()) {
                    InetAddress.getByName(targetIp)
                } else {
                    targetInetAddress ?: InetAddress.getByName("192.168.1.100")
                }
                val ds = socket ?: DatagramSocket().also { socket = it }
                val packet = DatagramPacket(nalUnit, nalUnit.size, address, port)
                ds.send(packet)
            } catch (_: Exception) {}
        }
    }

    fun stopCasting() {
        _isCasting.value = false
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        targetInetAddress = null
    }
}
