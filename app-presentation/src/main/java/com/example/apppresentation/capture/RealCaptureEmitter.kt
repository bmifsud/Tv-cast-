package com.example.apppresentation.capture

import android.content.Intent
import com.example.screencapture.ScreenCaptureService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class RealCaptureEmitter : CaptureEmitter {
    private val _isCapturing = MutableStateFlow(false)
    override val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _frameStream = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val frameStream: Flow<ByteArray> = _frameStream.asSharedFlow()

    override fun startCapture(projectionData: Intent?) {
        _isCapturing.value = true
    }

    override fun stopCapture() {
        _isCapturing.value = false
    }

    fun emitFrame(nalUnit: ByteArray) {
        _frameStream.tryEmit(nalUnit)
    }
}
