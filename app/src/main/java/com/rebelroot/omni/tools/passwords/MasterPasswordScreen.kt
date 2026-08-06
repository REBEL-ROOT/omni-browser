/*
 * Omni Browser - Password Manager master password / unlock screen.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.tools.passwords

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.res.stringResource
import com.rebelroot.omni.R
import javax.crypto.Cipher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

enum class VaultAuthStep {
    CREATE_PASSWORD,
    CONFIRM_PASSWORD,
    ENABLE_BIOMETRIC,
    UNLOCK
}

// ─── Root screen ─────────────────────────────────────────────────────────────

@Composable
fun MasterPasswordScreen(
    masterPasswordManager: MasterPasswordManager,
    modifier: Modifier = Modifier,
    onUnlockSuccess: (ByteArray) -> Unit
) {
    val context = LocalContext.current

    var step by remember {
        mutableStateOf(
            if (masterPasswordManager.isVaultCreated()) VaultAuthStep.UNLOCK
            else VaultAuthStep.CREATE_PASSWORD
        )
    }

    // Create / confirm state
    var createPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var createPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    // Unlock state
    var unlockPassword by rememberSaveable { mutableStateOf("") }
    var unlockPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var showPasswordFallback by rememberSaveable { mutableStateOf(false) }

    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // Whether the device actually supports strong biometrics
    val biometricAvailable = remember(context) {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    // Auto-trigger biometric prompt when landing on UNLOCK and biometric is enabled
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(step, showPasswordFallback) {
        if (step == VaultAuthStep.UNLOCK &&
            !showPasswordFallback &&
            masterPasswordManager.isBiometricEnabled() &&
            biometricAvailable
        ) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val unlockCipher = masterPasswordManager.getUnlockCipher()
                    if (unlockCipher == null) {
                        showPasswordFallback = true
                        return@LifecycleEventObserver
                    }
                    launchBiometricWithCrypto(
                        activity = context as FragmentActivity,
                        cipher = unlockCipher,
                        onSuccess = { authCipher ->
                            val keyBytes = masterPasswordManager.unwrapWithAuthCipher(authCipher)
                            if (keyBytes != null) onUnlockSuccess(keyBytes)
                            else showPasswordFallback = true
                        },
                        onFallback = { showPasswordFallback = true }
                    )
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        } else {
            onDispose {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "vault-auth-step"
        ) { authStep ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                when (authStep) {

                    // ── Step 1: create ──────────────────────────────────────
                    VaultAuthStep.CREATE_PASSWORD -> CreatePasswordStep(
                        password = createPassword,
                        onPasswordChange = { createPassword = it; errorMessage = null },
                        visible = createPasswordVisible,
                        onVisibilityToggle = { createPasswordVisible = !createPasswordVisible },
                        errorMessage = errorMessage,
                        onContinue = {
                            if (createPassword.length < 5) {
                                errorMessage = context.getString(R.string.pm_error_min_chars)
                                return@CreatePasswordStep
                            }
                            errorMessage = null
                            step = VaultAuthStep.CONFIRM_PASSWORD
                        }
                    )

                    // ── Step 2: confirm ─────────────────────────────────────
                    VaultAuthStep.CONFIRM_PASSWORD -> ConfirmPasswordStep(
                        confirmPassword = confirmPassword,
                        onPasswordChange = { confirmPassword = it; errorMessage = null },
                        visible = confirmPasswordVisible,
                        onVisibilityToggle = { confirmPasswordVisible = !confirmPasswordVisible },
                        errorMessage = errorMessage,
                        onConfirm = {
                            if (confirmPassword != createPassword) {
                                errorMessage = context.getString(R.string.pm_error_passwords_mismatch)
                                return@ConfirmPasswordStep
                            }
                            masterPasswordManager.createVault(createPassword)
                            createPassword = ""
                            confirmPassword = ""
                            errorMessage = null
                            // Go to biometric opt-in only if hardware is available
                            step = if (biometricAvailable) VaultAuthStep.ENABLE_BIOMETRIC
                                   else VaultAuthStep.UNLOCK
                        },
                        onBack = {
                            step = VaultAuthStep.CREATE_PASSWORD
                            errorMessage = null
                        }
                    )

                    // ── Step 3: enable biometric ────────────────────────────
                    VaultAuthStep.ENABLE_BIOMETRIC -> EnableBiometricStep(
                        onEnable = {
                            // 1. Create Keystore key bound to biometric
                            masterPasswordManager.createOrReplaceKeystoreKey()
                            // 2. Get encrypt cipher and launch biometric to authorise it
                            val enrollCipher = masterPasswordManager.getEnrollCipher()
                            if (enrollCipher == null) {
                                // Keystore unavailable — skip biometric, go straight to unlock
                                masterPasswordManager.setBiometricEnabled(false)
                                step = VaultAuthStep.UNLOCK
                                return@EnableBiometricStep
                            }
                            // Retrieve the vault key bytes created in step 2
                            val vaultKey = masterPasswordManager.getStoredKeyBytes()
                                ?: run {
                                    masterPasswordManager.setBiometricEnabled(false)
                                    step = VaultAuthStep.UNLOCK
                                    return@EnableBiometricStep
                                }
                            launchBiometricWithCrypto(
                                activity = context as FragmentActivity,
                                cipher = enrollCipher,
                                onSuccess = { authCipher ->
                                    masterPasswordManager.enrollWithAuthCipher(authCipher, vaultKey)
                                    masterPasswordManager.setBiometricEnabled(true)
                                    step = VaultAuthStep.UNLOCK
                                },
                                onFallback = {
                                    // User declined — disable biometric silently
                                    masterPasswordManager.setBiometricEnabled(false)
                                    masterPasswordManager.deleteKeystoreKeyPublic()
                                    step = VaultAuthStep.UNLOCK
                                }
                            )
                        },
                        onSkip = {
                            masterPasswordManager.setBiometricEnabled(false)
                            step = VaultAuthStep.UNLOCK
                        }
                    )

                    // ── Step 4: unlock ──────────────────────────────────────
                    VaultAuthStep.UNLOCK -> {
                        val biometricOn = masterPasswordManager.isBiometricEnabled() && biometricAvailable
                        UnlockStep(
                            biometricEnabled = biometricOn,
                            showPasswordFallback = showPasswordFallback,
                            password = unlockPassword,
                            onPasswordChange = { unlockPassword = it; errorMessage = null },
                            visible = unlockPasswordVisible,
                            onVisibilityToggle = { unlockPasswordVisible = !unlockPasswordVisible },
                            errorMessage = errorMessage,
                            onUseBiometric = {
                                showPasswordFallback = false
                                val unlockCipher = masterPasswordManager.getUnlockCipher()
                                if (unlockCipher == null) {
                                    // No wrapped key — fall back to password
                                    showPasswordFallback = true
                                    errorMessage = context.getString(R.string.pm_error_biometric_key)
                                } else {
                                    launchBiometricWithCrypto(
                                        activity = context as FragmentActivity,
                                        cipher = unlockCipher,
                                        onSuccess = { authCipher ->
                                            val keyBytes = masterPasswordManager.unwrapWithAuthCipher(authCipher)
                                            if (keyBytes != null) onUnlockSuccess(keyBytes)
                                            else { showPasswordFallback = true; errorMessage = context.getString(R.string.pm_error_biometric_key) }
                                        },
                                        onFallback = { showPasswordFallback = true }
                                    )
                                }
                            },
                            onUnlockWithPassword = {
                                val keyBytes = masterPasswordManager.verifyMasterPassword(unlockPassword)
                                if (keyBytes == null) {
                                    errorMessage = context.getString(R.string.pm_error_incorrect_password)
                                    return@UnlockStep
                                }
                                onUnlockSuccess(keyBytes)
                                unlockPassword = ""
                                errorMessage = null
                            },
                            onShowPasswordFallback = { showPasswordFallback = true }
                        )
                    }
                }
            }
        }
    }
}

// ─── Step composables ────────────────────────────────────────────────────────

@Composable
private fun CreatePasswordStep(
    password: String,
    onPasswordChange: (String) -> Unit,
    visible: Boolean,
    onVisibilityToggle: () -> Unit,
    errorMessage: String?,
    onContinue: () -> Unit
) {
    StepHeader(
        icon = Icons.Rounded.ShieldMoon,
        title = stringResource(R.string.pm_create_master_title),
        subtitle = stringResource(R.string.pm_create_master_subtitle)
    )
    Spacer(Modifier.height(28.dp))
    PasswordField(
        value = password,
        onValueChange = onPasswordChange,
        visible = visible,
        onVisibilityToggle = onVisibilityToggle,
        label = stringResource(R.string.pm_master_password_label)
    )
    Spacer(Modifier.height(10.dp))
    StrengthMeter(password = password)
    if (errorMessage != null) {
        Spacer(Modifier.height(6.dp))
        ErrorText(errorMessage)
    }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(stringResource(R.string.pm_continue), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun ConfirmPasswordStep(
    confirmPassword: String,
    onPasswordChange: (String) -> Unit,
    visible: Boolean,
    onVisibilityToggle: () -> Unit,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    StepHeader(
        icon = Icons.Rounded.Key,
        title = stringResource(R.string.pm_confirm_title),
        subtitle = stringResource(R.string.pm_confirm_subtitle)
    )
    Spacer(Modifier.height(28.dp))
    PasswordField(
        value = confirmPassword,
        onValueChange = onPasswordChange,
        visible = visible,
        onVisibilityToggle = onVisibilityToggle,
        label = stringResource(R.string.pm_reenter_label)
    )
    if (errorMessage != null) {
        Spacer(Modifier.height(6.dp))
        ErrorText(errorMessage)
    }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(stringResource(R.string.pm_create_vault), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pm_back), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EnableBiometricStep(
    onEnable: () -> Unit,
    onSkip: () -> Unit
) {
    StepHeader(
        icon = Icons.Filled.Fingerprint,
        title = stringResource(R.string.pm_biometric_title),
        subtitle = stringResource(R.string.pm_biometric_subtitle)
    )
    Spacer(Modifier.height(32.dp))

    // Feature card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeatureBullet(
                icon = Icons.Filled.Fingerprint,
                text = stringResource(R.string.pm_biometric_bullet1)
            )
            FeatureBullet(
                icon = Icons.Rounded.Lock,
                text = stringResource(R.string.pm_biometric_bullet2)
            )
            FeatureBullet(
                icon = Icons.Rounded.ShieldMoon,
                text = stringResource(R.string.pm_biometric_bullet3)
            )
        }
    }

    Spacer(Modifier.height(28.dp))
    Button(
        onClick = onEnable,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(
            Icons.Filled.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(18.dp).padding(end = 0.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.pm_enable_fingerprint), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.pm_skip_for_now), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UnlockStep(
    biometricEnabled: Boolean,
    showPasswordFallback: Boolean,
    password: String,
    onPasswordChange: (String) -> Unit,
    visible: Boolean,
    onVisibilityToggle: () -> Unit,
    errorMessage: String?,
    onUseBiometric: () -> Unit,
    onUnlockWithPassword: () -> Unit,
    onShowPasswordFallback: () -> Unit
) {
    if (biometricEnabled && !showPasswordFallback) {
        // ── Biometric primary UI ──────────────────────────────────────────
        StepHeader(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.pm_unlock_biometric_title),
            subtitle = stringResource(R.string.pm_unlock_biometric_subtitle)
        )
        Spacer(Modifier.height(36.dp))

        Surface(
            onClick = onUseBiometric,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = stringResource(R.string.pm_tap_to_authenticate_cd),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.pm_tap_to_authenticate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(10.dp))
            ErrorText(errorMessage)
        }

        Spacer(Modifier.height(40.dp))

        Surface(
            onClick = onShowPasswordFallback,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.pm_use_password_instead),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

    } else {
        // ── Password fallback UI ──────────────────────────────────────────
        StepHeader(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.pm_unlock_password_title),
            subtitle = stringResource(R.string.pm_unlock_password_subtitle)
        )
        Spacer(Modifier.height(28.dp))
        PasswordField(
            value = password,
            onValueChange = onPasswordChange,
            visible = visible,
            onVisibilityToggle = onVisibilityToggle,
            label = stringResource(R.string.pm_master_password_label)
        )
        if (errorMessage != null) {
            Spacer(Modifier.height(6.dp))
            ErrorText(errorMessage)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onUnlockWithPassword,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.pm_unlock), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        if (biometricEnabled) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onUseBiometric, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.pm_use_fingerprint), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ─── Shared sub-composables ──────────────────────────────────────────────────

@Composable
private fun StepHeader(icon: ImageVector, title: String, subtitle: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeatureBullet(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onVisibilityToggle: () -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onVisibilityToggle) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        }
    )
}

@Composable
private fun StrengthMeter(password: String) {
    val strength = remember(password) { calculateStrength(password) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val filled = when {
                password.isBlank() -> 0
                strength.progress >= 1.0f -> 4
                strength.progress >= 0.65f -> 2
                else -> 1
            }
            repeat(4) { index ->
                Surface(
                    modifier = Modifier.weight(1f).height(4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = if (index < filled) strength.color else strength.color.copy(alpha = 0.15f)
                ) {}
            }
        }
        Text(
            text = when (strength.label) {
                "Strong" -> stringResource(R.string.pm_strength_strong)
                "Fair"   -> stringResource(R.string.pm_strength_fair)
                else     -> stringResource(R.string.pm_strength_weak)
            },
            color = strength.color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ─── Strength logic ──────────────────────────────────────────────────────────

private data class PasswordStrength(val label: String, val progress: Float, val color: Color)

private fun calculateStrength(password: String): PasswordStrength {
    if (password.isBlank()) return PasswordStrength("Weak", 0.1f, Color(0xFFEF4444))
    val hasUpper = password.any(Char::isUpperCase)
    val hasLower = password.any(Char::isLowerCase)
    val hasDigit = password.any(Char::isDigit)
    val hasSymbol = password.any { !it.isLetterOrDigit() }
    return when {
        password.length >= 12 && hasUpper && hasLower && hasDigit && hasSymbol ->
            PasswordStrength("Strong", 1.0f, Color(0xFF16A34A))
        password.length >= 8 && hasUpper && hasLower && (hasDigit || hasSymbol) ->
            PasswordStrength("Fair", 0.65f, Color(0xFFF59E0B))
        else -> PasswordStrength("Weak", 0.3f, Color(0xFFEF4444))
    }
}

// ─── Biometric prompt helper ─────────────────────────────────────────────────

/**
 * Launches a BiometricPrompt with a CryptoObject wrapping [cipher].
 * The authenticated cipher is returned in [onSuccess] — use it immediately
 * to encrypt or decrypt; it is only valid within the callback.
 */
private fun launchBiometricWithCrypto(
    activity: FragmentActivity,
    cipher: Cipher,
    onSuccess: (Cipher) -> Unit,
    onFallback: () -> Unit
) {
    val canAuthenticate = BiometricManager.from(activity)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
        onFallback()
        return
    }
    try {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.pm_biometric_prompt_title))
            .setSubtitle("Confirm your fingerprint to continue")
            .setNegativeButtonText(activity.getString(R.string.pm_biometric_prompt_negative))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authCipher = result.cryptoObject?.cipher
                    if (authCipher != null) onSuccess(authCipher)
                    else onFallback()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFallback()
                }
                override fun onAuthenticationFailed() {
                    // Bad scan — system handles retry UI; don't fall back yet
                }
            }
        )
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    } catch (e: Exception) {
        onFallback()
    }
}
