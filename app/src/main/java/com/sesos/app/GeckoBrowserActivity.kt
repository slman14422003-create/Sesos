package com.sesos.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension

/** Shared GeckoView runtime + the bridge to the built-in extension (assets/sesos-ext). */
object GeckoBridge {
    val url = MutableStateFlow("")
    @Volatile private var runtime: GeckoRuntime? = null
    private var extensionRequested = false

    fun runtime(ctx: Context): GeckoRuntime =
        runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(ctx.applicationContext).also { runtime = it }
        }

    private val delegate = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender
        ): GeckoResult<Any>? {
            try { handle(message) } catch (e: Throwable) { Log.e("Sesos", "gecko message", e) }
            return null
        }
    }

    fun ensureExtension(ctx: Context) {
        val rt = runtime(ctx)
        if (extensionRequested) return
        extensionRequested = true
        rt.webExtensionController
            .ensureBuiltIn("resource://android/assets/sesos-ext/", "sesos@sesos.app")
            .accept(
                { ext -> ext?.setMessageDelegate(delegate, "browser") },
                { e -> Log.e("Sesos", "extension install failed", e) }
            )
    }

    private fun handle(message: Any) {
        val o = message as? JSONObject ?: return
        when (o.optString("type")) {
            "url" -> {
                url.value = o.optString("url")
                val ua = o.optString("ua")
                if (ua.isNotBlank()) CookieStore.saveUserAgent(ua)
            }
            "cookies" -> saveCookies(o.optJSONArray("cookies") ?: return)
        }
    }

    /** Writes a session for every site whose login cookies are present (never overwrites with an empty one). */
    private fun saveCookies(all: JSONArray) {
        for (site in Site.entries) {
            val mine = JSONArray()
            var hasSession = false
            for (i in 0 until all.length()) {
                val c = all.optJSONObject(i) ?: continue
                val domain = c.optString("domain")
                if (site.domainKeys.none { domain.contains(it) }) continue
                mine.put(c)
                if (c.optString("name") in site.sessionCookies) hasSession = true
            }
            if (hasSession) CookieStore.importText(site, mine.toString())
        }
    }
}

class GeckoBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val site = Site.valueOf(intent.getStringExtra("site") ?: Site.INSTAGRAM.name)
        GeckoBridge.url.value = ""
        setContent { GeckoScreen(site) { finish() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeckoScreen(site: Site, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val runtime = remember { GeckoBridge.runtime(ctx) }
    LaunchedEffect(Unit) { GeckoBridge.ensureExtension(ctx) }
    val session = remember {
        GeckoSession().also {
            it.open(runtime)
            it.loadUri(site.loginUrl)
        }
    }
    DisposableEffect(session) { onDispose { session.close() } }

    val url by GeckoBridge.url.collectAsState()
    val sessions by CookieStore.sessions.collectAsState()
    var desktop by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF6C4DF6))) {
        Scaffold(
            floatingActionButton = {
                if (looksLikeMedia(url)) ExtendedFloatingActionButton(onClick = {
                    if (site.id !in sessions) {
                        Toast.makeText(ctx, "غير مسجّل دخول، سيُحاول التنزيل كمحتوى عام", Toast.LENGTH_SHORT).show()
                    }
                    ctx.startActivity(
                        Intent(ctx, MainActivity::class.java).apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                    )
                    onClose()
                }) { Text("تنزيل هذا الفيديو") }
            },
            topBar = {
                TopAppBar(
                    title = { Text(site.label) },
                    actions = {
                        Text(
                            if (site.id in sessions) "✓ مسجّل" else "غير مسجّل",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        TextButton(onClick = onClose) { Text("تم") }
                    }
                )
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { session.goBack() }) { Text("رجوع") }
                    TextButton(onClick = { session.reload() }) { Text("تحديث") }
                    TextButton(onClick = {
                        desktop = !desktop
                        session.settings.userAgentMode =
                            if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                        session.settings.viewportMode =
                            if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                        session.reload()
                    }) { Text(if (desktop) "جوال" else "كمبيوتر") }
                    TextButton(onClick = {
                        ctx.startActivity(Intent(ctx, LoginActivity::class.java).putExtra("site", site.name))
                    }) { Text("WebView") }
                }
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { c -> GeckoView(c).also { it.setSession(session) } }
                )
            }
        }
    }
}
