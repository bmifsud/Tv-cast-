package com.example.screencapture

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class ScreenCaptureEncoder(
    private val mediaProjection: MediaProjection,
    val width: Int = 1280,
    val height: Int = 720,
    val dpi: Int = 300,
    val bitRate: Int = 2_000_000,
    val frameRate: Int = 30,
    val iFrameInterval: Int = 2,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private var mediaCodec: MediaCodec? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var inputSurface: Surface? = null
    private var encodingJob: Job? = null

    private val _encodedOutput = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val encodedOutput: SharedFlow<ByteArray> = _encodedOutput.asSharedFlow()

    @Volatile
    var isEncoding: Boolean = false
        private set

    fun start() {
        if (isEncoding) return

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()

        codec.start()
        mediaCodec = codec
        inputSurface = surface

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCaptureDisplay",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )

        isEncoding = true

        encodingJob = scope.launch {
            drainEncoder(codec)
        }
    }

    private suspend fun drainEncoder(codec: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (scope.isActive && isEncoding) {
            try {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outputBufferIndex >= 0) {
                    val outputBuffer: ByteBuffer? = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val data = ByteArray(bufferInfo.size)
                        outputBuffer.get(data)
                        _encodedOutput.emit(data)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                }
            } catch (e: Exception) {
                // Codec stopped or failed
                break
            }
        }
    }

    fun stop() {
        isEncoding = false
        encodingJob?.cancel()
        encodingJob = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            inputSurface?.release()
        } catch (_: Exception) {}
        inputSurface = null

        try {
            mediaCodec?.stop()
        } catch (_: Exception) {}
        try {
            mediaCodec?.release()
        } catch (_: Exception) {}
        mediaCodec = null
    }
}
