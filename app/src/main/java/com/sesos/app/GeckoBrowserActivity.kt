package com.sesos.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.sesos.app.ui.Corners
import com.sesos.app.ui.CircleButton
import com.sesos.app.ui.Spacing
import com.sesos.app.ui.bounceClick
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
        enableEdgeToEdge()
        val site = Site.valueOf(intent.getStringExtra("site") ?: Site.INSTAGRAM.name)
        GeckoBridge.url.value = ""
        setContent { GeckoScreen(site) { finish() } }
    }
}

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
    val signedIn = site.id in sessions
    val notSignedText = stringResource(R.string.not_signed_toast)

    com.sesos.app.ui.SesosTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(site.label, style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (signedIn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            stringResource(if (signedIn) R.string.logged_in else R.string.logged_out),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(Corners.pill),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.bounceClick(onClose)
                ) {
                    Text(
                        stringResource(R.string.done_action),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md)
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                shape = RoundedCornerShape(Corners.pill),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleButton(
                        onClick = { session.goBack() },
                        background = Color.Transparent
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    CircleButton(
                        onClick = { session.reload() },
                        background = Color.Transparent
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.reload),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    ToolChip(
                        label = stringResource(if (desktop) R.string.mobile_mode else R.string.desktop_mode),
                        selected = desktop,
                        onClick = {
                            desktop = !desktop
                            session.settings.userAgentMode =
                                if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                            session.settings.viewportMode =
                                if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                            session.reload()
                        }
                    )
                    ToolChip(
                        label = "WebView",
                        selected = false,
                        onClick = {
                            ctx.startActivity(Intent(ctx, LoginActivity::class.java).putExtra("site", site.name))
                        }
                    )
                }
            }

            Spacer(Modifier.size(Spacing.sm))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { c -> GeckoView(c).also { it.setSession(session) } }
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = looksLikeMedia(url),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.lg),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(Corners.pill),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 8.dp,
                        modifier = Modifier.bounceClick {
                            if (!signedIn) Toast.makeText(ctx, notSignedText, Toast.LENGTH_SHORT).show()
                            ctx.startActivity(
                                Intent(ctx, ShareDownloadActivity::class.java).apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, url)
                                }
                            )
                        }
                    ) {
                        Row(
                            Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(
                                stringResource(R.string.download_this),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Corners.pill),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.bounceClick(onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
        )
    }
}
