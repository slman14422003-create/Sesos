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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.os.Message
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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
    var desktop by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF6C4DF6))) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(site.label) },
                actions = {
                    TextButton(onClick = {
                        desktop = !desktop
                        web?.let { w ->
                            error = null; consoleErr = null
                            applyBrowserIdentity(w, site, desktop)
                            w.loadUrl(if (desktop) desktopUrl(site) else site.loginUrl)
                        }
                    }) { Text(if (desktop) "جوال" else "كمبيوتر") }
                    TextButton(onClick = { error = null; consoleErr = null; web?.reload() }) { Text("إعادة تحميل") }
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
                                settings.setSupportMultipleWindows(true)
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                setBackgroundColor(android.graphics.Color.WHITE)
                                if (android.os.Build.VERSION.SDK_INT >= 29) {
                                    @Suppress("DEPRECATION")
                                    settings.forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
                                }
                                applyBrowserIdentity(this, site, false)
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                // window.open() popups (Meta login uses them) are loaded in this same WebView
                                webChromeClient = object : WebChromeClient() {
                                    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                                        val tmp = WebView(view.context)
                                        tmp.webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                                view.loadUrl(r.url.toString()); return true
                                            }
                                        }
                                        (resultMsg.obj as WebView.WebViewTransport).webView = tmp
                                        resultMsg.sendToTarget()
                                        return true
                                    }

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
                                        return scheme !in setOf("http", "https", "about", "blob", "data", "javascript")
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
                                try {
                                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                        WebViewCompat.addDocumentStartJavaScript(this, CHROME_SHIM, setOf("https://*"))
                                    }
                                } catch (_: Throwable) { }
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


private fun chromeMajor(ua: String): String =
    Regex("Chrome/(\\d+)").find(ua)?.groupValues?.get(1) ?: "120"

/** Makes the WebView look like plain Chrome everywhere Meta checks (UA string + client hints + JS globals). */
private fun applyBrowserIdentity(web: WebView, site: Site, desktop: Boolean) {
    val s = web.settings
    val base = WebSettings.getDefaultUserAgent(web.context)
    val major = chromeMajor(base)
    if (site == Site.YOUTUBE) return // Google's embedded-login policy is left untouched
    s.userAgentString = if (desktop)
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Safari/537.36"
    else
        base.replace("; wv", "").replace(Regex(" Version/\\d+\\.\\d+"), "")
    s.useWideViewPort = true
    s.loadWithOverviewMode = true
    // Client hints (navigator.userAgentData) otherwise still say "Android WebView" and contradict the UA string.
    try {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val brands = listOf(
                UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium").setMajorVersion(major).setFullVersion("$major.0.0.0").build(),
                UserAgentMetadata.BrandVersion.Builder().setBrand("Google Chrome").setMajorVersion(major).setFullVersion("$major.0.0.0").build(),
                UserAgentMetadata.BrandVersion.Builder().setBrand("Not.A/Brand").setMajorVersion("99").setFullVersion("99.0.0.0").build()
            )
            WebSettingsCompat.setUserAgentMetadata(
                s,
                UserAgentMetadata.Builder().setBrandVersionList(brands).setMobile(!desktop).build()
            )
        }
    } catch (_: Throwable) { }
}

private const val CHROME_SHIM = "try{if(!window.chrome){window.chrome={runtime:{},app:{isInstalled:false}};}}catch(e){}"

private fun desktopUrl(site: Site) = when (site) {
    Site.FACEBOOK -> "https://www.facebook.com/login.php"
    else -> site.loginUrl
}
