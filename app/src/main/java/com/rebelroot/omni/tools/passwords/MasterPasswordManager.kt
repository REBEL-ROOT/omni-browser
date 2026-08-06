package com.rebelroot.omni.tools.passwords

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class MasterPasswordManager(context: Context) {

    companion object {
        private const val PREF_FILE          = "omni_password_vault_prefs"
        private const val SALT_KEY           = "pw_salt"
        private const val HASH_KEY           = "pw_hash"
        private const val ITERATIONS_KEY     = "pw_iterations"
        private const val BIOMETRIC_ENABLED_KEY = "pw_biometric_enabled"
        // Biometric-wrapped vault key: IV + ciphertext stored as Base64
        private const val BIO_WRAPPED_KEY    = "pw_bio_wrapped_key"
        private const val BIO_IV_KEY         = "pw_bio_iv"

        private const val KEYSTORE_ALIAS     = "omni_vault_bio_key"
        private const val ANDROID_KEYSTORE   = "AndroidKeyStore"
        private const val AES_GCM_NO_PAD     = "AES/GCM/NoPadding"
        private const val GCM_TAG_LEN        = 128

        private const val DEFAULT_ITERATIONS  = 310_000
        private const val KEY_LENGTH_BITS     = 256
        private const val SALT_LENGTH_BYTES   = 16

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

    // ── Vault lifecycle ──────────────────────────────────────────────────────

    fun isVaultCreated(): Boolean =
        sharedPreferences.contains(SALT_KEY) &&
        sharedPreferences.contains(HASH_KEY) &&
        sharedPreferences.contains(ITERATIONS_KEY)

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
        val salt       = getSalt()       ?: return null
        val iterations = getIterations() ?: return null
        val expected   = getHash()       ?: return null
        val candidate  = deriveKey(password, salt, iterations)
        return if (candidate.contentEquals(expected)) candidate else null
    }

    // ── Biometric preference ─────────────────────────────────────────────────

    fun isBiometricEnabled(): Boolean =
        sharedPreferences.getBoolean(BIOMETRIC_ENABLED_KEY, false)

    fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(BIOMETRIC_ENABLED_KEY, enabled)
            .apply()
        if (!enabled) {
            // Remove wrapped key material when biometric is disabled
            sharedPreferences.edit()
                .remove(BIO_WRAPPED_KEY)
                .remove(BIO_IV_KEY)
                .apply()
            deleteKeystoreKey()
        }
    }

    // ── Keystore key management ──────────────────────────────────────────────

    /**
     * Creates (or replaces) an AES-256-GCM key in the Android Keystore that
     * requires biometric authentication for every decrypt operation.
     * Call once after the user opts in to fingerprint unlock.
     */
    fun createOrReplaceKeystoreKey() {
        deleteKeystoreKey()
        val builder = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)

        // setUserAuthenticationParameters requires API 30; use deprecated
        // setUserAuthenticationValidityDurationSeconds on older devices.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(builder.build())
        kg.generateKey()
    }

    private fun deleteKeystoreKey() {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
            if (ks.containsAlias(KEYSTORE_ALIAS)) ks.deleteEntry(KEYSTORE_ALIAS)
        } catch (_: Exception) {}
    }

    /** Public alias for call sites that need to discard the key on enrolment failure. */
    fun deleteKeystoreKeyPublic() = deleteKeystoreKey()

    private fun getKeystoreKey(): SecretKey? = try {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
        ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
    } catch (_: Exception) { null }

    // ── Biometric enroll: wrap vault key with Keystore key ───────────────────

    /**
     * Returns an ENCRYPT-mode Cipher initialised with the Keystore key.
     * Pass this as BiometricPrompt.CryptoObject so that authentication
     * unlocks the key inside the Keystore, then call [enrollWithAuthCipher].
     */
    fun getEnrollCipher(): Cipher? = try {
        val key = getKeystoreKey() ?: return null
        Cipher.getInstance(AES_GCM_NO_PAD).also { it.init(Cipher.ENCRYPT_MODE, key) }
    } catch (_: Exception) { null }

    /**
     * Wraps [vaultKeyBytes] using the biometric-authenticated [cipher] and
     * stores the ciphertext + IV in EncryptedSharedPreferences.
     */
    fun enrollWithAuthCipher(cipher: Cipher, vaultKeyBytes: ByteArray) {
        val wrapped = cipher.doFinal(vaultKeyBytes)
        val iv      = cipher.iv
        sharedPreferences.edit()
            .putString(BIO_WRAPPED_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putString(BIO_IV_KEY,      Base64.encodeToString(iv,      Base64.NO_WRAP))
            .apply()
    }

    // ── Biometric unlock: unwrap vault key with Keystore key ─────────────────

    /**
     * Returns a DECRYPT-mode Cipher that requires biometric auth to use.
     * Pass as BiometricPrompt.CryptoObject.
     * Returns null if no enrolled biometric key is stored.
     */
    fun getUnlockCipher(): Cipher? = try {
        val key = getKeystoreKey() ?: return null
        val ivB64 = sharedPreferences.getString(BIO_IV_KEY, null) ?: return null
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        Cipher.getInstance(AES_GCM_NO_PAD).also {
            it.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LEN, iv))
        }
    } catch (_: Exception) { null }

    /**
     * Decrypts the stored wrapped key using the biometric-authenticated [cipher].
     * Returns the vault key bytes, or null on failure.
     */
    fun unwrapWithAuthCipher(cipher: Cipher): ByteArray? = try {
        val wrappedB64 = sharedPreferences.getString(BIO_WRAPPED_KEY, null) ?: return null
        val wrapped = Base64.decode(wrappedB64, Base64.NO_WRAP)
        cipher.doFinal(wrapped)
    } catch (_: Exception) { null }

    // ── Legacy / fallback ────────────────────────────────────────────────────

    /**
     * Direct key retrieval — used only as a last-resort fallback when the
     * Keystore path is unavailable (e.g. first run before enrolment).
     */
    fun getStoredKeyBytes(): ByteArray? = getHash()

    fun loadSessionKey(): ByteArray? = if (isVaultCreated()) getHash() else null

    fun clearSessionKey() { /* placeholder — EncryptedSharedPreferences entry always current */ }

    fun changeMasterPassword(oldPassword: String, newPassword: String): Boolean {
        val oldKey = verifyMasterPassword(oldPassword) ?: return false
        val newSalt = ByteArray(SALT_LENGTH_BYTES).also(secureRandom::nextBytes)
        val newKey  = deriveKey(newPassword, newSalt, DEFAULT_ITERATIONS)
        sharedPreferences.edit()
            .putString(SALT_KEY,     Base64.encodeToString(newSalt, Base64.NO_WRAP))
            .putString(HASH_KEY,     Base64.encodeToString(newKey,  Base64.NO_WRAP))
            .putInt(ITERATIONS_KEY,  DEFAULT_ITERATIONS)
            .remove(BIO_WRAPPED_KEY) // force re-enrolment after password change
            .remove(BIO_IV_KEY)
            .apply()
        deleteKeystoreKey()
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
        return if (sharedPreferences.contains(ITERATIONS_KEY))
            sharedPreferences.getInt(ITERATIONS_KEY, DEFAULT_ITERATIONS)
        else null
    }
}
