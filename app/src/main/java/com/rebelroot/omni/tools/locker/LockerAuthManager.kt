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
     * Checks if biometric (fingerprint/face) hardware is available and enrolled.
     */
    fun canAuthenticateWithBiometrics(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Launches the system biometrics sheet (fingerprint only, no device PIN fallback).
     */
    fun authenticate(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val biometricManager = BiometricManager.from(activity)
        
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            onError("Biometrics not available or not enrolled.")
            return
        }

        try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Private Locker")
                .setSubtitle("Confirm fingerprint to enter")
                .setNegativeButtonText("Use In-App PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
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
