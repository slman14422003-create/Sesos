package com.sesos.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sesos.app.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Continues the system splash seamlessly: same background, same icon, same 288dp canvas.
 * The "S" breathes, the download badge lifts and drops with a bounce, a ring ripples out of it,
 * then the whole logo zooms through the screen and fades away while the app eases in.
 */
@Composable
fun SplashOverlay(onRevealStart: () -> Unit, onDone: () -> Unit) {
    val bg = if (isSystemInDarkTheme()) Color.Black else Color.White
    val ringColor = Accent

    val sScale = remember { Animatable(1f) }
    val badgeLift = remember { Animatable(0f) }     // 0 = home position, 1 = raised
    val badgeScale = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            // the S takes a soft breath
            launch {
                sScale.animateTo(1.07f, tween(260, easing = FastOutSlowInEasing))
                sScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
            // badge lifts, then drops back with an elastic bounce (a download "landing")
            launch {
                delay(120)
                badgeLift.animateTo(1f, tween(200, easing = LinearOutSlowInEasing))
                launch { badgeScale.animateTo(1.18f, tween(120)); badgeScale.animateTo(1f, spring(0.35f, 300f)) }
                badgeLift.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 320f))
            }
            // ripple when the badge lands
            launch {
                delay(360)
                ring.animateTo(1f, tween(640, easing = LinearOutSlowInEasing))
            }
        }
        delay(120)
        onRevealStart()
        exit.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(1f - exit.value)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(288.dp)
                .graphicsLayer {
                    val z = 1f + exit.value * 0.55f
                    scaleX = z
                    scaleY = z
                },
            contentAlignment = Alignment.Center
        ) {
            // ripple ring centred on the badge (badge centre ~ 67.6% / 67.9% of the 432px canvas)
            Canvas(Modifier.fillMaxSize()) {
                val r = ring.value
                if (r > 0f && r < 1f) {
                    val c = Offset(size.width * 0.676f, size.height * 0.679f)
                    drawCircle(
                        color = ringColor.copy(alpha = 0.45f * (1f - r)),
                        radius = size.width * (0.075f + 0.38f * r),
                        center = c,
                        style = Stroke(width = size.width * 0.012f * (1f - r) + 1f)
                    )
                }
            }
            Image(
                painterResource(R.drawable.splash_s),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = sScale.value
                    scaleY = sScale.value
                }
            )
            Image(
                painterResource(R.drawable.splash_badge),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationY = -badgeLift.value * 26.dp.toPx()
                    scaleX = badgeScale.value
                    scaleY = badgeScale.value
                    // scale around the badge centre, not the canvas centre
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.676f, 0.679f)
                }
            )
        }
    }
}
