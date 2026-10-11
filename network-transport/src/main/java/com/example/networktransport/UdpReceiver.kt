package com.example.networktransport

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.net.SocketTimeoutException

class UdpReceiver(
    private val port: Int = 8889,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _isReceiving = MutableStateFlow(false)
    val isReceiving: StateFlow<Boolean> = _isReceiving.asStateFlow()

    private val _incomingStream = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingStream: Flow<ByteArray> = _incomingStream.asSharedFlow()

    @Volatile
    private var socket: DatagramSocket? = null
    private var receiveJob: Job? = null

    fun startReceiving() {
        if (_isReceiving.value) return
        _isReceiving.value = true

        receiveJob = scope.launch(Dispatchers.IO) {
            try {
                val ds = try {
                    DatagramSocket(port).apply {
                        soTimeout = 200
                    }
                } catch (_: Exception) { null }
                socket = ds
                if (ds != null) {
                    val buffer = ByteArray(65535)
                    while (isActive && _isReceiving.value && !ds.isClosed) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            ds.receive(packet)
                            val data = buffer.copyOf(packet.length)
                            _incomingStream.emit(data)
                        } catch (_: SocketTimeoutException) {
                            if (!isActive || !_isReceiving.value) break
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

    fun stopReceiving() {
        _isReceiving.value = false
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        receiveJob?.cancel()
        receiveJob = null
    }
}
