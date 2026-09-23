package com.example.imiq

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun SwipeableSevenPointCard(
    sectionTitle: String,
    progressText: String,
    progressFraction: Float,
    title: String,
    question: String,
    needKey: String,
    selectedValue: Int?,
    labels: List<String>,
    lowLabel: String,
    highLabel: String,
    previousPreviewTitle: String?,
    nextPreviewTitle: String?,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onSelect: (Int) -> Unit,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val needVisualProfile =
        remember(needKey) {
            NeedVisualProfiles.forKey(needKey)
        }


    var cardWidthPx by remember {
        mutableFloatStateOf(1f)
    }

    var dragOffset by remember {
        mutableFloatStateOf(0f)
    }

    var animationLocked by remember {
        mutableStateOf(false)
    }

    val scope =
        rememberCoroutineScope()

    val threshold =
        cardWidthPx * 0.20f

    val maxInteractiveTravel =
        cardWidthPx * 0.60f

    val dragFraction =
        (
            abs(dragOffset) /
                threshold.coerceAtLeast(1f)
            )
            .coerceIn(
                0f,
                1f,
            )

    val nextGesture =
        dragOffset > 4f

    val previousGesture =
        dragOffset < -4f

    val previewTitle =
        when {
            previousGesture &&
                previousPreviewTitle != null ->
                previousPreviewTitle

            nextGesture &&
                nextPreviewTitle != null ->
                nextPreviewTitle

            nextPreviewTitle != null ->
                nextPreviewTitle

            else ->
                previousPreviewTitle
        }

    val primary =
        MaterialTheme
            .colorScheme
            .primary

    val secondary =
        MaterialTheme
            .colorScheme
            .secondary

    val surface =
        MaterialTheme
            .colorScheme
            .surface

    val surfaceVariant =
        MaterialTheme
            .colorScheme
            .surfaceVariant

    /*
     * Cognitive Passport local light palette.
     */
    val passportSurfaceTop =
        Color(0xFFFCFAF6)

    val passportSurfaceBottom =
        Color(0xFFF3EFE7)

    val passportTextPrimary =
        Color(0xFF24262B)

    val passportTextSecondary =
        Color(0xFF5F6268)
    /*
     * Ambient colour mirrors the already selected discrete
     * response. It is visual feedback only.
     */
    val rawAmbientColor =
        when (selectedValue) {
            1 -> Color(0xFFE53935)
            2 -> Color(0xFFF05A47)
            3 -> Color(0xFFF59E42)
            4 -> Color(0xFF9B8FD8)
            5 -> Color(0xFF58B4E8)
            6 -> Color(0xFF318CE7)
            7 -> Color(0xFF1769D2)
            else -> Color(0xFF9B8FD8)
        }

    val ambientColor by
        animateColorAsState(
            targetValue =
                rawAmbientColor,

            animationSpec =
                tween(
                    durationMillis = 520
                ),

            label =
                "need-card-ambient-color",
        )

    /*
     * Very slow ambient motion.
     * This carries no response information.
     */
    val auraTransition =
        rememberInfiniteTransition(
            label =
                "need-card-aura"
        )

    val auraDriftX by
        auraTransition.animateFloat(
            initialValue = -28f,
            targetValue = 28f,

            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 7600
                        ),

                    repeatMode =
                        RepeatMode.Reverse,
                ),

            label =
                "need-aura-x",
        )

    val auraDriftY by
        auraTransition.animateFloat(
            initialValue = 18f,
            targetValue = -24f,

            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 9100
                        ),

                    repeatMode =
                        RepeatMode.Reverse,
                ),

            label =
                "need-aura-y",
        )

    val auraPulse by
        auraTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.08f,

            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 2600
                        ),

                    repeatMode =
                        RepeatMode.Reverse,
                ),

            label =
                "need-aura-pulse",
        )

    /*
     * New Need content enters with a small organic reveal.
     */
    val contentReveal =
        remember(title) {
            Animatable(0f)
        }

    LaunchedEffect(title) {

        contentReveal.animateTo(
            targetValue = 1f,

            animationSpec =
                tween(
                    durationMillis = 460
                ),
        )
    }

    val cardShape =
        RoundedCornerShape(
            28.dp
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(
                    10.dp
                ),

        contentAlignment =
            Alignment.Center,
    ) {

        /*
         * BACK CARD
         */
        if (
            previewTitle != null
        ) {

            Card(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {

                            val scale =
                                0.925f +
                                    dragFraction *
                                        0.075f

                            scaleX =
                                scale

                            scaleY =
                                scale

                            translationY =
                                28f *
                                    (
                                        1f -
                                            dragFraction
                                        )

                            alpha =
                                0.46f +
                                    dragFraction *
                                        0.54f

                            rotationX =
                                3.5f *
                                    (
                                        1f -
                                            dragFraction
                                        )

                            cameraDistance =
                                28f * density
                        },

                shape =
                    cardShape,

                elevation =
                    CardDefaults
                        .cardElevation(
                            defaultElevation =
                                3.dp +
                                    (
                                        5.dp *
                                            dragFraction
                                        ),
                        ),

                colors =
                    CardDefaults
                        .cardColors(
                            containerColor =
                                surfaceVariant,
                        ),
            ) {

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            surfaceVariant,
                                            surface,
                                        )
                                )
                            )
                            .padding(
                                horizontal = 26.dp,
                                vertical = 30.dp,
                            ),
                ) {

                    Column {

                        Text(
                            text =
                                when {
                                    previousGesture ->
                                        "PREVIOUS"

                                    else ->
                                        "NEXT"
                                },

                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,

                            fontWeight =
                                FontWeight.SemiBold,

                            color =
                                if (previousGesture)
                                    secondary
                                else
                                    primary,
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )

                        Text(
                            text =
                                previewTitle,

                            fontSize =
                                23.sp,

                            fontWeight =
                                FontWeight.SemiBold,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant,
                        )

                        Spacer(
                            Modifier.height(
                                190.dp
                            )
                        )
                    }
                }
            }
        }

        /*
         * FRONT CARD
         */
        Card(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged {

                        cardWidthPx =
                            it.width
                                .toFloat()
                                .coerceAtLeast(
                                    1f
                                )
                    }
                    .graphicsLayer {

                        translationX =
                            dragOffset

                        rotationZ =
                            (
                                dragOffset /
                                    cardWidthPx
                                ) * 6.5f

                        rotationY =
                            -(
                                dragOffset /
                                    cardWidthPx
                                ) * 9f

                        scaleX =
                            1f -
                                dragFraction *
                                    0.012f

                        scaleY =
                            1f -
                                dragFraction *
                                    0.012f

                        alpha =
                            1f -
                                (
                                    abs(
                                        dragOffset
                                    ) /
                                        (
                                            cardWidthPx *
                                                1.55f
                                            )
                                )
                                    .coerceIn(
                                        0f,
                                        0.28f,
                                    )

                        cameraDistance =
                            30f * density

                        shadowElevation =
                            18f +
                                dragFraction *
                                    10f
                    }
                    .border(
                        width =
                            1.dp,

                        color =
                            when {

                                nextGesture ->
                                    primary.copy(
                                        alpha =
                                            0.18f +
                                                dragFraction *
                                                    0.32f
                                    )

                                previousGesture ->
                                    secondary.copy(
                                        alpha =
                                            0.18f +
                                                dragFraction *
                                                    0.32f
                                    )

                                else ->
                                    MaterialTheme
                                        .colorScheme
                                        .outlineVariant
                                        .copy(
                                            alpha =
                                                0.35f
                                        )
                            },

                        shape =
                            cardShape,
                    )
                    .clip(
                        cardShape
                    )
                    .pointerInput(
                        selectedValue,
                        cardWidthPx,
                        canGoPrevious,
                        canGoNext,
                        animationLocked,
                    ) {

                        detectDragGestures(

                            onDrag = {
                                change,
                                dragAmount ->

                                if (
                                    !animationLocked
                                ) {

                                    change.consume()

                                    dragOffset =
                                        (
                                            dragOffset +
                                                dragAmount.x
                                            )
                                            .coerceIn(
                                                -maxInteractiveTravel,
                                                maxInteractiveTravel,
                                            )
                                }
                            },

                            onDragCancel = {

                                if (
                                    !animationLocked
                                ) {

                                    animationLocked =
                                        true

                                    val start =
                                        dragOffset

                                    scope.launch {

                                        val animation =
                                            Animatable(
                                                start
                                            )

                                        animation.animateTo(
                                            targetValue =
                                                0f,

                                            animationSpec =
                                                spring(
                                                    dampingRatio =
                                                        0.70f,

                                                    stiffness =
                                                        360f,
                                                ),
                                        ) {

                                            dragOffset =
                                                value
                                        }

                                        dragOffset =
                                            0f

                                        animationLocked =
                                            false
                                    }
                                }
                            },

                            onDragEnd = {

                                if (
                                    !animationLocked
                                ) {

                                    val goNext =
                                        dragOffset >=
                                            threshold &&
                                            selectedValue != null &&
                                            canGoNext

                                    val goPrevious =
                                        dragOffset <=
                                            -threshold &&
                                            canGoPrevious

                                    if (
                                        goNext ||
                                        goPrevious
                                    ) {

                                        animationLocked =
                                            true

                                        val start =
                                            dragOffset

                                        val exitTarget =
                                            if (
                                                goNext
                                            ) {
                                                cardWidthPx *
                                                    1.55f
                                            } else {
                                                -cardWidthPx *
                                                    1.55f
                                            }

                                        scope.launch {

                                            val animation =
                                                Animatable(
                                                    start
                                                )

                                            animation.animateTo(
                                                targetValue =
                                                    exitTarget,

                                                animationSpec =
                                                    tween(
                                                        durationMillis =
                                                            275,

                                                        easing =
                                                            FastOutLinearInEasing,
                                                    ),
                                            ) {

                                                dragOffset =
                                                    value
                                            }

                                            if (
                                                goNext
                                            ) {
                                                onSwipeNext()
                                            } else {
                                                onSwipePrevious()
                                            }

                                            dragOffset =
                                                0f

                                            animationLocked =
                                                false
                                        }

                                    } else {

                                        animationLocked =
                                            true

                                        val start =
                                            dragOffset

                                        scope.launch {

                                            val animation =
                                                Animatable(
                                                    start
                                                )

                                            animation.animateTo(
                                                targetValue =
                                                    0f,

                                                animationSpec =
                                                    spring(
                                                        dampingRatio =
                                                            0.66f,

                                                        stiffness =
                                                            340f,
                                                    ),
                                            ) {

                                                dragOffset =
                                                    value
                                            }

                                            dragOffset =
                                                0f

                                            animationLocked =
                                                false
                                        }
                                    }
                                }
                            },
                        )
                    },

            shape =
                cardShape,

            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            Color.Transparent,
                    ),

            elevation =
                CardDefaults
                    .cardElevation(
                        defaultElevation =
                            12.dp,
                    ),
        ) {

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        passportSurfaceTop,
                                        passportSurfaceBottom,
                                    )
                            )
                        )
            ) {

                CognitiveField(
                    profile = needVisualProfile,
                    selectedValue = selectedValue,
                    modifier = Modifier.fillMaxSize(),
                )


                AdaptiveReadabilityVeil(
                    profile = needVisualProfile,
                    modifier = Modifier.fillMaxSize(),
                )


                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = 26.dp,
                                vertical = 30.dp,
                            )
                            .graphicsLayer {

                                alpha =
                                    contentReveal.value

                                translationY =
                                    (
                                        1f -
                                            contentReveal.value
                                        ) * 18f

                                val revealScale =
                                    0.985f +
                                        contentReveal.value *
                                            0.015f

                                scaleX =
                                    revealScale

                                scaleY =
                                    revealScale
                            },
                ) {

                    Text(
                        text =
                            sectionTitle.uppercase(),

                        fontSize =
                            12.sp,

                        fontWeight =
                            FontWeight.Medium,

                        letterSpacing =
                            1.8.sp,

                        fontFamily =
                            PassportBodyFontFamily,

                        color =
                            ambientColor.copy(
                                alpha = 0.90f
                            ),
                    )

                    Spacer(
                        Modifier.height(
                            5.dp
                        )
                    )

                    Text(
                        text =
                            progressText,

                        style =
                            MaterialTheme
                                .typography
                                .labelMedium.copy(fontFamily = PassportBodyFontFamily),

                        color =
                            passportTextSecondary.copy(alpha = 0.92f),
                    )

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    androidx.compose.material3.LinearProgressIndicator(
                        progress = {
                            progressFraction
                                .coerceIn(
                                    0f,
                                    1f,
                                )
                        },

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    4.dp
                                )
                                .clip(
                                    CircleShape
                                ),

                        color =
                            ambientColor,

                        trackColor =
                            ambientColor.copy(
                                alpha = 0.13f
                            ),
                    )

                    Spacer(
                        Modifier.height(
                            28.dp
                        )
                    )


                    /*
                     * Direction feedback appears progressively
                     * while the card is being dragged.
                     */
                    if (
                        abs(
                            dragOffset
                        ) > 12f
                    ) {

                        Text(
                            text =
                                if (
                                    nextGesture
                                )
                                    "NEXT"
                                else
                                    "PREVIOUS",

                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                if (
                                    nextGesture
                                )
                                    primary
                                else
                                    secondary,

                            modifier =
                                Modifier
                                    .graphicsLayer {

                                        alpha =
                                            dragFraction
                                    },
                        )

                        Spacer(
                            Modifier.height(
                                7.dp
                            )
                        )
                    }
                    /*
                     * Long localized titles use the whole card width.
                     * The semantic object moves below the title so that
                     * labels such as "Flexibility and autonomy" remain
                     * readable without shrinking the visual object.
                     */
                    val useStackedTitleLayout =
                        title.length > 18

                    if (
                        useStackedTitleLayout
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                        ) {
                            Text(
                                text =
                                    title,

                                fontSize =
                                    36.sp,

                                fontWeight =
                                    FontWeight.SemiBold,

                                letterSpacing =
                                    (-0.4f).sp,

                                lineHeight =
                                    41.sp,

                                fontFamily =
                                    PassportDisplayFontFamily,

                                color =
                                    passportTextPrimary,

                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                            )

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(
                                            176.dp
                                        )
                            ) {
                                NeedSemanticObject(
                                    profile =
                                        needVisualProfile,

                                    selectedValue =
                                        selectedValue,

                                    modifier =
                                        Modifier
                                            .align(
                                                Alignment.CenterEnd
                                            )
                                            .size(
                                                176.dp
                                            ),
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                        ) {
                            Text(
                                text =
                                    title,

                                fontSize =
                                    36.sp,

                                fontWeight =
                                    FontWeight.SemiBold,

                                letterSpacing =
                                    (-0.4f).sp,

                                lineHeight =
                                    41.sp,

                                fontFamily =
                                    PassportDisplayFontFamily,

                                color =
                                    passportTextPrimary,

                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            end = 158.dp
                                        ),
                            )

                            NeedSemanticObject(
                                profile =
                                    needVisualProfile,

                                selectedValue =
                                    selectedValue,

                                modifier =
                                    Modifier
                                        .align(
                                            Alignment.TopEnd
                                        )
                                        .size(
                                            176.dp
                                        ),
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(
                            11.dp
                        )
                    )

                    Text(
                        text =
                            question,

                        fontSize =
                            17.sp,

                        fontWeight =
                            FontWeight.Normal,

                        letterSpacing =
                            0.sp,

                        color =
                            passportTextSecondary,

                        lineHeight =
                            26.sp,

                        fontFamily =
                            PassportBodyFontFamily,
                    )

                    Spacer(
                        Modifier.weight(
                            1f
                        )
                    )


                    SemanticSevenPointChoice(
                        selectedValue =
                            selectedValue,

                        labels =
                            labels,

                        lowLabel =
                            lowLabel,

                        highLabel =
                            highLabel,

                        onSelect =
                            onSelect,
                    )

                    Spacer(
                        Modifier.height(
                            27.dp
                        )
                    )

                    Spacer(
                        Modifier.weight(
                            1f
                        )
                    )

                    Text(
                        text =
                            when {

                                selectedValue ==
                                    null ->
                                    "Choose a response first"

                                canGoPrevious &&
                                    canGoNext ->
                                    "\u2190 previous   \u00B7   next \u2192"

                                canGoNext ->
                                    "Swipe right \u2192 next"

                                canGoPrevious ->
                                    "Swipe left \u2190 previous"

                                else ->
                                    "Response saved"
                            },

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall.copy(fontFamily = PassportBodyFontFamily),

                        color =
                            passportTextSecondary.copy(alpha = 0.88f),
                    )
                }
            }
        }
    }
}












