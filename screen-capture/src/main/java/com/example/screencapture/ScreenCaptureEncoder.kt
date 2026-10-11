package com.example.screencapture

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.CancellationException
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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    existingVirtualDisplay: VirtualDisplay? = null
) {
    private var mediaCodec: MediaCodec? = null
    var virtualDisplay: VirtualDisplay? = existingVirtualDisplay
        private set
    private var inputSurface: Surface? = null
    private var encodingJob: Job? = null

    private val _encodedOutput = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val encodedOutput: SharedFlow<ByteArray> = _encodedOutput.asSharedFlow()

    @Volatile
    var isEncoding: Boolean = false
        private set

    fun start() {
        if (isEncoding) return

        var codec: MediaCodec? = null
        var surface: Surface? = null
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
            }

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec.createInputSurface()

            codec.start()
            mediaCodec = codec
            inputSurface = surface

            val currentVd = virtualDisplay
            if (currentVd == null) {
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
            } else {
                currentVd.setSurface(surface)
                currentVd.resize(width, height, dpi)
            }

            isEncoding = true

            val activeCodec = codec
            encodingJob = scope.launch {
                drainEncoder(activeCodec)
            }
        } catch (e: Exception) {
            isEncoding = false
            try { virtualDisplay?.release() } catch (_: Exception) {}
            virtualDisplay = null
            try { surface?.release() } catch (_: Exception) {}
            inputSurface = null
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            mediaCodec = null
            throw e
        }
    }

    private suspend fun drainEncoder(codec: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            while (scope.isActive && isEncoding) {
                val outputBufferIndex = try {
                    codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ScreenCaptureEncoder", "Error dequeuing output buffer", e)
                    break
                }

                if (outputBufferIndex >= 0) {
                    val outputBuffer: ByteBuffer? = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val data = ByteArray(bufferInfo.size)
                        outputBuffer.get(data)
                        _encodedOutput.emit(data)
                    }
                    try {
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ScreenCaptureEncoder", "Error draining encoder", e)
        } finally {
            isEncoding = false
        }
    }

    fun stop(releaseVirtualDisplay: Boolean = true) {
        isEncoding = false
        encodingJob?.cancel()
        encodingJob = null

        if (releaseVirtualDisplay) {
            try {
                virtualDisplay?.release()
            } catch (_: Exception) {}
            virtualDisplay = null
        }

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
