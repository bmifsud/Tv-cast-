package com.example.screencapture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: ScreenCaptureEncoder? = null
    private var encoderCollectJob: Job? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _encodedStream = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val encodedStream: Flow<ByteArray> = _encodedStream.asSharedFlow()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCapture()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != 0 && resultData != null) {
                    startCapture(resultCode, resultData)
                }
            }
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    fun startCaptureWithProjection(projection: MediaProjection, width: Int = 1280, height: Int = 720, dpi: Int = 300) {
        if (mediaProjection != projection) {
            stopPreviousSession()
            mediaProjection = projection
            try {
                projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            } catch (_: Exception) {}
        }
        startForegroundService()

        initAndStartEncoder(projection, width, height, dpi)
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        stopPreviousSession()
        startForegroundService()

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection != null) {
            mediaProjection = projection
            try {
                projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
            } catch (_: Exception) {}

            initAndStartEncoder(projection, 1280, 720, 300)
        }
    }

    private fun stopPreviousSession() {
        encoderCollectJob?.cancel()
        encoderCollectJob = null

        encoder?.stop(releaseVirtualDisplay = true)
        encoder = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
        } catch (_: Exception) {}
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
    }

    private fun startForegroundService() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initAndStartEncoder(projection: MediaProjection, width: Int, height: Int, dpi: Int) {
        encoderCollectJob?.cancel()
        encoderCollectJob = null

        encoder?.stop(releaseVirtualDisplay = false)

        val newEncoder = ScreenCaptureEncoder(
            mediaProjection = projection,
            width = width,
            height = height,
            dpi = dpi,
            existingVirtualDisplay = virtualDisplay
        )
        encoder = newEncoder

        encoderCollectJob = serviceScope.launch {
            newEncoder.encodedOutput.collect { bytes ->
                _encodedStream.emit(bytes)
            }
        }

        newEncoder.start()
        virtualDisplay = newEncoder.virtualDisplay
    }

    fun stopCapture() {
        stopPreviousSession()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopCapture()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel used for Screen Capture Foreground Service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recording")
            .setContentText("Recording screen in progress...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "screen_capture_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.screencapture.action.START"
        const val ACTION_STOP = "com.example.screencapture.action.STOP"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
    }
}
