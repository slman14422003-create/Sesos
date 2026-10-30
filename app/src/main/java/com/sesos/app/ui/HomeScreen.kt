package com.sesos.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesos.app.DownloadCenter
import com.sesos.app.DownloadItem
import com.sesos.app.DownloadService
import com.sesos.app.Engine
import com.sesos.app.HomeMode
import com.sesos.app.Kind
import com.sesos.app.MainViewModel
import com.sesos.app.MediaInfo
import com.sesos.app.R
import com.sesos.app.SearchTab
import com.sesos.app.SearchViewModel
import com.sesos.app.Yt

private data class Cat(val label: Int, val query: Int)

private val categories = listOf(
    Cat(R.string.cat_trending, R.string.q_trending),
    Cat(R.string.cat_arabic, R.string.q_arabic),
    Cat(R.string.cat_pop, R.string.q_pop),
    Cat(R.string.cat_hiphop, R.string.q_hiphop),
    Cat(R.string.cat_relax, R.string.q_relax),
    Cat(R.string.cat_quran, R.string.q_quran),
    Cat(R.string.cat_nasheed, R.string.q_nasheed),
    Cat(R.string.cat_workout, R.string.q_workout)
)

@Composable
fun HomeScreen(vm: MainViewModel, s: SearchViewModel) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val engine by Engine.state.collectAsStateWithLifecycle()
    val downloads by DownloadCenter.items.collectAsStateWithLifecycle()
    val inset = LocalBarInset.current

    BackHandler(enabled = s.mode != HomeMode.IDLE) { s.back() }

    val started = stringResource(R.string.download_started)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = inset),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item(key = "title") {
            Text("Sesos", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = Spacing.sm))
        }
        item(key = "searchbar") {
            SearchBar(
                value = s.text,
                onValue = { s.text = it },
                onPaste = { clipboard.getText()?.text?.let { s.text = it } },
                onClear = { s.clear() },
                onGo = { focus.clearFocus(); s.submit(vm) }
            )
        }

        if (engine is Engine.State.Loading) item {
            Text(
                stringResource(R.string.engine_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        (engine as? Engine.State.Failed)?.let { f ->
            item {
                Text(
                    stringResource(R.string.engine_failed, f.msg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        when (s.mode) {
            HomeMode.IDLE -> idleSection(s)
            HomeMode.LINK -> linkSection(vm, s)
            HomeMode.SEARCH -> searchSection(s, vm, downloads) { track ->
                startTrackDownload(ctx, track)
                vm.message = started
            }
        }
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValue: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onGo: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(Corners.pill),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(start = Spacing.lg, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            TextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.url_hint), style = MaterialTheme.typography.bodyMedium, maxLines = 1) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onGo() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            if (value.isNotEmpty()) {
                CircleButton(onClick = onClear, size = 32.dp) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.clear),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
            } else {
                TextButton(onClick = onPaste) {
                    Text(stringResource(R.string.paste), color = MaterialTheme.colorScheme.primary)
                }
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .bounceClick(onGo),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.analyze),
                    tint = Color.White
                )
            }
        }
    }
}

