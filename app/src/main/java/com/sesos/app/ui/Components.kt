package com.sesos.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sesos.app.DownloadCenter
import com.sesos.app.DownloadItem
import com.sesos.app.DownloadService
import com.sesos.app.Kind
import com.sesos.app.MainViewModel
import com.sesos.app.MediaInfo
import com.sesos.app.R
import com.sesos.app.Status

/** Space the screens leave at the bottom so the floating bar never covers content. */
val LocalBarInset = compositionLocalOf { 96.dp }

internal fun fmtDuration(sec: Int): String =
    if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60)
    else "%d:%02d".format(sec / 60, sec % 60)

@Composable
internal fun SesosCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(Corners.card),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = Spacing.sm))
}

@Composable
internal fun Thumb(model: Any?, size: Dp, placeholder: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(Corners.sm))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painterResource(placeholder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2)
            )
        }
    }
}

/** Round download button that turns into a progress ring, then a check mark. */
@Composable
internal fun DownloadAction(state: DownloadItem?, onDownload: () -> Unit) {
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        if (state != null && state.status == Status.RUNNING) {
            if (state.progress > 0f) CircularProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.size(30.dp),
                strokeWidth = 3.dp
            ) else CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
        } else if (state != null && state.status == Status.DONE) {
            Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(R.string.download_done),
                tint = MaterialTheme.colorScheme.tertiary
            )
        } else {
            CircleButton(
                onClick = onDownload,
                size = 40.dp,
                background = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    painterResource(R.drawable.ic_download),
                    contentDescription = stringResource(R.string.download_mp3),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

internal fun startTrackDownload(ctx: Context, t: com.sesos.app.Yt.Track) {
    DownloadService.start(
        ctx,
        MediaInfo(t.url, t.title, t.thumb, t.channel, t.duration, emptyList(), false, true, null),
        Kind.AUDIO, null, false
    )
}

@Composable
internal fun InfoCard(info: MediaInfo, vm: MainViewModel) {
    val pill = RoundedCornerShape(Corners.pill)
    SesosCard {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            info.thumbnail?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(Corners.md))
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    info.uploader,
                    info.duration.takeIf { it > 0 }?.let { fmtDuration(it) }
                ).joinToString(" • ")
                if (sub.isNotBlank()) Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (info.hasVideo) {
                Text(
                    stringResource(R.string.video),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (info.heights.isEmpty()) {
                        Button(onClick = { vm.download(Kind.VIDEO) }, shape = pill) {
                            Text(stringResource(R.string.best_quality))
                        }
                    }
                    info.heights.take(4).forEach { h ->
                        Surface(
                            shape = pill,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.bounceClick { vm.download(Kind.VIDEO, h) }
                        ) {
                            Text(
                                "${h}p",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                            )
                        }
                    }
                }
            }
            if (info.hasAudio) {
                OutlinedButton(
                    onClick = { vm.download(Kind.AUDIO) },
                    shape = pill,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) { Text(stringResource(R.string.download_audio)) }
            }
            if (info.imageUrl != null) {
                Button(onClick = { vm.download(Kind.IMAGE) }, shape = pill) {
                    Text(stringResource(R.string.download_image))
                }
            }
            if (info.hasVideo || info.hasAudio) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = vm.downloadAll, onCheckedChange = { vm.downloadAll = it })
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        stringResource(R.string.download_all),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun DownloadRow(d: DownloadItem) {
    SesosCard {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (d.thumbnail != null) Thumb(d.thumbnail, 56.dp, R.drawable.ic_music_note)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(d.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when (d.status) {
                    Status.RUNNING -> {
                        if (d.progress > 0f) LinearProgressIndicator(
                            progress = { d.progress },
                            modifier = Modifier.fillMaxWidth().clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) else LinearProgressIndicator(
                            Modifier.fillMaxWidth().clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${(d.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { DownloadService.cancel(d.id) }) {
                                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Status.DONE -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.download_done), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { DownloadCenter.remove(d.id) }) {
                            Text(stringResource(R.string.hide), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Status.ERROR -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            stringResource(R.string.download_failed, d.message ?: ""),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { DownloadCenter.remove(d.id) }) {
                                Text(stringResource(R.string.hide), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Status.CANCELED -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.download_canceled),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { DownloadCenter.remove(d.id) }) {
                            Text(stringResource(R.string.hide), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

internal fun Context.openUri(uri: Uri, mime: String?) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try { startActivity(i) } catch (_: Exception) {}
}

internal fun Context.shareUri(uri: Uri, mime: String?) {
    val send = Intent(Intent.ACTION_SEND).setType(mime ?: "*/*")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {}
}

internal fun Context.open(d: DownloadItem) {
    val uri = d.uri ?: return
    openUri(Uri.parse(uri), d.mime)
}
