package com.sesos.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.sesos.app.Engine
import com.sesos.app.R
import com.sesos.app.Yt
import kotlinx.coroutines.CancellationException

private const val UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/** Plays a YouTube result in-app (resolved by yt-dlp) so it can be checked before downloading. */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPreviewDialog(
    track: Yt.Track,
    onDismiss: () -> Unit,
    onDownloadAudio: () -> Unit,
    onDownloadVideo: () -> Unit
) {
    val ctx = LocalContext.current
    var attempt by remember(track.id) { mutableIntStateOf(0) }
    var loading by remember(track.id) { mutableStateOf(true) }
    var failed by remember(track.id) { mutableStateOf(false) }

    val player = remember(track.id) {
        val http = DefaultHttpDataSource.Factory().setUserAgent(UA).setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) loading = false
            }

            override fun onPlayerError(error: PlaybackException) {
                // this client's link was refused -> resolve again with the next client
                if (attempt + 1 < Engine.streamAttempts) attempt += 1 else { failed = true; loading = false }
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    LaunchedEffect(track.id, attempt) {
        loading = true
        try {
            val url = Engine.streamUrl(track.url, attempt)
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (attempt + 1 < Engine.streamAttempts) attempt += 1 else { failed = true; loading = false }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            shape = RoundedCornerShape(Corners.card),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    CircleButton(onClick = onDismiss, size = 36.dp) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(18.dp))
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(Corners.md))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        factory = { c ->
                            PlayerView(c).apply {
                                this.player = player
                                useController = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                keepScreenOn = true
                            }
                        }
                    )
                    if (loading && !failed) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 3.dp)
                            Text(stringResource(R.string.preview_loading), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (failed) {
                        Text(
                            stringResource(R.string.preview_failed),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(Spacing.lg)
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Button(onClick = onDownloadVideo, shape = RoundedCornerShape(Corners.pill), modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.preview_download_video), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onDownloadAudio,
                        shape = RoundedCornerShape(Corners.pill),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.preview_download_mp3), maxLines = 1) }
                }
            }
        }
    }
}
