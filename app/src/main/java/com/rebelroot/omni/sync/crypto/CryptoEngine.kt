package com.rebelroot.omni.sync.crypto

import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedEnvelope(
    val senderDeviceId: String,
    val sequenceNumber: Long,
    val ivBase64: String,
    val ciphertextBase64: String,
    val timestamp: Long = System.currentTimeMillis()
)

object CryptoEngine {

    private const val EC_CURVE = "secp256r1"
    private const val KEY_AGREEMENT_ALGO = "ECDH"
    private const val CIPHER_ALGO = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val HKDF_ALGO = "HmacSHA256"

    private val secureRandom = SecureRandom()

    fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec(EC_CURVE), secureRandom)
        return keyGen.generateKeyPair()
    }

    fun parsePublicKey(encodedBytes: ByteArray): PublicKey {
        val spec = X509EncodedKeySpec(encodedBytes)
        val factory = KeyFactory.getInstance("EC")
        return factory.generatePublic(spec)
    }

    fun parsePublicKeyBase64(base64Str: String): PublicKey {
        val bytes = Base64.getDecoder().decode(base64Str)
        return parsePublicKey(bytes)
    }

    fun deriveSharedSecret(privateKey: PrivateKey, peerPublicKey: PublicKey): SecretKey {
        val keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGO)
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val rawSecret = keyAgreement.generateSecret()

        val salt = "omni-sync-v1-salt".toByteArray(Charsets.UTF_8)
        val info = "omni-sync-aes-gcm-key".toByteArray(Charsets.UTF_8)
        val aesKeyBytes = hkdf(rawSecret, salt, info, 32)
        return SecretKeySpec(aesKeyBytes, "AES")
    }

    fun encryptPayload(
        payloadBytes: ByteArray,
        secretKey: SecretKey,
        sequenceNumber: Long,
        senderDeviceId: String
    ): EncryptedEnvelope {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(CIPHER_ALGO)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val aad = (senderDeviceId + ":" + sequenceNumber).toByteArray(Charsets.UTF_8)
        cipher.updateAAD(aad)

        val ciphertext = cipher.doFinal(payloadBytes)

        return EncryptedEnvelope(
            senderDeviceId = senderDeviceId,
            sequenceNumber = sequenceNumber,
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext)
        )
    }

    fun decryptPayload(
        envelope: EncryptedEnvelope,
        secretKey: SecretKey
    ): ByteArray {
        val iv = Base64.getDecoder().decode(envelope.ivBase64)
        val ciphertext = Base64.getDecoder().decode(envelope.ciphertextBase64)

        val cipher = Cipher.getInstance(CIPHER_ALGO)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val aad = (envelope.senderDeviceId + ":" + envelope.sequenceNumber).toByteArray(Charsets.UTF_8)
        cipher.updateAAD(aad)

        return cipher.doFinal(ciphertext)
    }

    fun deriveSasCode(pubKeyA: ByteArray, pubKeyB: ByteArray, nonce: ByteArray): String {
        // Sort keys lexicographically so initiator & responder produce identical SAS
        val cmp = compareByteArrays(pubKeyA, pubKeyB)
        val first = if (cmp <= 0) pubKeyA else pubKeyB
        val second = if (cmp <= 0) pubKeyB else pubKeyA

        val mac = Mac.getInstance(HKDF_ALGO)
        mac.init(SecretKeySpec(nonce, HKDF_ALGO))
        mac.update(first)
        mac.update(second)
        val hash = mac.doFinal()

        val num = ((hash[0].toInt() and 0x7F) shl 24) or
                ((hash[1].toInt() and 0xFF) shl 16) or
                ((hash[2].toInt() and 0xFF) shl 8) or
                (hash[3].toInt() and 0xFF)
        val code = Math.abs(num) % 1_000_000
        return String.format("%06d", code)
    }

    private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
        val minLen = Math.min(a.size, b.size)
        for (i in 0 until minLen) {
            val byteA = a[i].toInt() and 0xFF
            val byteB = b[i].toInt() and 0xFF
            if (byteA != byteB) return byteA - byteB
        }
        return a.size - b.size
    }

    fun generateRandomNonce(length: Int = 16): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(HKDF_ALGO)
        mac.init(SecretKeySpec(salt, HKDF_ALGO))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, HKDF_ALGO))
        mac.update(info)
        mac.update(1.toByte())
        val okm = mac.doFinal()
        return okm.copyOf(length)
    }
}
