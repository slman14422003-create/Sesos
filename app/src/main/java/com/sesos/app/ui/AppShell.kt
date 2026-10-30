package com.sesos.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesos.app.DownloadCenter
import com.sesos.app.MainViewModel
import com.sesos.app.R
import com.sesos.app.SearchViewModel
import com.sesos.app.Status

enum class NavTab(val label: Int) {
    HOME(R.string.nav_home),
    LIBRARY(R.string.nav_library),
    SETTINGS(R.string.nav_settings)
}

@Composable
fun AppShell(vm: MainViewModel, search: SearchViewModel) {
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(NavTab.HOME) }
    val downloads by DownloadCenter.items.collectAsStateWithLifecycle()

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }
    BackHandler(enabled = tab != NavTab.HOME) { tab = NavTab.HOME }

    SesosTheme {
        val density = LocalDensity.current
        val imeOpen = WindowInsets.ime.getBottom(density) > 0
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CompositionLocalProvider(LocalBarInset provides navBottom + 100.dp) {
                Box(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                    Crossfade(targetState = tab, label = "tab") { t ->
                        when (t) {
                            NavTab.HOME -> HomeScreen(vm, search)
                            NavTab.LIBRARY -> LibraryScreen()
                            NavTab.SETTINGS -> SettingsScreen(vm)
                        }
                    }
                }
            }

            SnackbarHost(
                snack,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp)
            )

            if (!imeOpen) {
                FloatingNavBar(
                    selected = tab,
                    busy = downloads.any { it.status == Status.RUNNING },
                    onSelect = { tab = it },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)
                )
            }
        }
    }
}

@Composable
fun FloatingNavBar(
    selected: NavTab,
    busy: Boolean,
    onSelect: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Corners.pill),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTab.entries.forEach { t ->
                NavItem(t, t == selected, badge = busy && t == NavTab.LIBRARY) { onSelect(t) }
            }
        }
    }
}

@Composable
private fun tabIcon(t: NavTab): Painter = when (t) {
    NavTab.HOME -> rememberVectorPainter(Icons.Filled.Home)
    NavTab.LIBRARY -> painterResource(R.drawable.ic_folder)
    NavTab.SETTINGS -> rememberVectorPainter(Icons.Filled.Settings)
}

@Composable
private fun NavItem(tab: NavTab, selected: Boolean, badge: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "navBg"
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navFg"
    )
    Row(
        Modifier
            .bounceClick(onClick)
            .clip(CircleShape)
            .background(bg)
            .animateContentSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Icon(
                painter = tabIcon(tab),
                contentDescription = stringResource(tab.label),
                tint = fg,
                modifier = Modifier.size(24.dp)
            )
            if (badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(tab.label),
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                maxLines = 1
            )
        }
    }
}
