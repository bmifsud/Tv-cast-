package com.example.apppresentation.network

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

interface Sender {
    val isCasting: StateFlow<Boolean>
    fun startCasting(targetDevice: DiscoveredDevice, projectionData: Intent?)
    fun sendFrame(nalUnit: ByteArray)
    fun stopCasting()
}
