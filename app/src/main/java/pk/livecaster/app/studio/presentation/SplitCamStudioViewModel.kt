package pk.livecaster.app.studio.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import pk.livecaster.app.broadcast.domain.model.BroadcastStatus
import pk.livecaster.app.core.di.AppContainer
import pk.livecaster.app.core.storage.entity.BroadcastEntity
import pk.livecaster.app.core.storage.entity.DestinationEntity
import pk.livecaster.app.streaming.encoder.AudioEncoderConfig
import pk.livecaster.app.streaming.encoder.VideoEncoderConfig
import pk.livecaster.app.streaming.publisher.RtmpEndpoint
import pk.livecaster.app.streaming.service.LiveStreamingService
import pk.livecaster.app.streaming.state.StreamStatus
import pk.livecaster.app.streaming.state.StreamTelemetry
import java.util.UUID
import kotlin.random.Random

enum class StudioFilter(val label: String, val description: String) {
    NORMAL("Normal", "Raw Natural Camera"),
    BEAUTY_GLOW("Beauty Glow", "Skin Smoothing & Soft Lighting"),
    VIBRANT("Vivid", "Punchy Color & High Saturation"),
    WARM_SUNSET("Warm Studio", "Golden Hour Cinematic Tint"),
    NOIR_BW("Noir B&W", "High Contrast Monochrome"),
    CYBERPUNK("Cyberpunk", "Neon Cyan & Magenta Grading")
}

data class StreamPlatformPreset(
    val id: String,
    val name: String,
    val defaultRtmpUrl: String,
    val defaultKeyHint: String,
    val brandColorHex: Long
)

val PLATFORM_PRESETS = listOf(
    StreamPlatformPreset("YOUTUBE", "YouTube Live", "rtmp://a.rtmp.youtube.com/live2/", "e.g. abcd-1234-efgh-5678", 0xFFFF0000),
    StreamPlatformPreset("FACEBOOK", "Facebook Live", "rtmps://live-api-s.facebook.com:443/rtmp/", "e.g. FB-123456789-0-AbCdE", 0xFF1877F2),
    StreamPlatformPreset("TWITCH", "Twitch", "rtmp://live.twitch.tv/app/", "e.g. live_12345678_abcdefgh", 0xFF9146FF),
    StreamPlatformPreset("KICK", "Kick", "rtmp://fa723fc1b171.global-contribute.live-video.net/api/v2/hls/", "e.g. sk_us-west_123456", 0xFF53FC18),
    StreamPlatformPreset("CUSTOM_RTMP", "Custom RTMP", "rtmp://live.livecaster.pk/live", "Stream Key / Token", 0xFF00E5FF)
)

data class StudioChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderName: String,
    val platform: String, // YOUTUBE, FACEBOOK, TWITCH, KICK, HOST
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SplitCamStudioUiState(
    val destinations: List<DestinationEntity> = emptyList(),
    val selectedDestinationIds: Set<Long> = emptySet(),
    val telemetry: StreamTelemetry = StreamTelemetry(),
    val activeFilter: StudioFilter = StudioFilter.NORMAL,
    val isMicMuted: Boolean = false,
    val isTorchOn: Boolean = false,
    val isFrontCamera: Boolean = false,
    val audioVuLevel: Float = 0.05f,
    // Overlays
    val isChatVisible: Boolean = true,
    val isLowerThirdVisible: Boolean = true,
    val isWatermarkVisible: Boolean = true,
    val streamTitle: String = "Live Multistream Studio",
    val hostName: String = "Live Host",
    val tickerText: String = "🔴 Welcome to the live stream! Multistreaming powered by LiveCaster Studio",
    // Quality Settings
    val resolution: String = "720p",
    val videoBitrateKbps: Int = 3500,
    val frameRate: Int = 30,
    val audioBitrateKbps: Int = 128,
    val isLandscapeMode: Boolean = false,
    // Chat messages
    val chatMessages: List<StudioChatMessage> = emptyList(),
    // Active session
    val currentBroadcastId: Long? = null,
    // Destination editing
    val editingDestination: DestinationEntity? = null,
    // Sheets
    val showDestinationsSheet: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showFiltersSheet: Boolean = false,
    val showOverlaysSheet: Boolean = false,
    val showHistorySheet: Boolean = false,
    val showEndConfirmDialog: Boolean = false,
    val showAddDestinationDialog: Boolean = false,
    val recentBroadcasts: List<BroadcastEntity> = emptyList()
)

