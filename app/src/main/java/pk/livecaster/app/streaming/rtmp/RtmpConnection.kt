package pk.livecaster.app.streaming.rtmp

import android.util.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class RtmpConnection : RtmpStreamSink {

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var outChunkSize = 128
    private var inChunkSize = 128
    private var isConnected = false
    private var assignedStreamId = 1

    var onStatusListener: ((statusMessage: String, isError: Boolean) -> Unit)? = null
    var onPublishVerified: (() -> Unit)? = null
    var onPublishFailed: ((String) -> Unit)? = null

    @Volatile private var isPublishVerified = false
    fun isPublishVerified(): Boolean = isPublishVerified

    private var connectLatch: CountDownLatch? = null
    private var createStreamLatch: CountDownLatch? = null

    fun connect(
        rtmpUrl: String,
        streamKey: String,
        width: Int,
        height: Int,
        fps: Int,
        videoBitrateKbps: Int
    ): Boolean {
        try {
            // Normalize URL and Key: handle case where user pasted combined URL+Key
            val (cleanUrl, cleanKey) = normalizeUrlAndKey(rtmpUrl.trim(), streamKey.trim())

            val isRtmps = cleanUrl.startsWith("rtmps://", ignoreCase = true)
            val host = extractHost(cleanUrl)
            val port = extractPort(cleanUrl, isRtmps)
            val app = extractApp(cleanUrl)
            val tcUrl = cleanUrl.trimEnd('/')

            Log.d(TAG, "Connecting RTMP: host=$host, port=$port, app=$app, isRtmps=$isRtmps, tcUrl=$tcUrl")
            onStatusListener?.invoke("Connecting to $host:$port…", false)

            val s: Socket = if (isRtmps) {
                val raw = Socket()
                raw.tcpNoDelay = true
                raw.connect(InetSocketAddress(host, port), 10000)
                val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val ssl = factory.createSocket(raw, host, port, true) as SSLSocket
                ssl.useClientMode = true
                try {
                    val params = ssl.sslParameters
                    params.serverNames = listOf(SNIHostName(host))
                    ssl.sslParameters = params
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set SNI explicitly: ${e.message}")
                }
                ssl.startHandshake()
                ssl
            } else {
                val raw = Socket()
                raw.tcpNoDelay = true
                raw.connect(InetSocketAddress(host, port), 10000)
                raw
            }

            s.tcpNoDelay = true
            s.soTimeout = 10000
            socket = s
            outputStream = BufferedOutputStream(s.getOutputStream(), 32768)
            inputStream = s.getInputStream()

            // Reset chunk size to standard initial 128 bytes
            outChunkSize = 128
            inChunkSize = 128

            // 1. RTMP C0/C1 -> S0/S1 -> C2 -> S2 Handshake
            onStatusListener?.invoke("Handshake with streaming server…", false)
            performHandshake()

            isConnected = true
            connectLatch = CountDownLatch(1)
            createStreamLatch = CountDownLatch(1)

            // Start reader thread immediately to process server control packets & ACKs
            startReaderThread()

            // 2. Send Connect Command with initial 128 byte chunk size
            onStatusListener?.invoke("Negotiating RTMP session…", false)
            sendConnect(app, tcUrl)

            // 3. Send Set Chunk Size to 4096 (effective for all subsequent messages)
            sendSetChunkSize(4096)

            // Wait for NetConnection.Connect.Success from server
            val connectSuccess = connectLatch?.await(8, TimeUnit.SECONDS) ?: false
            if (!connectSuccess && !isConnected) {
                throw IllegalStateException("Connection rejected or timed out by server")
            }

            // 4. Stream negotiation
            sendReleaseStream(cleanKey)
            sendFCPublish(cleanKey)
            sendCreateStream()

            // Wait for createStream response
            createStreamLatch?.await(5, TimeUnit.SECONDS)

            // 5. Publish to assigned stream ID
            onStatusListener?.invoke("Publishing stream key to server…", false)
            sendPublish(cleanKey, assignedStreamId)

            // 6. MetaData on assigned stream ID
            sendMetaData(width, height, fps, videoBitrateKbps, assignedStreamId)

            // 7. Initial AAC audio header
            sendAacSequenceHeader(44100, 2)

            // Disable socket read timeout for ongoing streaming session
            try {
                s.soTimeout = 0
            } catch (_: Exception) {}

            Log.d(TAG, "RTMP connection successfully established and published to streamId=$assignedStreamId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to RTMP server", e)
            close()
            throw e
        }
    }

    private fun normalizeUrlAndKey(url: String, key: String): Pair<String, String> {
        if (key.isBlank() && url.contains("/rtmp/")) {
            val after = url.substringAfterLast("/")
            val before = url.substringBeforeLast("/") + "/"
            if (after.startsWith("FB-") || after.length > 8) {
                return Pair(before, after)
            }
        }
        return Pair(url, key)
    }

    private fun performHandshake() {
        val out = outputStream ?: throw IllegalStateException("No output stream")
        val inStream = inputStream ?: throw IllegalStateException("No input stream")

        // C0
        out.write(0x03)

        // C1 (1536 bytes)
        val c1 = ByteArray(1536)
        SecureRandom().nextBytes(c1)
        c1[0] = 0; c1[1] = 0; c1[2] = 0; c1[3] = 0
        c1[4] = 0; c1[5] = 0; c1[6] = 0; c1[7] = 0
        out.write(c1)
        out.flush()

        // S0
        val s0 = inStream.read()
        if (s0 != 0x03) {
            throw IllegalStateException("Invalid RTMP version received from server: $s0")
        }

        // S1 (1536 bytes)
        val s1 = ByteArray(1536)
        readFully(inStream, s1)

        // C2 (echo of S1)
        out.write(s1)
        out.flush()

        // S2 (echo of C1)
        val s2 = ByteArray(1536)
        readFully(inStream, s2)
    }

    private fun readFully(inStream: InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val read = inStream.read(target, offset, target.size - offset)
            if (read < 0) throw IllegalStateException("Unexpected EOF during RTMP handshake")
            offset += read
        }
    }

    private fun sendSetChunkSize(size: Int) {
        val payload = ByteArray(4)
        payload[0] = ((size shr 24) and 0x7F).toByte()
        payload[1] = ((size shr 16) and 0xFF).toByte()
        payload[2] = ((size shr 8) and 0xFF).toByte()
        payload[3] = (size and 0xFF).toByte()
        sendRtmpPacket(csid = 2, messageType = 1, streamId = 0, timestamp = 0, payload = payload)
        outChunkSize = size
    }

    private fun sendConnect(app: String, tcUrl: String) {
        val amf = AmfWriter()
        amf.writeString("connect")
        amf.writeNumber(1.0) // Transaction ID
        val connectProps = mapOf(
            "app" to app,
            "flashVer" to "FMLE/3.0 (compatible; FMSc/1.0)",
            "tcUrl" to tcUrl,
            "type" to "nonprivate",
            "fpad" to false,
            "capabilities" to 15.0,
            "audioCodecs" to 3191.0,
            "videoCodecs" to 252.0,
            "videoFunction" to 1.0
        )
        amf.writeObject(connectProps)
        sendRtmpPacket(csid = 3, messageType = 20, streamId = 0, timestamp = 0, payload = amf.toByteArray())
    }

    private fun sendReleaseStream(streamKey: String) {
        val amf = AmfWriter()
        amf.writeString("releaseStream")
        amf.writeNumber(2.0)
        amf.writeNull()
        amf.writeString(streamKey)
        sendRtmpPacket(csid = 3, messageType = 20, streamId = 0, timestamp = 0, payload = amf.toByteArray())
    }

    private fun sendFCPublish(streamKey: String) {
        val amf = AmfWriter()
        amf.writeString("FCPublish")
        amf.writeNumber(3.0)
        amf.writeNull()
        amf.writeString(streamKey)
        sendRtmpPacket(csid = 3, messageType = 20, streamId = 0, timestamp = 0, payload = amf.toByteArray())
    }

    private fun sendCreateStream() {
        val amf = AmfWriter()
        amf.writeString("createStream")
        amf.writeNumber(4.0)
        amf.writeNull()
        sendRtmpPacket(csid = 3, messageType = 20, streamId = 0, timestamp = 0, payload = amf.toByteArray())
    }

    private fun sendPublish(streamKey: String, streamId: Int) {
        val amf = AmfWriter()
        amf.writeString("publish")
        amf.writeNumber(5.0)
        amf.writeNull()
        amf.writeString(streamKey)
        amf.writeString("live")
        sendRtmpPacket(csid = 3, messageType = 20, streamId = streamId, timestamp = 0, payload = amf.toByteArray())
    }

    private fun sendMetaData(width: Int, height: Int, fps: Int, videoBitrateKbps: Int, streamId: Int) {
        val amf = AmfWriter()
        amf.writeString("@setDataFrame")
        amf.writeString("onMetaData")
        val meta = mapOf(
            "duration" to 0.0,
            "width" to width.toDouble(),
            "height" to height.toDouble(),
            "videodatarate" to videoBitrateKbps.toDouble(),
            "framerate" to fps.toDouble(),
            "videocodecid" to 7.0, // AVC / H.264
            "audiodatarate" to 128.0,
            "audiosamplerate" to 44100.0,
            "audiosamplesize" to 16.0,
            "stereo" to true,
            "audiocodecid" to 10.0 // AAC
        )
        amf.writeEcmaArray(meta)
        sendRtmpPacket(csid = 3, messageType = 18, streamId = streamId, timestamp = 0, payload = amf.toByteArray())
    }

    override fun sendAvcSequenceHeader(sps: ByteArray, pps: ByteArray) {
        val cleanSps = removeStartCode(sps)
        val cleanPps = removeStartCode(pps)
        val out = ByteArrayOutputStream()
        // FLV Video Tag header
        out.write(0x17) // 1: Keyframe, 7: AVC
        out.write(0x00) // AVC sequence header
        out.write(0x00) // Composition time
        out.write(0x00)
        out.write(0x00)

        // AVCDecoderConfigurationRecord
        out.write(0x01) // configurationVersion
        out.write(if (cleanSps.size > 1) cleanSps[1].toInt() and 0xFF else 0x42)
        out.write(if (cleanSps.size > 2) cleanSps[2].toInt() and 0xFF else 0x00)
        out.write(if (cleanSps.size > 3) cleanSps[3].toInt() and 0xFF else 0x1F)
        out.write(0xFF)

        // SPS
        out.write(0xE1)
        out.write((cleanSps.size shr 8) and 0xFF)
        out.write(cleanSps.size and 0xFF)
        out.write(cleanSps)

        // PPS
        out.write(0x01)
        out.write((cleanPps.size shr 8) and 0xFF)
        out.write(cleanPps.size and 0xFF)
        out.write(cleanPps)

        val payload = out.toByteArray()
        sendRtmpPacket(csid = 6, messageType = 9, streamId = assignedStreamId, timestamp = 0, payload = payload)
    }

    override fun sendVideoNalu(nalu: ByteArray, isKeyframe: Boolean, timestampMs: Long) {
        val cleanNalu = removeStartCode(nalu)
        if (cleanNalu.isEmpty()) return

        val out = ByteArrayOutputStream(cleanNalu.size + 9)
        // FLV Video Tag Header
        out.write(if (isKeyframe) 0x17 else 0x27)
        out.write(0x01) // AVC NALU
        out.write(0x00) // Composition Time Offset
        out.write(0x00)
        out.write(0x00)

        // 4 bytes NAL length
        val len = cleanNalu.size
        out.write((len shr 24) and 0xFF)
        out.write((len shr 16) and 0xFF)
        out.write((len shr 8) and 0xFF)
        out.write(len and 0xFF)
        out.write(cleanNalu)

        sendRtmpPacket(csid = 6, messageType = 9, streamId = assignedStreamId, timestamp = timestampMs, payload = out.toByteArray())
    }

    private fun removeStartCode(data: ByteArray): ByteArray {
        return if (data.size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 0.toByte() && data[3] == 1.toByte()) {
            data.copyOfRange(4, data.size)
        } else if (data.size >= 3 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 1.toByte()) {
            data.copyOfRange(3, data.size)
        } else {
            data
        }
    }

    override fun sendAacSequenceHeader(sampleRate: Int, channelCount: Int) {
        val out = ByteArrayOutputStream()
        out.write(0xAF) // 10: AAC, 3: 44kHz, 1: 16-bit, 1: Stereo
        out.write(0x00) // AAC sequence header

        // AudioSpecificConfig (2 bytes for AAC-LC)
        val audioObjectType = 2
        val sampleRateIndex = 4
        val byte1 = (audioObjectType shl 3) or (sampleRateIndex shr 1)
        val byte2 = ((sampleRateIndex and 0x01) shl 7) or (channelCount shl 3)
        out.write(byte1 and 0xFF)
        out.write(byte2 and 0xFF)

        sendRtmpPacket(csid = 4, messageType = 8, streamId = assignedStreamId, timestamp = 0, payload = out.toByteArray())
    }

    override fun sendAudioFrame(data: ByteArray, offset: Int, size: Int, timestampMs: Long) {
        val out = ByteArrayOutputStream(size + 2)
        out.write(0xAF)
        out.write(0x01) // AAC raw
        out.write(data, offset, size)
        sendRtmpPacket(csid = 4, messageType = 8, streamId = assignedStreamId, timestamp = timestampMs, payload = out.toByteArray())
    }

    @Synchronized
    private fun sendRtmpPacket(csid: Int, messageType: Int, streamId: Int, timestamp: Long, payload: ByteArray) {
        val out = outputStream ?: return
        val length = payload.size
        var offset = 0

        // Type 0 Chunk Header (11 bytes header + 1 byte basic)
        // Basic header
        out.write((0x00 shl 6) or (csid and 0x3F))

        // Message header (11 bytes)
        val ts = (timestamp and 0xFFFFFF).toInt()
        out.write((ts shr 16) and 0xFF)
        out.write((ts shr 8) and 0xFF)
        out.write(ts and 0xFF)

        out.write((length shr 16) and 0xFF)
        out.write((length shr 8) and 0xFF)
        out.write(length and 0xFF)

        out.write(messageType and 0xFF)

        // Stream ID (4 bytes, Little Endian)
        out.write(streamId and 0xFF)
        out.write((streamId shr 8) and 0xFF)
        out.write((streamId shr 16) and 0xFF)
        out.write((streamId shr 24) and 0xFF)

        // Write first chunk using current outChunkSize (128 for connect, 4096 thereafter)
        val firstChunkSize = minOf(outChunkSize, length)
        out.write(payload, 0, firstChunkSize)
        offset += firstChunkSize

        // Subsequent chunks: Type 3 Chunk Header (1 byte basic header)
        while (offset < length) {
            val chunkLen = minOf(outChunkSize, length - offset)
            out.write((0x03 shl 6) or (csid and 0x3F))
            out.write(payload, offset, chunkLen)
            offset += chunkLen
        }
        out.flush()
    }

    private var readerThread: Thread? = null

    private fun startReaderThread() {
        readerThread = Thread({
            val buffer = ByteArray(4096)
            val inStream = inputStream ?: return@Thread
            while (isConnected) {
                try {
                    val read = inStream.read(buffer)
                    if (read < 0) {
                        Log.d(TAG, "Server closed socket stream")
                        isConnected = false
                        break
                    }
                    if (read > 0) {
                        val str = String(buffer, 0, minOf(read, 2048), Charsets.ISO_8859_1)

                        if (str.contains("NetConnection.Connect.Success")) {
                            Log.d(TAG, "RTMP connection accepted: NetConnection.Connect.Success")
                            connectLatch?.countDown()
                            onStatusListener?.invoke("Session accepted by server", false)
                        }

                        if (str.contains("_result")) {
                            connectLatch?.countDown()
                            createStreamLatch?.countDown()
                        }

                        if (str.contains("NetStream.Publish.Start")) {
                            Log.d(TAG, "RTMP server confirmed: NetStream.Publish.Start")
                            isPublishVerified = true
                            onPublishVerified?.invoke()
                            onStatusListener?.invoke("Live broadcast transmission verified", false)
                        } else if (str.contains("NetStream.Publish.BadName") || str.contains("Connect.Rejected") || str.contains("Publish.Denied")) {
                            Log.e(TAG, "RTMP server rejected stream: $str")
                            onPublishFailed?.invoke("Facebook rejected stream key. Please check your key in Live Producer.")
                            onStatusListener?.invoke("Server rejected stream key. Please check key in Live Producer.", true)
                        }
                    }
                } catch (e: Exception) {
                    if (isConnected) {
                        Log.w(TAG, "Reader thread exception: ${e.message}")
                    }
                    break
                }
            }
        }, "LiveCaster-RtmpReader")
        readerThread?.start()
    }

    fun isConnected(): Boolean = isConnected

    fun close() {
        isConnected = false
        connectLatch?.countDown()
        createStreamLatch?.countDown()
        readerThread?.interrupt()
        readerThread = null
        try { outputStream?.flush() } catch (_: Exception) {}
        try { outputStream?.close() } catch (_: Exception) {}
        try { inputStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        outputStream = null
        inputStream = null
    }

    private fun extractHost(url: String): String {
        return try {
            val clean = url.substringAfter("://").substringBefore("/")
            clean.substringBefore(":")
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    private fun extractPort(url: String, isRtmps: Boolean): Int {
        val defaultPort = if (isRtmps) 443 else 1935
        return try {
            val clean = url.substringAfter("://").substringBefore("/")
            if (clean.contains(":")) {
                clean.substringAfter(":").toIntOrNull() ?: defaultPort
            } else {
                defaultPort
            }
        } catch (_: Exception) {
            defaultPort
        }
    }

    private fun extractApp(url: String): String {
        return try {
            val path = url.substringAfter("://").substringAfter("/", "").trim().trimEnd('/')
            if (path.isNotBlank()) path else "live"
        } catch (_: Exception) {
            "live"
        }
    }

    companion object {
        private const val TAG = "RtmpConnection"
    }
}