private fun LazyListScope.idleSection(s: SearchViewModel) {
    if (s.recents.isNotEmpty()) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.recent_searches), Modifier.weight(1f))
                TextButton(onClick = { s.clearRecents() }) { Text(stringResource(R.string.clear)) }
            }
        }
        items(s.recents, key = { "recent-$it" }) { q -> RecentRow(q, Modifier.animateItem()) { s.search(q) } }
    }
    item { SectionTitle(stringResource(R.string.browse)) }
    items(categories.chunked(2), key = { row -> "cat-" + row.first().query }) { pair ->
        val ctx = LocalContext.current
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            pair.forEach { c ->
                val idx = categories.indexOf(c)
                CategoryCard(Modifier.weight(1f).staggerIn(idx), stringResource(c.label), idx) { s.search(ctx.getString(c.query)) }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RecentRow(q: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corners.md))
            .bounceClick(onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            painterResource(R.drawable.ic_history),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(q, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategoryCard(modifier: Modifier, label: String, index: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg = when (index % 3) {
        0 -> scheme.primaryContainer
        1 -> scheme.surfaceContainerHigh
        else -> scheme.surfaceContainerHighest
    }
    val fg = if (index % 3 == 0) scheme.onPrimaryContainer else scheme.onSurface
    Surface(
        modifier = modifier.height(76.dp).bounceClick(onClick),
        shape = RoundedCornerShape(Corners.lg),
        color = bg,
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(painterResource(R.drawable.ic_music_note), contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun LazyListScope.linkSection(vm: MainViewModel, s: SearchViewModel) {
    if (s.searched.isNotBlank()) item {
        TextButton(onClick = { s.back() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.back_to_results))
        }
    }
    if (vm.loading) item {
        LinearProgressIndicator(
            Modifier.fillMaxWidth().clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
    vm.error?.let { e ->
        item { Text(e, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    vm.info?.let { info -> item { InfoCard(info, vm) } }
}

private fun LazyListScope.searchSection(
    s: SearchViewModel,
    vm: MainViewModel,
    downloads: List<DownloadItem>,
    onTrackDownload: (Yt.Track) -> Unit
) {
    val p = s.playlist
    if (p != null) {
        playlistSection(s, vm, p, downloads, onTrackDownload)
        return
    }
    item(key = "tabs") { TabsRow(s) }
    when (s.tab) {
        SearchTab.SONGS -> {
            if (s.songsLoading) item { Busy(stringResource(R.string.searching)) }
            s.songsError?.let { e -> item { ErrorText(stringResource(R.string.search_failed, e)) } }
            if (!s.songsLoading && s.songsError == null && s.songs.isEmpty()) item { EmptyText(stringResource(R.string.no_results)) }
            items(s.songs, key = { it.id }) { t ->
                TrackRow(t, Modifier.animateItem(), downloads.firstOrNull { it.url == t.url && it.kind == Kind.AUDIO }, { s.openOptions(t, vm) }) {
                    onTrackDownload(t)
                }
            }
        }
        SearchTab.PLAYLISTS -> {
            if (s.listsLoading) item { Busy(stringResource(R.string.searching)) }
            s.listsError?.let { e -> item { ErrorText(stringResource(R.string.search_failed, e)) } }
            if (!s.listsLoading && s.listsError == null && s.lists.isEmpty()) item { EmptyText(stringResource(R.string.no_results)) }
            items(s.lists, key = { it.id }) { l -> PlaylistRow(l, Modifier.animateItem()) { s.openPlaylist(l) } }
        }
    }
}

private fun LazyListScope.playlistSection(
    s: SearchViewModel,
    vm: MainViewModel,
    p: Yt.Playlist,
    downloads: List<DownloadItem>,
    onTrackDownload: (Yt.Track) -> Unit
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CircleButton(onClick = { s.back() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(p.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    item {
        val ctx = LocalContext.current
        val started = stringResource(R.string.download_started)
        Button(
            onClick = {
                DownloadService.start(
                    ctx,
                    MediaInfo(p.url, p.title, p.thumb, p.channel, 0, emptyList(), false, true, null),
                    Kind.AUDIO, null, true
                )
                vm.message = started
            },
            shape = RoundedCornerShape(Corners.pill),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.download_playlist_mp3))
        }
    }
    if (s.tracksLoading) item { Busy(stringResource(R.string.playlist_loading)) }
    s.tracksError?.let { e -> item { ErrorText(stringResource(R.string.search_failed, e)) } }
    if (!s.tracksLoading && s.tracksError == null && s.tracks.isEmpty()) item { EmptyText(stringResource(R.string.no_results)) }
    items(s.tracks, key = { it.id }) { t ->
        TrackRow(t, Modifier.animateItem(), downloads.firstOrNull { it.url == t.url && it.kind == Kind.AUDIO }, { s.openOptions(t, vm) }) {
            onTrackDownload(t)
        }
    }
}

@Composable
private fun TabsRow(s: SearchViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corners.pill))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        SearchTab.entries.forEach { t ->
            val sel = s.tab == t
            val bg by animateColorAsState(
                if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, label = "tabBg"
            )
            Box(
                Modifier
                    .weight(1f)
                    .bounceClick { s.selectTab(t) }
                    .clip(RoundedCornerShape(Corners.pill))
                    .background(bg)
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(if (t == SearchTab.SONGS) R.string.tab_songs else R.string.tab_playlists),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrackRow(t: Yt.Track, modifier: Modifier, state: DownloadItem?, onOpen: () -> Unit, onDownload: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Corners.lg),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().bounceClick(onOpen).padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Thumb(t.thumb, 64.dp, R.drawable.ic_music_note)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(t.channel, t.duration.takeIf { it > 0 }?.let { fmtDuration(it) }).joinToString(" • ")
                if (sub.isNotBlank()) Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DownloadAction(state, onDownload)
        }
    }
}

@Composable
private fun PlaylistRow(l: Yt.Playlist, modifier: Modifier, onOpen: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Corners.lg),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().bounceClick(onOpen).padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Thumb(l.thumb, 64.dp, R.drawable.ic_playlist)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(l.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    l.channel,
                    l.count.takeIf { it > 0 }?.let { stringResource(R.string.tracks_count, it) }
                ).joinToString(" • ")
                if (sub.isNotBlank()) Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                painterResource(R.drawable.ic_playlist),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = Spacing.md).size(22.dp)
            )
        }
    }
}

@Composable
private fun Busy(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(
            Modifier.fillMaxWidth().clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 6, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxl)
    )
}