class SplitCamStudioViewModel(
    private val appContainer: AppContainer
) : ViewModel() {

    private val context: Context get() = appContainer.context
    private val database = appContainer.database
    private val publisher = appContainer.rtmpPublisher

    private val _uiState = MutableStateFlow(SplitCamStudioUiState())
    val uiState: StateFlow<SplitCamStudioUiState> = _uiState.asStateFlow()

    private var vuMeterJob: Job? = null
    private var chatSimJob: Job? = null

    init {
        initializeDefaultDestinations()
        observeDestinations()
        observeTelemetry()
        observeRecentBroadcasts()
        startAudioVuMeter()
    }

    private fun initializeDefaultDestinations() {
        viewModelScope.launch {
            val existing = database.destinationDao().getAllDestinations().firstOrNull()
            if (existing.isNullOrEmpty()) {
                // Pre-populate standard platform destinations with clear preset URLs
                val defaults = listOf(
                    DestinationEntity(
                        name = "YouTube Live",
                        platform = "YOUTUBE",
                        rtmpUrl = "rtmp://a.rtmp.youtube.com/live2/",
                        streamKey = "",
                        isEnabled = true
                    ),
                    DestinationEntity(
                        name = "Facebook Live",
                        platform = "FACEBOOK",
                        rtmpUrl = "rtmps://live-api-s.facebook.com:443/rtmp/",
                        streamKey = "",
                        isEnabled = true
                    ),
                    DestinationEntity(
                        name = "Twitch",
                        platform = "TWITCH",
                        rtmpUrl = "rtmp://live.twitch.tv/app/",
                        streamKey = "",
                        isEnabled = false
                    ),
                    DestinationEntity(
                        name = "Kick",
                        platform = "KICK",
                        rtmpUrl = "rtmp://fa723fc1b171.global-contribute.live-video.net/api/v2/hls/",
                        streamKey = "",
                        isEnabled = false
                    ),
                    DestinationEntity(
                        name = "Custom RTMP",
                        platform = "CUSTOM_RTMP",
                        rtmpUrl = "rtmp://live.livecaster.pk/live",
                        streamKey = "live_stream_key",
                        isEnabled = false
                    )
                )
                for (dest in defaults) {
                    database.destinationDao().insertDestination(dest)
                }
            }
        }
    }

    private fun observeDestinations() {
        viewModelScope.launch {
            database.destinationDao().getAllDestinations().collect { list ->
                val currentSelected = _uiState.value.selectedDestinationIds
                // If selection is empty, select enabled ones by default
                val newSelected = if (currentSelected.isEmpty()) {
                    list.filter { it.isEnabled }.map { it.id }.toSet()
                } else {
                    currentSelected.intersect(list.map { it.id }.toSet())
                }
                _uiState.value = _uiState.value.copy(
                    destinations = list,
                    selectedDestinationIds = if (newSelected.isEmpty() && list.isNotEmpty()) setOf(list.first().id) else newSelected
                )
            }
        }
    }

    private fun observeTelemetry() {
        viewModelScope.launch {
            publisher.telemetry.collect { tele ->
                _uiState.value = _uiState.value.copy(telemetry = tele)
                val bcId = _uiState.value.currentBroadcastId
                if (bcId != null && tele.status == StreamStatus.LIVE && tele.durationSeconds % 5 == 0L) {
                    database.broadcastDao().updateTelemetry(bcId, tele.durationSeconds, tele.currentViewers)
                }
            }
        }
    }

    private fun observeRecentBroadcasts() {
        viewModelScope.launch {
            database.broadcastDao().getAllBroadcasts().collect { list ->
                _uiState.value = _uiState.value.copy(recentBroadcasts = list)
            }
        }
    }

    fun toggleDestinationSelection(id: Long) {
        val current = _uiState.value.selectedDestinationIds.toMutableSet()
        if (current.contains(id)) {
            // Keep at least 1 destination selected if possible
            if (current.size > 1) {
                current.remove(id)
            }
        } else {
            current.add(id)
        }
        _uiState.value = _uiState.value.copy(selectedDestinationIds = current)
    }

    fun setFilter(filter: StudioFilter) {
        _uiState.value = _uiState.value.copy(activeFilter = filter)
    }

    fun toggleTorch(): Boolean {
        val newState = publisher.toggleTorch()
        _uiState.value = _uiState.value.copy(isTorchOn = newState)
        return newState
    }

    fun toggleMic(): Boolean {
        val newState = publisher.toggleMicMute()
        _uiState.value = _uiState.value.copy(isMicMuted = newState)
        return newState
    }

    fun toggleCamera(): Boolean {
        val newState = publisher.toggleCameraFacing()
        _uiState.value = _uiState.value.copy(isFrontCamera = newState)
        return newState
    }

    fun toggleChatVisibility() {
        _uiState.value = _uiState.value.copy(isChatVisible = !_uiState.value.isChatVisible)
    }

    fun toggleLowerThirdVisibility() {
        _uiState.value = _uiState.value.copy(isLowerThirdVisible = !_uiState.value.isLowerThirdVisible)
    }

    fun toggleWatermarkVisibility() {
        _uiState.value = _uiState.value.copy(isWatermarkVisible = !_uiState.value.isWatermarkVisible)
    }

    fun updateOverlays(title: String, host: String, ticker: String) {
        _uiState.value = _uiState.value.copy(
            streamTitle = title,
            hostName = host,
            tickerText = ticker
        )
    }

    fun updateQualitySettings(
        resolution: String,
        fps: Int,
        bitrateKbps: Int,
        audioBitrateKbps: Int,
        isLandscape: Boolean
    ) {
        _uiState.value = _uiState.value.copy(
            resolution = resolution,
            frameRate = fps,
            videoBitrateKbps = bitrateKbps,
            audioBitrateKbps = audioBitrateKbps,
            isLandscapeMode = isLandscape
        )
    }

    fun feedVideoFrame(yuvBytes: ByteArray) {
        publisher.encodeVideoFrame(yuvBytes)
    }

    fun startLiveStream() {
        val state = _uiState.value
        if (state.telemetry.status == StreamStatus.LIVE || state.telemetry.status == StreamStatus.CONNECTING) return

        val selectedDestinations = state.destinations.filter { state.selectedDestinationIds.contains(it.id) }
        val endpoints = selectedDestinations.map { dest ->
            RtmpEndpoint(
                name = dest.name,
                rtmpUrl = dest.rtmpUrl.trim(),
                streamKey = dest.streamKey.trim().ifEmpty { "live_stream_key" }
            )
        }

        if (endpoints.isEmpty()) {
            return
        }

        val (resWidth, resHeight) = when (state.resolution) {
            "1080p" -> Pair(1920, 1080)
            "480p" -> Pair(854, 480)
            else -> Pair(1280, 720)
        }

        val videoConfig = VideoEncoderConfig(
            width = if (state.isLandscapeMode) resWidth else resHeight,
            height = if (state.isLandscapeMode) resHeight else resWidth,
            frameRate = state.frameRate,
            bitrateKbps = state.videoBitrateKbps
        )
        val audioConfig = AudioEncoderConfig(
            bitrateKbps = state.audioBitrateKbps
        )

        LiveStreamingService.startService(context, state.streamTitle)

        viewModelScope.launch {
            val bcEntity = BroadcastEntity(
                title = state.streamTitle,
                description = "Multistream to ${endpoints.joinToString { it.name }}",
                rtmpUrl = endpoints.joinToString("|") { it.rtmpUrl },
                streamKey = endpoints.joinToString("|") { it.streamKey },
                platform = "MULTI_DESTINATION",
                status = "LIVE",
                resolution = state.resolution,
                bitrateKbps = state.videoBitrateKbps,
                fps = state.frameRate,
                startedAt = System.currentTimeMillis()
            )
            val newId = database.broadcastDao().insertBroadcast(bcEntity)
            _uiState.value = _uiState.value.copy(currentBroadcastId = newId)

            publisher.startPublishing(endpoints, videoConfig, audioConfig)
            startChatSimulation()
        }
    }

    fun stopLiveStream() {
        publisher.stopPublishing()
        LiveStreamingService.stopService(context)
        chatSimJob?.cancel()

        val bcId = _uiState.value.currentBroadcastId
        if (bcId != null) {
            viewModelScope.launch {
                database.broadcastDao().updateStatus(bcId, BroadcastStatus.ENDED.name)
            }
        }
        _uiState.value = _uiState.value.copy(
            showEndConfirmDialog = false,
            currentBroadcastId = null
        )
    }

    fun promptEndConfirmation(show: Boolean) {
        _uiState.value = _uiState.value.copy(showEndConfirmDialog = show)
    }

    fun saveDestination(
        id: Long = 0,
        name: String,
        platform: String,
        rtmpUrl: String,
        streamKey: String
    ) {
        viewModelScope.launch {
            val entity = DestinationEntity(
                id = id,
                name = name,
                platform = platform,
                rtmpUrl = rtmpUrl.trim(),
                streamKey = streamKey.trim(),
                isEnabled = true
            )
            if (id == 0L) {
                val newId = database.destinationDao().insertDestination(entity)
                val sel = _uiState.value.selectedDestinationIds.toMutableSet()
                sel.add(newId)
                _uiState.value = _uiState.value.copy(selectedDestinationIds = sel)
            } else {
                database.destinationDao().updateDestination(entity)
            }
            _uiState.value = _uiState.value.copy(showAddDestinationDialog = false, editingDestination = null)
        }
    }

    fun startEditDestination(dest: DestinationEntity?) {
        _uiState.value = _uiState.value.copy(
            editingDestination = dest,
            showAddDestinationDialog = true
        )
    }

    fun deleteDestination(destination: DestinationEntity) {
        viewModelScope.launch {
            database.destinationDao().deleteDestination(destination)
        }
    }

    fun sendHostChatMessage(text: String) {
        if (text.isBlank()) return
        val hostMsg = StudioChatMessage(
            senderName = _uiState.value.hostName,
            platform = "HOST",
            text = text.trim()
        )
        val list = _uiState.value.chatMessages.takeLast(30).toMutableList()
        list.add(hostMsg)
        _uiState.value = _uiState.value.copy(chatMessages = list)
    }

    private fun startChatSimulation() {
        chatSimJob?.cancel()
        chatSimJob = viewModelScope.launch {
            val sampleMessages = listOf(
                Pair("Ahmad Ali", "Great stream quality! Audio sounds crystal clear 🔥"),
                Pair("Sara Khan", "Hello from Lahore! Loving the live setup ❤️"),
                Pair("TechGuy99", "What bitrate are you pushing right now?"),
                Pair("GamerPK", "Stream is super smooth on YouTube and Facebook both!"),
                Pair("Zainab", "Awesome multi-cam quality! 👋"),
                Pair("Bilal_22", "Watching live on Twitch, no lag at all! 🚀"),
                Pair("LiveFan", "Drop the stream key tutorial please!")
            )
            val platforms = listOf("YOUTUBE", "FACEBOOK", "TWITCH", "KICK")
            var idx = 0
            while (_uiState.value.telemetry.status == StreamStatus.LIVE || _uiState.value.telemetry.status == StreamStatus.CONNECTING) {
                delay(Random.nextLong(3500, 7000))
                val (author, text) = sampleMessages[idx % sampleMessages.size]
                val platform = platforms[idx % platforms.size]
                val msg = StudioChatMessage(
                    senderName = author,
                    platform = platform,
                    text = text
                )
                val list = _uiState.value.chatMessages.takeLast(25).toMutableList()
                list.add(msg)
                _uiState.value = _uiState.value.copy(chatMessages = list)
                idx++
            }
        }
    }

    private fun startAudioVuMeter() {
        vuMeterJob?.cancel()
        vuMeterJob = viewModelScope.launch {
            while (true) {
                delay(100)
                if (!_uiState.value.isMicMuted && _uiState.value.telemetry.status == StreamStatus.LIVE) {
                    val level = Random.nextFloat().coerceIn(0.25f, 0.95f)
                    _uiState.value = _uiState.value.copy(audioVuLevel = level)
                } else if (!_uiState.value.isMicMuted) {
                    // Ambient studio audio indicator
                    val level = Random.nextFloat().coerceIn(0.08f, 0.28f)
                    _uiState.value = _uiState.value.copy(audioVuLevel = level)
                } else {
                    _uiState.value = _uiState.value.copy(audioVuLevel = 0.02f)
                }
            }
        }
    }

    // Sheet toggles
    fun setShowDestinationsSheet(show: Boolean) { _uiState.value = _uiState.value.copy(showDestinationsSheet = show) }
    fun setShowSettingsSheet(show: Boolean) { _uiState.value = _uiState.value.copy(showSettingsSheet = show) }
    fun setShowFiltersSheet(show: Boolean) { _uiState.value = _uiState.value.copy(showFiltersSheet = show) }
    fun setShowOverlaysSheet(show: Boolean) { _uiState.value = _uiState.value.copy(showOverlaysSheet = show) }
    fun setShowHistorySheet(show: Boolean) { _uiState.value = _uiState.value.copy(showHistorySheet = show) }
    fun setShowAddDestinationDialog(show: Boolean) { _uiState.value = _uiState.value.copy(showAddDestinationDialog = show) }

    override fun onCleared() {
        super.onCleared()
        vuMeterJob?.cancel()
        chatSimJob?.cancel()
    }
}
