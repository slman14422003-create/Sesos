package com.sesos.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class HomeMode { IDLE, LINK, SEARCH }
enum class SearchTab { SONGS, PLAYLISTS }

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("sesos_prefs", Context.MODE_PRIVATE)

    var text by mutableStateOf("")
    var mode by mutableStateOf(HomeMode.IDLE)
    var tab by mutableStateOf(SearchTab.SONGS)
    var searched by mutableStateOf("")

    var songs by mutableStateOf<List<Yt.Track>>(emptyList())
    var songsLoading by mutableStateOf(false)
    var songsError by mutableStateOf<String?>(null)

    var lists by mutableStateOf<List<Yt.Playlist>>(emptyList())
    var listsLoading by mutableStateOf(false)
    var listsError by mutableStateOf<String?>(null)

    var playlist by mutableStateOf<Yt.Playlist?>(null)
    var tracks by mutableStateOf<List<Yt.Track>>(emptyList())
    var tracksLoading by mutableStateOf(false)
    var tracksError by mutableStateOf<String?>(null)

    var preview by mutableStateOf<Yt.Track?>(null)

    var recents by mutableStateOf(loadRecents())

    private var songsSeq = 0
    private var listsSeq = 0
    private var tracksSeq = 0
    private var listsFor = ""

    private fun loadRecents(): List<String> =
        (prefs.getString("recents", "") ?: "").split('\n').filter { it.isNotBlank() }

    private fun addRecent(q: String) {
        recents = (listOf(q) + recents.filterNot { it.equals(q, ignoreCase = true) }).take(8)
        prefs.edit().putString("recents", recents.joinToString("\n")).apply()
    }

    fun clearRecents() {
        recents = emptyList()
        prefs.edit().remove("recents").apply()
    }

    private fun err(t: Throwable): String =
        (t.message ?: t.javaClass.simpleName).let { if (it.length > 220) it.takeLast(220) else it }

    /** The single bar accepts either a link (analysed) or free text (YouTube search). */
    fun submit(vm: MainViewModel) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (UrlUtils.extract(t) != null) {
            searched = ""
            playlist = null
            mode = HomeMode.LINK
            vm.url = t
            vm.analyze()
        } else {
            search(t)
        }
    }

    fun search(query: String) {
        val q = query.replace('\n', ' ').trim()
        if (q.isEmpty()) return
        text = q
        searched = q
        mode = HomeMode.SEARCH
        playlist = null
        tab = SearchTab.SONGS
        lists = emptyList()
        listsError = null
        listsFor = ""
        addRecent(q)
        val my = ++songsSeq
        songsLoading = true
        songsError = null
        songs = emptyList()
        viewModelScope.launch {
            try {
                val r = Yt.searchSongs(q)
                if (my == songsSeq) songs = r
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (my == songsSeq) songsError = err(t)
            } finally {
                if (my == songsSeq) songsLoading = false
            }
        }
    }

    fun selectTab(t: SearchTab) {
        tab = t
        if (t == SearchTab.PLAYLISTS && listsFor != searched && searched.isNotBlank()) loadLists(searched)
    }

    private fun loadLists(q: String) {
        val my = ++listsSeq
        listsFor = q
        listsLoading = true
        listsError = null
        lists = emptyList()
        viewModelScope.launch {
            try {
                val r = Yt.searchPlaylists(q)
                if (my == listsSeq) lists = r
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (my == listsSeq) listsError = err(t)
            } finally {
                if (my == listsSeq) listsLoading = false
            }
        }
    }

    fun openPlaylist(p: Yt.Playlist) {
        playlist = p
        tracks = emptyList()
        tracksError = null
        tracksLoading = true
        val my = ++tracksSeq
        viewModelScope.launch {
            try {
                val r = Yt.playlistTracks(p)
                if (my == tracksSeq) tracks = r
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (my == tracksSeq) tracksError = err(t)
            } finally {
                if (my == tracksSeq) tracksLoading = false
            }
        }
    }

    /** Opens the full options (video qualities, MP3, image) of one search result. */
    fun openOptions(t: Yt.Track, vm: MainViewModel) {
        mode = HomeMode.LINK
        vm.url = t.url
        vm.analyze()
    }

    fun clear() {
        text = ""
        mode = HomeMode.IDLE
        playlist = null
    }

    /** Returns true when the back press was consumed. */
    fun back(): Boolean = when {
        playlist != null -> { playlist = null; true }
        mode == HomeMode.LINK && searched.isNotBlank() -> { mode = HomeMode.SEARCH; text = searched; true }
        mode != HomeMode.IDLE -> { clear(); true }
        else -> false
    }
}
