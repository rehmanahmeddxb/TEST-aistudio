package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StudioAmber
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioDark
import com.example.ui.theme.StudioTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Branded AMS startup splash. It is rendered *over* the already-initializing studio so it never
 * delays app startup; the animation is short (~1.2s) and then fades into the studio.
 */
@Composable
fun AmsSplashScreen(onFinished: () -> Unit) {
    val logoScale = remember { Animatable(0.85f) }
    val logoAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(18f) }
    val sloganAlpha = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    // Subtle continuous glow pulse on the badge.
    val glowTransition = rememberInfiniteTransition(label = "splash_glow")
    val glow by glowTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    LaunchedEffect(Unit) {
        // 1. Logo scales 85% -> 100% and fades in.
        launch { logoScale.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing)) }
        launch { logoAlpha.animateTo(1f, animationSpec = tween(450)) }
        // 2. "AMS" fades / slides in.
        delay(260)
        launch { titleAlpha.animateTo(1f, animationSpec = tween(360)) }
        launch { titleOffsetY.animateTo(0f, animationSpec = tween(360)) }
        // 3. Slogan fades in.
        delay(180)
        sloganAlpha.animateTo(1f, animationSpec = tween(300))
        // 4. Brief hold, then fade out into the studio.
        delay(320)
        overlayAlpha.animateTo(0f, animationSpec = tween(260))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioDark)
            .alpha(overlayAlpha.value),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AmsLogoMark(
                scale = logoScale.value,
                alpha = logoAlpha.value,
                glowAlpha = glow
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "AMS",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 6.sp,
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .offset(y = titleOffsetY.value.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create. React. Record.",
                color = StudioTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                modifier = Modifier.alpha(sloganAlpha.value)
            )
        }
    }
}

/** Clean, modern studio mark: the letters A + M + S combined into a single badge. */
@Composable
private fun AmsLogoMark(
    scale: Float,
    alpha: Float,
    glowAlpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(108.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF123048), Color(0xFF0E1016))
                )
            )
            .border(1.5.dp, StudioCyan.copy(alpha = 0.55f), RoundedCornerShape(28.dp))
            .border(1.dp, StudioCyan.copy(alpha = glowAlpha), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", color = StudioCyan, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text(
                "M",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.offset(x = (-3).dp)
            )
            Text(
                "S",
                color = StudioAmber,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.offset(x = (-6).dp)
            )
        }
    }
}
