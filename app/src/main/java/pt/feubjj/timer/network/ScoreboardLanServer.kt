package pt.feubjj.timer.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pt.feubjj.timer.ui.viewmodel.FeuTimerSessionController
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Embedded zero-latency (`TCP_NODELAY`) HTTP/1.1 + RFC 6455 WebSocket LAN server running on the
 * Android TV (`:7777` by default) so any mobile phone on the same Wi-Fi can open the single-screen
 * Portrait Referee Console and control athlete names, points, match clock, and saved results live.
 */
class ScoreboardLanServer(
    private val controller: FeuTimerSessionController,
    private val refereeHtmlProvider: () -> ByteArray,
    private val scope: CoroutineScope,
    private val preferredPorts: List<Int> = listOf(7777, 7778, 7779, 8777, 0)
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var broadcastJob: Job? = null
    private var ipMonitorJob: Job? = null

    private val wsClients: MutableSet<WsClient> =
        Collections.newSetFromMap(ConcurrentHashMap<WsClient, Boolean>())

    var boundPort: Int = -1
        private set

    fun start() {
        if (serverSocket != null) return

        var bound: ServerSocket? = null
        for (port in preferredPorts) {
            val candidate = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", port))
                }
            }.getOrNull()
            if (candidate != null) {
                bound = candidate
                boundPort = candidate.localPort
                break
            }
        }
        val activeServer = bound ?: return
        serverSocket = activeServer
        publishLanStatus()

        // Accept incoming mobile HTTP / WebSocket connections
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !activeServer.isClosed) {
                val client = runCatching { activeServer.accept() }.getOrNull() ?: break
                launch(Dispatchers.IO) {
                    handleSocket(client)
                }
            }
        }

        // Broadcast every state change immediately to all connected phones (< 5 ms)
        broadcastJob = scope.launch(Dispatchers.IO) {
            var lastPayload = ""
            controller.uiState.collect {
                if (wsClients.isEmpty()) return@collect
                val json = controller.toRefereeStateJson()
                if (json != lastPayload) {
                    lastPayload = json
                    broadcastTextFrame(json)
                }
            }
        }

        // Refresh LAN IPv4 address periodically in case Wi-Fi reconnects
        ipMonitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                publishLanStatus()
                delay(4_000L)
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        broadcastJob?.cancel()
        ipMonitorJob?.cancel()
        for (c in wsClients) {
            runCatching { c.socket.close() }
        }
        wsClients.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        boundPort = -1
    }

    private fun publishLanStatus() {
        if (boundPort <= 0) return
        val ip = LanAddressResolver.findLocalIpv4Address()
        val displayUrl = if (ip != null) "$ip:$boundPort" else null
        controller.updateLanServerStatus(url = displayUrl, connectedClients = wsClients.size)
    }

    private fun handleSocket(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            val headerBlock = readHttpHeaderBlock(input) ?: run {
                socket.close()
                return
            }
            val lines = headerBlock.split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty().split(" ")
            if (requestLine.size < 2) {
                socket.close()
                return
            }
            val method = requestLine[0].uppercase()
            val rawPath = requestLine[1].substringBefore('?')

            val headers = LinkedHashMap<String, String>()
            for (i in 1 until lines.size) {
                val idx = lines[i].indexOf(':')
                if (idx > 0) {
                    headers[lines[i].substring(0, idx).trim().lowercase()] =
                        lines[i].substring(idx + 1).trim()
                }
            }

            // 1. RFC 6455 WebSocket Upgrade (`/ws`)
            val upgrade = headers["upgrade"]?.lowercase()
            val wsKey = headers["sec-websocket-key"]
            if (method == "GET" && upgrade == "websocket" && !wsKey.isNullOrBlank()) {
                upgradeToWebSocket(socket, input, output, wsKey)
                return
            }

            // 2. CORS preflight
            if (method == "OPTIONS") {
                writeHttpResponse(output, "204 No Content", "text/plain", ByteArray(0))
                socket.close()
                return
            }

            // 3. Instant JSON command fallback (`POST /api/cmd`)
            if (method == "POST" && rawPath == "/api/cmd") {
                val contentLen = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 65536) ?: 0
                val bodyBytes = readExactBytes(input, contentLen)
                val nextJson = controller.handleRefereeJsonCommand(bodyBytes.toString(Charsets.UTF_8))
                broadcastTextFrame(nextJson)
                writeHttpResponse(output, "200 OK", "application/json; charset=utf-8", nextJson.toByteArray(Charsets.UTF_8))
                socket.close()
                return
            }

            // 4. Current state snapshot (`GET /api/state`)
            if (method == "GET" && rawPath == "/api/state") {
                val json = controller.toRefereeStateJson()
                writeHttpResponse(output, "200 OK", "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8))
                socket.close()
                return
            }

            // 5. Serve Single-Page Portrait Mobile Referee Console (`GET /` or any path)
            val htmlBytes = refereeHtmlProvider()
            writeHttpResponse(output, "200 OK", "text/html; charset=utf-8", htmlBytes)
            socket.close()
        } catch (_: Throwable) {
            runCatching { socket.close() }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun upgradeToWebSocket(
        socket: Socket,
        input: InputStream,
        output: OutputStream,
        secWebSocketKey: String
    ) {
        val magic = secWebSocketKey.trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val sha1 = MessageDigest.getInstance("SHA-1").digest(magic.toByteArray(Charsets.US_ASCII))
        val acceptKey = Base64.Default.encode(sha1)

        val handshake = buildString {
            append("HTTP/1.1 101 Switching Protocols\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Accept: ").append(acceptKey).append("\r\n\r\n")
        }
        output.write(handshake.toByteArray(Charsets.US_ASCII))
        output.flush()

        val client = WsClient(socket, output)
        wsClients += client
        publishLanStatus()

        try {
            // Immediately push full current state with zero delay
            client.sendText(controller.toRefereeStateJson())

            while (!socket.isClosed) {
                val b0 = input.read()
                if (b0 < 0) break
                val b1 = input.read()
                if (b1 < 0) break

                val opcode = b0 and 0x0F
                val masked = (b1 and 0x80) != 0
                var payloadLen = (b1 and 0x7F).toLong()

                if (payloadLen == 126L) {
                    val hi = input.read()
                    val lo = input.read()
                    if (hi < 0 || lo < 0) break
                    payloadLen = ((hi shl 8) or lo).toLong()
                } else if (payloadLen == 127L) {
                    var len64 = 0L
                    repeat(8) {
                        val v = input.read()
                        if (v < 0) return@repeat
                        len64 = (len64 shl 8) or v.toLong()
                    }
                    payloadLen = len64
                }

                if (payloadLen < 0L || payloadLen > 131_072L) break
                val maskKey = if (masked) readExactBytes(input, 4) else ByteArray(0)
                val payload = readExactBytes(input, payloadLen.toInt())
                if (masked && maskKey.size == 4) {
                    for (i in payload.indices) {
                        payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
                    }
                }

                when (opcode) {
                    0x8 -> break // Close
                    0x9 -> client.sendControlFrame(0xA, payload) // Ping -> Pong
                    0x1 -> {
                        val text = payload.toString(Charsets.UTF_8)
                        val updatedState = controller.handleRefereeJsonCommand(text)
                        broadcastTextFrame(updatedState)
                    }
                }
            }
        } catch (_: Throwable) {
            // Client disconnected cleanly or Wi-Fi dropped
        } finally {
            wsClients -= client
            runCatching { socket.close() }
            publishLanStatus()
        }
    }

    private fun broadcastTextFrame(text: String) {
        val dead = mutableListOf<WsClient>()
        for (client in wsClients) {
            if (!client.sendText(text)) {
                dead += client
            }
        }
        if (dead.isNotEmpty()) {
            wsClients.removeAll(dead.toSet())
            publishLanStatus()
        }
    }

    private class WsClient(
        val socket: Socket,
        private val out: OutputStream
    ) {
        @Synchronized
        fun sendText(text: String): Boolean = sendFrame(0x1, text.toByteArray(Charsets.UTF_8))

        @Synchronized
        fun sendControlFrame(opcode: Int, payload: ByteArray): Boolean = sendFrame(opcode, payload)

        @Synchronized
        private fun sendFrame(opcode: Int, payload: ByteArray): Boolean {
            return try {
                out.write(0x80 or (opcode and 0x0F))
                val len = payload.size
                when {
                    len <= 125 -> out.write(len)
                    len <= 65535 -> {
                        out.write(126)
                        out.write((len ushr 8) and 0xFF)
                        out.write(len and 0xFF)
                    }
                    else -> {
                        out.write(127)
                        val l = len.toLong()
                        for (shift in 56 downTo 0 step 8) {
                            out.write(((l ushr shift) and 0xFF).toInt())
                        }
                    }
                }
                out.write(payload)
                out.flush()
                true
            } catch (_: Throwable) {
                runCatching { socket.close() }
                false
            }
        }
    }

    private fun writeHttpResponse(
        out: OutputStream,
        status: String,
        contentType: String,
        body: ByteArray
    ) {
        val headers = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.US_ASCII))
        out.write(body)
        out.flush()
    }

    private fun readHttpHeaderBlock(input: InputStream): String? {
        val buf = ByteArrayOutputStream(512)
        var matchState = 0
        while (buf.size() < 16_384) {
            val b = input.read()
            if (b < 0) return if (buf.size() > 0) buf.toString(Charsets.ISO_8859_1.name()) else null
            buf.write(b)
            matchState = when {
                matchState == 0 && b == '\r'.code -> 1
                matchState == 1 && b == '\n'.code -> 2
                matchState == 2 && b == '\r'.code -> 3
                matchState == 3 && b == '\n'.code -> return buf.toString(Charsets.ISO_8859_1.name())
                b == '\r'.code -> 1
                else -> 0
            }
        }
        return buf.toString(Charsets.ISO_8859_1.name())
    }

    private fun readExactBytes(input: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(out, offset, count - offset)
            if (read <= 0) break
            offset += read
        }
        return if (offset == count) out else out.copyOf(offset)
    }
}
