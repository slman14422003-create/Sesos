package com.sesos.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesos.app.CookieStore
import com.sesos.app.Engine
import com.sesos.app.GeckoBrowserActivity
import com.sesos.app.MainViewModel
import com.sesos.app.R
import com.sesos.app.Site

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val engine by Engine.state.collectAsStateWithLifecycle()
    val sessions by CookieStore.sessions.collectAsStateWithLifecycle()
    var importSite by remember { mutableStateOf<Site?>(null) }
    val okText = stringResource(R.string.session_imported)
    val inset = LocalBarInset.current
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
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Throwable) { "" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = inset),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }

        item {
            AccountsCard(
                sessions = sessions,
                onLogin = { site ->
                    ctx.startActivity(Intent(ctx, GeckoBrowserActivity::class.java).putExtra("site", site.name))
                },
                onImport = { site -> importSite = site; picker.launch(arrayOf("*/*")) },
                onLogout = { site -> CookieStore.logout(site) }
            )
        }

        item {
            SesosCard {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text(stringResource(R.string.engine_section), style = MaterialTheme.typography.titleLarge)
                    val (text, isError) = when (val e = engine) {
                        is Engine.State.Loading -> stringResource(R.string.engine_loading) to false
                        is Engine.State.Ready -> stringResource(R.string.engine_ready) to false
                        is Engine.State.Failed -> stringResource(R.string.engine_failed, e.msg) to true
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { vm.updateEngine() },
                        shape = RoundedCornerShape(Corners.pill),
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(stringResource(R.string.engine_update))
                    }
                }
            }
        }

        item {
            SesosCard {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.storage_section), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.storage_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SesosCard {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.about_section), style = MaterialTheme.typography.titleLarge)
                    Text("Sesos", style = MaterialTheme.typography.titleMedium)
                    if (version.isNotBlank()) Text(
                        stringResource(R.string.version_label, version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountsCard(
    sessions: Set<String>,
    onLogin: (Site) -> Unit,
    onImport: (Site) -> Unit,
    onLogout: (Site) -> Unit
) {
    SesosCard {
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
