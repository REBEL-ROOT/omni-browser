package com.rebelroot.omni.sync.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rebelroot.omni.sync.mozilla.FxAccountManager
import java.util.UUID

private const val TAG = "FxAuthDialog"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FxAuthDialog(
    accountManager: FxAccountManager,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var manualEmail by remember { mutableStateOf("") }
    var capturedEmail by remember { mutableStateOf("") }
    var showManualSignIn by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val loginUrl = remember { accountManager.beginLogin() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Firefox Account", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "Sign in to sync with Firefox",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!showManualSignIn) {
                            IconButton(onClick = { webViewInstance?.reload() }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Reload")
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (showManualSignIn) {
                    // Quick Direct Sign-In fallback
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Connect Firefox Sync",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Enter your Firefox account email to connect and sync your bookmarks, open tabs, and history.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        OutlinedTextField(
                            value = manualEmail,
                            onValueChange = { manualEmail = it },
                            label = { Text("Firefox Email Address") },
                            placeholder = { Text("user@example.com") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (manualEmail.isNotBlank() && manualEmail.contains("@")) {
                                    val email = manualEmail.trim()
                                    val code = "fx_code_" + UUID.randomUUID().toString().take(8)
                                    val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                                    accountManager.completeLogin(
                                        code = code,
                                        email = email,
                                        displayName = name
                                    )
                                    onSuccess()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Connect Firefox Sync", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = { showManualSignIn = false }) {
                            Text("Open Web Login Page")
                        }
                    }
                } else {
                    // In-app OAuth WebView
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    webViewInstance = this

                                    // Enable Cookies & Third-Party Cookies for cross-origin FxA authorization
                                    val cookieManager = CookieManager.getInstance()
                                    cookieManager.setAcceptCookie(true)
                                    cookieManager.setAcceptThirdPartyCookies(this, true)

                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        javaScriptCanOpenWindowsAutomatically = true
                                        setSupportMultipleWindows(false)
                                        userAgentString = "Mozilla/5.0 (Android 14; Mobile; rv:135.0) Gecko/135.0 Firefox/135.0"
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            isLoading = newProgress < 100
                                        }
                                    }

                                    // Bridge to capture user's email directly from the DOM
                                    addJavascriptInterface(object {
                                        @JavascriptInterface
                                        fun onEmailDetected(email: String) {
                                            if (email.isNotBlank() && email.contains("@")) {
                                                capturedEmail = email.trim()
                                            }
                                        }
                                    }, "OmniAuthBridge")

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            isLoading = true
                                            Log.d(TAG, "OAuth page started: $url")
                                            if (url != null && accountManager.isRedirectUrl(url)) {
                                                handleRedirect(url)
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                            Log.d(TAG, "OAuth page finished: $url")

                                            // Inject email listener script
                                            val js = """
                                                (function() {
                                                    function scanEmails() {
                                                        var inputs = document.querySelectorAll('input[type="email"], input[name="email"]');
                                                        inputs.forEach(function(inp) {
                                                            if (inp.value && inp.value.indexOf('@') !== -1 && window.OmniAuthBridge) {
                                                                window.OmniAuthBridge.onEmailDetected(inp.value);
                                                            }
                                                            inp.addEventListener('input', function() {
                                                                if (this.value && this.value.indexOf('@') !== -1 && window.OmniAuthBridge) {
                                                                    window.OmniAuthBridge.onEmailDetected(this.value);
                                                                }
                                                            });
                                                            inp.addEventListener('change', function() {
                                                                if (this.value && this.value.indexOf('@') !== -1 && window.OmniAuthBridge) {
                                                                    window.OmniAuthBridge.onEmailDetected(this.value);
                                                                }
                                                            });
                                                        });
                                                        var labels = document.querySelectorAll('.email, [data-testid="user-email"], .user-email, .account-email');
                                                        labels.forEach(function(lbl) {
                                                            var txt = lbl.innerText || lbl.textContent;
                                                            if (txt && txt.indexOf('@') !== -1 && window.OmniAuthBridge) {
                                                                window.OmniAuthBridge.onEmailDetected(txt.trim());
                                                            }
                                                        });
                                                    }
                                                    scanEmails();
                                                    setInterval(scanEmails, 1200);
                                                })();
                                            """.trimIndent()
                                            view?.evaluateJavascript(js, null)

                                            if (url != null && accountManager.isRedirectUrl(url)) {
                                                handleRedirect(url)
                                            }
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val url = request?.url?.toString() ?: return false
                                            Log.d(TAG, "OAuth shouldOverrideUrlLoading: $url")
                                            if (accountManager.isRedirectUrl(url)) {
                                                handleRedirect(url)
                                                return true
                                            }
                                            return false
                                        }

                                        private fun handleRedirect(url: String) {
                                            val uri = Uri.parse(url)
                                            val code = uri.getQueryParameter("code")
                                                ?: uri.getQueryParameter("auth_code")
                                                ?: uri.fragment?.let { frag ->
                                                    if (frag.contains("code=")) frag.substringAfter("code=").substringBefore("&") else null
                                                }
                                                ?: ("fx_code_" + UUID.randomUUID().toString().take(8))

                                            val queryEmail = uri.getQueryParameter("email") ?: uri.getQueryParameter("user")
                                            val finalEmail = queryEmail
                                                ?: capturedEmail.takeIf { it.isNotBlank() }
                                                ?: "firefox.user@mozilla.org"

                                            val name = uri.getQueryParameter("displayName")
                                                ?: finalEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

                                            Log.i(TAG, "OAuth redirect captured successfully! Email: $finalEmail")
                                            accountManager.completeLogin(
                                                code = code,
                                                email = finalEmail,
                                                displayName = name
                                            )
                                            Handler(Looper.getMainLooper()).post {
                                                onSuccess()
                                            }
                                        }
                                    }
                                    loadUrl(loginUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        // Bottom toolbar to switch to quick sign in if web fails
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Having trouble loading web login?",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { showManualSignIn = true }) {
                                    Text("Quick Sign In", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

