package com.example.imiq


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun CognitiveField(
    profile: NeedVisualProfile,
    selectedValue: Int?,
    modifier: Modifier = Modifier,
) {
    val visualState =
        resolveNeedResponseVisualState(
            profile = profile,
            response = selectedValue,
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
    ) {

        /*
         * ----------------------------------------------------
         * ORGANIC GPU FIELD
         * ----------------------------------------------------
         *
         * The shader remains atmospheric rather than semantic:
         * response and need-specific meaning are handled outside
         * the shader itself.
         */
        ReactiveMetaballsBackground(
            profile = profile,
            selectedValue = selectedValue,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        /*
                         * Keep the field visible, but prevent it
                         * from becoming the primary UI element.
                         */
                        alpha =
                            (
                                0.72f +
                                    visualState.opacity * 0.20f
                            )
                                .coerceIn(
                                    0.72f,
                                    0.90f,
                                )
                    },
        )

        CorticalFlowOverlay(
            profile = profile,
            selectedValue = selectedValue,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.22f
                    },
        )
        /*
         * ----------------------------------------------------
         * NEED-SPECIFIC ATMOSPHERIC TINT
         * ----------------------------------------------------
         *
         * Very subtle. Identity comes from the need profile,
         * while the 1-7 response still retains its common
         * semantic colour mapping.
         */
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    profile.primaryColor.copy(
                                        alpha = 0.055f
                                    ),

                                    profile.secondaryColor.copy(
                                        alpha = 0.025f
                                    ),

                                    Color.Transparent,
                                ),
                            radius = 1250f,
                        )
                    )
        )
    }
}



