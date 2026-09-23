package com.example.imiq

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs

/**
 * Human-like affective face used by the Cognitive Passport valence task.
 *
 * Scientific contract:
 * - selectedValue is an explicit participant response in 1..7.
 * - 1..7 remains discrete questionnaire data.
 * - animation only interpolates the visual representation.
 * - the face does NOT infer emotion, confidence, arousal or mental state.
 * - negative values represent unpleasantness, not a specific emotion
 *   such as anger, fear or disgust.
 */
@Composable
fun ValenceHumanFace(
    selectedValue: Int?,
    modifier: Modifier = Modifier,
) {
    require(
        selectedValue == null ||
            selectedValue in 1..7
    )

    val targetAffect =
        when (selectedValue) {
            1 -> -1.00f
            2 -> -0.67f
            3 -> -0.33f
            4 -> 0.00f
            5 -> 0.33f
            6 -> 0.67f
            7 -> 1.00f
            else -> 0.00f
        }

    val affect by
        animateFloatAsState(
            targetValue = targetAffect,
            animationSpec =
                tween(
                    durationMillis = 420
                ),
            label = "valenceFaceAffect",
        )

    val presenceTarget =
        if (selectedValue == null)
            0.58f
        else
            1.00f

    val presence by
        animateFloatAsState(
            targetValue = presenceTarget,
            animationSpec =
                tween(
                    durationMillis = 300
                ),
            label = "valenceFacePresence",
        )

    val negative =
        Color(0xFFE15E55)

    val neutral =
        Color(0xFF9587C8)

    val positive =
        Color(0xFF3D9DCE)

    val accent =
        if (affect < 0f) {
            lerp(
                neutral,
                negative,
                abs(affect),
            )
        } else {
            lerp(
                neutral,
                positive,
                affect,
            )
        }

    Canvas(
        modifier = modifier
    ) {
        val unit =
            size.minDimension

        val center =
            Offset(
                x = size.width * 0.50f,
                y = size.height * 0.51f,
            )

        val faceWidth =
            unit * 0.63f

        val faceHeight =
            unit * 0.76f

        val faceTopLeft =
            Offset(
                x = center.x - faceWidth / 2f,
                y = center.y - faceHeight / 2f,
            )

        /*
         * Ambient halo.
         * It reacts only to the explicit questionnaire response.
         */
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            accent.copy(
                                alpha =
                                    0.22f *
                                        presence
                            ),
                            accent.copy(
                                alpha =
                                    0.07f *
                                        presence
                            ),
                            Color.Transparent,
                        ),
                    center = center,
                    radius = unit * 0.57f,
                ),
            radius =
                unit *
                    (
                        0.45f +
                            abs(affect) *
                                0.035f
                    ),
            center = center,
        )

        /*
         * Face volume.
         *
         * The material is intentionally synthetic/editorial rather than
         * photorealistic so that sex, ethnicity and age are not encoded
         * as questionnaire cues.
         */
        drawOval(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color.White.copy(
                                alpha =
                                    0.96f *
                                        presence
                            ),
                            Color(0xFFF4EFE8).copy(
                                alpha =
                                    0.92f *
                                        presence
                            ),
                            accent.copy(
                                alpha =
                                    0.16f *
                                        presence
                            ),
                        ),
                    center =
                        Offset(
                            x =
                                center.x -
                                    faceWidth *
                                        0.15f,
                            y =
                                center.y -
                                    faceHeight *
                                        0.22f,
                        ),
                    radius =
                        unit * 0.57f,
                ),
            topLeft = faceTopLeft,
            size =
                Size(
                    width = faceWidth,
                    height = faceHeight,
                ),
        )

        drawOval(
            color =
                Color.White.copy(
                    alpha =
                        0.22f *
                            presence
                ),
            topLeft =
                Offset(
                    x =
                        center.x -
                            faceWidth *
                                0.25f,
                    y =
                        center.y -
                            faceHeight *
                                0.34f,
                ),
            size =
                Size(
                    width =
                        faceWidth *
                            0.50f,
                    height =
                        faceHeight *
                            0.30f,
                ),
        )

        /*
         * Eyes.
         *
         * Positive responses become subtly more open and relaxed.
         * Negative responses become slightly more contained.
         */
        val eyeY =
            center.y -
                faceHeight *
                    0.13f

        val eyeDistance =
            faceWidth *
                0.20f

        val eyeWidth =
            faceWidth *
                0.105f

        val eyeHeight =
            faceHeight *
                (
                    0.025f +
                        (affect + 1f) *
                            0.005f
                )

        listOf(
            center.x - eyeDistance,
            center.x + eyeDistance,
        ).forEach { eyeX ->

            drawOval(
                color =
                    Color(0xFF34333A).copy(
                        alpha =
                            0.88f *
                                presence
                    ),
                topLeft =
                    Offset(
                        x =
                            eyeX -
                                eyeWidth / 2f,
                        y =
                            eyeY -
                                eyeHeight / 2f,
                    ),
                size =
                    Size(
                        width = eyeWidth,
                        height = eyeHeight,
                    ),
            )

            drawCircle(
                color =
                    Color.White.copy(
                        alpha =
                            0.72f *
                                presence
                    ),
                radius =
                    unit * 0.007f,
                center =
                    Offset(
                        x =
                            eyeX -
                                eyeWidth *
                                    0.13f,
                        y =
                            eyeY -
                                eyeHeight *
                                    0.12f,
                    ),
            )
        }

        /*
         * Brows.
         * Kept deliberately subtle so negative valence does not become
         * a specific anger/disgust expression.
         */
        val browY =
            eyeY -
                faceHeight *
                    0.075f

        val browWidth =
            faceWidth *
                0.15f

        val browTilt =
            affect *
                faceHeight *
                0.015f

        drawLine(
            color =
                Color(0xFF4C4950).copy(
                    alpha =
                        0.55f *
                            presence
                ),
            start =
                Offset(
                    center.x -
                        eyeDistance -
                        browWidth / 2f,
                    browY +
                        browTilt,
                ),
            end =
                Offset(
                    center.x -
                        eyeDistance +
                        browWidth / 2f,
                    browY -
                        browTilt,
                ),
            strokeWidth =
                unit * 0.012f,
            cap =
                StrokeCap.Round,
        )

        drawLine(
            color =
                Color(0xFF4C4950).copy(
                    alpha =
                        0.55f *
                            presence
                ),
            start =
                Offset(
                    center.x +
                        eyeDistance -
                        browWidth / 2f,
                    browY -
                        browTilt,
                ),
            end =
                Offset(
                    center.x +
                        eyeDistance +
                        browWidth / 2f,
                    browY +
                        browTilt,
                ),
            strokeWidth =
                unit * 0.012f,
            cap =
                StrokeCap.Round,
        )

        /*
         * Nose: minimal, neutral across the whole scale.
         */
        drawLine(
            color =
                Color(0xFF77727A).copy(
                    alpha =
                        0.23f *
                            presence
                ),
            start =
                Offset(
                    center.x,
                    center.y -
                        faceHeight *
                            0.055f,
                ),
            end =
                Offset(
                    center.x -
                        faceWidth *
                            0.018f,
                    center.y +
                        faceHeight *
                            0.085f,
                ),
            strokeWidth =
                unit * 0.008f,
            cap =
                StrokeCap.Round,
        )

        /*
         * Mouth.
         *
         * affect < 0  -> downward/frown curvature
         * affect = 0  -> neutral line
         * affect > 0  -> upward/smile curvature
         *
         * Screen coordinates increase downward, therefore a positive
         * smile uses a lower midpoint relative to the corners.
         */
        val mouthY =
            center.y +
                faceHeight *
                    0.22f

        val mouthHalfWidth =
            faceWidth *
                (
                    0.18f +
                        abs(affect) *
                            0.035f
                    )

        val mouthCurve =
            affect *
                faceHeight *
                0.105f

        val mouth =
            Path().apply {

                moveTo(
                    center.x -
                        mouthHalfWidth,
                    mouthY,
                )

                quadraticBezierTo(
                    center.x,
                    mouthY +
                        mouthCurve,
                    center.x +
                        mouthHalfWidth,
                    mouthY,
                )
            }

        drawPath(
            path = mouth,
            color =
                Color(0xFF4A3E43).copy(
                    alpha =
                        0.82f *
                            presence
                ),
            style =
                Stroke(
                    width =
                        unit *
                            (
                                0.013f +
                                    abs(affect) *
                                        0.002f
                            ),
                    cap =
                        StrokeCap.Round,
                ),
        )

        /*
         * Positive cheek lift.
         * Very subtle to avoid turning the component into an emoji.
         */
        val positivePresence =
            affect.coerceAtLeast(
                0f
            )

        if (
            positivePresence >
            0.02f
        ) {
            val cheekAlpha =
                0.08f +
                    positivePresence *
                        0.09f

            val cheekY =
                center.y +
                    faceHeight *
                        0.075f

            drawCircle(
                color =
                    accent.copy(
                        alpha =
                            cheekAlpha *
                                presence
                    ),
                radius =
                    unit *
                        (
                            0.027f +
                                positivePresence *
                                    0.008f
                        ),
                center =
                    Offset(
                        center.x -
                            faceWidth *
                                0.235f,
                        cheekY,
                    ),
            )

            drawCircle(
                color =
                    accent.copy(
                        alpha =
                            cheekAlpha *
                                presence
                    ),
                radius =
                    unit *
                        (
                            0.027f +
                                positivePresence *
                                    0.008f
                        ),
                center =
                    Offset(
                        center.x +
                            faceWidth *
                                0.235f,
                        cheekY,
                    ),
            )
        }

        /*
         * Neutral outline keeps the face readable over the future
         * affective field.
         */
        drawOval(
            color =
                accent.copy(
                    alpha =
                        0.19f *
                            presence
                ),
            topLeft = faceTopLeft,
            size =
                Size(
                    width = faceWidth,
                    height = faceHeight,
                ),
            style =
                Stroke(
                    width =
                        unit * 0.006f
                ),
        )
    }
}
