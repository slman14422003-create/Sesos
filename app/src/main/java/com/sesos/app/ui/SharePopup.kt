package com.sesos.app.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sesos.app.DownloadCenter
import com.sesos.app.DownloadItem
import com.sesos.app.DownloadService
import com.sesos.app.Kind
import com.sesos.app.MainActivity
import com.sesos.app.MainViewModel
import com.sesos.app.MediaInfo
import com.sesos.app.R
import com.sesos.app.Status

@Composable
fun SharePopup(vm: MainViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val items by DownloadCenter.items.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scrimSource = remember { MutableInteractionSource() }
    val sheetSource = remember { MutableInteractionSource() }

    val started = vm.popupStartedAt
    val item = if (started > 0L) items.firstOrNull { (it.id.substringBefore('-').toLongOrNull() ?: 0L) >= started } else null
    val info = vm.info
    val err = vm.error

    SesosTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(interactionSource = scrimSource, indication = null) { onClose() },
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(tween(Motion.Normal)) { it } + fadeIn(tween(Motion.Normal))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = sheetSource, indication = null) {},
                    shape = RoundedCornerShape(topStart = Corners.pill, topEnd = Corners.pill),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        Modifier.navigationBarsPadding().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 40.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Sesos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            CircleButton(onClick = onClose, size = 36.dp) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.close),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        when {
                            started > 0L -> ProgressBlock(item, onClose) { d -> ctx.open(d) }
                            err != null -> ErrorBlock(err, onClose) {
                                ctx.startActivity(Intent(ctx, MainActivity::class.java))
                                onClose()
                            }
                            info != null && !vm.loading -> OptionsBlock(info, vm)
                            else -> Busy(stringResource(R.string.analyzing))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Busy(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(
            Modifier.fillMaxWidth().clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun ErrorBlock(message: String, onClose: () -> Unit, onOpenApp: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = onOpenApp, shape = RoundedCornerShape(Corners.pill)) {
                Text(stringResource(R.string.open_app))
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}

@Composable
private fun OptionsBlock(info: MediaInfo, vm: MainViewModel) {
    val pill = RoundedCornerShape(Corners.pill)
    fun start(kind: Kind, h: Int? = null) {
        vm.popupStartedAt = System.currentTimeMillis()
        vm.download(kind, h)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            info.thumbnail?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(Corners.sm))
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(
                    info.uploader,
                    info.duration.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) }
                ).joinToString(" • ")
                if (sub.isNotBlank()) Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (info.hasVideo) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (info.heights.isEmpty()) {
                    Button(onClick = { start(Kind.VIDEO) }, shape = pill) { Text(stringResource(R.string.best_quality)) }
                }
                info.heights.take(4).forEach { h ->
                    Surface(
                        shape = pill,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.bounceClick { start(Kind.VIDEO, h) }
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
                onClick = { start(Kind.AUDIO) },
                shape = pill,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) { Text(stringResource(R.string.download_audio)) }
        }
        if (info.imageUrl != null) {
            Button(onClick = { start(Kind.IMAGE) }, shape = pill) { Text(stringResource(R.string.download_image)) }
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

@Composable
private fun ProgressBlock(item: DownloadItem?, onClose: () -> Unit, onOpen: (DownloadItem) -> Unit) {
    if (item == null) {
        Busy(stringResource(R.string.download_starting))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        when (item.status) {
            Status.RUNNING -> {
                if (item.progress > 0f) LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ) else LinearProgressIndicator(
                    Modifier.fillMaxWidth().clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${(item.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { DownloadService.cancel(item.id) }) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Status.DONE -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.download_done), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onOpen(item) }) { Text(stringResource(R.string.open)) }
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.close), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Status.ERROR -> {
                Text(
                    stringResource(R.string.download_failed, item.message ?: ""),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
            }
            Status.CANCELED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.download_canceled),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
            }
        }
    }
}
