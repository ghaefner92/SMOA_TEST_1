package com.example.imiq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun AdaptiveReadabilityVeil(
    profile: NeedVisualProfile,
    modifier: Modifier = Modifier,
) {
    /*
     * The original version used black protection gradients.
     * The new visual language uses a warm off-white atmospheric veil:
     * readable enough for dark typography while allowing the cognitive
     * field and semantic object to remain visible underneath.
     */
    val protection =
        profile.readabilityDarkness
            .coerceIn(
                0.25f,
                0.70f,
            )

    val warmWhite =
        Color(
            0xFFF8F5EF
        )

    val softWhite =
        Color(
            0xFFFCFAF6
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
    ) {

        /*
         * Vertical reading veil.
         *
         * Strongest around the title/question region,
         * progressively dissolving toward the response area.
         */
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.00f to
                                        softWhite.copy(
                                            alpha =
                                                (
                                                    0.64f +
                                                        protection *
                                                        0.26f
                                                )
                                                    .coerceAtMost(
                                                        0.84f
                                                    )
                                        ),

                                    0.18f to
                                        warmWhite.copy(
                                            alpha =
                                                (
                                                    0.72f +
                                                        protection *
                                                        0.24f
                                                )
                                                    .coerceAtMost(
                                                        0.88f
                                                    )
                                        ),

                                    0.43f to
                                        warmWhite.copy(
                                            alpha =
                                                (
                                                    0.34f +
                                                        protection *
                                                        0.22f
                                                )
                                                    .coerceAtMost(
                                                        0.50f
                                                    )
                                        ),

                                    0.68f to
                                        warmWhite.copy(
                                            alpha =
                                                (
                                                    0.08f +
                                                        protection *
                                                        0.08f
                                                )
                                                    .coerceAtMost(
                                                        0.14f
                                                    )
                                        ),

                                    1.00f to
                                        Color.Transparent,
                                )
                        )
                    )
        )

        /*
         * Left reading protection.
         *
         * Keeps the textual side calm and luminous while allowing
         * the animated field to become more visible toward the right.
         */
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops =
                                arrayOf(
                                    0.00f to
                                        softWhite.copy(
                                            alpha =
                                                (
                                                    0.56f +
                                                        protection *
                                                        0.22f
                                                )
                                                    .coerceAtMost(
                                                        0.72f
                                                    )
                                        ),

                                    0.42f to
                                        warmWhite.copy(
                                            alpha =
                                                (
                                                    0.20f +
                                                        protection *
                                                        0.16f
                                                )
                                                    .coerceAtMost(
                                                        0.32f
                                                    )
                                        ),

                                    0.76f to
                                        Color.Transparent,

                                    1.00f to
                                        Color.Transparent,
                                )
                        )
                    )
        )
    }
}
