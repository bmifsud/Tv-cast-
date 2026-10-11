package com.example.screencapture

import android.app.NotificationManager
import android.content.Context
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Build
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

    @Before
    fun setUp() {
        controller = Robolectric.buildService(ScreenCaptureService::class.java)
        service = controller.get()
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

        try {
            service.startCaptureWithProjection(mockMediaProjection)
        } catch (_: Exception) {
            // MediaCodec in un-mocked Robolectric environment throws
        }

        val shadowService = shadowOf(service)
        assertTrue(shadowService.isForegroundStopped.not())
        assertEquals(ScreenCaptureService.NOTIFICATION_ID, shadowService.lastForegroundNotificationId)
        assertNotNull(shadowService.lastForegroundNotification)

        // Verify resource release on stopCapture / onDestroy
        controller.destroy()

        verify(mockMediaProjection).stop()
        verify(mockVirtualDisplay).release()
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
        try {
            service.startCaptureWithProjection(mockMediaProjection, 1280, 720, 300)
        } catch (_: Exception) {}

        // Re-initialise capture with new resolution using the same MediaProjection
        try {
            service.startCaptureWithProjection(mockMediaProjection, 1920, 1080, 320)
        } catch (_: Exception) {}

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

        try {
            service.startCaptureWithProjection(mockMediaProjection1)
        } catch (_: Exception) {}

        // Switching to a new MediaProjection instance
        try {
            service.startCaptureWithProjection(mockMediaProjection2)
        } catch (_: Exception) {}

        // Assert old projection was stopped and old VirtualDisplay released
        verify(mockMediaProjection1).stop()
        verify(mockVirtualDisplay1).release()

        // Assert new VirtualDisplay was created on new projection
        verify(mockMediaProjection2, times(1)).createVirtualDisplay(
            any(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()
        )
    }
}
