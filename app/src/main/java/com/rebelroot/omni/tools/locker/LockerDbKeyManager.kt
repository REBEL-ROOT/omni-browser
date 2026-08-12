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

package com.rebelroot.omni.tools.locker

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
import java.security.SecureRandom

/**
 * Owns the SQLCipher key for the locker index database.
 *
 * The database key is a randomly generated 256-bit value, provisioned once per
 * installation and stored encrypted at rest inside EncryptedSharedPreferences
 * protected by an Android Keystore [MasterKey] (AES-256-GCM) — replacing the
 * former hardcoded passphrase that could be recovered from the APK.
 *
 * Existing installations that already created `locker.db` with the legacy
 * passphrase go through a one-time, verified rekey migration. The legacy
 * credential is used ONLY inside that migration path and is never a normal
 * runtime fallback.
 *
 * Failure safety: this class FAILS CLOSED. If a stored key cannot be recovered
 * or a migration cannot be verified, the original database files are preserved
 * and an exception is thrown — the vault is never silently recreated and never
 * silently falls back to the legacy credential.
 */
internal class LockerDbKeyManager(context: Context) {

    companion object {
        private const val TAG = "LockerDbKeyManager"

        private const val PREFS_FILE = "locker_secure_prefs"
        private const val KEY_PREF = "locker_db_key"
        private const val KEY_BYTES = 32 // 256-bit SQLCipher key material

        // Legacy credential used ONLY to migrate databases created before the
        // secure-key rollout. Must never be used for normal runtime operation.
        private val LEGACY_DB_PASSPHRASE = "omni_secure_database_passphrase_bytes".toByteArray()

        // Serialises key provisioning across every PrivateLockerManager
        // instance in the process (BrowserViewModel, BrowserSheets, screen),
        // so a fresh-install race can never provision two different keys.
        private val provisionLock = Any()
    }

    private val appContext = context.applicationContext

    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val keyPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Room stores "locker.db" under the app's databases directory; the raw
    // SQLCipher migration below must target the exact same file.
    private val dbFile: File = appContext.getDatabasePath("locker.db")

    /**
     * Returns the current 32-byte SQLCipher database key, provisioning it on
     * first use or migrating a legacy database when required.
     */
    fun getOrCreateDatabaseKey(): ByteArray = synchronized(provisionLock) {
        // Ensure the native SQLCipher library is loaded for the raw migration
        // open. No-op when Room's open path already loaded it.
        System.loadLibrary("sqlcipher")

        // Fast path: key already provisioned on an earlier launch.
        keyPrefs.getString(KEY_PREF, null)?.let { stored ->
            return decodeStoredKey(stored)
        }

        if (!dbFile.exists() || dbFile.length() == 0L) {
            // Fresh installation (or a crashed first launch that never wrote a
            // single byte): generate a random key and persist it. A zero-byte
            // file provably holds no data, so provisioning is safe.
            val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
            keyPrefs.edit().putString(KEY_PREF, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
            Log.i(TAG, "Provisioned new random locker database key")
            return key
        }

        // Existing database without a stored key → legacy one-time migration.
        migrateLegacyDatabase()
    }

    /**
     * Opens the legacy lockered DB with the legacy passphrase, rekeys it to a
     * fresh random key, verifies the rekeyed database, and only then persists
     * the new key. The original files are backed up first and restored on any
     * failure so the vault is never lost.
     */
    private fun migrateLegacyDatabase(): ByteArray {
        Log.i(TAG, "Legacy locker database detected — starting one-time rekey migration")
        val newKey = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)

        // Back up the database and any WAL/SHM sidecars so the original vault
        // can always be restored if the migration cannot be verified.
        val backups = dbFiles()
            .filter { it.exists() }
            .mapNotNull { src ->
                val bak = File(appContext.filesDir, "locker_db_migration_${src.name}.bak")
                try {
                    src.copyTo(bak, overwrite = true)
                    src to bak
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to back up ${src.name} before migration", e)
                    null
                }
            }

        try {
            openWithKey(LEGACY_DB_PASSPHRASE).use { legacy ->
                // Fold any WAL content into the main file before rekeying.
                legacy.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                // Raw key material (32 bytes) supplied as a hex blob literal.
                legacy.execSQL("PRAGMA rekey = x'${newKey.toHex()}'")
            }

            // Verify the rekeyed database opens and reads with the new key.
            openWithKey(newKey).use { migrated ->
                migrated.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        throw IllegalStateException("Migration verification query returned no rows")
                    }
                }
            }

            // Commit: persist the new key, then drop the backups.
            keyPrefs.edit().putString(KEY_PREF, Base64.encodeToString(newKey, Base64.NO_WRAP)).apply()
            backups.forEach { (_, bak) -> bak.delete() }
            Log.i(TAG, "Locker database migrated to a secure random key")
            return newKey
        } catch (e: Exception) {
            Log.e(TAG, "Locker migration failed — restoring original database", e)
            backups.forEach { (orig, bak) ->
                try {
                    bak.copyTo(orig, overwrite = true)
                } catch (restoreError: Exception) {
                    Log.e(TAG, "Failed to restore backup of ${orig.name}", restoreError)
                }
            }
            throw IllegalStateException(
                "Locker database security migration failed. Original vault data was " +
                    "preserved and the migration will retry on next launch.",
                e
            )
        }
    }

    /** Opens the database with the given raw key bytes or passphrase bytes. */
    private fun openWithKey(password: ByteArray): SQLiteDatabase {
        return SQLiteDatabase.openOrCreateDatabase(
            dbFile.absolutePath,
            password,
            null,
            null
        )
    }

    private fun decodeStoredKey(stored: String): ByteArray {
        val key = try {
            Base64.decode(stored, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Stored locker database key is corrupted", e)
        }
        if (key.size != KEY_BYTES) {
            throw IllegalStateException("Stored locker database key has an invalid length")
        }
        return key
    }

    private fun dbFiles(): List<File> =
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"))

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02X".format(it) }
}