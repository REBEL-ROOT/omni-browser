package com.rebelroot.omni.sync.chat

import org.json.JSONObject
import java.util.UUID

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    TAB_LINK
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderDeviceId: String,
    val senderName: String,
    val isFromMe: Boolean,
    val type: MessageType,
    val text: String = "",
    val fileName: String? = null,
    val fileMimeType: String? = null,
    val fileSizeBytes: Long = 0L,
    val fileUriOrBase64: String? = null,
    val tabUrl: String? = null,
    val tabTitle: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.DELIVERED
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("senderDeviceId", senderDeviceId)
            put("senderName", senderName)
            put("type", type.name)
            put("text", text)
            if (fileName != null) put("fileName", fileName)
            if (fileMimeType != null) put("fileMimeType", fileMimeType)
            if (fileSizeBytes > 0) put("fileSizeBytes", fileSizeBytes)
            if (fileUriOrBase64 != null) put("fileData", fileUriOrBase64)
            if (tabUrl != null) put("tabUrl", tabUrl)
            if (tabTitle != null) put("tabTitle", tabTitle)
            put("timestamp", timestamp)
        }
    }

    companion object {
        fun fromJson(json: JSONObject, localDeviceId: String): ChatMessage {
            val senderId = json.optString("senderDeviceId", "remote_device")
            val typeStr = json.optString("type", "TEXT")
            val type = try { MessageType.valueOf(typeStr) } catch (_: Exception) { MessageType.TEXT }

            return ChatMessage(
                id = json.optString("id", UUID.randomUUID().toString()),
                senderDeviceId = senderId,
                senderName = json.optString("senderName", "Device"),
                isFromMe = senderId == localDeviceId,
                type = type,
                text = json.optString("text", ""),
                fileName = json.optString("fileName", "").takeIf { it.isNotBlank() },
                fileMimeType = json.optString("fileMimeType", "").takeIf { it.isNotBlank() },
                fileSizeBytes = json.optLong("fileSizeBytes", 0L),
                fileUriOrBase64 = json.optString("fileData", "").takeIf { it.isNotBlank() },
                tabUrl = json.optString("tabUrl", "").takeIf { it.isNotBlank() },
                tabTitle = json.optString("tabTitle", "").takeIf { it.isNotBlank() },
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                status = MessageStatus.DELIVERED
            )
        }
    }
}
