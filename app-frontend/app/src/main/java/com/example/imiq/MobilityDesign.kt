package com.example.imiq

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Legacy compatibility palette. New shared components use MaterialTheme as the
 * source of truth; this object remains only while existing screens migrate.
 */
object Mob {
    val bg = Color(0xFF07090D)
    val surface = Color(0xFF151A22)
    val surfaceHi = Color(0xFF1C232E)
    val glass = Color(0xFF1A212C)
    val border = Color(0x14FFFFFF)        // white @ 8%
    val borderHi = Color(0x29FFFFFF)      // white @ 16%

    val primary = Color(0xFF2DD4BF)       // teal-mint
    val onPrimary = Color(0xFF04130F)

    val textPrimary = Color(0xFFEEF2F6)
    val textSecondary = Color(0xFF9AA6B4)
    val textMuted = Color(0xFF5C6675)

    // Per-mode accents
    val bike = Color(0xFF34D399)
    val car = Color(0xFF38BDF8)
    val walk = Color(0xFF22D3EE)
    val transit = Color(0xFFA78BFA)

    val danger = Color(0xFFFB7185)

    val brandGradient = Brush.linearGradient(listOf(Color(0xFF2DD4BF), Color(0xFF22D3EE)))

    fun modeColor(mode: String): Color = when (mode.lowercase()) {
        "bike", "bike_pt" -> bike
        "car", "car_pt" -> car
        "foot", "walk" -> walk
        "pt" -> transit
        else -> primary
    }
}

@Composable
fun MobBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        content = content
    )
}

@Composable
fun MobGlassCard(
    modifier: Modifier = Modifier,
    color: Color = Mob.surface,
    radius: Dp = 16.dp,
    border: Color = Color.Transparent,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val container = if (color == Mob.surface) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else color
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(container)
            .then(if (border != Color.Transparent) Modifier.border(1.dp, border, RoundedCornerShape(radius)) else Modifier)
            .padding(padding),
        content = content
    )
}

@Composable
fun MobButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    color: Color = Mob.primary,
    enabled: Boolean = true
) {
    PrimaryActionButton(text, onClick, modifier.fillMaxWidth(), enabled, leadingIcon)
}

/** Animated circular match-score gauge with the percentage in the center. */
@Composable
fun ScoreRing(
    progress: Float,            // 0f..1f
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    stroke: Dp = 5.dp,
    animate: Boolean = true
) {
    val target = progress.coerceIn(0f, 1f)
    val anim by animateFloatAsState(
        targetValue = if (animate) target else 0f,
        animationSpec = tween(900),
        label = "ring"
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(
                color = color.copy(alpha = 0.16f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f, sweepAngle = 360f * anim, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
        }
        Text(
            "${(anim * 100).toInt()}",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(40.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Mob.borderHi)
    )
}

@Composable
fun MobSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
    )
}

@Composable
fun CircleGlyph(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 46.dp,
    bg: Color = tint.copy(alpha = 0.14f)
) {
    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(bg)
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.4f)), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(diameter * 0.5f))
    }
}
