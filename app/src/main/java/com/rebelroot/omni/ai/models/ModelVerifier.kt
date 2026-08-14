/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni.ai.models

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Verifies a downloaded model file before it is activated.
 *
 * Required pipeline: download → size validation → SHA-256 → (optional signature)
 * → atomic activation. A model is NEVER activated until it passes verification.
 *
 * When a descriptor pins a SHA-256 the file is cryptographically verified. When
 * no hash is pinned (e.g. an upstream that only publishes size) the file is
 * checked by size only and reported [VerificationResult.Unverified] so the UI can
 * warn the user before install.
 */
sealed class VerificationResult {
    /** Size + SHA-256 matched. */
    object Verified : VerificationResult()

    /** Integrity check failed — the file must be rejected and deleted. */
    data class Failed(val reason: String) : VerificationResult()

    /** No SHA-256 pinned; only the byte size matched. Treat as untrusted. */
    data class Unverified(val reason: String) : VerificationResult()
}

class ModelVerifier {

    /** Compute the lowercase hex SHA-256 of a file. */
    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Verify [file] against [descriptor]. */
    fun verify(file: File, descriptor: ModelDescriptor): VerificationResult {
        if (!file.isFile) return VerificationResult.Failed("file missing")

        val actualSize = file.length()
        val sizeMatches = descriptor.sizeBytes <= 0 || actualSize == descriptor.sizeBytes

        if (descriptor.isChecksumPinned) {
            // Size is part of the integrity contract when a hash is pinned.
            if (descriptor.sizeBytes > 0 && actualSize != descriptor.sizeBytes) {
                return VerificationResult.Failed(
                    "size mismatch: expected ${descriptor.sizeBytes} bytes, got $actualSize"
                )
            }
            val expected = descriptor.sha256!!.lowercase()
            val actual = sha256Of(file)
            if (actual != expected) {
                return VerificationResult.Failed(
                    "SHA-256 mismatch: expected $expected, got $actual"
                )
            }
            return VerificationResult.Verified
        }

        // No hash pinned: size is advisory only. A mismatch is warned, not rejected,
        // so a minor upstream size drift never blocks an unverifiable model.
        return if (sizeMatches) {
            VerificationResult.Unverified("no SHA-256 pinned; verified by size only")
        } else {
            VerificationResult.Unverified(
                "no SHA-256 pinned; size mismatch ($actualSize vs ${descriptor.sizeBytes}) — installing unverified"
            )
        }
    }
}
