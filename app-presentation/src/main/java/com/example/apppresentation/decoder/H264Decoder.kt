package com.example.apppresentation.decoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import android.util.Log

class H264Decoder {
    private var mediaCodec: MediaCodec? = null
    private var isConfigured = false

    @Synchronized
    fun start(surface: Surface, width: Int = 1920, height: Int = 1080) {
        if (isConfigured) return
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            val codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, surface, null, 0)
            codec.start()
            mediaCodec = codec
            isConfigured = true
            Log.d("H264Decoder", "MediaCodec H264 decoder started successfully.")
        } catch (e: Exception) {
            Log.e("H264Decoder", "Error starting MediaCodec H264 decoder", e)
        }
    }

    @Synchronized
    fun decodeNalUnit(nalUnit: ByteArray, presentationTimeUs: Long = System.nanoTime() / 1000) {
        val codec = mediaCodec ?: return
        if (!isConfigured) return

        try {
            val inputIndex = codec.dequeueInputBuffer(10_000L) // 10ms timeout
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(nalUnit)
                codec.queueInputBuffer(inputIndex, 0, nalUnit.size, presentationTimeUs, 0)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            while (outputIndex >= 0) {
                codec.releaseOutputBuffer(outputIndex, true)
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
            }
        } catch (e: Exception) {
            Log.e("H264Decoder", "Error decoding NAL unit", e)
        }
    }

    @Synchronized
    fun stop() {
        try {
            mediaCodec?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("H264Decoder", "Error stopping decoder", e)
        } finally {
            mediaCodec = null
            isConfigured = false
        }
    }

    fun isRunning(): Boolean = isConfigured
}
