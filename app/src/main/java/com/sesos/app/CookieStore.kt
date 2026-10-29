package com.sesos.app

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

enum class Site(
    val id: String,
    val label: String,
    val loginUrl: String,
    val hosts: List<String>,
    val cookieUrls: Map<String, String>,   // url to read from WebView -> cookie domain to write
    val sessionCookies: List<String>,      // any of these present = logged in
    val domainKeys: List<String>           // domains allowed in an imported cookies.txt
) {
    INSTAGRAM(
        "instagram", "Instagram", "https://www.instagram.com/accounts/login/",
        listOf("instagram.com"),
        mapOf("https://www.instagram.com" to ".instagram.com"),
        listOf("sessionid"), listOf("instagram.com")
    ),
    FACEBOOK(
        "facebook", "Facebook", "https://m.facebook.com/login",
        listOf("facebook.com", "fb.watch", "fb.com"),
        mapOf("https://www.facebook.com" to ".facebook.com", "https://m.facebook.com" to ".facebook.com"),
        listOf("c_user", "xs"), listOf("facebook.com", "fb.com")
    ),
    YOUTUBE(
        "youtube", "YouTube",
        "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F",
        listOf("youtube.com", "youtu.be"),
        mapOf(
            "https://www.youtube.com" to ".youtube.com",
            "https://accounts.google.com" to ".google.com",
            "https://www.google.com" to ".google.com"
        ),
        listOf("SAPISID", "__Secure-3PSID"), listOf("youtube.com", "google.com")
    );

    companion object {
        fun forUrl(url: String): Site? {
            val host = try { Uri.parse(url).host?.lowercase() } catch (_: Exception) { null } ?: return null
            return entries.firstOrNull { s -> s.hosts.any { host == it || host.endsWith(".$it") } }
        }
    }
}

object CookieStore {
    private lateinit var dir: File
    private lateinit var appCtx: Context
    val sessions = MutableStateFlow<Set<String>>(emptySet())

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        dir = File(appCtx.filesDir, "cookies").apply { mkdirs() }
        refresh()
    }

    fun file(site: Site) = File(dir, "${site.id}.txt")

    fun refresh() {
        sessions.value = Site.entries.filter { hasSession(it) }.map { it.id }.toSet()
    }

    fun hasSession(site: Site): Boolean {
        val f = file(site)
        if (!f.exists()) return false
        val text = f.readText()
        return site.sessionCookies.any { text.contains("\t$it\t") }
    }

    /** Reads the WebView session, writes a Netscape cookies.txt private to the app, then clears the WebView. */
    fun saveFromWebView(site: Site, webUa: String? = null): Boolean {
        val cm = CookieManager.getInstance()
        cm.flush()
        val exp = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        val seen = HashSet<String>()
        for ((url, domain) in site.cookieUrls) {
            cm.getCookie(url)?.split(";")?.forEach { part ->
                val kv = part.trim()
                val i = kv.indexOf('=')
                if (i > 0) {
                    val name = kv.substring(0, i)
                    val value = kv.substring(i + 1)
                    if (seen.add("$domain|$name")) sb.append("$domain\tTRUE\t/\tTRUE\t$exp\t$name\t$value\n")
                }
            }
        }
        val f = file(site)
        f.writeText(sb.toString())
        if (!hasSession(site)) { f.delete(); refresh(); return false }
        // keep the same browser fingerprint when yt-dlp uses this session
        appCtx.getSharedPreferences("sesos", Context.MODE_PRIVATE).edit()
            .putString("ua", webUa ?: WebSettings.getDefaultUserAgent(appCtx)).apply()
        cm.removeAllCookies(null)
        refresh()
        return true
    }

    /** Import a cookies.txt (keeps only lines of this site's own domains). */
    fun importFile(ctx: Context, site: Site, uri: Uri): Boolean {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return false
        val kept = text.lines().filter { line ->
            line.startsWith("# Netscape") ||
                (line.isNotBlank() && (!line.startsWith("#") || line.startsWith("#HttpOnly_")) &&
                    site.domainKeys.any { line.substringBefore('\t').contains(it) })
        }
        val out = (if (kept.firstOrNull()?.startsWith("# Netscape") == true) kept else listOf("# Netscape HTTP Cookie File") + kept)
            .joinToString("\n") + "\n"
        val f = file(site)
        f.writeText(out)
        if (!hasSession(site)) { f.delete(); refresh(); return false }
        refresh()
        return true
    }

    /** Accepts a Netscape cookies.txt text, or a plain Cookie header ("name=value; name2=value2"). */
    fun importText(site: Site, raw: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty()) return false
        val out = StringBuilder("# Netscape HTTP Cookie File\n")
        if (text.contains('\t')) {
            text.lines().forEach { line ->
                if (line.isNotBlank() && (!line.startsWith("#") || line.startsWith("#HttpOnly_")) &&
                    site.domainKeys.any { line.substringBefore('\t').contains(it) }) out.append(line).append('\n')
            }
        } else {
            val exp = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
            val domain = site.cookieUrls.values.first()
            text.removePrefix("Cookie:").trim().split(";").forEach { part ->
                val kv = part.trim()
                val i = kv.indexOf('=')
                if (i > 0) out.append("$domain\tTRUE\t/\tTRUE\t$exp\t${kv.substring(0, i)}\t${kv.substring(i + 1)}\n")
            }
        }
        val f = file(site)
        f.writeText(out.toString())
        if (!hasSession(site)) { f.delete(); refresh(); return false }
        refresh()
        return true
    }

    fun logout(site: Site) {
        file(site).delete()
        refresh()
    }

    fun userAgent(): String? =
        appCtx.getSharedPreferences("sesos", Context.MODE_PRIVATE).getString("ua", null)
}

/** Spaces out requests made with a logged-in session (bursts are what get accounts flagged). */
object Throttle {
    private val m = Mutex()
    private var last = 0L
    suspend fun awaitSlot(gapMs: Long = 3000) = m.withLock {
        val wait = last + gapMs - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        last = System.currentTimeMillis()
    }
}
