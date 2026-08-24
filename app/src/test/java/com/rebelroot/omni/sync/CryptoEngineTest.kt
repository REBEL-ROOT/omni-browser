package com.rebelroot.omni.sync

import com.rebelroot.omni.sync.crypto.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64
import javax.crypto.AEADBadTagException

class CryptoEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun ecdhKeyAgreement_and_aesGcmEncryption_roundtrip() {
        val aliceKeys = CryptoEngine.generateKeyPair()
        val bobKeys = CryptoEngine.generateKeyPair()

        val aliceSecret = CryptoEngine.deriveSharedSecret(aliceKeys.private, bobKeys.public)
        val bobSecret = CryptoEngine.deriveSharedSecret(bobKeys.private, aliceKeys.public)

        assertEquals("Shared secrets derived from both ends must be identical", aliceSecret, bobSecret)

        val plaintext = "Sensitive Bookmark: https://secret-vault.org".toByteArray(Charsets.UTF_8)
        val envelope = CryptoEngine.encryptPayload(plaintext, aliceSecret, sequenceNumber = 1L, senderDeviceId = "dev_alice")

        assertNotNull(envelope.ivBase64)
        assertNotNull(envelope.ciphertextBase64)

        val decrypted = CryptoEngine.decryptPayload(envelope, bobSecret)
        assertEquals("Decrypted plaintext must match original", String(plaintext), String(decrypted))
    }

    @Test(expected = AEADBadTagException::class)
    fun aesGcm_rejectsTamperedCiphertext() {
        val aliceKeys = CryptoEngine.generateKeyPair()
        val bobKeys = CryptoEngine.generateKeyPair()
        val secret = CryptoEngine.deriveSharedSecret(aliceKeys.private, bobKeys.public)

        val plaintext = "Hello World".toByteArray(Charsets.UTF_8)
        val envelope = CryptoEngine.encryptPayload(plaintext, secret, sequenceNumber = 1L, senderDeviceId = "dev_alice")

        val rawCiphertext = Base64.getDecoder().decode(envelope.ciphertextBase64)
        rawCiphertext[0] = (rawCiphertext[0].toInt() xor 0xFF).toByte()
        val tamperedEnvelope = envelope.copy(ciphertextBase64 = Base64.getEncoder().encodeToString(rawCiphertext))

        CryptoEngine.decryptPayload(tamperedEnvelope, secret)
    }

    @Test
    fun sasDerivation_matchesOnBothSides() {
        val aliceKeys = CryptoEngine.generateKeyPair()
        val bobKeys = CryptoEngine.generateKeyPair()
        val nonce = CryptoEngine.generateRandomNonce(16)

        val sasAlice = CryptoEngine.deriveSasCode(aliceKeys.public.encoded, bobKeys.public.encoded, nonce)
        val sasBob = CryptoEngine.deriveSasCode(aliceKeys.public.encoded, bobKeys.public.encoded, nonce)

        assertEquals("SAS code must be 6 digits and identical on both devices", sasAlice, sasBob)
        assertEquals(6, sasAlice.length)
    }

    @Test
    fun pairingEngine_createsAndAcceptsValidInvitation() {
        val aliceDir = tempFolder.newFolder("alice")
        val bobDir = tempFolder.newFolder("bob")

        val aliceKeys = DeviceKeyManager(aliceDir)
        val bobKeys = DeviceKeyManager(bobDir)

        val aliceTrust = TrustManager(aliceDir)
        val bobTrust = TrustManager(bobDir)

        val alicePairing = PairingEngine(aliceKeys, aliceTrust)
        val bobPairing = PairingEngine(bobKeys, bobTrust)

        val inv = alicePairing.createInvitation()
        val res = bobPairing.processIncomingInvitation(inv.toJson())

        assertTrue("Pairing must succeed", res is PairingResult.Success)
        val success = res as PairingResult.Success
        assertEquals(aliceKeys.deviceId, success.trustedDevice.deviceId)
        assertTrue(bobTrust.isDeviceTrusted(aliceKeys.deviceId))
    }

    @Test
    fun pairingEngine_rejectsExpiredInvitation() {
        val aliceDir = tempFolder.newFolder("alice_exp")
        val bobDir = tempFolder.newFolder("bob_exp")

        val aliceKeys = DeviceKeyManager(aliceDir)
        val bobKeys = DeviceKeyManager(bobDir)
        val bobTrust = TrustManager(bobDir)
        val bobPairing = PairingEngine(bobKeys, bobTrust)

        val inv = PairingInvitation(
            deviceId = aliceKeys.deviceId,
            deviceName = aliceKeys.deviceName,
            publicKeyBase64 = aliceKeys.publicKeyBase64,
            nonceBase64 = Base64.getEncoder().encodeToString(CryptoEngine.generateRandomNonce(16)),
            timestamp = 1000L
        )

        val res = bobPairing.processIncomingInvitation(inv.toJson(), now = 1000L + 10 * 60 * 1000L)
        assertTrue("Expired invitation must be rejected", res is PairingResult.Failed)
    }

    @Test
    fun trustManager_revocationBlocksDevice() {
        val dir = tempFolder.newFolder("trust_test")
        val trustManager = TrustManager(dir)

        val dev = TrustedDevice(
            deviceId = "dev_bad_01",
            deviceName = "Compromised Device",
            publicKeyBase64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."
        )
        trustManager.addTrustedDevice(dev)
        assertTrue(trustManager.isDeviceTrusted("dev_bad_01"))

        trustManager.revokeDevice("dev_bad_01")
        assertFalse("Revoked device must not be trusted", trustManager.isDeviceTrusted("dev_bad_01"))
    }
}
