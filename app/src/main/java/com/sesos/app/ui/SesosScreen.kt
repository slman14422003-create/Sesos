package com.sesos.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sesos.app.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SesosScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val items by DownloadCenter.items.collectAsStateWithLifecycle()
    val engine by Engine.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val sessions by CookieStore.sessions.collectAsStateWithLifecycle()
    var importSite by remember { mutableStateOf<Site?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val site = importSite
        if (uri != null && site != null) {
            val ok = try { CookieStore.importFile(ctx, site, uri) } catch (_: Exception) { false }
            Toast.makeText(ctx, if (ok) "تم استيراد الجلسة" else "الملف لا يحتوي جلسة ${site.label}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF6C4DF6))) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    title = { Text("Sesos") },
                    actions = { TextButton(onClick = { vm.updateEngine() }) { Text("تحديث المحرك") } }
                )
            }
        ) { pad ->
            LazyColumn(
                modifier = Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (engine is Engine.State.Loading) item {
                    Text("جارٍ تجهيز المحرك (أول تشغيل قد يستغرق بضع ثوانٍ)…")
                }
                (engine as? Engine.State.Failed)?.let { f -> item { Text("فشل تجهيز المحرك: ${f.msg}", color = MaterialTheme.colorScheme.error) } }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = vm.url, onValueChange = { vm.url = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("الصق الرابط أو شاركه إلى Sesos") },
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                clipboard.getText()?.text?.let { vm.url = it }
                            }) { Text("لصق") }
                            Button(onClick = { vm.analyze() }, enabled = !vm.loading) { Text("تحليل") }
                        }
                        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }

                vm.info?.let { info -> item { InfoCard(info, vm) } }

                item {
                    AccountsCard(
                        sessions = sessions,
                        onLogin = { site -> ctx.startActivity(Intent(ctx, com.sesos.app.GeckoBrowserActivity::class.java).putExtra("site", site.name)) },
                        onImport = { site -> importSite = site; picker.launch(arrayOf("*/*")) },
                        onLogout = { site -> CookieStore.logout(site) }
                    )
                }

                if (items.isNotEmpty()) item { Text("التنزيلات", style = MaterialTheme.typography.titleMedium) }
                items(items, key = { it.id }) { d -> DownloadRow(d) { ctx.open(d) } }
            }
        }
    }
}

@Composable
private fun InfoCard(info: MediaInfo, vm: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            info.thumbnail?.let {
                AsyncImage(
                    model = it, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp))
                )
            }
            Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(
                info.uploader,
                info.duration.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) }
            ).joinToString(" • ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall)

            if (info.hasVideo) {
                Text("فيديو")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (info.heights.isEmpty()) Button(onClick = { vm.download(Kind.VIDEO) }) { Text("أفضل جودة") }
                    info.heights.take(4).forEach { h ->
                        FilledTonalButton(
                            onClick = { vm.download(Kind.VIDEO, h) },
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text("${h}p") }
                    }
                }
            }
            if (info.hasAudio) {
                OutlinedButton(onClick = { vm.download(Kind.AUDIO) }) { Text("تحميل الصوت MP3") }
            }
            if (info.imageUrl != null) {
                Button(onClick = { vm.download(Kind.IMAGE) }) { Text("تحميل الصورة") }
            }
            if (info.hasVideo || info.hasAudio) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = vm.downloadAll, onCheckedChange = { vm.downloadAll = it })
                    Spacer(Modifier.width(8.dp))
                    Text("تحميل كل العناصر (ألبوم / قائمة)", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(d: DownloadItem, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(d.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when (d.status) {
                Status.RUNNING -> {
                    if (d.progress > 0f) LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("${(d.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { DownloadService.cancel(d.id) }) { Text("إلغاء") }
                    }
                }
                Status.DONE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تم التحميل ✓", Modifier.weight(1f))
                    TextButton(onClick = onOpen) { Text("فتح") }
                    TextButton(onClick = { DownloadCenter.remove(d.id) }) { Text("إخفاء") }
                }
                Status.ERROR -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("فشل: ${d.message ?: ""}", Modifier.weight(1f), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { DownloadCenter.remove(d.id) }) { Text("إخفاء") }
                }
                Status.CANCELED -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("أُلغي", Modifier.weight(1f))
                    TextButton(onClick = { DownloadCenter.remove(d.id) }) { Text("إخفاء") }
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("الحسابات", style = MaterialTheme.typography.titleMedium)
            Text(
                "تسجيل الدخول اختياري، ويلزم فقط عند ظهور رسالة login required. الجلسة تُحفظ داخل التطبيق ولا تُرسل لأي جهة. يُفضّل استخدام حساب ثانوي.",
                style = MaterialTheme.typography.bodySmall
            )
            Site.entries.forEach { site ->
                val on = site.id in sessions
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(site.label)
                        Text(if (on) "مسجّل ✓" else "غير مسجّل", style = MaterialTheme.typography.bodySmall)
                    }
                    if (on) {
                        TextButton(onClick = { onLogout(site) }) { Text("خروج وحذف") }
                    } else {
                        TextButton(onClick = { onImport(site) }) { Text("استيراد") }
                        Button(onClick = { onLogin(site) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("دخول") }
                    }
                }
            }
        }
    }
}
