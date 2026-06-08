package com.rebelroot.omni.tools.locker

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class LockerAuthManager(private val activity: FragmentActivity) {

    companion object {
        private const val TAG = "LockerAuthManager"
    }

    /**
     * Launches the system biometrics sheet. If biometrics are not configured,
     * it falls back to the system PIN/Pattern/Password screen automatically.
     */
    fun authenticate(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val biometricManager = BiometricManager.from(activity)
        
        // Verify biometric status
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            onError("Authentication is not configured on this device (No PIN/Biometrics).")
            return
        }

        try {
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
                        Log.i(TAG, "Locker unlock authenticated successfully.")
                        onSuccess()
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
            biometricPrompt.authenticate(promptInfo)

        } catch (e: Exception) {
            Log.e(TAG, "Fatal authentication initialization crash", e)
            onError("Authentication initialization failure.")
        }
    }
}
