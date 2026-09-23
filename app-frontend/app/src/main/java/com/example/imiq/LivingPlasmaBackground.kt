package com.example.imiq

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Strong persistent reactive plasma.
 *
 * selectedValue remains exactly the discrete response 1..7.
 * This component is visual feedback only.
 */
@Composable
fun LivingPlasmaBackground(
    selectedValue: Int?,
    modifier: Modifier = Modifier,
) {

    val infinite =
        rememberInfiniteTransition(
            label = "living-plasma"
        )

    val phaseA by
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 6200
                        ),
                    repeatMode =
                        RepeatMode.Restart,
                ),
            label = "plasma-phase-a",
        )

    val phaseB by
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 8300
                        ),
                    repeatMode =
                        RepeatMode.Restart,
                ),
            label = "plasma-phase-b",
        )

    val phaseC by
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 11100
                        ),
                    repeatMode =
                        RepeatMode.Restart,
                ),
            label = "plasma-phase-c",
        )

    /*
     * Strong one-shot response wave.
     */
    val responseWave =
        remember {
            Animatable(1f)
        }

    LaunchedEffect(selectedValue) {

        if (selectedValue != null) {

            responseWave.snapTo(0f)

            responseWave.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = 1450
                    ),
            )
        }
    }

    val responseColor =
        plasmaSemanticColor(
            selectedValue
        )

    Canvas(
        modifier = modifier
    ) {

        val w = size.width
        val h = size.height

        val discreteFraction =
            if (selectedValue != null)
                (selectedValue - 1) / 6f
            else
                0.5f

        /*
         * Approximate selected-orb origin.
         */
        val originX =
            w *
                (
                    0.12f +
                        discreteFraction *
                            0.76f
                    )

        val originY =
            h * 0.64f

        /*
         * ====================================================
         * 1. CARD-WIDE RESPONSE TINT
         * ====================================================
         *
         * Makes the selected response visibly influence the
         * entire atmosphere instead of remaining a faint spot.
         */
        drawRect(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            responseColor.copy(
                                alpha =
                                    if (selectedValue != null)
                                        0.20f
                                    else
                                        0.08f
                            ),
                            responseColor.copy(
                                alpha =
                                    if (selectedValue != null)
                                        0.10f
                                    else
                                        0.04f
                            ),
                            Color.Transparent,
                        ),
                    center =
                        Offset(
                            originX,
                            originY,
                        ),
                    radius =
                        w * 1.45f,
                )
        )


        /*
         * ====================================================
         * 2. LARGE MOVING PLASMA BODY
         * ====================================================
         */

        val bodyX =
            originX +
                sin(phaseA) *
                    w * 0.10f

        val bodyY =
            originY +
                cos(phaseB) *
                    h * 0.055f

        val breath =
            1f +
                sin(
                    phaseC * 1.15f
                ) * 0.11f

        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color.White.copy(
                                alpha =
                                    if (selectedValue != null)
                                        0.30f
                                    else
                                        0.08f
                            ),

                            responseColor.copy(
                                alpha =
                                    if (selectedValue != null)
                                        0.54f
                                    else
                                        0.14f
                            ),

                            responseColor.copy(
                                alpha =
                                    if (selectedValue != null)
                                        0.30f
                                    else
                                        0.08f
                            ),

                            responseColor.copy(
                                alpha =
                                    if (selectedValue != null)
                                        0.12f
                                    else
                                        0.04f
                            ),

                            Color.Transparent,
                        ),
                    center =
                        Offset(
                            bodyX,
                            bodyY,
                        ),
                    radius =
                        w *
                            0.53f *
                            breath,
                ),

            radius =
                w *
                    0.53f *
                    breath,

            center =
                Offset(
                    bodyX,
                    bodyY,
                ),
        )


        /*
         * ====================================================
         * 3. JELLYFISH LOBES
         * ====================================================
         *
         * Four independent luminous masses orbit slowly around
         * the response nucleus.
         */
        if (selectedValue != null) {

            for (i in 0 until 4) {

                val phaseOffset =
                    i *
                        (
                            PI.toFloat() /
                                2f
                            )

                val radiusX =
                    w *
                        (
                            0.11f +
                                i * 0.008f
                            )

                val radiusY =
                    h *
                        (
                            0.050f +
                                i * 0.005f
                            )

                val lobeX =
                    bodyX +
                        sin(
                            phaseA +
                                phaseOffset
                        ) *
                        radiusX

                val lobeY =
                    bodyY +
                        cos(
                            phaseB * 0.82f +
                                phaseOffset
                        ) *
                        radiusY

                val lobeBreath =
                    1f +
                        sin(
                            phaseC +
                                phaseOffset
                        ) *
                        0.14f

                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color.White.copy(
                                        alpha = 0.16f
                                    ),

                                    responseColor.copy(
                                        alpha = 0.42f
                                    ),

                                    responseColor.copy(
                                        alpha = 0.16f
                                    ),

                                    Color.Transparent,
                                ),

                            center =
                                Offset(
                                    lobeX,
                                    lobeY,
                                ),

                            radius =
                                w *
                                    0.23f *
                                    lobeBreath,
                        ),

                    radius =
                        w *
                            0.23f *
                            lobeBreath,

                    center =
                        Offset(
                            lobeX,
                            lobeY,
                        ),
                )
            }
        }


        /*
         * ====================================================
         * 4. CYAN/VIOLET CROSS-FIELD
         * ====================================================
         *
         * Prevents the result from looking like a single flat
         * coloured spotlight.
         */
        val crossX =
            w * 0.35f +
                cos(phaseB) *
                    w * 0.16f

        val crossY =
            h * 0.38f +
                sin(phaseC) *
                    h * 0.12f

        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color(0xFF7B61FF)
                                .copy(
                                    alpha = 0.25f
                                ),

                            Color(0xFF55DDEB)
                                .copy(
                                    alpha = 0.13f
                                ),

                            Color.Transparent,
                        ),

                    center =
                        Offset(
                            crossX,
                            crossY,
                        ),

                    radius =
                        w * 0.60f,
                ),

            radius =
                w * 0.60f,

            center =
                Offset(
                    crossX,
                    crossY,
                ),
        )


        if (selectedValue != null) {

            /*
             * =================================================
             * 5. STRONG INITIAL PROPAGATION
             * =================================================
             */

            val wave =
                responseWave.value

            val waveRadius =
                w *
                    (
                        0.05f +
                            wave *
                                1.35f
                        )

            val remaining =
                (
                    1f -
                        wave
                    )
                    .coerceIn(
                        0f,
                        1f
                    )

            /*
             * Large wash travelling through the card.
             */
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                responseColor.copy(
                                    alpha =
                                        remaining *
                                            0.58f
                                ),

                                responseColor.copy(
                                    alpha =
                                        remaining *
                                            0.32f
                                ),

                                responseColor.copy(
                                    alpha =
                                        remaining *
                                            0.12f
                                ),

                                Color.Transparent,
                            ),

                        center =
                            Offset(
                                originX,
                                originY,
                            ),

                        radius =
                            waveRadius,
                    ),

                radius =
                    waveRadius,

                center =
                    Offset(
                        originX,
                        originY,
                    ),
            )


            /*
             * Bright outer travelling ring.
             */
            drawCircle(
                color =
                    responseColor.copy(
                        alpha =
                            remaining *
                                0.88f
                    ),

                radius =
                    waveRadius,

                center =
                    Offset(
                        originX,
                        originY,
                    ),

                style =
                    Stroke(
                        width =
                            7f +
                                remaining *
                                    12f
                    ),
            )


            /*
             * White inner wave gives a luminous edge.
             */
            drawCircle(
                color =
                    Color.White.copy(
                        alpha =
                            remaining *
                                0.42f
                    ),

                radius =
                    waveRadius *
                        0.82f,

                center =
                    Offset(
                        originX,
                        originY,
                    ),

                style =
                    Stroke(
                        width = 4f
                    ),
            )


            /*
             * =================================================
             * 6. PERSISTENT SLOW PROPAGATION
             * =================================================
             *
             * Even after the initial response wave disappears,
             * the field keeps softly radiating.
             */
            val continuousPhase =
                (
                    phaseA /
                        (
                            2f *
                                PI.toFloat()
                            )
                    )
                    .coerceIn(
                        0f,
                        1f
                    )

            val persistentRadius =
                w *
                    (
                        0.18f +
                            continuousPhase *
                                0.94f
                        )

            val persistentAlpha =
                (
                    1f -
                        continuousPhase
                    ) *
                    0.26f

            drawCircle(
                color =
                    responseColor.copy(
                        alpha =
                            persistentAlpha
                    ),

                radius =
                    persistentRadius,

                center =
                    Offset(
                        bodyX,
                        bodyY,
                    ),

                style =
                    Stroke(
                        width = 5f
                    ),
            )


            /*
             * =================================================
             * 7. HOT LUMINOUS CORE
             * =================================================
             */

            val coreRadius =
                w *
                    (
                        0.105f +
                            sin(
                                phaseB *
                                    1.35f
                            ) *
                                0.012f
                        )

            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color.White.copy(
                                    alpha = 0.72f
                                ),

                                responseColor.copy(
                                    alpha = 0.74f
                                ),

                                responseColor.copy(
                                    alpha = 0.31f
                                ),

                                Color.Transparent,
                            ),

                        center =
                            Offset(
                                bodyX,
                                bodyY,
                            ),

                        radius =
                            coreRadius,
                    ),

                radius =
                    coreRadius,

                center =
                    Offset(
                        bodyX,
                        bodyY,
                    ),
            )
        }
    }
}


private fun plasmaSemanticColor(
    value: Int?,
): Color =
    when (value) {

        1 ->
            Color(0xFFE53935)

        2 ->
            Color(0xFFF05A47)

        3 ->
            Color(0xFFF59E42)

        4 ->
            Color(0xFF9B8FD8)

        5 ->
            Color(0xFF58B4E8)

        6 ->
            Color(0xFF318CE7)

        7 ->
            Color(0xFF1769D2)

        else ->
            Color(0xFF8D72FF)
    }
