package com.rebelroot.omni.sync.chat

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.rebelroot.omni.sync.notification.SyncNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class ChatRepository private constructor(
    private val localDeviceId: String,
    private val localDeviceName: String
) {
    private val TAG = "ChatRepository"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var activePeerHost: String? = null
    private var activePeerPort: Int = 8765

    fun setPeerEndpoint(host: String?, port: Int = 8765) {
        activePeerHost = host
        activePeerPort = port
    }

    fun onIncomingMessage(context: Context?, jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val msg = ChatMessage.fromJson(json, localDeviceId)

            // If it's a file payload with base64 data, save it to OmniDrop folder
            val processedMsg = if (msg.fileUriOrBase64 != null && msg.fileName != null && msg.type != MessageType.TEXT && msg.type != MessageType.TAB_LINK) {
                val savedFile = saveIncomingBase64File(context, msg.fileName, msg.fileUriOrBase64)
                if (savedFile != null) {
                    msg.copy(fileUriOrBase64 = savedFile.absolutePath, fileSizeBytes = savedFile.length())
                } else msg
            } else {
                msg
            }

            synchronized(_messages) {
                if (_messages.value.none { it.id == processedMsg.id }) {
                    _messages.value = _messages.value + processedMsg
                }
            }

            if (context != null) {
                val preview = when (processedMsg.type) {
                    MessageType.TEXT -> processedMsg.text
                    MessageType.IMAGE -> "📷 Photo: ${processedMsg.fileName}"
                    MessageType.VIDEO -> "🎥 Video: ${processedMsg.fileName}"
                    MessageType.DOCUMENT -> "📄 File: ${processedMsg.fileName}"
                    MessageType.AUDIO -> "🎵 Audio Note"
                    MessageType.TAB_LINK -> "🔗 ${processedMsg.tabTitle ?: processedMsg.tabUrl}"
                }
                SyncNotificationManager.notifySyncSuccess(
                    context = context,
                    peerName = processedMsg.senderName,
                    bookmarkCount = 0,
                    tabCount = 1
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming chat message: ${e.message}", e)
        }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        val msg = ChatMessage(
            senderDeviceId = localDeviceId,
            senderName = localDeviceName,
            isFromMe = true,
            type = MessageType.TEXT,
            text = text.trim()
        )
        addAndDispatch(msg)
    }

    fun sendTabLink(title: String, url: String) {
        val msg = ChatMessage(
            senderDeviceId = localDeviceId,
            senderName = localDeviceName,
            isFromMe = true,
            type = MessageType.TAB_LINK,
            text = url,
            tabTitle = title,
            tabUrl = url
        )
        addAndDispatch(msg)
    }

    fun sendFileMessage(context: Context, uri: Uri, fileName: String, mimeType: String) {
        scope.launch {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val type = when {
                    mimeType.startsWith("image/") -> MessageType.IMAGE
                    mimeType.startsWith("video/") -> MessageType.VIDEO
                    mimeType.startsWith("audio/") -> MessageType.AUDIO
                    else -> MessageType.DOCUMENT
                }

                val msg = ChatMessage(
                    senderDeviceId = localDeviceId,
                    senderName = localDeviceName,
                    isFromMe = true,
                    type = type,
                    text = fileName,
                    fileName = fileName,
                    fileMimeType = mimeType,
                    fileSizeBytes = bytes.size.toLong(),
                    fileUriOrBase64 = base64
                )
                addAndDispatch(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending file message: ${e.message}", e)
            }
        }
    }

    private fun addAndDispatch(msg: ChatMessage) {
        synchronized(_messages) {
            _messages.value = _messages.value + msg
        }

        scope.launch {
            dispatchToPeer(msg)
        }
    }

    private fun dispatchToPeer(msg: ChatMessage) {
        val host = activePeerHost ?: "127.0.0.1"
        val port = activePeerPort
        try {
            val url = URL("http://$host:$port/api/chat/send")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }

            val payload = msg.toJson().toString().toByteArray(StandardCharsets.UTF_8)
            conn.outputStream.use { it.write(payload) }

            val code = conn.responseCode
            if (code == 200) {
                Log.i(TAG, "Chat message dispatched successfully: ${msg.id}")
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dispatch chat message to $host:$port: ${e.message}")
        }
    }

    private fun saveIncomingBase64File(context: Context?, fileName: String, base64Data: String): File? {
        return try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val dropDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OmniDrop").apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(dropDir, fileName)
            FileOutputStream(targetFile).use { it.write(bytes) }
            Log.i(TAG, "Saved incoming file to: ${targetFile.absolutePath}")
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save incoming file: ${e.message}", e)
            null
        }
    }

    companion object {
        @Volatile
        private var instance: ChatRepository? = null

        fun getInstance(deviceId: String = "omni_phone", deviceName: String = "Omni Android"): ChatRepository {
            return instance ?: synchronized(this) {
                instance ?: ChatRepository(deviceId, deviceName).also { instance = it }
            }
        }
    }
}
