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
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import android.os.Message
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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

/** True when the page is a single video / reel / post that can be handed to the downloader. */
private fun looksLikeMedia(url: String): Boolean {
    val u = url.lowercase()
    return Regex("instagram\\.com/(p|reel|reels|tv)/").containsMatchIn(u) ||
        Regex("facebook\\.com/(watch|reel|share/(v|r)/|[^/?]+/videos/|photo|permalink|story\\.php)").containsMatchIn(u) ||
        Regex("(youtube\\.com/(watch|shorts)|youtu\\.be/)").containsMatchIn(u)
}

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val site = Site.valueOf(intent.getStringExtra("site") ?: Site.INSTAGRAM.name)
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
    val downloadHere: () -> Unit = {
        val logged = CookieStore.saveFromWebView(site, web?.settings?.userAgentString, clearWeb = false)
        if (!logged && !CookieStore.hasSession(site)) {
            Toast.makeText(ctx, "غير مسجّل دخول، سيُحاول التنزيل كمحتوى عام", Toast.LENGTH_SHORT).show()
        }
        val i = Intent(ctx, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, currentUrl)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        ctx.startActivity(i)
        onClose()
    }
    var showHelp by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = try { CookieStore.importFile(ctx, site, uri) } catch (_: Exception) { false }
            Toast.makeText(ctx, if (ok) "تم استيراد الجلسة" else "الملف لا يحتوي جلسة ${site.label}", Toast.LENGTH_LONG).show()
            if (ok) onClose()
        }
    }
    if (showPaste) AlertDialog(
        onDismissRequest = { showPaste = false },
        confirmButton = {
            TextButton(onClick = {
                val ok = CookieStore.importText(site, pasted)
                Toast.makeText(ctx, if (ok) "تم استيراد الجلسة" else "النص لا يحتوي جلسة ${site.label}", Toast.LENGTH_LONG).show()
                if (ok) { showPaste = false; onClose() }
            }) { Text("حفظ") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    pasted = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
                }) { Text("من الحافظة") }
                TextButton(onClick = { showPaste = false }) { Text("إلغاء") }
            }
        },
        title = { Text("لصق الكوكيز") },
        text = {
            OutlinedTextField(
                value = pasted, onValueChange = { pasted = it },
                modifier = Modifier.fillMaxWidth(), minLines = 4,
                label = { Text("JSON أو Netscape أو name=value; ...") }
            )
        }
    )
    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false },
        confirmButton = {
            TextButton(onClick = {
                showHelp = false
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(site.loginUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) { Toast.makeText(ctx, "لا يوجد متصفح", Toast.LENGTH_SHORT).show() }
            }) { Text("فتح المتصفح") }
        },
        dismissButton = { TextButton(onClick = { showHelp = false }) { Text("إلغاء") } },
        title = { Text("الدخول من متصفح خارجي") },
        text = {
            Text(
                "أنجح طريقة مجانية الآن:\n" +
                "1) ثبّت Firefox للأندرويد، وأضف إضافة Cookie-Editor.\n" +
                "2) سجّل دخولك في ${site.label} من Firefox (أو عبر فيسبوك).\n" +
                "3) افتح Cookie-Editor ← Export ← JSON (يُنسخ للحافظة).\n" +
                "4) ارجع هنا واضغط \"لصق\" ثم \"من الحافظة\" ثم \"حفظ\"."
            )
        }
    )

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF6C4DF6))) {
        Scaffold(floatingActionButton = {
            if (looksLikeMedia(currentUrl)) ExtendedFloatingActionButton(onClick = downloadHere) { Text("تنزيل هذا الفيديو") }
        }, topBar = {
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
                        val ok = CookieStore.saveFromWebView(site, web?.settings?.userAgentString, clearWeb = false)
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showHelp = true }) { Text("متصفح خارجي") }
                    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("ملف") }
                    OutlinedButton(onClick = { showPaste = true }) { Text("لصق") }
                }
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

                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        if (url != null) currentUrl = url
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        // like Snaptube: browsing while logged in keeps the downloader session fresh
                                        try { CookieStore.saveFromWebView(site, view?.settings?.userAgentString, clearWeb = false) } catch (_: Throwable) {}
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

private const val CHROME_SHIM = """
(function(){
  try{ if(!window.chrome){ window.chrome={runtime:{},app:{isInstalled:false}}; } }catch(e){}
  try{ Object.defineProperty(Navigator.prototype,'webdriver',{get:function(){return false;},configurable:true}); }catch(e){}
  try{
    var uad = navigator.userAgentData;
    if (uad) {
      var m = (navigator.userAgent.match(/Chrome\\/(\\d+)/)||[])[1] || '120';
      var brands = [{brand:'Chromium',version:m},{brand:'Google Chrome',version:m},{brand:'Not.A/Brand',version:'99'}];
      Object.defineProperty(Object.getPrototypeOf(uad),'brands',{get:function(){return brands;},configurable:true});
    }
  }catch(e){}
  if (window.top !== window) return;
  var tries = 0;
  var btnRe = /^(decline optional cookies|allow all cookies|allow essential and optional cookies|accept all|accept)$/i;
  function clickConsent(){
    var els = document.querySelectorAll('button,[role="button"],a');
    var allow = null;
    for (var i=0;i<els.length;i++){
      var t=(els[i].innerText||els[i].textContent||'').trim();
      if(/^decline optional cookies$/i.test(t)){ els[i].click(); return true; }
      if(!allow && btnRe.test(t)) allow = els[i];
    }
    if(allow){ allow.click(); return true; }
    return false;
  }
  function killScrim(){
    var divs = document.querySelectorAll('div');
    for (var i=0;i<divs.length;i++){
      var d=divs[i], cs;
      try{ cs=getComputedStyle(d); }catch(e){ continue; }
      if(cs.position!=='fixed') continue;
      var r=d.getBoundingClientRect();
      if(r.width < window.innerWidth*0.9 || r.height < window.innerHeight*0.9) continue;
      if(d.querySelector('input,button,form,a')) continue;
      if((d.innerText||'').trim().length > 3) continue;
      var bg=cs.backgroundColor||'';
      if(bg && bg!=='rgba(0, 0, 0, 0)' && bg!=='transparent'){ d.style.setProperty('display','none','important'); }
    }
    if(document.documentElement) document.documentElement.style.setProperty('overflow','auto','important');
    if(document.body) document.body.style.setProperty('overflow','auto','important');
  }
  var iv=setInterval(function(){
    tries++;
    try{ clickConsent(); killScrim(); }catch(e){}
    if(tries>60) clearInterval(iv);
  },500);
})();
"""

private fun desktopUrl(site: Site) = when (site) {
    Site.FACEBOOK -> "https://www.facebook.com/login.php"
    else -> site.loginUrl
}
