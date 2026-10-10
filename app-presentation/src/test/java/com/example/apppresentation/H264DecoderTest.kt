package com.example.apppresentation

import com.example.apppresentation.decoder.H264Decoder
import org.junit.Assert.assertFalse
import org.junit.Test

class H264DecoderTest {

    @Test
    fun testInitialStateNotRunning() {
        val decoder = H264Decoder()
        assertFalse(decoder.isRunning())
    }

    @Test
    fun testStopWhenNotRunning() {
        val decoder = H264Decoder()
        decoder.stop()
        assertFalse(decoder.isRunning())
    }
}
