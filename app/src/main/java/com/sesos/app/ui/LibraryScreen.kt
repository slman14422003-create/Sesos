package com.sesos.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.util.Size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesos.app.DownloadCenter
import com.sesos.app.Kind
import com.sesos.app.Library
import com.sesos.app.R
import com.sesos.app.SavedFile
import com.sesos.app.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen() {
    val ctx = LocalContext.current
    val downloads by DownloadCenter.items.collectAsStateWithLifecycle()
    var files by remember { mutableStateOf<List<SavedFile>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var toDelete by remember { mutableStateOf<SavedFile?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val inset = LocalBarInset.current

    val doneCount = downloads.count { it.status == Status.DONE }
    LaunchedEffect(doneCount, tick) {
        files = withContext(Dispatchers.IO) { Library.load(ctx) }
        loaded = true
    }

    val active = downloads.filter { it.status != Status.DONE }
    val shown = when (filter) {
        1 -> files.filter { it.kind == Kind.AUDIO }
        2 -> files.filter { it.kind == Kind.VIDEO }
        3 -> files.filter { it.kind == Kind.IMAGE }
        else -> files
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = inset),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item(key = "title") {
            Text(
                stringResource(R.string.lib_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }

        if (active.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.lib_active)) }
            items(active, key = { it.id }) { d -> DownloadRow(d, Modifier.animateItem()) }
        }

        item(key = "filters") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val labels = listOf(R.string.filter_all, R.string.filter_audio, R.string.filter_video, R.string.filter_images)
                labels.forEachIndexed { i, l ->
                    FilterPill(stringResource(l), filter == i) { filter = i }
                }
            }
        }

        if (loaded && shown.isEmpty()) item {
            Column(
                Modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(56.dp)
                )
                Text(stringResource(R.string.lib_empty), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.lib_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(shown, key = { it.uri.toString() }) { f ->
            FileRow(
                f,
                Modifier.animateItem(),
                onOpen = { ctx.openUri(f.uri, f.mime) },
                onShare = { ctx.shareUri(f.uri, f.mime) },
                onDelete = { toDelete = f }
            )
        }
    }

    toDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_msg, f.title)) },
            confirmButton = {
                TextButton(onClick = {
                    Library.delete(ctx, f)
                    toDelete = null
                    tick++
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Corners.pill),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.bounceClick(onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        )
    }
}

@Composable
private fun FileRow(f: SavedFile, modifier: Modifier, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
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
            FileThumb(f, 60.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(f.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val type = when (f.kind) {
                    Kind.AUDIO -> stringResource(R.string.filter_audio)
                    Kind.VIDEO -> stringResource(R.string.filter_video)
                    Kind.IMAGE -> stringResource(R.string.filter_images)
                }
                Text(
                    type + " • " + Formatter.formatShortFileSize(ctx, f.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CircleButton(onClick = onShare, size = 36.dp) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share), modifier = Modifier.size(18.dp))
            }
            CircleButton(onClick = onDelete, size = 36.dp) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FileThumb(f: SavedFile, size: Dp) {
    val ctx = LocalContext.current
    val placeholder = when (f.kind) {
        Kind.AUDIO -> R.drawable.ic_music_note
        Kind.VIDEO -> R.drawable.ic_video
        Kind.IMAGE -> R.drawable.ic_image
    }
    if (f.kind == Kind.IMAGE) {
        Thumb(f.uri, size, placeholder)
        return
    }
    val bmp by produceState<Bitmap?>(ThumbCache.get(f.uri), f.uri) {
        if (value == null) value = withContext(Dispatchers.IO) { loadThumb(ctx, f.uri)?.also { ThumbCache.put(f.uri, it) } }
    }
    val b = bmp
    if (b == null) {
        Thumb(null, size, placeholder)
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(Corners.sm))) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun loadThumb(ctx: Context, uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT < 29) return null
    return try { ctx.contentResolver.loadThumbnail(uri, Size(256, 256), null) } catch (_: Throwable) { null }
}

/** Keeps recently decoded video/audio thumbnails so scrolling back never reloads them. */
private object ThumbCache {
    private val lru = android.util.LruCache<String, Bitmap>(96)
    fun get(u: Uri): Bitmap? = lru.get(u.toString())
    fun put(u: Uri, b: Bitmap) { lru.put(u.toString(), b) }
}
