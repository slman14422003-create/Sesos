package com.sesos.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
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

    var showSplash by rememberSaveable { mutableStateOf(true) }
    val reveal = remember { Animatable(if (showSplash) 0f else 1f) }
    val scope = rememberCoroutineScope()

    SesosTheme {
        val density = LocalDensity.current
        val imeOpen = WindowInsets.ime.getBottom(density) > 0
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // the whole app eases in behind the splash (slight zoom + fade)
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val p = reveal.value
                    alpha = 0.35f + 0.65f * p
                    scaleX = 0.97f + 0.03f * p
                    scaleY = 0.97f + 0.03f * p
                }
            ) {
                CompositionLocalProvider(LocalBarInset provides navBottom + 100.dp) {
                    Box(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                (fadeIn(tween(280, delayMillis = 70, easing = FastOutSlowInEasing)) +
                                    slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { dir * it / 8 } +
                                    scaleIn(tween(340, easing = FastOutSlowInEasing), initialScale = 0.97f))
                                    .togetherWith(
                                        fadeOut(tween(160)) +
                                            slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -dir * it / 10 }
                                    )
                                    .using(SizeTransform(clip = false))
                            },
                            label = "tab"
                        ) { t ->
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

                AnimatedVisibility(
                    visible = !imeOpen && !showSplash,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(300)),
                    exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(160))
                ) {
                    FloatingNavBar(
                        selected = tab,
                        busy = downloads.any { it.status == Status.RUNNING },
                        onSelect = { tab = it },
                        modifier = Modifier.navigationBarsPadding().padding(bottom = 14.dp)
                    )
                }
            }

            if (showSplash) {
                SplashOverlay(
                    onRevealStart = { scope.launch { reveal.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) } },
                    onDone = { showSplash = false }
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
