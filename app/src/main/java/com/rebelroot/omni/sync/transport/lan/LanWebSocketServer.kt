package com.rebelroot.omni.sync.transport.lan

import android.util.Log
import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.bookmarks.storage.saveBookmarks
import com.rebelroot.omni.sync.conflict.ConflictEngine
import com.rebelroot.omni.sync.core.SyncBridge
import com.rebelroot.omni.sync.crypto.DeviceKeyManager
import com.rebelroot.omni.sync.crypto.EncryptedEnvelope
import com.rebelroot.omni.sync.crypto.TrustManager
import com.rebelroot.omni.sync.model.SyncOperation
import com.rebelroot.omni.sync.storage.SyncStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread

private const val TAG = "LanWebSocketServer"

/**
 * Lightweight, zero-dependency embedded WebSocket (RFC 6455) and HTTP REST LAN server.
 * Enables Desktop WebExtensions (Chrome, Firefox, Edge) to connect over local Wi-Fi,
 * authenticate, and bi-directionally synchronize bookmarks, tabs, and mutations.
 */
class LanWebSocketServer(
    var port: Int = 8765,
    private val keyManager: DeviceKeyManager,
    private val trustManager: TrustManager,
    private val storage: SyncStorage,
    private val conflictEngine: ConflictEngine,
    private val collection: BookmarkCollection,
    private val syncBridge: SyncBridge = SyncBridge.getInstance()
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val clientThreads = Executors.newCachedThreadPool()

    fun start() {
        synchronized(LanWebSocketServer::class.java) {
            activeServerInstance?.stop()
            activeServerInstance = this
        }

        if (isRunning) return
        isRunning = true

        val preferredPort = if (port > 0) port else 8765
        var bound = false

        for (attempt in 1..3) {
            try {
                val sSocket = ServerSocket()
                sSocket.reuseAddress = true
                sSocket.bind(InetSocketAddress(preferredPort))
                serverSocket = sSocket
                port = sSocket.localPort
                bound = true
                Log.i(TAG, "LanWebSocketServer listening on port $port (IP: ${getLocalIpAddress()})")
                break
            } catch (e: Exception) {
                Log.w(TAG, "Bind attempt $attempt to port $preferredPort failed: ${e.message}")
                if (attempt < 3) {
                    try { Thread.sleep(200) } catch (_: Exception) {}
                }
            }
        }

        if (!bound) {
            try {
                val fallbackSocket = ServerSocket()
                fallbackSocket.reuseAddress = true
                fallbackSocket.bind(InetSocketAddress(0))
                serverSocket = fallbackSocket
                port = fallbackSocket.localPort
                Log.i(TAG, "LanWebSocketServer dynamic port allocated: $port")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind server socket", e)
                isRunning = false
                return
            }
        }

        thread(isDaemon = true, name = "Omni-LanWebSocket-Acceptor") {
            while (isRunning && serverSocket?.isClosed == false) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    clientThreads.execute {
                        handleClientConnection(socket)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleClientConnection(socket: Socket) {
        try {
            socket.soTimeout = 30_000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))

            val firstLine = reader.readLine() ?: return
            val headers = mutableMapOf<String, String>()
            var line = reader.readLine()
            while (!line.isNullOrBlank()) {
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val k = line.substring(0, colon).trim().lowercase()
                    val v = line.substring(colon + 1).trim()
                    headers[k] = v
                }
                line = reader.readLine()
            }

            // Check if WebSocket Upgrade request
            val isUpgrade = headers["upgrade"]?.equals("websocket", ignoreCase = true) == true
            val secKey = headers["sec-websocket-key"]

            if (isUpgrade && !secKey.isNullOrBlank()) {
                // Perform RFC 6455 Handshake
                val acceptKey = computeSecWebSocketAccept(secKey)
                val requestedProtocol = headers["sec-websocket-protocol"]
                val protocolHeader = if (!requestedProtocol.isNullOrBlank() && requestedProtocol.contains("omni-sync-v1")) {
                    "Sec-WebSocket-Protocol: omni-sync-v1\r\n"
                } else {
                    ""
                }
                val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: $acceptKey\r\n" +
                        protocolHeader + "\r\n"

                output.write(response.toByteArray(StandardCharsets.UTF_8))
                output.flush()

                handleWebSocketFrames(socket, input, output)
            } else if (firstLine.startsWith("POST /api/sync/exchange")) {
                // Handle HTTP REST exchange fallback
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val bodyChars = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = reader.read(bodyChars, readTotal, contentLength - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                val body = String(bodyChars, 0, readTotal)
                val responseJson = processSyncExchangePayload(body)
                val responseBytes = responseJson.toByteArray(StandardCharsets.UTF_8)
                sendHttpResponse(socket, output, "200 OK", "application/json; charset=UTF-8", responseBytes)
            } else if (firstLine.startsWith("POST /api/sync/pair")) {
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val bodyChars = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = reader.read(bodyChars, readTotal, contentLength - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                val body = String(bodyChars, 0, readTotal)
                val responseJson = processPairingPayload(body)
                val responseBytes = responseJson.toByteArray(StandardCharsets.UTF_8)
                sendHttpResponse(socket, output, "200 OK", "application/json; charset=UTF-8", responseBytes)
            } else if (firstLine.startsWith("POST /api/chat/send")) {
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val bodyChars = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = reader.read(bodyChars, readTotal, contentLength - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                val body = String(bodyChars, 0, readTotal)
                val chatRepo = com.rebelroot.omni.sync.chat.ChatRepository.getInstance(keyManager.deviceId, keyManager.deviceName)
                val appCtx = com.rebelroot.omni.OmniApplication.appContext
                chatRepo.onIncomingMessage(appCtx, body)

                val responseJson = JSONObject().apply {
                    put("status", "success")
                    put("timestamp", System.currentTimeMillis())
                }.toString()
                val responseBytes = responseJson.toByteArray(StandardCharsets.UTF_8)
                sendHttpResponse(socket, output, "200 OK", "application/json; charset=UTF-8", responseBytes)
            } else if (firstLine.startsWith("GET /api/chat/messages")) {
                val chatRepo = com.rebelroot.omni.sync.chat.ChatRepository.getInstance(keyManager.deviceId, keyManager.deviceName)
                val messagesArray = org.json.JSONArray()
                chatRepo.messages.value.forEach { msg ->
                    messagesArray.put(msg.toJson())
                }
                val responseJson = JSONObject().apply {
                    put("status", "success")
                    put("messages", messagesArray)
                }.toString()
                val responseBytes = responseJson.toByteArray(StandardCharsets.UTF_8)
                sendHttpResponse(socket, output, "200 OK", "application/json; charset=UTF-8", responseBytes)
            } else if (firstLine.startsWith("OPTIONS ")) {
                sendHttpResponse(socket, output, "200 OK", "text/plain", ByteArray(0))
            } else {
                // Return Status Info
                val statusJson = JSONObject().apply {
                    put("status", "online")
                    put("deviceId", keyManager.deviceId)
                    put("deviceName", keyManager.deviceName)
                    put("publicKeyBase64", Base64.getEncoder().encodeToString(keyManager.keyPair.public.encoded))
                    put("lanHost", getLocalIpAddress())
                    put("port", port)
                    put("version", "1.0.0")
                }.toString()

                val bytes = statusJson.toByteArray(StandardCharsets.UTF_8)
                sendHttpResponse(socket, output, "200 OK", "application/json; charset=UTF-8", bytes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling client connection: ${e.message}")
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendHttpResponse(socket: Socket, output: OutputStream, status: String, contentType: String, body: ByteArray) {
        try {
            val headers = "HTTP/1.1 $status\r\n" +
                    "Content-Type: $contentType\r\n" +
                    "Content-Length: ${body.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: *\r\n" +
                    "Connection: close\r\n\r\n"
            output.write(headers.toByteArray(StandardCharsets.UTF_8))
            if (body.isNotEmpty()) {
                output.write(body)
            }
            output.flush()
        } catch (_: Exception) {}
        try {
            socket.shutdownOutput()
        } catch (_: Exception) {}
        try {
            socket.close()
        } catch (_: Exception) {}
    }

    private fun handleWebSocketFrames(socket: Socket, input: InputStream, output: OutputStream) {
        val dis = DataInputStream(input)
        try {
            while (isRunning && !socket.isClosed) {
                val b1 = dis.read()
                if (b1 == -1) break

                val opcode = b1 and 0x0F
                if (opcode == 8) { // Close
                    break
                }

                val b2 = dis.read()
                if (b2 == -1) break

                val isMasked = (b2 and 0x80) != 0
                var payloadLength = (b2 and 0x7F).toLong()

                if (payloadLength == 126L) {
                    payloadLength = dis.readUnsignedShort().toLong()
                } else if (payloadLength == 127L) {
                    payloadLength = dis.readLong()
                }

                val maskingKey = ByteArray(4)
                if (isMasked) {
                    dis.readFully(maskingKey)
                }

                val payload = ByteArray(payloadLength.toInt())
                dis.readFully(payload)

                if (isMasked) {
                    for (i in payload.indices) {
                        payload[i] = (payload[i].toInt() xor maskingKey[i % 4].toInt()).toByte()
                    }
                }

                if (opcode == 1 || opcode == 2) { // Text or Binary message
                    val messageText = String(payload, StandardCharsets.UTF_8)
                    val responseJson = processSyncExchangePayload(messageText)

                    sendWebSocketTextMessage(output, responseJson)
                }
            }
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendWebSocketTextMessage(output: OutputStream, text: String) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        output.write(0x81) // FIN + text opcode

        if (bytes.size <= 125) {
            output.write(bytes.size)
        } else if (bytes.size <= 65535) {
            output.write(126)
            output.write((bytes.size shr 8) and 0xFF)
            output.write(bytes.size and 0xFF)
        } else {
            output.write(127)
            val len = bytes.size.toLong()
            for (i in 7 downTo 0) {
                output.write(((len shr (i * 8)) and 0xFF).toInt())
            }
        }

        output.write(bytes)
        output.flush()
    }

    fun processSyncExchangePayload(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            val action = json.optString("action", "SYNC_EXCHANGE")
            val senderDeviceId = json.optString("deviceId", "unknown_desktop")
            val operationsArray = json.optJSONArray("operations")

            var appliedCount = 0
            if (operationsArray != null) {
                for (i in 0 until operationsArray.length()) {
                    val opObj = operationsArray.getJSONObject(i)
                    val opId = opObj.getString("opId")
                    val opTypeStr = opObj.getString("opType")
                    val entityTypeStr = opObj.getString("entityType")
                    val entityId = opObj.getString("entityId")
                    val hlcStr = opObj.getString("hlc")

                    val hlc = com.rebelroot.omni.sync.model.Hlc.parse(hlcStr)
                    val opType = com.rebelroot.omni.sync.model.SyncOpType.valueOf(opTypeStr)
                    val entityType = com.rebelroot.omni.sync.model.SyncEntityType.valueOf(entityTypeStr)

                    val bookmarkPayload = if (opObj.has("bookmarkPayload")) {
                        val bp = opObj.getJSONObject("bookmarkPayload")
                        com.rebelroot.omni.sync.model.BookmarkPayload(
                            parentId = bp.optString("parentId", "root"),
                            position = bp.optString("position", "a0"),
                            title = bp.optString("title", ""),
                            url = bp.optString("url", ""),
                            createdAt = bp.optLong("createdAt", System.currentTimeMillis()),
                            modifiedAt = bp.optLong("modifiedAt", System.currentTimeMillis()),
                            isDeleted = bp.optBoolean("isDeleted", false)
                        )
                    } else null

                    val folderPayload = if (opObj.has("folderPayload")) {
                        val fp = opObj.getJSONObject("folderPayload")
                        com.rebelroot.omni.sync.model.FolderPayload(
                            parentId = fp.optString("parentId", "root"),
                            position = fp.optString("position", "a0"),
                            title = fp.optString("title", ""),
                            createdAt = fp.optLong("createdAt", System.currentTimeMillis()),
                            modifiedAt = fp.optLong("modifiedAt", System.currentTimeMillis()),
                            isDeleted = fp.optBoolean("isDeleted", false)
                        )
                    } else null

                    val op = SyncOperation(
                        opId = opId,
                        opType = opType,
                        entityType = entityType,
                        entityId = entityId,
                        hlc = hlc,
                        bookmarkPayload = bookmarkPayload,
                        folderPayload = folderPayload,
                        isLocalOrigin = false
                    )

                    val result = conflictEngine.processIncomingOperation(collection, op)
                    if (result.applied) {
                        appliedCount++
                        syncBridge.recordBookmarkMutation(op)
                    }
                }
            }

            // Ingest incoming open tabs from desktop peer
            val tabsArray = json.optJSONArray("openTabs")
            if (tabsArray != null) {
                val tabList = mutableListOf<com.rebelroot.omni.sync.mozilla.TabInfo>()
                for (j in 0 until tabsArray.length()) {
                    val tObj = tabsArray.getJSONObject(j)
                    val url = tObj.optString("url", "")
                    if (url.isNotBlank() && url != "about:blank") {
                        tabList.add(
                            com.rebelroot.omni.sync.mozilla.TabInfo(
                                title = tObj.optString("title", url),
                                url = url,
                                iconUrl = if (tObj.has("favicon") && !tObj.isNull("favicon")) tObj.getString("favicon") else null,
                                lastAccessed = System.currentTimeMillis()
                            )
                        )
                    }
                }
                if (tabList.isNotEmpty()) {
                    val senderName = json.optString("deviceName", "Desktop Browser")
                    syncBridge.updateRemoteDeviceTabs(senderDeviceId, senderName, tabList)
                }
            }

            // Build local open tabs array to return to desktop peer
            val localTabsArray = JSONArray()
            val currentTabs = syncBridge.localTabs
            currentTabs.forEach { tab ->
                if (!tab.isIncognito && tab.url.isNotBlank() && tab.url != "about:blank") {
                    localTabsArray.put(JSONObject().apply {
                        put("title", tab.title.takeIf { it.isNotBlank() } ?: tab.url)
                        put("url", tab.url)
                    })
                }
            }

            // Fetch pending outbox operations to return to desktop peer
            val pendingOutbox = storage.pendingOutboxOperations()
            val outboxArray = JSONArray()
            pendingOutbox.forEach { op ->
                outboxArray.put(JSONObject().apply {
                    put("opId", op.opId)
                    put("opType", op.opType.name)
                    put("entityType", op.entityType.name)
                    put("entityId", op.entityId)
                    put("hlc", op.hlc.toString())
                    op.bookmarkPayload?.let { bp ->
                        put("bookmarkPayload", JSONObject().apply {
                            put("parentId", bp.parentId)
                            put("position", bp.position)
                            put("title", bp.title)
                            put("url", bp.url)
                            put("createdAt", bp.createdAt)
                            put("modifiedAt", bp.modifiedAt)
                        })
                    }
                    op.folderPayload?.let { fp ->
                        put("folderPayload", JSONObject().apply {
                            put("parentId", fp.parentId)
                            put("position", fp.position)
                            put("title", fp.title)
                            put("createdAt", fp.createdAt)
                            put("modifiedAt", fp.modifiedAt)
                        })
                    }
                })
            }

            val appCtx = com.rebelroot.omni.OmniApplication.appContext
            if (appCtx != null) {
                val senderName = json.optString("deviceName", "Desktop Browser")
                com.rebelroot.omni.sync.notification.SyncNotificationManager.notifySyncSuccess(
                    context = appCtx,
                    peerName = senderName,
                    bookmarkCount = appliedCount,
                    tabCount = tabsArray?.length() ?: 0
                )
            }

            JSONObject().apply {
                put("status", "success")
                put("appliedCount", appliedCount)
                put("remoteOperations", outboxArray)
                put("remoteTabs", localTabsArray)
                put("serverDeviceId", keyManager.deviceId)
                put("timestamp", System.currentTimeMillis())
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error in processSyncExchangePayload: ${e.message}", e)
            JSONObject().apply {
                put("status", "error")
                put("message", e.message ?: "Failed to process payload")
            }.toString()
        }
    }

    fun processPairingPayload(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            val remoteDeviceId = json.getString("deviceId")
            val remoteDeviceName = json.optString("deviceName", "Desktop Browser")
            val remotePubKey = if (json.has("publicKey")) json.getString("publicKey") else json.getString("publicKeyBase64")
            val nonceStr = json.optString("nonce", "")

            val myPubKey = keyManager.keyPair.public.encoded
            val peerPubKey = Base64.getDecoder().decode(remotePubKey)
            val nonce = if (nonceStr.isNotEmpty()) Base64.getDecoder().decode(nonceStr) else com.rebelroot.omni.sync.crypto.CryptoEngine.generateRandomNonce(16)

            val sas = com.rebelroot.omni.sync.crypto.CryptoEngine.deriveSasCode(myPubKey, peerPubKey, nonce)

            val trusted = com.rebelroot.omni.sync.crypto.TrustedDevice(
                deviceId = remoteDeviceId,
                deviceName = remoteDeviceName,
                publicKeyBase64 = remotePubKey,
                pairedAt = System.currentTimeMillis()
            )
            trustManager.addTrustedDevice(trusted)
            Log.i(TAG, "Successfully registered paired device: $remoteDeviceName ($remoteDeviceId)")

            val appCtx = com.rebelroot.omni.OmniApplication.appContext
            if (appCtx != null) {
                com.rebelroot.omni.sync.notification.SyncNotificationManager.notifyDevicePaired(
                    context = appCtx,
                    peerName = remoteDeviceName
                )
            }

            JSONObject().apply {
                put("status", "success")
                put("sasCode", sas)
                put("phoneDeviceId", keyManager.deviceId)
                put("phoneDeviceName", keyManager.deviceName)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Pairing error: ${e.message}", e)
            JSONObject().apply {
                put("status", "error")
                put("message", e.message ?: "Failed to process pairing")
            }.toString()
        }
    }

    private fun computeSecWebSocketAccept(key: String): String {
        val magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest((key.trim() + magic).toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        clientThreads.shutdownNow()
    }

    companion object {
        private var activeServerInstance: LanWebSocketServer? = null

        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue

                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            } catch (_: Exception) {}
            return "127.0.0.1"
        }
    }
}
