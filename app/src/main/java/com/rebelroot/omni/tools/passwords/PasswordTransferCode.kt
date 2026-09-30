/*
 * Omni Browser - Password LAN transfer code
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * Out-of-band code that authorises password import/export over the LAN.
 *
 * Deliberately NOT handed out during pairing: `/api/sync/pair` is
 * unauthenticated (any device on the network can call it to pair), so a secret
 * issued there could be obtained by any LAN peer and would protect nothing.
 * Instead the user reads this code on the phone and types it into the desktop
 * extension, which proves physical access to the device holding the vault.
 */

package com.rebelroot.omni.tools.passwords

import android.content.Context
import java.io.File
import java.security.SecureRandom

object PasswordTransferCode {
    private const val FILE_NAME = "omni_sync_password_code.txt"

    /** Returns the current code, generating one on first use. Blank on failure. */
    fun getOrCreate(context: Context): String {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                file.readText().trim().ifBlank { writeNew(file) }
            } else {
                writeNew(file)
            }
        } catch (e: Exception) {
            ""
        }
    }

    /** Replaces the code (invalidates any previously entered code). */
    fun rotate(context: Context): String {
        return try {
            writeNew(File(context.filesDir, FILE_NAME))
        } catch (e: Exception) {
            ""
        }
    }

    private fun writeNew(file: File): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        // 32 hex chars — unambiguous and easy to read off a phone and type.
        val code = bytes.joinToString("") { "%02X".format(it) }
        file.writeText(code)
        return code
    }
}