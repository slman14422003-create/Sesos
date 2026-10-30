package com.sesos.app

import android.app.Application
import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Engine {
    sealed interface State {
        data object Loading : State
        data object Ready : State
        data class Failed(val msg: String) : State
    }

    val state = MutableStateFlow<State>(State.Loading)
    private val updating = MutableStateFlow(false)
    private val updateLock = Mutex()
    private var appCtx: Application? = null
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    /** YouTube clients, best first. android_vr/android_sdkless are rejected with 403 since Aug 2026. */
    private val ytClients = listOf(
        "default,-android_vr,-android_sdkless",
        "tv,web_safari,mweb",
        "ios,web_safari"
    )

    fun init(app: Application) {
        appCtx = app
        try {
            YoutubeDL.getInstance().init(app)
            FFmpeg.getInstance().init(app)
            state.value = State.Ready
        } catch (t: Throwable) {
            android.util.Log.e("Sesos", "engine init failed", t)
            val root = generateSequence(t) { it.cause }.last()
            state.value = State.Failed(
                t.javaClass.name + ": " + (t.message ?: "") +
                    (if (root !== t) " | cause: " + root.javaClass.name + ": " + (root.message ?: "") else "") +
                    " | " + (t.stackTrace.firstOrNull()?.toString() ?: "")
            )
        }
    }

    private suspend fun await() {
        val s = state.first { it !is State.Loading }
        if (s is State.Failed) throw IllegalStateException(s.msg)
        // never start a job in the middle of an engine update (files are being replaced)
        if (updating.value) withTimeoutOrNull(60_000) { updating.first { !it } }
    }

    suspend fun ready() = await()

    /** Update the bundled yt-dlp (sites change often, so this matters). Nightly = fastest fixes. */
    suspend fun updateYtDlp(ctx: Context): String = withContext(Dispatchers.IO) {
        val s = state.first { it !is State.Loading }
        if (s is State.Failed) throw IllegalStateException(s.msg)
        updateLock.withLock {
            updating.value = true
            try {
                val status = YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.NIGHTLY)
                ctx.getSharedPreferences("sesos_prefs", Context.MODE_PRIVATE)
                    .edit().putLong("ytdlp_updated_at", System.currentTimeMillis()).apply()
                status?.name ?: "UNKNOWN"
            } finally {
                updating.value = false
            }
        }
    }

    /** Silent background refresh, at most once every 12 hours. Never throws. */
    suspend fun autoUpdate(ctx: Context) {
        try {
            val last = ctx.getSharedPreferences("sesos_prefs", Context.MODE_PRIVATE).getLong("ytdlp_updated_at", 0L)
            if (System.currentTimeMillis() - last < 12L * 3600_000L) return
            updateYtDlp(ctx)
        } catch (t: Throwable) {
            android.util.Log.w("Sesos", "auto update skipped: " + t.message)
        }
    }

    /** Boils a yt-dlp traceback down to the one line that matters. */
    fun friendly(t: Throwable): String {
        val raw = t.message ?: t.javaClass.simpleName
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val noise = listOf("File \"", "^", "~", "return ", "Traceback", "[debug]", "raise ", "self.", "yield ")
        val pick = lines.lastOrNull { it.startsWith("ERROR:") }
            ?: lines.lastOrNull { l -> noise.none { l.startsWith(it) } }
            ?: raw
        return pick.removePrefix("ERROR:").trim()
            .replace(Regex("^\\[[A-Za-z:_]+\\]\\s*[\\w-]+:\\s*"), "")
            .let { if (it.length > 220) it.take(220) + "…" else it }
    }

    private fun isYoutube(url: String) = Regex("(youtube\\.com|youtu\\.be|music\\.youtube)", RegexOption.IGNORE_CASE).containsMatchIn(url)

    private fun needsEngineUpdate(msg: String) =
        Regex("403|forbidden|sign in|confirm you.re not a bot|unable to extract|nsig|player", RegexOption.IGNORE_CASE).containsMatchIn(msg)

    /** Adds the saved session of this site (if any) + consistent user agent + throttling. */
    private suspend fun applyAuth(req: YoutubeDLRequest, url: String) {
        val site = Site.forUrl(url) ?: return
        if (!CookieStore.hasSession(site)) return
        req.addOption("--cookies", CookieStore.file(site).absolutePath)
        CookieStore.userAgent()?.let { req.addOption("--user-agent", it) }
        req.addOption("--sleep-requests", "1")
        Throttle.awaitSlot()
    }

    private fun applyAuthBlocking(req: YoutubeDLRequest, url: String) {
        val site = Site.forUrl(url) ?: return
        if (!CookieStore.hasSession(site)) return
        req.addOption("--cookies", CookieStore.file(site).absolutePath)
        CookieStore.userAgent()?.let { req.addOption("--user-agent", it) }
        req.addOption("--sleep-requests", "1")
    }

    suspend fun analyze(url: String): MediaInfo = withContext(Dispatchers.IO) {
        await()
        try {
            val req = YoutubeDLRequest(url)
            req.addOption("--no-playlist")
            req.addOption("--no-warnings")
            req.addOption("--socket-timeout", "20")
            if (isYoutube(url)) req.addOption("--extractor-args", "youtube:player_client=" + ytClients[0])
            applyAuth(req, url)
            val info = YoutubeDL.getInstance().getInfo(req)
            val formats = info.formats.orEmpty()
            val vf = formats.filter { !it.vcodec.isNullOrEmpty() && it.vcodec != "none" }
            val heights = vf.map { it.height }.filter { it > 0 }.distinct().sortedDescending().take(6)
            val hasAudio = formats.any { !it.acodec.isNullOrEmpty() && it.acodec != "none" }
            val hasVideo = vf.isNotEmpty()
            MediaInfo(
                pageUrl = url,
                title = info.title ?: "Sesos",
                thumbnail = info.thumbnail,
                uploader = info.uploader,
                duration = info.duration,
                heights = heights,
                hasVideo = hasVideo,
                hasAudio = hasAudio || hasVideo,
                imageUrl = if (!hasVideo && !hasAudio) info.thumbnail else null
            )
        } catch (e: Exception) {
            // yt-dlp found no video (photo posts, direct image links...) -> try the page's image
            fallbackImage(url) ?: throw e
        }
    }

    private fun fallbackImage(url: String): MediaInfo? {
        val imageExt = Regex("\\.(jpe?g|png|webp|gif)(\\?.*)?$", RegexOption.IGNORE_CASE)
        if (imageExt.containsMatchIn(url)) {
            return MediaInfo(url, url.substringAfterLast('/').substringBefore('?'), url, null, 0,
                emptyList(), false, false, url)
        }
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", UA)
                connectTimeout = 15000; readTimeout = 15000
            }
            val html = conn.inputStream.use { it.readNBytes(1_000_000).toString(Charsets.UTF_8) }
            val img = metaContent(html, "og:image") ?: return null
            val title = metaContent(html, "og:title") ?: "Sesos"
            MediaInfo(url, title, img, null, 0, emptyList(), false, false, img)
        } catch (e: Exception) {
            null
        }
    }

    private fun metaContent(html: String, prop: String): String? {
        val a = Regex("<meta[^>]+property=[\"']$prop[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val b = Regex("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']$prop[\"']", RegexOption.IGNORE_CASE)
        val v = a.find(html)?.groupValues?.get(1) ?: b.find(html)?.groupValues?.get(1)
        return v?.replace("&amp;", "&")
    }

    /** Downloads into tmpDir and returns the finished files. Retries with other YouTube clients. */
    suspend fun download(
        id: String, info: MediaInfo, kind: Kind, height: Int?, all: Boolean, tmpDir: File,
        onProgress: (Float) -> Unit
    ): List<File> = withContext(Dispatchers.IO) {
        await()
        tmpDir.mkdirs()
        if (kind == Kind.IMAGE) {
            return@withContext listOf(downloadImage(info, tmpDir, onProgress))
        }

        // forward progress only when it moves by >=1% so the UI isn't flooded with updates
        var lastSent = -1f
        val progress: (Float) -> Unit = { p ->
            if (p >= 1f || p - lastSent >= 0.01f) { lastSent = p; onProgress(p) }
        }

        val clients: List<String?> = if (isYoutube(info.pageUrl)) ytClients else listOf(null)
        var lastError: Throwable? = null
        var updatedOnce = false
        var attempt = 0
        while (attempt < clients.size) {
            if (DownloadCenter.canceled.contains(id)) throw IllegalStateException("canceled")
            Site.forUrl(info.pageUrl)?.let { if (CookieStore.hasSession(it)) Throttle.awaitSlot() }
            val req = buildRequest(info, kind, height, all, tmpDir, clients[attempt])
            try {
                YoutubeDL.getInstance().execute(req, id) { p, _, _ ->
                    if (p >= 0f) progress((p / 100f).coerceIn(0f, 1f))
                }
                lastError = null
                break
            } catch (e: Exception) {
                if (DownloadCenter.canceled.contains(id)) throw e
                // A playlist with some unavailable items still yields the rest.
                if (all && outputs(tmpDir).isNotEmpty()) { lastError = null; break }
                lastError = e
                // first sign of a site change: pull the newest yt-dlp once, then retry the same client
                if (!updatedOnce && needsEngineUpdate(e.message ?: "")) {
                    updatedOnce = true
                    val ctx = appCtx
                    if (ctx != null && DownloadCenter.items.value.count { it.status == Status.RUNNING } <= 1) {
                        try { updateYtDlp(ctx); lastSent = -1f; continue } catch (_: Throwable) {}
                    }
                }
                attempt++
            }
        }
        lastError?.let { throw it }
        val files = outputs(tmpDir)
        if (files.isEmpty()) throw IllegalStateException("no output file")
        files
    }

    private fun buildRequest(
        info: MediaInfo, kind: Kind, height: Int?, all: Boolean, tmpDir: File, ytClient: String?
    ): YoutubeDLRequest {
        val req = YoutubeDLRequest(info.pageUrl)
        if (!all) req.addOption("--no-playlist")
        req.addOption("--no-mtime")
        req.addOption("--no-warnings")
        req.addOption("--retries", "5")
        req.addOption("--fragment-retries", "5")
        req.addOption("--extractor-retries", "3")
        req.addOption("--socket-timeout", "20")
        req.addOption("--concurrent-fragments", "4")
        if (ytClient != null) req.addOption("--extractor-args", "youtube:player_client=$ytClient")
        applyAuthBlocking(req, info.pageUrl)
        val name = if (all) "%(playlist_index)s - %(title).70s [%(id)s].%(ext)s" else "%(title).70s [%(id)s].%(ext)s"
        req.addOption("-o", File(tmpDir, name).absolutePath)
        if (kind == Kind.VIDEO) {
            val h = height?.let { "[height<=$it]" } ?: ""
            val progressiveFirst = Site.forUrl(info.pageUrl) == Site.INSTAGRAM
            val sel = if (progressiveFirst) "b$h/bv*$h+ba/b" else "bv*$h[ext=mp4]+ba[ext=m4a]/bv*$h+ba/b$h/b"
            req.addOption("-f", sel)
            req.addOption("--merge-output-format", "mp4")
        } else {
            req.addOption("-f", "bestaudio/best")
            req.addOption("-x")
            req.addOption("--audio-format", "mp3")
            req.addOption("--audio-quality", "0")
            req.addOption("--embed-metadata")
        }
        return req
    }

    private fun outputs(dir: File): List<File> = dir.listFiles().orEmpty().filter {
        it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.endsWith(".temp")
    }.sortedBy { it.name }

    private fun downloadImage(info: MediaInfo, tmpDir: File, onProgress: (Float) -> Unit): File {
        val src = info.imageUrl ?: throw IllegalStateException("no image url")
        val conn = (URL(src).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", UA)
            connectTimeout = 15000; readTimeout = 30000
        }
        val ext = when {
            conn.contentType?.contains("png") == true -> "png"
            conn.contentType?.contains("webp") == true -> "webp"
            conn.contentType?.contains("gif") == true -> "gif"
            else -> "jpg"
        }
        val out = File(tmpDir, UrlUtils.safeName(info.title) + "." + ext)
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(32 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        return out
    }
}
