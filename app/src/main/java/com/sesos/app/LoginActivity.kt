package com.sesos.app

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf("") }
    var consoleErr by remember { mutableStateOf<String?>(null) }
    val webVer = remember {
        try { android.webkit.WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } } catch (_: Throwable) { null }
    }
    var web by remember { mutableStateOf<WebView?>(null) }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF6C4DF6))) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(site.label) },
                actions = {
                    TextButton(onClick = { error = null; web?.reload() }) { Text("إعادة تحميل") }
                    TextButton(onClick = {
                        val ok = CookieStore.saveFromWebView(site, web?.settings?.userAgentString)
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
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                webVer?.let { Text("WebView: $it", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp)) }
                consoleErr?.let { Text("JS: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp)) }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { c ->
                            WebView(c).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.setSupportMultipleWindows(false)
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                setBackgroundColor(android.graphics.Color.WHITE)
                                if (android.os.Build.VERSION.SDK_INT >= 29) {
                                    @Suppress("DEPRECATION")
                                    settings.forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
                                }
                                // Meta sites serve a broken/blank page to the "; wv" WebView marker -> use the plain Chrome-style UA.
                                // (Not applied to Google/YouTube: Google's embedded-login policy is left untouched.)
                                if (site != Site.YOUTUBE) {
                                    settings.userAgentString = settings.userAgentString
                                        .replace("; wv", "")
                                        .replace(Regex(" Version/\\d+\\.\\d+"), "")
                                }
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                    }

                                    override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                                        if (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                                            consoleErr = m.message().take(160)
                                        }
                                        return true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val scheme = request.url.scheme?.lowercase()
                                        // ignore "open in app" deep links (intent://, fb://, instagram://...) and stay on the web page
                                        return scheme != "http" && scheme != "https"
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        currentUrl = url ?: ""
                                        error = null
                                    }

                                    override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                                        if (request.isForMainFrame) error = "خطأ ${err.errorCode}: ${err.description}\n${request.url.host}"
                                    }

                                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, resp: WebResourceResponse) {
                                        if (request.isForMainFrame) error = "HTTP ${resp.statusCode} من ${request.url.host}"
                                    }

                                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, e: SslError) {
                                        handler.cancel()
                                        error = "خطأ شهادة SSL — تأكد من التاريخ والوقت في الهاتف"
                                    }
                                }
                                web = this
                                loadUrl(site.loginUrl)
                            }
                        }
                    )
                }
            }
        }
    }
}
