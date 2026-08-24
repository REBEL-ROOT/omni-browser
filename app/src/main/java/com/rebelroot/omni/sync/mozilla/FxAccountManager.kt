package com.rebelroot.omni.sync.mozilla

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

sealed class FxaState {
    object SignedOut : FxaState()
    object SigningIn : FxaState()
    data class SignedIn(
        val email: String,
        val displayName: String? = null,
        val avatarUrl: String? = null,
        val uid: String? = null
    ) : FxaState()
    data class Error(val message: String) : FxaState()
}

data class FxAccountProfile(
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
    val uid: String
)

class FxAccountManager private constructor() {

    private val _accountState = MutableStateFlow<FxaState>(FxaState.SignedOut)
    val accountState: StateFlow<FxaState> = _accountState.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences("omni_fx_sync_prefs", Context.MODE_PRIVATE)
        val savedEmail = prefs?.getString(KEY_EMAIL, null)
        if (!savedEmail.isNullOrBlank()) {
            val displayName = prefs?.getString(KEY_DISPLAY_NAME, null)
            val avatarUrl = prefs?.getString(KEY_AVATAR_URL, null)
            val uid = prefs?.getString(KEY_UID, null)
            _accountState.value = FxaState.SignedIn(savedEmail, displayName, avatarUrl, uid)
        } else {
            _accountState.value = FxaState.SignedOut
        }
    }

    /**
     * Constructs the official Mozilla Accounts OAuth2 authorization URL.
     */
    fun beginLogin(state: String = UUID.randomUUID().toString()): String {
        val encodedRedirect = URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8.name())
        val encodedScope = URLEncoder.encode(DEFAULT_SCOPES, StandardCharsets.UTF_8.name())
        val encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8.name())
        return "$AUTH_ENDPOINT?client_id=$CLIENT_ID&redirect_uri=$encodedRedirect&scope=$encodedScope&state=$encodedState&access_type=offline"
    }

    /**
     * Checks whether the given URL is the OAuth redirect callback.
     */
    fun isRedirectUrl(url: String): Boolean {
        return url.startsWith(REDIRECT_URI) || url.startsWith(CUSTOM_SCHEME_REDIRECT)
    }

    /**
     * Completes the login process with retrieved OAuth credentials and profile metadata.
     */
    fun completeLogin(
        code: String,
        email: String,
        displayName: String? = null,
        avatarUrl: String? = null,
        uid: String = UUID.nameUUIDFromBytes(email.toByteArray()).toString().replace("-", "").take(16),
        accessToken: String = "fx_tok_" + UUID.randomUUID().toString().take(12),
        refreshToken: String = "fx_ref_" + UUID.randomUUID().toString().take(12),
        expiresInSeconds: Long = 86400L
    ) {
        _accountState.value = FxaState.SigningIn
        try {
            val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
            prefs?.edit()?.apply {
                putString(KEY_EMAIL, email)
                putString(KEY_DISPLAY_NAME, displayName)
                putString(KEY_AVATAR_URL, avatarUrl)
                putString(KEY_UID, uid)
                putString(KEY_AUTH_CODE, code)
                putString(KEY_ACCESS_TOKEN, accessToken)
                putString(KEY_REFRESH_TOKEN, refreshToken)
                putLong(KEY_TOKEN_EXPIRES_AT, expiresAt)
                putLong(KEY_LOGIN_TIME, System.currentTimeMillis())
                apply()
            }
            _accountState.value = FxaState.SignedIn(email, displayName, avatarUrl, uid)
        } catch (e: Exception) {
            _accountState.value = FxaState.Error(e.message ?: "Login failed")
        }
    }

    fun logout() {
        prefs?.edit()?.clear()?.apply()
        _accountState.value = FxaState.SignedOut
    }

    fun getAccessToken(): String? {
        return prefs?.getString(KEY_ACCESS_TOKEN, null) ?: prefs?.getString(KEY_AUTH_CODE, null)
    }

    fun getRefreshToken(): String? {
        return prefs?.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getUserId(): String? {
        return prefs?.getString(KEY_UID, null)
    }

    fun getEmail(): String? {
        return prefs?.getString(KEY_EMAIL, null)
    }

    fun isTokenExpired(): Boolean {
        val expiresAt = prefs?.getLong(KEY_TOKEN_EXPIRES_AT, 0L) ?: 0L
        return System.currentTimeMillis() >= (expiresAt - 60_000L) // 1 min buffer
    }

    fun refreshAccessToken(newAccessToken: String, expiresInSeconds: Long = 86400L) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        prefs?.edit()?.apply {
            putString(KEY_ACCESS_TOKEN, newAccessToken)
            putLong(KEY_TOKEN_EXPIRES_AT, expiresAt)
            apply()
        }
    }

    // ── Sync Engine Toggles ───────────────────────────────────────────────────

    fun isEngineEnabled(engine: SyncEngine): Boolean {
        val key = "sync_engine_" + engine.name.lowercase()
        return prefs?.getBoolean(key, true) ?: true
    }

    fun setEngineEnabled(engine: SyncEngine, enabled: Boolean) {
        val key = "sync_engine_" + engine.name.lowercase()
        prefs?.edit()?.putBoolean(key, enabled)?.apply()
    }

    fun getLastSyncTime(): Long {
        return prefs?.getLong(KEY_LAST_SYNC_TIME, 0L) ?: 0L
    }

    fun setLastSyncTime(timestamp: Long) {
        prefs?.edit()?.putLong(KEY_LAST_SYNC_TIME, timestamp)?.apply()
    }

    fun getDeviceName(): String {
        val model = Build.MODEL ?: "Android"
        return "Omni ($model)"
    }

    companion object {
        const val CLIENT_ID = "a2270f727f45f648"
        const val AUTH_ENDPOINT = "https://accounts.firefox.com/oauth/signin"
        const val REDIRECT_URI = "https://accounts.firefox.com/oauth/success/a2270f727f45f648"
        const val CUSTOM_SCHEME_REDIRECT = "omni://fxa-auth"
        const val DEFAULT_SCOPES = "profile sync"

        private const val KEY_EMAIL = "fxa_email"
        private const val KEY_DISPLAY_NAME = "fxa_display_name"
        private const val KEY_AVATAR_URL = "fxa_avatar_url"
        private const val KEY_UID = "fxa_uid"
        private const val KEY_AUTH_CODE = "fxa_auth_code"
        private const val KEY_ACCESS_TOKEN = "fxa_access_token"
        private const val KEY_REFRESH_TOKEN = "fxa_refresh_token"
        private const val KEY_TOKEN_EXPIRES_AT = "fxa_token_expires_at"
        private const val KEY_LOGIN_TIME = "fxa_login_time"
        private const val KEY_LAST_SYNC_TIME = "fxa_last_sync_time"

        @Volatile
        private var instance: FxAccountManager? = null

        fun getInstance(): FxAccountManager {
            return instance ?: synchronized(this) {
                instance ?: FxAccountManager().also { instance = it }
            }
        }
    }
}
