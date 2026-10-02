package pk.livecaster.app.studio.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.theme.FacebookBrandColor
import com.example.ui.theme.LiveRed
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioDark
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.YouTubeBrandColor
import pk.livecaster.app.core.storage.entity.DestinationEntity
import pk.livecaster.app.core.util.Formatters
import pk.livecaster.app.streaming.capture.CameraCaptureManager
import pk.livecaster.app.streaming.state.StreamStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitCamStudioScreen(
    viewModel: SplitCamStudioViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraManager = remember { CameraCaptureManager(context) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val cam = perms[Manifest.permission.CAMERA] ?: hasCameraPermission
        val aud = perms[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
        hasCameraPermission = cam
        hasAudioPermission = aud
        if (cam) {
            previewViewRef?.let { cameraManager.bindCamera(lifecycleOwner, it) }
        }
    }

    LaunchedEffect(Unit) {
        cameraManager.onYuvFrameAvailable = { yuvBytes ->
            viewModel.feedVideoFrame(yuvBytes)
        }
        if (!hasCameraPermission || !hasAudioPermission) {
            permissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraManager.release()
        }
    }

    val isLive = uiState.telemetry.status == StreamStatus.LIVE
    val isConnecting = uiState.telemetry.status == StreamStatus.CONNECTING

    // Pulsing animation for On Air badge & button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val livePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioDark)
    ) {
        // 1. Fullscreen Camera Viewport
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        cameraManager.bindCamera(lifecycleOwner, this)
                        previewViewRef = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("splitcam_camera_viewport")
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D1117)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = LiveRed,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Camera & Audio Access Needed",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Grant camera and microphone permissions to broadcast live like SplitCam Studio.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            permissionsLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LiveRed),
                        modifier = Modifier.testTag("grant_permissions_button")
                    ) {
                        Text("Grant Permissions", color = Color.White)
                    }
                }
            }
        }

        // 2. Real-Time Color Filter Overlay (Beauty, Warm, Vivid, Noir, Cyberpunk)
        StudioFilterOverlay(filter = uiState.activeFilter)

        // 3. Top Gradient Scrim & Studio Telemetry HUD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Live Status Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = when {
                                    isLive -> LiveRed.copy(alpha = 0.25f)
                                    isConnecting -> StudioAmber.copy(alpha = 0.25f)
                                    else -> Color.Black.copy(alpha = 0.6f)
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = when {
                                    isLive -> LiveRed.copy(alpha = livePulseAlpha)
                                    isConnecting -> StudioAmber
                                    else -> StudioBorder
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isLive -> LiveRed.copy(alpha = livePulseAlpha)
                                        isConnecting -> StudioAmber
                                        else -> TextMuted
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isLive -> "ON AIR"
                                isConnecting -> "CONNECTING…"
                                else -> "STANDBY"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = when {
                                    isLive -> LiveRed
                                    isConnecting -> StudioAmber
                                    else -> TextSecondary
                                },
                                letterSpacing = 1.sp
                            )
                        )
                    }

                    // Center: Stream Duration Timer
                    Text(
                        text = Formatters.formatDuration(uiState.telemetry.durationSeconds),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isLive) Color.White else TextMuted
                        )
                    )

                    // Right: Viewers Counter & Studio Watermark
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                tint = StudioCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = Formatters.formatViewers(uiState.telemetry.currentViewers),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Technical HUD bar: Bitrate, FPS, Resolution, Audio VU Meter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HudPill(label = "${uiState.resolution} ${uiState.frameRate}fps")
                        Spacer(modifier = Modifier.width(6.dp))
                        HudPill(
                            label = if (isLive) Formatters.formatBitrate(uiState.telemetry.currentBitrateKbps) else "${uiState.videoBitrateKbps} kbps"
                        )
                    }

                    // Audio VU Level Meter
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isMicMuted) Icons.Default.MicOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (uiState.isMicMuted) LiveRed else StudioGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF22272E))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(uiState.audioVuLevel.coerceIn(0.05f, 1f))
                                    .height(5.dp)
                                    .background(
                                        when {
                                            uiState.audioVuLevel > 0.85f -> LiveRed
                                            uiState.audioVuLevel > 0.65f -> StudioAmber
                                            else -> StudioGreen
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }

        // 4. Floating Left Side Controls Rack (Flip, Flash, Mic, Filters, Chat, Overlays, Settings)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .border(1.dp, StudioBorder.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Flip Camera
            FloatingToolButton(
                icon = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                isActive = false,
                onClick = {
                    val isFront = cameraManager.switchCamera(lifecycleOwner, previewViewRef ?: return@FloatingToolButton)
                    viewModel.toggleCamera()
                },
                testTag = "btn_switch_camera"
            )

            // Torch / Flash
            FloatingToolButton(
                icon = if (uiState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashlightOff,
                contentDescription = "Toggle Flash",
                isActive = uiState.isTorchOn,
                activeColor = StudioAmber,
                onClick = {
                    val on = viewModel.toggleTorch()
                    cameraManager.toggleTorch(on)
                },
                testTag = "btn_toggle_torch"
            )

            // Mic Mute / Unmute
            FloatingToolButton(
                icon = if (uiState.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Toggle Mic",
                isActive = !uiState.isMicMuted,
                activeColor = StudioGreen,
                inactiveColor = LiveRed,
                onClick = { viewModel.toggleMic() },
                testTag = "btn_toggle_mic"
            )

            // Visual Effects & Filters
            FloatingToolButton(
                icon = Icons.Default.AutoFixHigh,
                contentDescription = "Filters",
                isActive = uiState.activeFilter != StudioFilter.NORMAL,
                activeColor = StudioCyan,
                onClick = { viewModel.setShowFiltersSheet(true) },
                testTag = "btn_studio_filters"
            )

            // In-Stream Live Chat Overlay
            FloatingToolButton(
                icon = Icons.Default.Chat,
                contentDescription = "Live Chat",
                isActive = uiState.isChatVisible,
                activeColor = StudioCyan,
                onClick = { viewModel.toggleChatVisibility() },
                testTag = "btn_toggle_chat"
            )

            // Streamer Lower-Third / Banner Overlay
            FloatingToolButton(
                icon = Icons.Default.Subtitles,
                contentDescription = "Banner",
                isActive = uiState.isLowerThirdVisible,
                activeColor = StudioAmber,
                onClick = { viewModel.setShowOverlaysSheet(true) },
                testTag = "btn_toggle_overlay"
            )

            // Encoder & Stream Settings
            FloatingToolButton(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                isActive = false,
                onClick = { viewModel.setShowSettingsSheet(true) },
                testTag = "btn_studio_settings"
            )

            // Broadcast Logs & History
            FloatingToolButton(
                icon = Icons.Default.History,
                contentDescription = "History",
                isActive = false,
                onClick = { viewModel.setShowHistorySheet(true) },
                testTag = "btn_studio_history"
            )
        }

        // 5. In-Stream Floating Live Chat Overlay (SplitCam Feature)
        if (uiState.isChatVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp, top = 80.dp, bottom = 180.dp)
                    .width(260.dp)
            ) {
                LiveChatFloatingBox(
                    messages = uiState.chatMessages,
                    onSendMessage = { text -> viewModel.sendHostChatMessage(text) }
                )
            }
        }

        // 6. Lower Third / Stream Banner Overlay
        if (uiState.isLowerThirdVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 185.dp)
            ) {
                StreamLowerThirdBanner(
                    title = uiState.streamTitle,
                    host = uiState.hostName,
                    ticker = uiState.tickerText,
                    isLive = isLive
                )
            }
        }

        // 7. Bottom SplitCam Multi-Streaming Dock
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Multi-Platform Destination Selector Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MULTISTREAM DESTINATIONS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            color = StudioCyan,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${uiState.selectedDestinationIds.size} ACTIVE)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    )
                }

                Text(
                    text = "Manage / Add +",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = StudioCyan,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .clickable { viewModel.setShowDestinationsSheet(true) }
                        .padding(4.dp)
                        .testTag("btn_manage_destinations")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Platform Chips with 1-Tap Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.destinations.forEach { dest ->
                    val isSelected = uiState.selectedDestinationIds.contains(dest.id)
                    val brandColor = when (dest.platform) {
                        "FACEBOOK" -> FacebookBrandColor
                        "YOUTUBE" -> YouTubeBrandColor
                        "TWITCH" -> Color(0xFF9146FF)
                        "KICK" -> Color(0xFF53FC18)
                        else -> StudioCyan
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) brandColor.copy(alpha = 0.22f) else Color(0xFF161B22)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) brandColor else StudioBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.toggleDestinationSelection(dest.id) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) brandColor else TextMuted)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = dest.name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = brandColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                // Quick Add Destination Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161B22))
                        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                        .clickable { viewModel.setShowAddDestinationDialog(true) }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = StudioCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add RTMP",
                            style = MaterialTheme.typography.labelMedium.copy(color = StudioCyan)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main "GO LIVE" Action Button
            Button(
                onClick = {
                    if (isLive || isConnecting) {
                        viewModel.promptEndConfirmation(true)
                    } else {
                        viewModel.startLiveStream()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_go_live_toggle"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLive || isConnecting) Color(0xFFB00020) else LiveRed
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isLive || isConnecting) Icons.Default.Stop else Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when {
                            isLive -> "END LIVE STREAM"
                            isConnecting -> "VERIFYING INGEST (${uiState.telemetry.verificationCountdownSeconds}s)…"
                            else -> "GO LIVE (${uiState.selectedDestinationIds.size} PLATFORMS)"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }

        // Error message banner if any
        uiState.telemetry.errorMessage?.let { errorMsg ->
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1115)),
                border = androidx.compose.foundation.BorderStroke(1.dp, LiveRed)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFFB4AB)),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.stopLiveStream() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet 1: Destinations Manager
    if (uiState.showDestinationsSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowDestinationsSheet(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = StudioCard,
            contentColor = TextPrimary
        ) {
            DestinationsManagerSheetContent(
                destinations = uiState.destinations,
                selectedIds = uiState.selectedDestinationIds,
                onToggleSelect = { viewModel.toggleDestinationSelection(it) },
                onAddDestination = { viewModel.setShowAddDestinationDialog(true) },
                onDeleteDestination = { viewModel.deleteDestination(it) },
                onClose = { viewModel.setShowDestinationsSheet(false) }
            )
        }
    }

    // Modal Sheet 2: Encoder Settings
    if (uiState.showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowSettingsSheet(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = StudioCard,
            contentColor = TextPrimary
        ) {
            EncoderSettingsSheetContent(
                resolution = uiState.resolution,
                fps = uiState.frameRate,
                bitrateKbps = uiState.videoBitrateKbps,
                audioBitrateKbps = uiState.audioBitrateKbps,
                isLandscape = uiState.isLandscapeMode,
                onApply = { res, f, b, a, land ->
                    viewModel.updateQualitySettings(res, f, b, a, land)
                    viewModel.setShowSettingsSheet(false)
                }
            )
        }
    }

    // Modal Sheet 3: Studio Filters Tray
    if (uiState.showFiltersSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowFiltersSheet(false) },
            containerColor = StudioCard,
            contentColor = TextPrimary
        ) {
            StudioFiltersSheetContent(
                activeFilter = uiState.activeFilter,
                onSelectFilter = {
                    viewModel.setFilter(it)
                    viewModel.setShowFiltersSheet(false)
                }
            )
        }
    }

    // Modal Sheet 4: Overlays & Branding Customizer
    if (uiState.showOverlaysSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowOverlaysSheet(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = StudioCard,
            contentColor = TextPrimary
        ) {
            OverlaysCustomizerSheetContent(
                currentTitle = uiState.streamTitle,
                currentHost = uiState.hostName,
                currentTicker = uiState.tickerText,
                isLowerThirdOn = uiState.isLowerThirdVisible,
                onToggleLowerThird = { viewModel.toggleLowerThirdVisibility() },
                onSave = { t, h, tick ->
                    viewModel.updateOverlays(t, h, tick)
                    viewModel.setShowOverlaysSheet(false)
                }
            )
        }
    }

    // Modal Sheet 5: History & Session Logs
    if (uiState.showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowHistorySheet(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = StudioCard,
            contentColor = TextPrimary
        ) {
            BroadcastHistorySheetContent(
                broadcasts = uiState.recentBroadcasts,
                onClose = { viewModel.setShowHistorySheet(false) }
            )
        }
    }

    // Add Destination Dialog
    if (uiState.showAddDestinationDialog) {
        AddDestinationDialog(
            onDismiss = { viewModel.setShowAddDestinationDialog(false) },
            onSave = { name, platform, url, key ->
                viewModel.saveDestination(
                    name = name,
                    platform = platform,
                    rtmpUrl = url,
                    streamKey = key
                )
            }
        )
    }

    // End Stream Confirmation Dialog
    if (uiState.showEndConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.promptEndConfirmation(false) },
            containerColor = StudioCard,
            title = {
                Text(
                    text = stringResource(R.string.end_broadcast),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.end_stream_confirm),
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.stopLiveStream() },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveRed)
                ) {
                    Text("Stop Broadcasting", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.promptEndConfirmation(false) }) {
                    Text("Continue Streaming", color = TextSecondary)
                }
            }
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Sub-Components & Sheets
// -------------------------------------------------------------------------------------------------

