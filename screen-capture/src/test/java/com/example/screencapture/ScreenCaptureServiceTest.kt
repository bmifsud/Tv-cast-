package com.example.screencapture

import android.app.NotificationManager
import android.content.Context
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.projection.MediaProjection
import android.os.Build
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class ScreenCaptureServiceTest {

    private lateinit var controller: ServiceController<ScreenCaptureService>
    private lateinit var service: ScreenCaptureService
    private lateinit var mockCodec: MediaCodec
    private lateinit var mockSurface: Surface

    @Before
    fun setUp() {
        controller = Robolectric.buildService(ScreenCaptureService::class.java)
        service = controller.get()

        mockCodec = mock(MediaCodec::class.java)
        mockSurface = mock(Surface::class.java)
        `when`(mockCodec.createInputSurface()).thenReturn(mockSurface)
        service.codecFactory = { mockCodec }
    }

    @Test
    fun `test notification channel creation on service create`() {
        controller.create()

        val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(ScreenCaptureService.CHANNEL_ID)

        assertNotNull(channel)
        assertEquals(ScreenCaptureService.CHANNEL_ID, channel.id)
    }

    @Test
    fun `test encoder configuration parameters`() {
        val mockMediaProjection = mock(MediaProjection::class.java)

        val encoder = ScreenCaptureEncoder(
            mediaProjection = mockMediaProjection,
            width = 1920,
            height = 1080,
            dpi = 320,
            bitRate = 4_000_000,
            frameRate = 60,
            iFrameInterval = 1
        )

        assertEquals(1920, encoder.width)
        assertEquals(1080, encoder.height)
        assertEquals(320, encoder.dpi)
        assertEquals(4_000_000, encoder.bitRate)
        assertEquals(60, encoder.frameRate)
        assertEquals(1, encoder.iFrameInterval)
    }

    @Test
    fun `test foreground service state transitions and startCaptureWithProjection`() {
        controller.create()

        val mockMediaProjection = mock(MediaProjection::class.java)
        val mockVirtualDisplay = mock(VirtualDisplay::class.java)

        `when`(mockMediaProjection.createVirtualDisplay(
            any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()
        )).thenReturn(mockVirtualDisplay)

        service.startCaptureWithProjection(mockMediaProjection)

        val shadowService = shadowOf(service)
        assertTrue(shadowService.isForegroundStopped.not())
        assertEquals(ScreenCaptureService.NOTIFICATION_ID, shadowService.lastForegroundNotificationId)
        assertNotNull(shadowService.lastForegroundNotification)

        // Verify resource release on stopCapture / onDestroy
        controller.destroy()

        verify(mockMediaProjection).stop()
        verify(mockVirtualDisplay, times(1)).release()
        assertTrue(shadowService.isForegroundStopped)
    }

    @Test
    fun `test virtual display reuse on encoder reinitialization`() {
        controller.create()

        val mockMediaProjection = mock(MediaProjection::class.java)
        val mockVirtualDisplay = mock(VirtualDisplay::class.java)

        `when`(mockMediaProjection.createVirtualDisplay(
            any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()
        )).thenReturn(mockVirtualDisplay)

        // Initial capture setup
        service.startCaptureWithProjection(mockMediaProjection, 1280, 720, 300)

        // Re-initialise capture with new resolution using the same MediaProjection
        service.startCaptureWithProjection(mockMediaProjection, 1920, 1080, 320)

        // Assert createVirtualDisplay was only called once for this MediaProjection
        verify(mockMediaProjection, times(1)).createVirtualDisplay(
            any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()
        )

        // Assert VirtualDisplay setSurface and resize were called to re-configure existing VirtualDisplay
        verify(mockVirtualDisplay).setSurface(any())
        verify(mockVirtualDisplay).resize(1920, 1080, 320)
        verify(mockVirtualDisplay, never()).release()
    }

    @Test
    fun `test new virtual display created for new media projection`() {
        controller.create()

        val mockMediaProjection1 = mock(MediaProjection::class.java)
        val mockVirtualDisplay1 = mock(VirtualDisplay::class.java)
        `when`(mockMediaProjection1.createVirtualDisplay(
            any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()
        )).thenReturn(mockVirtualDisplay1)

        val mockMediaProjection2 = mock(MediaProjection::class.java)
        val mockVirtualDisplay2 = mock(VirtualDisplay::class.java)
        `when`(mockMediaProjection2.createVirtualDisplay(
            any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()
        )).thenReturn(mockVirtualDisplay2)

        service.startCaptureWithProjection(mockMediaProjection1)

        // Switching to a new MediaProjection instance
        service.startCaptureWithProjection(mockMediaProjection2)

        // Assert old projection was stopped and old VirtualDisplay released exact-once
        verify(mockMediaProjection1).stop()
        verify(mockVirtualDisplay1, times(1)).release()

        // Assert new VirtualDisplay was created on new projection
        verify(mockMediaProjection2, times(1)).createVirtualDisplay(
            any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()
        )
    }

    @Test
    fun `test startup failure clears retained virtual display for subsequent restart`() {
        controller.create()

        val mockMediaProjection = mock(MediaProjection::class.java)
        val mockVirtualDisplay1 = mock(VirtualDisplay::class.java)
        val mockVirtualDisplay2 = mock(VirtualDisplay::class.java)

        `when`(mockMediaProjection.createVirtualDisplay(
            any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()
        )).thenReturn(mockVirtualDisplay1).thenReturn(mockVirtualDisplay2)

        // Successful initial start
        service.startCaptureWithProjection(mockMediaProjection, 1280, 720, 300)

        // Make codecFactory throw on next start
        val failingCodec = mock(MediaCodec::class.java)
        `when`(failingCodec.createInputSurface()).thenThrow(RuntimeException("Codec creation failed"))
        service.codecFactory = { failingCodec }

        try {
            service.startCaptureWithProjection(mockMediaProjection, 1280, 720, 300)
        } catch (_: Exception) {}

        // Verify initial display was released when start failed
        verify(mockVirtualDisplay1, times(1)).release()

        // Restore working codecFactory
        val mockCodec2 = mock(MediaCodec::class.java)
        val mockSurface2 = mock(Surface::class.java)
        `when`(mockCodec2.createInputSurface()).thenReturn(mockSurface2)
        service.codecFactory = { mockCodec2 }

        // Next start should create a fresh VirtualDisplay rather than reusing the released one
        service.startCaptureWithProjection(mockMediaProjection, 1280, 720, 300)

        verify(mockMediaProjection, times(2)).createVirtualDisplay(
            any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()
        )
    }
}
