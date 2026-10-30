package com.sesos.app

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object Yt {
    data class Track(val id: String, val title: String, val channel: String?, val duration: Int) {
        val url: String get() = "https://www.youtube.com/watch?v=$id"
        val thumb: String get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
    }

    data class Playlist(val id: String, val title: String, val channel: String?, val count: Int, val thumb: String?) {
        val url: String get() = "https://www.youtube.com/playlist?list=$id"
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

    private suspend fun flat(target: String, end: Int): JSONArray = withContext(Dispatchers.IO) {
        Engine.ready()
        val req = YoutubeDLRequest(target)
        req.addOption("--flat-playlist")
        req.addOption("--dump-single-json")
        req.addOption("--no-warnings")
        req.addOption("--playlist-end", end.toString())
        val resp = YoutubeDL.getInstance().execute(req, "yt-" + System.nanoTime()) { _, _, _ -> }
        val out = resp.out.trim()
        val start = out.indexOf('{')
        if (start < 0) throw IllegalStateException("empty response")
        JSONObject(out.substring(start)).optJSONArray("entries") ?: JSONArray()
    }

    private fun tracks(arr: JSONArray): List<Track> {
        val res = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.str("id") ?: continue
            if (id.length != 11) continue
            val title = o.str("title") ?: continue
            if (title.startsWith("[Private") || title.startsWith("[Deleted")) continue
            res.add(Track(id, title, o.str("channel") ?: o.str("uploader"), o.optDouble("duration", 0.0).toInt()))
        }
        return res
    }

    suspend fun searchSongs(query: String): List<Track> {
        val q = query.replace('\n', ' ').trim()
        return tracks(flat("ytsearch30:$q", 30))
    }

    suspend fun searchPlaylists(query: String): List<Playlist> {
        val q = URLEncoder.encode(query.replace('\n', ' ').trim(), "UTF-8")
        val arr = flat("https://www.youtube.com/results?search_query=$q&sp=EgIQAw%253D%253D", 25)
        val res = ArrayList<Playlist>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val fromUrl = o.str("url")?.substringAfter("list=", "")?.substringBefore("&")?.takeIf { it.isNotBlank() }
            val id = fromUrl ?: o.str("id") ?: continue
            if (id.length < 10) continue
            val title = o.str("title") ?: continue
            val thumbs = o.optJSONArray("thumbnails")
            var thumb: String? = null
            if (thumbs != null) {
                for (k in 0 until thumbs.length()) {
                    thumb = thumbs.optJSONObject(k)?.str("url") ?: thumb
                }
            }
            if (thumb != null && thumb.startsWith("//")) thumb = "https:$thumb"
            res.add(Playlist(id, title, o.str("channel") ?: o.str("uploader"), o.optInt("playlist_count", 0), thumb))
        }
        return res
    }

    suspend fun playlistTracks(p: Playlist): List<Track> = tracks(flat(p.url, 200))
}
