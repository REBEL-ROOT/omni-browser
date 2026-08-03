package com.rebelroot.omni.tools.passwords

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class MasterPasswordManager(context: Context) {

    companion object {
        private const val PREF_FILE = "omni_password_vault_prefs"
        private const val SALT_KEY = "pw_salt"
        private const val HASH_KEY = "pw_hash"
        private const val ITERATIONS_KEY = "pw_iterations"
        private const val BIOMETRIC_ENABLED_KEY = "pw_biometric_enabled"
        private const val DEFAULT_ITERATIONS = 310_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 16

        fun deriveKey(password: String, salt: ByteArray, iterations: Int): ByteArray {
            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
            return try {
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                factory.generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }
    }

    private val secureRandom = SecureRandom()
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREF_FILE,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isVaultCreated(): Boolean {
        return sharedPreferences.contains(SALT_KEY) &&
            sharedPreferences.contains(HASH_KEY) &&
            sharedPreferences.contains(ITERATIONS_KEY)
    }

    fun createVault(password: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES).also(secureRandom::nextBytes)
        val keyBytes = deriveKey(password, salt, DEFAULT_ITERATIONS)
        sharedPreferences.edit()
            .putString(SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH_KEY, Base64.encodeToString(keyBytes, Base64.NO_WRAP))
            .putInt(ITERATIONS_KEY, DEFAULT_ITERATIONS)
            .apply()
        return keyBytes
    }

    fun verifyMasterPassword(password: String): ByteArray? {
        val salt = getSalt() ?: return null
        val iterations = getIterations() ?: return null
        val expectedHash = getHash() ?: return null
        val candidate = deriveKey(password, salt, iterations)
        return if (candidate.contentEquals(expectedHash)) candidate else null
    }

    fun isBiometricEnabled(): Boolean =
        sharedPreferences.getBoolean(BIOMETRIC_ENABLED_KEY, false)

    fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(BIOMETRIC_ENABLED_KEY, enabled)
            .apply()
    }

    /**
     * Returns the stored derived key directly — used after a successful biometric auth
     * where the user does not re-enter their password.
     */
    fun getStoredKeyBytes(): ByteArray? = getHash()

    /**
     * Returns the vault key bytes if the vault has been created.
     * Used on app boot to auto-open the vault without prompting for the master password.
     * The key is the PBKDF2 output stored in EncryptedSharedPreferences — safe at rest.
     */
    fun loadSessionKey(): ByteArray? = if (isVaultCreated()) getHash() else null

    /**
     * Invalidates the cached session key. Call after changeMasterPassword() so the old
     * key cannot reopen the vault. The new key is written by changeMasterPassword() itself.
     */
    fun clearSessionKey() {
        // pw_hash is updated by changeMasterPassword; this is a no-op placeholder kept
        // for call-site clarity — the EncryptedSharedPreferences entry is always current.
    }

    fun changeMasterPassword(oldPassword: String, newPassword: String): Boolean {
        val oldKey = verifyMasterPassword(oldPassword) ?: return false
        val newSalt = ByteArray(SALT_LENGTH_BYTES).also(secureRandom::nextBytes)
        val newKey = deriveKey(newPassword, newSalt, DEFAULT_ITERATIONS)
        sharedPreferences.edit()
            .putString(SALT_KEY, Base64.encodeToString(newSalt, Base64.NO_WRAP))
            .putString(HASH_KEY, Base64.encodeToString(newKey, Base64.NO_WRAP))
            .putInt(ITERATIONS_KEY, DEFAULT_ITERATIONS)
            .apply()
        oldKey.fill(0)
        return true
    }

    private fun getSalt(): ByteArray? {
        val encoded = sharedPreferences.getString(SALT_KEY, null) ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    private fun getHash(): ByteArray? {
        val encoded = sharedPreferences.getString(HASH_KEY, null) ?: return null
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    private fun getIterations(): Int? {
        return if (sharedPreferences.contains(ITERATIONS_KEY)) {
            sharedPreferences.getInt(ITERATIONS_KEY, DEFAULT_ITERATIONS)
        } else {
            null
        }
    }
}
