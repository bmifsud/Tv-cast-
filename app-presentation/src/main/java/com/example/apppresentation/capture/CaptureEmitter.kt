package com.example.apppresentation.capture

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface CaptureEmitter {
    val isCapturing: StateFlow<Boolean>
    val frameStream: Flow<ByteArray>
    fun startCapture(projectionData: Intent?)
    fun stopCapture()
}
