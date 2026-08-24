package com.rebelroot.omni.sync.transport.lan

import com.rebelroot.omni.sync.crypto.EncryptedEnvelope
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

enum class LanFrameType {
    HANDSHAKE_INIT,
    HANDSHAKE_RESP,
    ENCRYPTED_SYNC_MSG,
    HEARTBEAT_PING,
    HEARTBEAT_PONG,
    DISCONNECT
}

data class LanFrame(
    val frameType: LanFrameType,
    val payloadJson: String
) {
    companion object {
        const val MAX_FRAME_BYTES = 10 * 1024 * 1024 // 10 MB

        fun writeToStream(frame: LanFrame, output: OutputStream) {
            val dataOut = DataOutputStream(output)
            val bytes = frame.payloadJson.toByteArray(Charsets.UTF_8)
            val typeByte = frame.frameType.ordinal.toByte()

            dataOut.writeInt(bytes.size + 1)
            dataOut.writeByte(typeByte.toInt())
            dataOut.write(bytes)
            dataOut.flush()
        }

        fun readFromStream(input: InputStream): LanFrame {
            val dataIn = DataInputStream(input)
            val length = dataIn.readInt()
            require(length in 1..MAX_FRAME_BYTES) { "Invalid frame length: $length" }

            val typeOrdinal = dataIn.readByte().toInt()
            val frameType = LanFrameType.values().getOrElse(typeOrdinal) { LanFrameType.DISCONNECT }

            val payloadBytes = ByteArray(length - 1)
            dataIn.readFully(payloadBytes)
            val payloadJson = String(payloadBytes, Charsets.UTF_8)

            return LanFrame(frameType, payloadJson)
        }
    }
}