@Composable
fun HudPill(label: String) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .border(0.5.dp, StudioBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun FloatingToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    activeColor: Color = StudioCyan,
    inactiveColor: Color = Color.White,
    testTag: String = ""
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (isActive) activeColor.copy(alpha = 0.25f) else Color(0x33FFFFFF)
            )
            .border(
                1.dp,
                if (isActive) activeColor else Color.Transparent,
                CircleShape
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) activeColor else inactiveColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun StudioFilterOverlay(filter: StudioFilter) {
    when (filter) {
        StudioFilter.NORMAL -> Unit
        StudioFilter.BEAUTY_GLOW -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFDAB9).copy(alpha = 0.12f),
                                Color(0xFFFFE4E1).copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        StudioFilter.VIBRANT -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF003366).copy(alpha = 0.08f))
            )
        }
        StudioFilter.WARM_SUNSET -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFF8C00).copy(alpha = 0.14f),
                                Color(0xFFFF4500).copy(alpha = 0.09f)
                            )
                        )
                    )
            )
        }
        StudioFilter.NOIR_BW -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }
        StudioFilter.CYBERPUNK -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.12f),
                                Color(0xFFFF007F).copy(alpha = 0.12f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun StreamLowerThirdBanner(
    title: String,
    host: String,
    ticker: String,
    isLive: Boolean
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(LiveRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isLive) "LIVE" else "STUDIO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "$host • $ticker",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LiveChatFloatingBox(
    messages: List<StudioChatMessage>,
    onSendMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.70f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LIVE CHAT FEED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = StudioCyan,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = "${messages.size} msgs",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages) { msg ->
                    val badgeColor = when (msg.platform) {
                        "YOUTUBE" -> YouTubeBrandColor
                        "FACEBOOK" -> FacebookBrandColor
                        "TWITCH" -> Color(0xFF9146FF)
                        "KICK" -> Color(0xFF53FC18)
                        else -> StudioAmber
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .background(badgeColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = msg.platform.take(2),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = badgeColor,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = msg.senderName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFE2E8F0),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Host Reply
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text("Reply as Host…", color = TextMuted, fontSize = 11.sp)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White,
                        fontSize = 11.sp
                    ),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StudioCyan,
                        unfocusedBorderColor = StudioBorder,
                        focusedContainerColor = Color(0x33000000),
                        unfocusedContainerColor = Color(0x33000000)
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = StudioCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DestinationsManagerSheetContent(
    destinations: List<DestinationEntity>,
    selectedIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    onAddDestination: () -> Unit,
    onDeleteDestination: (DestinationEntity) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Multistream Destinations",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Button(
                onClick = onAddDestination,
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("+ Add Platform", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select all platforms you want to stream to simultaneously. Toggle ON and hit GO LIVE!",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(destinations) { dest ->
                val isSelected = selectedIds.contains(dest.id)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) StudioCyan else StudioBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dest.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dest.rtmpUrl,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Switch(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect(dest.id) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = StudioCyan
                            )
                        )

                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onDeleteDestination(dest) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Direct Quick Links to Facebook Live Producer & YouTube Studio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/live/producer"))
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = FacebookBrandColor),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("FB Live Producer", fontSize = 11.sp)
            }

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com/channel/live"))
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = YouTubeBrandColor),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("YouTube Studio", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun EncoderSettingsSheetContent(
    resolution: String,
    fps: Int,
    bitrateKbps: Int,
    audioBitrateKbps: Int,
    isLandscape: Boolean,
    onApply: (String, Int, Int, Int, Boolean) -> Unit
) {
    var selRes by remember { mutableStateOf(resolution) }
    var selFps by remember { mutableIntStateOf(fps) }
    var selBitrate by remember { mutableFloatStateOf(bitrateKbps.toFloat()) }
    var selAudioBitrate by remember { mutableIntStateOf(audioBitrateKbps) }
    var selLandscape by remember { mutableStateOf(isLandscape) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Studio Encoder & Quality Settings",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Resolution
        Text("VIDEO RESOLUTION", style = MaterialTheme.typography.labelSmall.copy(color = StudioCyan))
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1080p", "720p", "480p").forEach { res ->
                FilterChip(
                    selected = selRes == res,
                    onClick = { selRes = res },
                    label = { Text(res) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StudioCyan.copy(alpha = 0.25f),
                        selectedLabelColor = StudioCyan
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // FPS
        Text("FRAME RATE (FPS)", style = MaterialTheme.typography.labelSmall.copy(color = StudioCyan))
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60).forEach { f ->
                FilterChip(
                    selected = selFps == f,
                    onClick = { selFps = f },
                    label = { Text("$f FPS") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StudioCyan.copy(alpha = 0.25f),
                        selectedLabelColor = StudioCyan
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Video Bitrate
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("VIDEO BITRATE", style = MaterialTheme.typography.labelSmall.copy(color = StudioCyan))
            Text("${selBitrate.toInt()} kbps", style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary))
        }
        Slider(
            value = selBitrate,
            onValueChange = { selBitrate = it },
            valueRange = 1500f..6000f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = StudioCyan,
                activeTrackColor = StudioCyan
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Audio Bitrate
        Text("AUDIO BITRATE", style = MaterialTheme.typography.labelSmall.copy(color = StudioCyan))
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(96, 128, 192).forEach { ab ->
                FilterChip(
                    selected = selAudioBitrate == ab,
                    onClick = { selAudioBitrate = ab },
                    label = { Text("$ab kbps") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StudioCyan.copy(alpha = 0.25f),
                        selectedLabelColor = StudioCyan
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Orientation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("LANDSCAPE MODE", style = MaterialTheme.typography.labelSmall.copy(color = StudioCyan))
                Text(
                    text = if (selLandscape) "Landscape 16:9 (YouTube / Twitch)" else "Portrait 9:16 (Shorts / Reels / TikTok)",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
            Switch(
                checked = selLandscape,
                onCheckedChange = { selLandscape = it },
                colors = SwitchDefaults.colors(checkedTrackColor = StudioCyan)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { onApply(selRes, selFps, selBitrate.toInt(), selAudioBitrate, selLandscape) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = LiveRed),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Apply Studio Settings", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StudioFiltersSheetContent(
    activeFilter: StudioFilter,
    onSelectFilter: (StudioFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Live Camera Video Effects",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Select a real-time color grade or beauty effect for your broadcast feed.",
            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioFilter.values().forEach { filter ->
                val isSelected = activeFilter == filter
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectFilter(filter) },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) StudioCyan.copy(alpha = 0.2f) else Color(0xFF161B22)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) StudioCyan else StudioBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) StudioCyan else TextPrimary
                                )
                            )
                            Text(
                                text = filter.description,
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = StudioCyan)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun OverlaysCustomizerSheetContent(
    currentTitle: String,
    currentHost: String,
    currentTicker: String,
    isLowerThirdOn: Boolean,
    onToggleLowerThird: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }
    var host by remember { mutableStateOf(currentHost) }
    var ticker by remember { mutableStateOf(currentTicker) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Broadcast Overlays & Branding",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show Lower-Third Overlay", color = TextPrimary)
            Switch(
                checked = isLowerThirdOn,
                onCheckedChange = { onToggleLowerThird() },
                colors = SwitchDefaults.colors(checkedTrackColor = StudioCyan)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Stream Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = StudioCyan,
                unfocusedBorderColor = StudioBorder
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = StudioCyan,
                unfocusedBorderColor = StudioBorder
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = ticker,
            onValueChange = { ticker = it },
            label = { Text("Scrolling Live Ticker Message") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = StudioCyan,
                unfocusedBorderColor = StudioBorder
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onSave(title, host, ticker) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = LiveRed),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Update On-Screen Overlays", color = Color.White, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun BroadcastHistorySheetContent(
    broadcasts: List<pk.livecaster.app.core.storage.entity.BroadcastEntity>,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Broadcast Session History",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (broadcasts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recorded broadcasts yet. Hit GO LIVE to start your first session!",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(broadcasts) { bc ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = bc.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Duration: ${Formatters.formatDuration(bc.durationSeconds)}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = StudioCyan)
                                )
                                Text(
                                    text = "${bc.resolution} @ ${bc.fps}fps",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun AddDestinationDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var selectedPreset by remember { mutableStateOf(PLATFORM_PRESETS.first()) }
    var name by remember { mutableStateOf(selectedPreset.name) }
    var rtmpUrl by remember { mutableStateOf(selectedPreset.defaultRtmpUrl) }
    var streamKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioCard,
        title = {
            Text(
                text = "Add RTMP Streaming Destination",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Select Platform Preset:", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PLATFORM_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset.id == preset.id,
                            onClick = {
                                selectedPreset = preset
                                name = preset.name
                                rtmpUrl = preset.defaultRtmpUrl
                            },
                            label = { Text(preset.name, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Destination Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rtmpUrl,
                    onValueChange = { rtmpUrl = it },
                    label = { Text("RTMP Server Ingest URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = streamKey,
                    onValueChange = { streamKey = it },
                    label = { Text("Stream Key") },
                    placeholder = { Text(selectedPreset.defaultKeyHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rtmpUrl.isNotBlank()) {
                        onSave(name, selectedPreset.id, rtmpUrl, streamKey)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = LiveRed)
            ) {
                Text("Save Destination", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
