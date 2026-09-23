package com.example.imiq

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Branded launch screen — logo glow + gradient wordmark + tagline, with a quick
 * entrance animation, then auto-advances to the app. First impression for the demo.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val s = LocalStrings.current
    var visible by remember { mutableStateOf(false) }

    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700, easing = LinearOutSlowInEasing),
        label = "appear"
    )

    // Soft pulsing halo behind the logo.
    val infinite = rememberInfiniteTransition(label = "halo")
    val haloScale by infinite.animateFloat(
        initialValue = 0.9f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "haloScale"
    )
    val haloAlpha by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "haloAlpha"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(1700)
        onDone()
    }

    MobBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Halo
                Box(
                    Modifier
                        .size(170.dp)
                        .scale(haloScale)
                        .alpha(haloAlpha)
                        .clip(CircleShape)
                        .background(Mob.primary)
                )
                Image(
                    painter = painterResource(id = R.drawable.imiq_logo),
                    contentDescription = "IMIQ",
                    modifier = Modifier
                        .size(118.dp)
                        .scale(0.8f + 0.2f * appear)
                        .alpha(appear)
                        .clip(RoundedCornerShape(28.dp))
                )
            }

            Spacer(Modifier.height(26.dp))

            Text(
                "IMIQ",
                style = TextStyle(brush = Mob.brandGradient),
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.alpha(appear)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                s.splashTagline,
                color = Mob.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(appear)
            )
        }

        // Subtle loading dots near the bottom.
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 64.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    val dot by infinite.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(700, easing = LinearOutSlowInEasing),
                            RepeatMode.Reverse,
                            initialStartOffset = androidx.compose.animation.core.StartOffset(i * 160)
                        ),
                        label = "dot$i"
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .alpha(dot)
                            .clip(CircleShape)
                            .background(Mob.primary)
                    )
                }
            }
        }
    }
}
