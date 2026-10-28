package com.sesos.app

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val site = Site.valueOf(intent.getStringExtra("site") ?: Site.INSTAGRAM.name)
        if (savedInstanceState == null) CookieManager.getInstance().removeAllCookies(null)
        setContent { LoginScreen(site) { finish() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(site: Site, onClose: () -> Unit) {
    val ctx = LocalContext.current
    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF6C4DF6))) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(site.label) },
                actions = {
                    TextButton(onClick = {
                        val ok = CookieStore.saveFromWebView(site)
                        Toast.makeText(
                            ctx,
                            if (ok) "تم حفظ الجلسة بأمان على جهازك" else "ما زلت غير مسجّل الدخول، أكمل الدخول أولاً",
                            Toast.LENGTH_LONG
                        ).show()
                        if (ok) onClose()
                    }) { Text("تم تسجيل الدخول") }
                }
            )
        }) { pad ->
            AndroidView(
                modifier = Modifier.padding(pad).fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        loadUrl(site.loginUrl)
                    }
                }
            )
        }
    }
}
