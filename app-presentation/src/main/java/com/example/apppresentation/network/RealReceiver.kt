package com.example.apppresentation.network

import com.example.networktransport.UdpReceiver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class RealReceiver(
    private val udpReceiver: UdpReceiver = UdpReceiver()
) : Receiver {
    override val isReceiving: StateFlow<Boolean> = udpReceiver.isReceiving
    override val incomingStream: Flow<ByteArray> = udpReceiver.incomingStream

    override fun startReceiving() {
        udpReceiver.startReceiving()
    }

    override fun stopReceiving() {
        udpReceiver.stopReceiving()
    }
}
