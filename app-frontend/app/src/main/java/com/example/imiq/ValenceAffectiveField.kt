package com.example.imiq

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Atmospheric affective field for the Valence task.
 *
 * selectedValue remains the participant's exact discrete response 1..7.
 * This component is visual feedback only and is never fed into HOTCO.
 */
@Composable
fun ValenceAffectiveField(
    selectedValue: Int?,
    modifier: Modifier = Modifier,
) {
    require(
        selectedValue == null ||
            selectedValue in 1..7
    )

    val negative =
        Color(0xFFE36A61)

    val neutral =
        Color(0xFF9A8FC8)

    val positive =
        Color(0xFF4AA6D1)

    val targetColor =
        when (selectedValue) {
            1 -> negative
            2 -> Color(0xFFE77D70)
            3 -> Color(0xFFC58A9D)
            4 -> neutral
            5 -> Color(0xFF7B9BCB)
            6 -> Color(0xFF5AA6D0)
            7 -> positive
            else -> Color(0xFFB7AECF)
        }

    val fieldColor by
        animateColorAsState(
            targetValue = targetColor,
            animationSpec =
                tween(
                    durationMillis = 520
                ),
            label = "valenceFieldColor",
        )

    /*
     * Negative states are somewhat more spatially contained.
     * Positive states become broader and lighter.
     *
     * This is presentation only, not an estimate of arousal.
     */
    val targetExpansion =
        when (selectedValue) {
            1 -> 0.72f
            2 -> 0.77f
            3 -> 0.83f
            4 -> 0.90f
            5 -> 0.97f
            6 -> 1.04f
            7 -> 1.10f
            else -> 0.88f
        }

    val expansion by
        animateFloatAsState(
            targetValue = targetExpansion,
            animationSpec =
                tween(
                    durationMillis = 520
                ),
            label = "valenceFieldExpansion",
        )

    val targetPresence =
        if (selectedValue == null)
            0.45f
        else
            1f

    val presence by
        animateFloatAsState(
            targetValue = targetPresence,
            animationSpec =
                tween(
                    durationMillis = 350
                ),
            label = "valenceFieldPresence",
        )

    /*
     * Slow ambient drift.
     * Its speed does not encode valence.
     */
    val transition =
        rememberInfiniteTransition(
            label = "valenceFieldDrift"
        )

    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue =
                2f *
                    PI.toFloat(),
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 12500,
                            easing = LinearEasing,
                        )
                ),
            label = "valenceFieldPhase",
        )

    Canvas(
        modifier = modifier
    ) {
        val w =
            size.width

        val h =
            size.height

        val center =
            Offset(
                x = w * 0.58f,
                y = h * 0.50f,
            )

        /*
         * Broad primary atmosphere.
         */
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            fieldColor.copy(
                                alpha =
                                    0.23f *
                                        presence
                            ),
                            fieldColor.copy(
                                alpha =
                                    0.09f *
                                        presence
                            ),
                            Color.Transparent,
                        ),
                    center = center,
                    radius =
                        size.minDimension *
                            0.72f *
                            expansion,
                ),
            radius =
                size.minDimension *
                    0.72f *
                    expansion,
            center = center,
        )

        /*
         * Secondary drifting light volume.
         */
        val driftX =
            cos(phase) *
                w *
                0.075f

        val driftY =
            sin(phase) *
                h *
                0.050f

        val secondaryCenter =
            Offset(
                x =
                    center.x +
                        driftX,
                y =
                    center.y +
                        driftY,
            )

        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color.White.copy(
                                alpha =
                                    0.26f *
                                        presence
                            ),
                            fieldColor.copy(
                                alpha =
                                    0.08f *
                                        presence
                            ),
                            Color.Transparent,
                        ),
                    center =
                        secondaryCenter,
                    radius =
                        size.minDimension *
                            0.39f *
                            expansion,
                ),
            radius =
                size.minDimension *
                    0.39f *
                    expansion,
            center =
                secondaryCenter,
        )

        /*
         * Opposing peripheral volume gives the field depth
         * without reproducing the metaball language of Needs.
         */
        val peripheralCenter =
            Offset(
                x =
                    w * 0.22f -
                        driftX * 0.45f,
                y =
                    h * 0.72f -
                        driftY * 0.35f,
            )

        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            fieldColor.copy(
                                alpha =
                                    0.10f *
                                        presence
                            ),
                            Color.Transparent,
                        ),
                    center =
                        peripheralCenter,
                    radius =
                        size.minDimension *
                            0.36f,
                ),
            radius =
                size.minDimension *
                    0.36f,
            center =
                peripheralCenter,
        )
    }
}
