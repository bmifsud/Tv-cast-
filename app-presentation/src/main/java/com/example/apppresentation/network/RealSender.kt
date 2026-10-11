package com.example.apppresentation.network

import android.content.Intent
import com.example.networktransport.UdpSender
import kotlinx.coroutines.flow.StateFlow

class RealSender(
    private val udpSender: UdpSender = UdpSender()
) : Sender {

    override val isCasting: StateFlow<Boolean> = udpSender.isCasting

    override fun startCasting(targetDevice: DiscoveredDevice, projectionData: Intent?) {
        udpSender.startCasting(targetDevice.ipAddress, projectionData)
    }

    override fun stopCasting() {
        udpSender.stopCasting()
    }

    override fun sendFrame(nalUnit: ByteArray) {
        udpSender.sendFrame(nalUnit)
    }
}
