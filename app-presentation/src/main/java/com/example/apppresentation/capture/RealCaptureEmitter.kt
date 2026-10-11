package com.example.apppresentation.capture

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.example.screencapture.ScreenCaptureService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RealCaptureEmitter(
    private var context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : CaptureEmitter {

    private val _isCapturing = MutableStateFlow(false)
    override val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _frameStream = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val frameStream: Flow<ByteArray> = _frameStream.asSharedFlow()

    private var collectJob: Job? = null
    private var boundContext: Context? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? ScreenCaptureService.LocalBinder
            val screenCaptureService = binder?.getService() ?: return

            collectJob?.cancel()
            collectJob = scope.launch {
                screenCaptureService.encodedStream.collect { bytes ->
                    _frameStream.emit(bytes)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            collectJob?.cancel()
            collectJob = null
        }
    }

    override fun startCapture(projectionData: Intent?, context: Context?) {
        _isCapturing.value = true
        if (context != null) {
            this.context = context
        }
        val ctx = this.context ?: return
        if (projectionData == null) return

        val serviceIntent = Intent(ctx, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, Activity.RESULT_OK)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, projectionData)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(serviceIntent)
        } else {
            ctx.startService(serviceIntent)
        }

        boundContext = ctx
        try {
            ctx.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (_: Exception) {}
    }

    override fun stopCapture() {
        _isCapturing.value = false
        collectJob?.cancel()
        collectJob = null

        boundContext?.let { ctx ->
            try {
                ctx.unbindService(serviceConnection)
            } catch (_: Exception) {}
            val stopIntent = Intent(ctx, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP
            }
            ctx.startService(stopIntent)
        }
        boundContext = null
    }

    fun emitFrame(nalUnit: ByteArray) {
        _frameStream.tryEmit(nalUnit)
    }
}
