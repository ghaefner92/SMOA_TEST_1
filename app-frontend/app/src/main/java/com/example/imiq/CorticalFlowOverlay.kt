package com.example.imiq

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CorticalFlowOverlay(
    profile: NeedVisualProfile,
    selectedValue: Int?,
    modifier: Modifier = Modifier,
) {
    val visualState =
        resolveNeedResponseVisualState(
            profile = profile,
            response = selectedValue,
        )

    val transition =
        rememberInfiniteTransition(
            label = "cortical-flow"
        )

    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis =
                                (
                                    profile.dynamics.driftSeconds *
                                        1000f
                                )
                                    .roundToInt()
                                    .coerceAtLeast(3500),
                            easing = LinearEasing,
                        ),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "cortical-flow-phase",
        )

    val pulse by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis =
                                (
                                    profile.dynamics.pulseSeconds *
                                        1000f
                                )
                                    .roundToInt()
                                    .coerceAtLeast(2500),
                            easing = LinearEasing,
                        ),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "cortical-flow-pulse",
        )

    val wave =
        sin(
            phase *
                2f *
                PI.toFloat()
        )

    val glowWave =
        (
            0.5f +
                0.5f *
                sin(
                    pulse *
                        2f *
                        PI.toFloat()
                )
        )

    Canvas(
        modifier = modifier
    ) {
        val d =
            min(
                size.width,
                size.height,
            )

        if (d <= 0f) {
            return@Canvas
        }

        val responseColor =
            visualState.responseColor

        val lineColor =
            lerp(
                profile.accentColor,
                responseColor,
                0.38f,
            )

        val secondaryLine =
            lerp(
                profile.secondaryColor,
                Color.White,
                0.22f,
            )

        /*
         * Abstract cortical ribbons.
         * No closed outline, no hemispheres, no anatomical silhouette.
         */
        fun ribbon(
            start: Offset,
            c1: Offset,
            c2: Offset,
            end: Offset,
            alpha: Float,
            width: Float,
        ) {
            val p =
                Path().apply {
                    moveTo(
                        start.x,
                        start.y,
                    )

                    cubicTo(
                        c1.x,
                        c1.y,
                        c2.x,
                        c2.y,
                        end.x,
                        end.y,
                    )
                }

            drawPath(
                path = p,
                color =
                    lineColor.copy(
                        alpha = alpha
                    ),
                style =
                    Stroke(
                        width = width,
                        cap = StrokeCap.Round,
                    ),
            )
        }

        val shift =
            wave *
                d *
                0.018f *
                profile.dynamics.driftAmplitude
                    .coerceAtLeast(0.06f)

        ribbon(
            start =
                Offset(
                    size.width * 0.18f,
                    size.height * 0.42f + shift,
                ),
            c1 =
                Offset(
                    size.width * 0.30f,
                    size.height * 0.28f,
                ),
            c2 =
                Offset(
                    size.width * 0.45f,
                    size.height * 0.54f,
                ),
            end =
                Offset(
                    size.width * 0.60f,
                    size.height * 0.40f - shift,
                ),
            alpha = 0.15f,
            width = d * 0.010f,
        )

        ribbon(
            start =
                Offset(
                    size.width * 0.30f,
                    size.height * 0.60f,
                ),
            c1 =
                Offset(
                    size.width * 0.42f,
                    size.height * 0.46f + shift,
                ),
            c2 =
                Offset(
                    size.width * 0.56f,
                    size.height * 0.70f,
                ),
            end =
                Offset(
                    size.width * 0.72f,
                    size.height * 0.54f - shift,
                ),
            alpha = 0.12f,
            width = d * 0.009f,
        )

        ribbon(
            start =
                Offset(
                    size.width * 0.50f,
                    size.height * 0.32f,
                ),
            c1 =
                Offset(
                    size.width * 0.62f,
                    size.height * 0.20f + shift,
                ),
            c2 =
                Offset(
                    size.width * 0.72f,
                    size.height * 0.46f,
                ),
            end =
                Offset(
                    size.width * 0.84f,
                    size.height * 0.34f - shift,
                ),
            alpha = 0.14f,
            width = d * 0.009f,
        )

        ribbon(
            start =
                Offset(
                    size.width * 0.42f,
                    size.height * 0.72f,
                ),
            c1 =
                Offset(
                    size.width * 0.56f,
                    size.height * 0.60f,
                ),
            c2 =
                Offset(
                    size.width * 0.70f,
                    size.height * 0.82f + shift,
                ),
            end =
                Offset(
                    size.width * 0.84f,
                    size.height * 0.68f,
                ),
            alpha = 0.10f,
            width = d * 0.008f,
        )

        /*
         * Small internal activity nodes.
         * They should feel embedded rather than decorative.
         */
        val nodes =
            listOf(
                Offset(
                    size.width * 0.34f,
                    size.height * 0.43f,
                ),
                Offset(
                    size.width * 0.52f,
                    size.height * 0.56f,
                ),
                Offset(
                    size.width * 0.66f,
                    size.height * 0.37f,
                ),
                Offset(
                    size.width * 0.73f,
                    size.height * 0.64f,
                ),
            )

        val nodeAlpha =
            (
                0.22f +
                    glowWave *
                    0.18f
            )
                .coerceIn(
                    0.18f,
                    0.40f,
                )

        nodes.forEachIndexed { index, node ->

            val radius =
                d *
                    (
                        0.030f +
                            index * 0.002f
                    )

            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color.White.copy(
                                    alpha = nodeAlpha
                                ),
                                secondaryLine.copy(
                                    alpha =
                                        nodeAlpha *
                                            0.45f
                                ),
                                Color.Transparent,
                            ),
                        center = node,
                        radius = radius,
                    ),
                center = node,
                radius = radius,
            )

            drawCircle(
                color =
                    Color.White.copy(
                        alpha =
                            nodeAlpha *
                                0.82f
                    ),
                center = node,
                radius =
                    d *
                        0.0045f,
            )
        }
    }
}
