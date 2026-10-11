package com.example.apppresentation.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface Receiver {
    val isReceiving: StateFlow<Boolean>
    val incomingStream: Flow<ByteArray>
    fun startReceiving()
    fun stopReceiving()
}
