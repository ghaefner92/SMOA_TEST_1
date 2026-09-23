package com.example.imiq

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Seven-point semantic choice.
 *
 * Scientific/data contract:
 * - no numerical values are displayed
 * - positions map exactly to Int 1..7
 * - visual size/color do not alter stored values
 * - no gesture property is interpreted as confidence
 *
 * Visual metaphor:
 * - low values: warmer + physically smaller
 * - high values: cooler + physically larger
 * - selected value emits a soft animated glow
 */
@Composable
fun SemanticSevenPointChoice(
    selectedValue: Int?,
    labels: List<String>,
    lowLabel: String,
    highLabel: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {

    require(
        selectedValue == null ||
            selectedValue in 1..7
    )

    require(
        labels.size == 7
    )

    val colors =
        MaterialTheme.colorScheme

    val haptic =
        LocalHapticFeedback.current

    /*
     * Very subtle breathing light.
     * It has NO data meaning.
     */
    val infiniteTransition =
        rememberInfiniteTransition(
            label = "semantic-glow"
        )

    val glowPulse by
        infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.10f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 1250
                        ),
                    repeatMode =
                        RepeatMode.Reverse,
                ),
            label = "semantic-glow-pulse",
        )

    val glowAlpha by
        infiniteTransition.animateFloat(
            initialValue = 0.22f,
            targetValue = 0.38f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 1250
                        ),
                    repeatMode =
                        RepeatMode.Reverse,
                ),
            label = "semantic-glow-alpha",
        )

    val selectedColor =
        selectedValue
            ?.let {
                semanticOrbColor(it)
            }
            ?: colors.primary

    Column(
        modifier =
            modifier.fillMaxWidth(),

        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {

        /*
         * Scale anchors.
         */
        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween,
        ) {

            Text(
                text =
                    lowLabel,

                style =
                    MaterialTheme
                        .typography
                        .labelMedium.copy(fontFamily = PassportBodyFontFamily),

                color =
                    semanticOrbColor(1)
                        .copy(alpha = 0.85f),
            )

            Text(
                text =
                    highLabel,

                style =
                    MaterialTheme
                        .typography
                        .labelMedium.copy(fontFamily = PassportBodyFontFamily),

                color =
                    semanticOrbColor(7)
                        .copy(alpha = 0.90f),
            )
        }

        Spacer(
            Modifier.height(
                24.dp
            )
        )

        /*
         * Orb field.
         */
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(74.dp),

            contentAlignment =
                Alignment.Center,
        ) {

            /*
             * Full warm -> cool semantic light track.
             */
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 18.dp
                        )
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                colors =
                                    (1..7).map {
                                        semanticOrbColor(it)
                                            .copy(alpha = 0.50f)
                                    }
                            )
                        )
            )

            /*
             * Seven exact discrete response targets.
             */
            Row(
                modifier =
                    Modifier.fillMaxSize(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically,
            ) {

                (1..7).forEach {
                    value ->

                    val selected =
                        selectedValue == value

                    val valueColor =
                        semanticOrbColor(
                            value
                        )

                    /*
                     * Even before selection there is a subtle
                     * physical progression from 1 -> 7.
                     */
                    val baseSize =
                        semanticOrbBaseSize(
                            value
                        )

                    val selectedScale by
                        animateFloatAsState(
                            targetValue =
                                if (selected)
                                    1.46f
                                else
                                    1f,

                            animationSpec =
                                tween(
                                    durationMillis = 240
                                ),

                            label =
                                "semantic-scale-$value",
                        )

                    val elevation by
                        animateDpAsState(
                            targetValue =
                                if (selected)
                                    13.dp
                                else
                                    1.dp,

                            animationSpec =
                                tween(
                                    durationMillis = 220
                                ),

                            label =
                                "semantic-elevation-$value",
                        )

                    val borderColor by
                        animateColorAsState(
                            targetValue =
                                if (selected)
                                    Color.White
                                        .copy(alpha = 0.88f)
                                else
                                    valueColor
                                        .copy(alpha = 0.65f),

                            label =
                                "semantic-border-$value",
                        )

                    /*
                     * Fixed interaction cell.
                     *
                     * graphicsLayer lets the selected orb visually
                     * grow beyond its layout size without moving
                     * neighbouring response positions.
                     */
                    Box(
                        modifier =
                            Modifier
                                .width(38.dp)
                                .fillMaxHeight()
                                .clickable {

                                    if (
                                        selectedValue != value
                                    ) {
                                        haptic
                                            .performHapticFeedback(
                                                HapticFeedbackType
                                                    .TextHandleMove
                                            )
                                    }

                                    onSelect(
                                        value
                                    )
                                },

                        contentAlignment =
                            Alignment.Center,
                    ) {

                        /*
                         * Outer energetic halo.
                         */
                        if (selected) {

                            Box(
                                modifier =
                                    Modifier
                                        .size(39.dp)
                                        .graphicsLayer {

                                            scaleX =
                                                glowPulse * 1.35f

                                            scaleY =
                                                glowPulse * 1.35f

                                            alpha =
                                                glowAlpha
                                        }
                                        .background(
                                            brush =
                                                Brush.radialGradient(
                                                    colors =
                                                        listOf(
                                                            valueColor
                                                                .copy(
                                                                    alpha = 0.58f
                                                                ),
                                                            valueColor
                                                                .copy(
                                                                    alpha = 0.25f
                                                                ),
                                                            Color.Transparent,
                                                        )
                                                ),

                                            shape =
                                                CircleShape,
                                        )
                            )

                            /*
                             * Inner halo gives a denser light core.
                             */
                            Box(
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .graphicsLayer {

                                            scaleX =
                                                1.20f

                                            scaleY =
                                                1.20f
                                        }
                                        .background(
                                            color =
                                                valueColor.copy(
                                                    alpha = 0.18f
                                                ),

                                            shape =
                                                CircleShape,
                                        )
                            )
                        }

                        /*
                         * Main orb.
                         */
                        Box(
                            modifier =
                                Modifier
                                    .size(
                                        baseSize
                                    )
                                    .graphicsLayer {

                                        scaleX =
                                            selectedScale

                                        scaleY =
                                            selectedScale
                                    }
                                    .shadow(
                                        elevation =
                                            elevation,

                                        shape =
                                            CircleShape,

                                        clip =
                                            false,
                                    )
                                    .clip(
                                        CircleShape
                                    )
                                    .background(
                                        brush =
                                            Brush.radialGradient(
                                                colors =
                                                    if (selected) {
                                                        listOf(
                                                            Color.White
                                                                .copy(
                                                                    alpha = 0.72f
                                                                ),

                                                            valueColor,

                                                            valueColor
                                                                .copy(
                                                                    alpha = 0.92f
                                                                ),
                                                        )
                                                    } else {
                                                        listOf(
                                                            Color.White
                                                                .copy(
                                                                    alpha = 0.38f
                                                                ),

                                                            valueColor
                                                                .copy(
                                                                    alpha = 0.88f
                                                                ),

                                                            valueColor
                                                                .copy(
                                                                    alpha = 0.72f
                                                                ),
                                                        )
                                                    }
                                            )
                                    )
                                    .border(
                                        width =
                                            if (selected)
                                                1.8.dp
                                            else
                                                1.dp,

                                        color =
                                            borderColor,

                                        shape =
                                            CircleShape,
                                    ),

                            contentAlignment =
                                Alignment.Center,
                        ) {

                            /*
                             * Specular highlight:
                             * gives the selected orb a luminous,
                             * almost glass-like centre.
                             */
                            if (selected) {

                                Box(
                                    modifier =
                                        Modifier
                                            .size(
                                                7.dp
                                            )
                                            .clip(
                                                CircleShape
                                            )
                                            .background(
                                                Color.White.copy(
                                                    alpha = 0.92f
                                                )
                                            )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(
            Modifier.height(
                27.dp
            )
        )

        /*
         * Semantic answer capsule.
         */
        Box(
            modifier =
                Modifier
                    .clip(
                        RoundedCornerShape(
                            20.dp
                        )
                    )
                    .background(
                        if (
                            selectedValue != null
                        ) {
                            selectedColor.copy(
                                alpha = 0.14f
                            )
                        } else {
                            colors.surfaceVariant
                                .copy(
                                    alpha = 0.45f
                                )
                        }
                    )
                    .border(
                        width =
                            if (
                                selectedValue != null
                            )
                                1.dp
                            else
                                0.dp,

                        color =
                            if (
                                selectedValue != null
                            )
                                selectedColor.copy(
                                    alpha = 0.32f
                                )
                            else
                                Color.Transparent,

                        shape =
                            RoundedCornerShape(
                                20.dp
                            ),
                    )
                    .padding(
                        horizontal = 20.dp,
                        vertical = 10.dp,
                    ),

            contentAlignment =
                Alignment.Center,
        ) {

            Text(
                text =
                    selectedValue
                        ?.let {
                            labels[
                                it - 1
                            ]
                        }
                        ?: "Tap one point to answer",

                style =
                    MaterialTheme
                        .typography
                        .titleMedium.copy(fontFamily = PassportBodyFontFamily),

                fontWeight =
                    if (
                        selectedValue != null
                    )
                        FontWeight.SemiBold
                    else
                        FontWeight.Normal,

                color =
                    if (
                        selectedValue != null
                    )
                        selectedColor
                    else
                        colors.onSurfaceVariant,
            )
        }
    }
}


/*
 * Warm -> cool semantic palette.
 *
 * This is visual only; values remain exactly 1..7.
 */
private fun semanticOrbColor(
    value: Int,
): Color =
    when (value) {

        1 ->
            Color(
                0xFFE53935
            )

        2 ->
            Color(
                0xFFF05A47
            )

        3 ->
            Color(
                0xFFF59E42
            )

        4 ->
            Color(
                0xFF9B8FD8
            )

        5 ->
            Color(
                0xFF58B4E8
            )

        6 ->
            Color(
                0xFF318CE7
            )

        7 ->
            Color(
                0xFF1769D2
            )

        else ->
            Color.Gray
    }


/*
 * Physical intensity mapping.
 *
 * Low values occupy less visual mass.
 * High values occupy progressively more visual mass.
 */
private fun semanticOrbBaseSize(
    value: Int,
) =
    when (value) {

        1 ->
            23.dp

        2 ->
            24.dp

        3 ->
            25.dp

        4 ->
            26.dp

        5 ->
            27.dp

        6 ->
            29.dp

        7 ->
            31.dp

        else ->
            26.dp
    }

