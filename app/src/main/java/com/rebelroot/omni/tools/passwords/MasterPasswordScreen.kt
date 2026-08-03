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
                    launchBiometricPrompt(
                        activity = context as FragmentActivity,
                        onSuccess = {
                            // Biometric success — derive key from stored hash directly
                            val keyBytes = masterPasswordManager.getStoredKeyBytes()
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
                                errorMessage = "Use at least 5 characters"
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
                                errorMessage = "Passwords do not match"
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
                            masterPasswordManager.setBiometricEnabled(true)
                            step = VaultAuthStep.UNLOCK
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
                                launchBiometricPrompt(
                                    activity = context as FragmentActivity,
                                    onSuccess = {
                                        val keyBytes = masterPasswordManager.getStoredKeyBytes()
                                        if (keyBytes != null) onUnlockSuccess(keyBytes)
                                        else { showPasswordFallback = true; errorMessage = "Biometric key error — use password" }
                                    },
                                    onFallback = { showPasswordFallback = true }
                                )
                            },
                            onUnlockWithPassword = {
                                val keyBytes = masterPasswordManager.verifyMasterPassword(unlockPassword)
                                if (keyBytes == null) {
                                    errorMessage = "Incorrect password"
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
        title = "Create master password",
        subtitle = "This password encrypts your vault.\nChoose something strong and memorable."
    )
    Spacer(Modifier.height(28.dp))
    PasswordField(
        value = password,
        onValueChange = onPasswordChange,
        visible = visible,
        onVisibilityToggle = onVisibilityToggle,
        label = "Master password"
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
        Text("Continue", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
        title = "Confirm password",
        subtitle = "Re-enter your master password\nto confirm it's correct."
    )
    Spacer(Modifier.height(28.dp))
    PasswordField(
        value = confirmPassword,
        onValueChange = onPasswordChange,
        visible = visible,
        onVisibilityToggle = onVisibilityToggle,
        label = "Re-enter password"
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
        Text("Create vault", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text("Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EnableBiometricStep(
    onEnable: () -> Unit,
    onSkip: () -> Unit
) {
    StepHeader(
        icon = Icons.Filled.Fingerprint,
        title = "Enable fingerprint unlock",
        subtitle = "Use your fingerprint to unlock\nyour vault quickly and securely."
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
                text = "Unlock instantly with your fingerprint"
            )
            FeatureBullet(
                icon = Icons.Rounded.Lock,
                text = "Your password is still required as backup"
            )
            FeatureBullet(
                icon = Icons.Rounded.ShieldMoon,
                text = "Biometric data never leaves your device"
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
            modifier = Modifier
                .size(18.dp)
                .padding(end = 0.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text("Enable fingerprint", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
        Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            title = "Unlock vault",
            subtitle = "Use your fingerprint to access\nyour saved passwords."
        )
        Spacer(Modifier.height(36.dp))

        // Large fingerprint tap target
        Surface(
            onClick = onUseBiometric,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = "Tap to use fingerprint",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Tap to authenticate",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(10.dp))
            ErrorText(errorMessage)
        }

        Spacer(Modifier.height(40.dp))

        // "Use password instead" label — subtle, placed at bottom
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
                    text = "Use password instead",
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
            title = "Enter master password",
            subtitle = "Enter your master password\nto access your saved passwords."
        )
        Spacer(Modifier.height(28.dp))
        PasswordField(
            value = password,
            onValueChange = onPasswordChange,
            visible = visible,
            onVisibilityToggle = onVisibilityToggle,
            label = "Master password"
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
            Text("Unlock", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
        // Show "use fingerprint" back option if biometric is still enabled
        if (biometricEnabled) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onUseBiometric, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Filled.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text("Use fingerprint", color = MaterialTheme.colorScheme.primary)
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
            text = strength.label,
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

private fun launchBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
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
            .setTitle("Unlock vault")
            .setSubtitle("Confirm your fingerprint to continue")
            .setNegativeButtonText("Use password")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User pressed "Use password" or dismissed
                    onFallback()
                }
                override fun onAuthenticationFailed() {
                    // Bad scan — system handles retry UI; don't fall back yet
                }
            }
        )
        prompt.authenticate(promptInfo)
    } catch (e: Exception) {
        onFallback()
    }
}
