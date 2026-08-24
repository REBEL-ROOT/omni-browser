package com.rebelroot.omni.sync

import com.rebelroot.omni.bookmarks.model.BookmarkCollection
import com.rebelroot.omni.sync.adapter.ApplyResult
import com.rebelroot.omni.sync.adapter.BookmarkAdapter
import com.rebelroot.omni.sync.crypto.CryptoEngine
import com.rebelroot.omni.sync.crypto.EncryptedEnvelope
import com.rebelroot.omni.sync.model.*
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class SecurityAuditTest {

    @Test
    fun adversarial_tamperedCiphertext_rejected() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        val aliceKeys = kpg.generateKeyPair()
        val bobKeys = kpg.generateKeyPair()

        val secret = CryptoEngine.deriveSharedSecret(aliceKeys.private, bobKeys.public)
        val plaintext = "Sensitive Bookmark Title".toByteArray(Charsets.UTF_8)
        val envelope = CryptoEngine.encryptPayload(plaintext, secret, sequenceNumber = 1L, senderDeviceId = "dev_alice")

        val tamperedCiphertext = "AAAA" + envelope.ciphertextBase64.substring(4)
        val tamperedEnvelope = envelope.copy(ciphertextBase64 = tamperedCiphertext)

        try {
            CryptoEngine.decryptPayload(tamperedEnvelope, secret)
            fail("Decryption of tampered ciphertext must throw AEADBadTagException")
        } catch (_: Exception) {
            // Expected
        }
    }

    @Test
    fun adversarial_maliciousUri_rejected() {
        val clock = HlcClock("dev_alice")
        val adapter = BookmarkAdapter(clock)
        val collection = BookmarkCollection()

        val maliciousOp = SyncOperation(
            opType = SyncOpType.CREATE,
            entityType = SyncEntityType.BOOKMARK,
            entityId = "bmk_xss_01",
            hlc = clock.now(),
            bookmarkPayload = BookmarkPayload(
                title = "Malicious Bookmark",
                url = "javascript:alert(document.cookie)"
            )
        )

        val result = adapter.applyRemoteOperation(collection, maliciousOp)
        assertTrue("Javascript URI must result in Rejected", result is ApplyResult.Rejected)
        val rejected = result as ApplyResult.Rejected
        assertTrue(rejected.reason.contains("Security violation"))
        assertEquals(0, collection.allBookmarks().size)
    }
}
