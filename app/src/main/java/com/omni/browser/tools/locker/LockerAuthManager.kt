package com.omni.browser.tools.locker

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class LockerAuthManager(private val activity: FragmentActivity) {

    companion object {
        private const val TAG = "LockerAuthManager"
        private const val KEY_NAME = "omni_locker_biometric_key"
    }

    /**
     * Retrieves or creates a hardware-isolated SecretKey that requires
     * biometric user authentication for any decryption attempts.
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        
        // If key already exists, return it
        keyStore.getKey(KEY_NAME, null)?.let {
            return it as SecretKey
        }

        // Otherwise generate a new key bound to biometric state
        val keyGenParams = KeyGenParameterSpec.Builder(
            KEY_NAME,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true) // Bound key access to authentication
            .setInvalidatedByBiometricEnrollment(true) // Invalidate key if new fingerprints are added
            .build()

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        keyGenerator.init(keyGenParams)
        return keyGenerator.generateKey()
    }

    /**
     * Initializes the cipher in decryption/encryption mode
     */
    private fun getInitializedCipher(mode: Int): Cipher {
        val cipher = Cipher.getInstance(
            "${KeyProperties.KEY_ALGORITHM_AES}/" +
            "${KeyProperties.BLOCK_MODE_GCM}/" +
            KeyProperties.ENCRYPTION_PADDING_NONE
        )
        cipher.init(mode, getOrCreateSecretKey())
        return cipher
    }

    /**
     * Launches the system biometrics sheet. If biometrics are not configured,
     * it falls back to the system PIN/Pattern/Password screen automatically.
     */
    fun authenticate(
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit
    ) {
        val biometricManager = BiometricManager.from(activity)
        
        // Verify biometric status
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            onError("Biometric authentication is not configured on this device.")
            return
        }

        try {
            // We use DECRYPT_MODE to initialized our cipher since it is used to unlock the vault
            val cipher = getInitializedCipher(Cipher.DECRYPT_MODE)
            val cryptoObject = BiometricPrompt.CryptoObject(cipher)

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Private Locker")
                .setSubtitle("Confirm biometrics or device PIN to enter")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            val biometricPrompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        Log.i(TAG, "Locker unlock authenticated successfully by hardware.")
                        result.cryptoObject?.cipher?.let { authenticatedCipher ->
                            onSuccess(authenticatedCipher)
                        } ?: run {
                            onError("Decryption cipher initialization error.")
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        Log.e(TAG, "Biometric authentication error: $errString ($errorCode)")
                        onError(errString.toString())
                    }

                    override fun onAuthenticationFailed() {
                        Log.w(TAG, "Biometric authentication attempt failed.")
                        // BiometricPrompt handles updating the prompt overlay on retry-able attempts
                    }
                }
            )
            biometricPrompt.authenticate(promptInfo, cryptoObject)

        } catch (e: Exception) {
            Log.e(TAG, "Fatal Keystore cipher initialization crash", e)
            onError("Cryptographic keystore initialization failure.")
        }
    }
}
