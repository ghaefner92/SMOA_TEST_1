package com.example.imiq

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A persistent card/3D scene. Changing mode updates its content without destroying
 * Filament resources. The questionnaire stores only the original answer in 1..7.
 */
@Composable
fun SwipeableValenceCard(
    sectionTitle: String,
    progressText: String,
    progressFraction: Float,
    title: String,
    question: String,
    selectedValue: Int?,
    transportMode: String? = null,
    de: Boolean,
    previousPreviewTitle: String?,
    nextPreviewTitle: String?,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onSelect: (Int) -> Unit,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(selectedValue == null || selectedValue in 1..7)
    var cardWidthPx by remember { mutableFloatStateOf(1f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var reveal by remember { mutableFloatStateOf(1f) }
    var animationLocked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val nextAllowed by rememberUpdatedState(canGoNext && selectedValue != null)
    val previousAllowed by rememberUpdatedState(canGoPrevious)
    val selectAnswer by rememberUpdatedState(onSelect)
    val goNext by rememberUpdatedState(onSwipeNext)
    val goPrevious by rememberUpdatedState(onSwipePrevious)
    val flingThreshold = with(LocalDensity.current) { 900.dp.toPx() }
    val dragFraction = (abs(dragOffset) / (cardWidthPx * 0.65f)).coerceIn(0f, 1f)
    val previewIsPrevious = dragOffset < -1f || (nextPreviewTitle == null && dragOffset <= 1f)
    val previewTitle = if (previewIsPrevious) previousPreviewTitle else nextPreviewTitle
    val progress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "valenceProgress",
    )
    val shape = RoundedCornerShape(28.dp)
    val surfaceTop = Color(0xFFFCFAF6)
    val surfaceBottom = Color(0xFFF4F0E9)
    val primaryText = Color(0xFF24262B)
    val secondaryText = Color(0xFF62656B)
    val accent = Color(0xFF8D84C6)

    fun settle(velocity: Float = 0f, cancelled: Boolean = false) {
        if (animationLocked) return
        val direction = if (cancelled) 0 else valenceSwipeDirection(
            offset = dragOffset,
            width = cardWidthPx,
            velocity = velocity,
            flingThreshold = flingThreshold,
            canGoNext = nextAllowed,
            canGoPrevious = previousAllowed,
        )
        animationLocked = true
        scope.launch {
            try {
                val motion = Animatable(dragOffset)
                if (direction == 0) {
                    motion.animateTo(
                        0f,
                        spring(dampingRatio = 0.86f, stiffness = 420f),
                        initialVelocity = velocity.coerceIn(-flingThreshold, flingThreshold),
                    ) { dragOffset = value }
                } else {
                    motion.animateTo(
                        direction * cardWidthPx * 1.2f,
                        tween(210, easing = FastOutLinearInEasing),
                    ) { dragOffset = value }
                    // Hide the content swap, then bring the same native scene back into view.
                    reveal = 0f
                    if (direction > 0) goNext() else goPrevious()
                    dragOffset = -direction * cardWidthPx * 0.08f
                    withFrameNanos { }
                    Animatable(0f).animateTo(
                        1f,
                        spring(dampingRatio = 1f, stiffness = 380f),
                    ) {
                        reveal = value
                        dragOffset = -direction * cardWidthPx * 0.08f * (1f - value)
                    }
                }
            } finally {
                dragOffset = 0f
                reveal = 1f
                animationLocked = false
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val compact = maxHeight < 520.dp
        val horizontalPadding = if (maxWidth < 350.dp) 16.dp else 22.dp
        if (previewTitle != null) {
            Card(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 0.95f + dragFraction * 0.05f
                    scaleY = scaleX
                    translationY = 10.dp.toPx() * (1f - dragFraction)
                    alpha = 0.55f + dragFraction * 0.45f
                },
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECE7F2)),
            ) {
                Column(Modifier.padding(horizontal = horizontalPadding, vertical = 24.dp)) {
                    Text(
                        text = if (previewIsPrevious) {
                            if (de) "VORHERIGE" else "PREVIOUS"
                        } else {
                            if (de) "NÄCHSTE" else "NEXT"
                        },
                        color = accent,
                        fontFamily = PassportBodyFontFamily,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        previewTitle,
                        color = primaryText,
                        fontFamily = PassportDisplayFontFamily,
                        fontSize = 28.sp,
                    )
                }
            }
        }
        Card(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { cardWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .graphicsLayer {
                    translationX = dragOffset
                    rotationZ = (dragOffset / cardWidthPx) * 4f
                    scaleX = (1f - dragFraction * 0.012f) * (0.975f + reveal * 0.025f)
                    scaleY = scaleX
                    alpha = reveal
                }
                .border(1.dp, Color(0xFFE5DFD8), shape)
                .clip(shape)
                .pointerInput(cardWidthPx, flingThreshold) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onHorizontalDrag = { change, amount ->
                            if (!animationLocked) {
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                val proposed = dragOffset + amount
                                val allowed = if (proposed >= 0f) nextAllowed else previousAllowed
                                dragOffset = (dragOffset + amount * if (allowed) 1f else 0.22f)
                                    .coerceIn(-cardWidthPx * 0.85f, cardWidthPx * 0.85f)
                            }
                        },
                        onDragEnd = { settle(tracker.calculateVelocity().x) },
                        onDragCancel = { settle(cancelled = true) },
                    )
                },
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = surfaceTop),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(surfaceTop, surfaceBottom))
            )) {
                ValenceAffectiveField(selectedValue, Modifier.fillMaxSize())
                Column(
                    modifier = Modifier.fillMaxSize().padding(
                        horizontal = horizontalPadding,
                        vertical = if (compact) 14.dp else 20.dp,
                    ),
                ) {
                    Text(
                        sectionTitle.uppercase(),
                        color = secondaryText,
                        fontFamily = PassportBodyFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        progressText,
                        color = secondaryText,
                        fontFamily = PassportBodyFontFamily,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = accent,
                        trackColor = Color(0xFFE4DFD7),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
                    Text(
                        title,
                        color = primaryText,
                        fontFamily = PassportDisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (compact) 30.sp else 36.sp,
                        lineHeight = if (compact) 34.sp else 40.sp,
                        letterSpacing = (-0.4).sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        question,
                        color = secondaryText,
                        fontFamily = PassportBodyFontFamily,
                        fontSize = if (compact) 14.sp else 16.sp,
                        lineHeight = if (compact) 19.sp else 23.sp,
                    )
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Keep a square viewport even when the footer leaves less vertical space.
                        val faceSize = minOf(maxWidth, maxHeight, 280.dp)
                        Valence3DFace(
                            selectedValue = selectedValue,
                            modifier = Modifier.size(faceSize),
                            transportMode = transportMode,
                        )
                    }
                    ValenceSevenPointChoice(
                        selectedValue = selectedValue,
                        de = de,
                        onSelect = { if (!animationLocked) selectAnswer(it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                    Text(
                        text = when {
                            selectedValue == null -> if (de) "Wähle zuerst eine Antwort" else "Choose a response first"
                            canGoNext && canGoPrevious -> if (de) "← vorherige  ·  nächste →" else "← previous  ·  next →"
                            canGoNext -> if (de) "Nach rechts wischen →" else "Swipe right to continue →"
                            canGoPrevious -> if (de) "← Zurückwischen · Antworten vollständig" else "← Swipe back · All responses complete"
                            else -> if (de) "Antwort gespeichert" else "Response saved"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = secondaryText,
                        fontFamily = PassportBodyFontFamily,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Right advances, left returns, matching Needs. A fling must agree with actual travel. */
internal fun valenceSwipeDirection(
    offset: Float,
    width: Float,
    velocity: Float,
    flingThreshold: Float,
    canGoNext: Boolean,
    canGoPrevious: Boolean,
): Int {
    val distance = width.coerceAtLeast(1f)
    val threshold = distance * 0.20f
    val minimumFlingTravel = distance * 0.06f
    return when {
        canGoNext && (offset >= threshold ||
            (offset >= minimumFlingTravel && velocity >= flingThreshold)) -> 1
        canGoPrevious && (offset <= -threshold ||
            (offset <= -minimumFlingTravel && velocity <= -flingThreshold)) -> -1
        else -> 0
    }
}
