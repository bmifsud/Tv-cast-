package com.example.apppresentation.network

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StubSender : Sender {
    private val _isCasting = MutableStateFlow(false)
    override val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    var lastSentNalUnit: ByteArray? = null
        private set

    override fun startCasting(targetDevice: DiscoveredDevice, projectionData: Intent?) {
        _isCasting.value = true
    }

    override fun sendFrame(nalUnit: ByteArray) {
        lastSentNalUnit = nalUnit
    }

    override fun stopCasting() {
        _isCasting.value = false
    }
}
