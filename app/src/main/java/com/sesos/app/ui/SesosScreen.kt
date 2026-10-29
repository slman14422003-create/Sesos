package com.sesos.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sesos.app.DownloadCenter
import com.sesos.app.DownloadItem
import com.sesos.app.DownloadService
import com.sesos.app.CookieStore
import com.sesos.app.Engine
import com.sesos.app.Kind
import com.sesos.app.MainViewModel
import com.sesos.app.MediaInfo
import com.sesos.app.R
import com.sesos.app.Site
import com.sesos.app.Status

@Composable
fun SesosScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val items by DownloadCenter.items.collectAsStateWithLifecycle()
    val engine by Engine.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val sessions by CookieStore.sessions.collectAsStateWithLifecycle()
    var importSite by remember { mutableStateOf<Site?>(null) }
    val okText = stringResource(R.string.session_imported)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val site = importSite
        if (uri != null && site != null) {
            val ok = try { CookieStore.importFile(ctx, site, uri) } catch (_: Exception) { false }
            Toast.makeText(
                ctx,
                if (ok) okText else ctx.getString(R.string.session_missing, site.label),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }

    SesosTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) }
        ) { pad ->
            LazyColumn(
                modifier = Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item { Header(onUpdate = { vm.updateEngine() }) }

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

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        UrlBar(
                            value = vm.url,
                            onValue = { vm.url = it },
                            enabled = !vm.loading,
                            onPaste = { clipboard.getText()?.text?.let { vm.url = it } },
                            onGo = { vm.analyze() }
                        )
                        if (vm.loading) LinearProgressIndicator(
                            Modifier.fillMaxWidth().clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        vm.error?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                vm.info?.let { info -> item { InfoCard(info, vm) } }

                item {
                    AccountsCard(
                        sessions = sessions,
                        onLogin = { site ->
                            ctx.startActivity(
                                Intent(ctx, com.sesos.app.GeckoBrowserActivity::class.java).putExtra("site", site.name)
                            )
                        },
                        onImport = { site -> importSite = site; picker.launch(arrayOf("*/*")) },
                        onLogout = { site -> CookieStore.logout(site) }
                    )
                }

                if (items.isNotEmpty()) item {
                    Text(
                        stringResource(R.string.downloads),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                }
                items(items, key = { it.id }) { d -> DownloadRow(d) { ctx.open(d) } }

                item { Spacer(Modifier.height(Spacing.xxl)) }
            }
        }
    }
}

@Composable
private fun Header(onUpdate: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Sesos",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(Corners.pill),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.bounceClick(onUpdate)
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.engine_update),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun UrlBar(
    value: String,
    onValue: (String) -> Unit,
    enabled: Boolean,
    onPaste: () -> Unit,
    onGo: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(Corners.pill),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(start = Spacing.xxs, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.url_hint), style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            TextButton(onClick = onPaste) {
                Text(stringResource(R.string.paste), color = MaterialTheme.colorScheme.primary)
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .bounceClick { if (enabled) onGo() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.analyze),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(Corners.card),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}

@Composable
private fun InfoCard(info: MediaInfo, vm: MainViewModel) {
    val pill = RoundedCornerShape(Corners.pill)
    Card {
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
                    info.duration.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) }
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
private fun DownloadRow(d: DownloadItem, onOpen: () -> Unit) {
    Card {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        stringResource(R.string.download_done),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onOpen) { Text(stringResource(R.string.open)) }
                    TextButton(onClick = { DownloadCenter.remove(d.id) }) {
                        Text(stringResource(R.string.hide), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Status.ERROR -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        stringResource(R.string.download_failed, d.message ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
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

private fun android.content.Context.open(d: DownloadItem) {
    val uri = d.uri ?: return
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), d.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try { startActivity(i) } catch (_: Exception) {}
}

@Composable
private fun AccountsCard(
    sessions: Set<String>,
    onLogin: (Site) -> Unit,
    onImport: (Site) -> Unit,
    onLogout: (Site) -> Unit
) {
    Card {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(stringResource(R.string.accounts), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.accounts_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Site.entries.forEach { site ->
                val on = site.id in sessions
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            site.label.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(site.label, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (on) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.outline
                                    )
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                stringResource(if (on) R.string.logged_in else R.string.logged_out),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (on) {
                        TextButton(onClick = { onLogout(site) }) {
                            Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(onClick = { onImport(site) }) { Text(stringResource(R.string.import_session)) }
                        Button(
                            onClick = { onLogin(site) },
                            shape = RoundedCornerShape(Corners.pill),
                            contentPadding = PaddingValues(horizontal = Spacing.lg),
                            colors = ButtonDefaults.buttonColors()
                        ) { Text(stringResource(R.string.login)) }
                    }
                }
            }
        }
    }
}
