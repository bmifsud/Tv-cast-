package com.example.apppresentation.ui

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.apppresentation.MainViewModel
import com.example.apppresentation.decoder.H264Decoder
import com.example.apppresentation.utils.DeviceTypeDetector

object TestTags {
    const val TV_CONTAINER = "tv_container"
    const val TV_SURFACE_VIEW = "tv_surface_view"
    const val TV_READY_OVERLAY = "tv_ready_overlay"
    const val TABLET_CONTAINER = "tablet_container"
    const val DISCOVERING_INDICATOR = "discovering_indicator"
    const val START_CASTING_BUTTON = "start_casting_button"
    const val STOP_CASTING_BUTTON = "stop_casting_button"
    const val CASTING_ACTIVE_INDICATOR = "casting_active_indicator"
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    isTvOverride: Boolean? = null
) {
    val context = LocalContext.current
    val isTv = isTvOverride ?: remember(context) { DeviceTypeDetector.isTv(context) }

    if (isTv) {
        TvReceiverScreen(modifier = modifier)
    } else {
        TabletSenderScreen(viewModel = viewModel, modifier = modifier)
    }
}

@Composable
fun TvReceiverScreen(modifier: Modifier = Modifier) {
    val decoder = remember { H264Decoder() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag(TestTags.TV_CONTAINER)
    ) {
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            decoder.start(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {}

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            decoder.stop()
                        }
                    })
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag(TestTags.TV_SURFACE_VIEW)
        )

        // "Ready to Receive" overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .testTag(TestTags.TV_READY_OVERLAY),
            contentAlignment = Alignment.TopStart
        ) {
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Ready to Receive",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Waiting for incoming cast connection...",
                    color = Color.LightGray,
                    fontSize = 16.sp
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            decoder.stop()
        }
    }
}

@Composable
fun TabletSenderScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val isCasting by viewModel.isCasting.collectAsState()
    val devices by viewModel.discoveredDevices.collectAsState()

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startCasting(result.data)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(TestTags.TABLET_CONTAINER),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Tablet Sender Mode",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isCasting) {
            Text(
                text = "Casting to ${viewModel.selectedDevice.value?.name ?: "TV Box"}...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(TestTags.CASTING_ACTIVE_INDICATOR)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.stopCasting() },
                modifier = Modifier.testTag(TestTags.STOP_CASTING_BUTTON)
            ) {
                Text(text = "Stop Casting")
            }
        } else {
            if (isDiscovering) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.testTag(TestTags.DISCOVERING_INDICATOR)
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(text = "Discovering TV...")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (devices.isNotEmpty()) {
                Text(
                    text = "Discovered: ${devices.first().name} (${devices.first().ipAddress})",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.selectDevice(devices.first())
                        val projectionManager =
                            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                        projectionManager?.let { manager ->
                            mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
                        } ?: run {
                            // Fallback if projectionManager is null in preview/test context
                            viewModel.startCasting(null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .testTag(TestTags.START_CASTING_BUTTON)
                ) {
                    Text(text = "Start Casting")
                }
            }
        }
    }
}
