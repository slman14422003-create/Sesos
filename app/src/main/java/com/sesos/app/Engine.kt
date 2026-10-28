package com.sesos.app

import android.app.Application
import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    fun init(app: Application) {
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
    }

    /** Update the bundled yt-dlp (sites change often, so this matters). */
    suspend fun updateYtDlp(ctx: Context): String = withContext(Dispatchers.IO) {
        await()
        val status = YoutubeDL.getInstance().updateYoutubeDL(ctx, YoutubeDL.UpdateChannel.STABLE)
        status?.name ?: "UNKNOWN"
    }

    suspend fun analyze(url: String): MediaInfo = withContext(Dispatchers.IO) {
        await()
        try {
            val req = YoutubeDLRequest(url)
            req.addOption("--no-playlist")
            req.addOption("--no-warnings")
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

    /** Downloads into tmpDir and returns the finished files. */
    suspend fun download(
        id: String, info: MediaInfo, kind: Kind, height: Int?, all: Boolean, tmpDir: File,
        onProgress: (Float) -> Unit
    ): List<File> = withContext(Dispatchers.IO) {
        await()
        tmpDir.mkdirs()
        if (kind == Kind.IMAGE) {
            return@withContext listOf(downloadImage(info, tmpDir, onProgress))
        }
        val req = YoutubeDLRequest(info.pageUrl)
        if (!all) req.addOption("--no-playlist")
        req.addOption("--no-mtime")
        req.addOption("--no-warnings")
        val name = if (all) "%(playlist_index)s - %(title).70s [%(id)s].%(ext)s" else "%(title).70s [%(id)s].%(ext)s"
        req.addOption("-o", File(tmpDir, name).absolutePath)
        if (kind == Kind.VIDEO) {
            val h = height?.let { "[height<=$it]" } ?: ""
            req.addOption("-f", "bv*$h[ext=mp4]+ba[ext=m4a]/bv*$h+ba/b$h/b")
            req.addOption("--merge-output-format", "mp4")
        } else {
            req.addOption("-f", "bestaudio/best")
            req.addOption("-x")
            req.addOption("--audio-format", "mp3")
            req.addOption("--audio-quality", "0")
        }
        YoutubeDL.getInstance().execute(req, id) { progress, _, _ ->
            if (progress >= 0f) onProgress((progress / 100f).coerceIn(0f, 1f))
        }
        val files = tmpDir.listFiles().orEmpty().filter {
            it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.endsWith(".temp")
        }
        if (files.isEmpty()) throw IllegalStateException("no output file")
        files.sortedBy { it.name }
    }

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
