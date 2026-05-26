package com.im.client.data.remote

import android.util.Log
import com.google.protobuf.GeneratedMessageLite
import com.im.protocol.ImProto
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

class TcpConnection private constructor() {

    private val seqGenerator = AtomicInteger(1)
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingPackets = Channel<ImPacket>(Channel.BUFFERED)
    val incomingPackets: Channel<ImPacket> = _incomingPackets

    private val pendingRequests = mutableMapOf<Int, CompletableDeferred<ImPacket>>()

    private var host: String = "10.0.2.2"
    private var port: Int = 8800
    private var reconnectAttempts = 0
    private var isAuthenticated = false

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, AUTHENTICATED
    }

    companion object {
        private const val TAG = "TcpConnection"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val MAX_RECONNECT_DELAY_MS = 60_000L

        @Volatile
        private var instance: TcpConnection? = null

        fun getInstance(): TcpConnection {
            return instance ?: synchronized(this) {
                instance ?: TcpConnection().also { instance = it }
            }
        }
    }

    fun configure(host: String, port: Int) {
        this.host = host
        this.port = port
    }

    fun connect() {
        scope.launch {
            if (_connectionState.value != ConnectionState.DISCONNECTED) return@launch
            _connectionState.value = ConnectionState.CONNECTING
            tryConnect()
        }
    }

    private suspend fun tryConnect() {
        try {
            val sock = Socket(host, port)
            sock.tcpNoDelay = true
            sock.soTimeout = 0
            socket = sock
            inputStream = sock.getInputStream()
            outputStream = sock.getOutputStream()
            _connectionState.value = ConnectionState.CONNECTED
            reconnectAttempts = 0
            Log.i(TAG, "Connected to $host:$port")

            readJob = scope.launch { readLoop() }
        } catch (e: Exception) {
            Log.e(TAG, "Connect failed: ${e.message}")
            _connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    fun disconnect() {
        scope.launch {
            isAuthenticated = false
            heartbeatJob?.cancel()
            readJob?.cancel()
            reconnectJob?.cancel()
            try { socket?.close() } catch (_: Exception) {}
            socket = null
            inputStream = null
            outputStream = null
            _connectionState.value = ConnectionState.DISCONNECTED
            pendingRequests.clear()
        }
    }

    private suspend fun scheduleReconnect() {
        val delay = minOf(
            (1000L * (1L shl reconnectAttempts.coerceAtMost(5))),
            MAX_RECONNECT_DELAY_MS
        )
        reconnectAttempts++
        Log.i(TAG, "Reconnect in ${delay}ms (attempt $reconnectAttempts)")
        delay(delay)
        if (_connectionState.value == ConnectionState.DISCONNECTED) {
            tryConnect()
        }
    }

    fun authenticate(token: String, deviceId: String, platform: Int = 2) {
        scope.launch {
            val req = ImProto.AuthRequest.newBuilder()
                .setToken(token)
                .setDeviceId(deviceId)
                .setPlatform(platform)
                .build()
            val response = sendRequest(Cmd.AUTH, MsgType.REQUEST, req)
            if (response != null) {
                val ack = ImProto.AuthResponse.parseFrom(response.bodyBytes)
                if (ack.code == 0) {
                    isAuthenticated = true
                    _connectionState.value = ConnectionState.AUTHENTICATED
                    startHeartbeat()
                    Log.i(TAG, "Auth success: userId=${ack.userInfo.userId}")
                } else {
                    Log.e(TAG, "Auth failed: ${ack.msg}")
                    disconnect()
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (_connectionState.value == ConnectionState.AUTHENTICATED) {
                    try {
                        val req = ImProto.HeartbeatRequest.newBuilder().build()
                        sendRequest(Cmd.HEARTBEAT, MsgType.REQUEST, req)
                    } catch (e: Exception) {
                        Log.w(TAG, "Heartbeat failed: ${e.message}")
                    }
                }
            }
        }
    }

    suspend fun sendRequest(cmd: Int, msgType: Int, body: GeneratedMessageLite<*, *>): ImPacket? {
        val seq = seqGenerator.getAndIncrement()
        val packet = ImPacket(cmd, msgType, seq, body.toByteArray())
        val deferred = CompletableDeferred<ImPacket>()
        synchronized(pendingRequests) {
            pendingRequests[seq] = deferred
        }
        sendRaw(packet)
        return try {
            withTimeout(15_000L) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            synchronized(pendingRequests) { pendingRequests.remove(seq) }
            Log.w(TAG, "Request timeout: cmd=0x${cmd.toString(16)}")
            null
        }
    }

    fun sendNotify(cmd: Int, msgType: Int, body: GeneratedMessageLite<*, *>) {
        val seq = seqGenerator.getAndIncrement()
        val packet = ImPacket(cmd, msgType, seq, body.toByteArray())
        scope.launch { sendRaw(packet) }
    }

    private suspend fun sendRaw(packet: ImPacket) {
        try {
            val os = outputStream ?: return
            val bytes = packet.encode()
            os.write(bytes)
            os.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
            _connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    private suspend fun readLoop() {
        val headerBuf = ByteArray(Cmd.HEADER_LEN)
        try {
            while (isActive) {
                val is = inputStream ?: break

                readFully(is, headerBuf, Cmd.HEADER_LEN)
                val headerBb = ByteBuffer.wrap(headerBuf)
                val magic = headerBb.short.toInt() and 0xFFFF
                if (magic != Cmd.MAGIC) continue

                headerBb.short // skip version
                val cmd = headerBb.short.toInt() and 0xFFFF
                val msgType = headerBb.get().toInt() and 0xFF
                val sequenceId = headerBb.int
                val dataLength = headerBb.int
                // skip 7-byte padding (already consumed by headerBuf being 22 bytes)

                val bodyBytes = if (dataLength > 0) {
                    val body = ByteArray(dataLength)
                    readFully(is, body, dataLength)
                    body
                } else ByteArray(0)

                val packet = ImPacket(cmd, msgType, sequenceId, bodyBytes)

                if (packet.msgType == MsgType.RESPONSE) {
                    synchronized(pendingRequests) {
                        pendingRequests.remove(packet.sequenceId)?.let {
                            it.complete(packet)
                        }
                    }
                } else {
                    _incomingPackets.send(packet)
                }

                if (packet.cmd == Cmd.KICKOFF) {
                    Log.w(TAG, "Kicked off by server")
                    disconnect()
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Read loop error: ${e.message}")
        } finally {
            if (_connectionState.value != ConnectionState.DISCONNECTED) {
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        }
    }

    private suspend fun readFully(is: InputStream, buf: ByteArray, len: Int) {
        var read = 0
        while (read < len) {
            val n = is.read(buf, read, len - read)
            if (n < 0) throw java.io.IOException("Connection closed")
            read += n
        }
    }
}
