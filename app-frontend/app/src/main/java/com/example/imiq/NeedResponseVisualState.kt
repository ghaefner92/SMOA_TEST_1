package com.example.imiq

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs

data class NeedResponseVisualState(
    val response: Int?,
    val scale: Float,
    val halo: Float,
    val opacity: Float,
    val pulseAmplitude: Float,
    val deformationMultiplier: Float,
    val turbulenceMultiplier: Float,
    val gloss: Float,
    val responseColor: Color,
)

fun resolveNeedResponseVisualState(
    profile: NeedVisualProfile,
    response: Int?,
): NeedResponseVisualState {

    val safeResponse =
        response?.coerceIn(1, 7)

    if (safeResponse == null) {
        return NeedResponseVisualState(
            response = null,
            scale = profile.dynamics.baseScale * 0.88f,
            halo = profile.dynamics.haloStrength * 0.55f,
            opacity = 0.46f,
            pulseAmplitude = 0.015f,
            deformationMultiplier = 0.72f,
            turbulenceMultiplier = 0.70f,
            gloss = profile.dynamics.glossStrength * 0.70f,
            responseColor = profile.secondaryColor,
        )
    }

    val normalized =
        (safeResponse - 1) / 6f

    val centerDistance =
        abs(safeResponse - 4) / 3f

    val lowColor =
        Color(0xFFE65B49)

    val middleColor =
        Color(0xFF9A86D8)

    val highColor =
        Color(0xFF3D84EA)

    val responseColor =
        if (safeResponse <= 4) {
            val local =
                (safeResponse - 1) / 3f

            lerp(
                lowColor,
                middleColor,
                local,
            )
        } else {
            val local =
                (safeResponse - 4) / 3f

            lerp(
                middleColor,
                highColor,
                local,
            )
        }

    /*
     * Response controls visual presence, not psychological state.
     *
     * Higher values:
     * - slightly larger
     * - brighter
     * - stronger halo
     * - more visually coherent
     *
     * Motion speed is intentionally NOT increased.
     */
    val scale =
        profile.dynamics.baseScale *
            (
                0.78f +
                    normalized * 0.36f
            )

    val halo =
        profile.dynamics.haloStrength *
            (
                0.62f +
                    normalized * 0.78f
            )

    val opacity =
        0.50f +
            normalized * 0.30f

    /*
     * Pulse remains restrained.
     * Reliability can therefore become "stronger"
     * without looking more excited.
     */
    val pulseAmplitude =
        0.018f +
            normalized * 0.020f

    /*
     * Extreme responses have slightly stronger visual definition.
     * This is purely presentation.
     */
    val definitionBoost =
        0.92f +
            centerDistance * 0.12f

    val deformationMultiplier =
        definitionBoost

    val turbulenceMultiplier =
        0.84f +
            normalized * 0.16f

    val gloss =
        profile.dynamics.glossStrength *
            (
                0.78f +
                    normalized * 0.30f
            )

    return NeedResponseVisualState(
        response = safeResponse,
        scale = scale,
        halo = halo,
        opacity = opacity,
        pulseAmplitude = pulseAmplitude,
        deformationMultiplier = deformationMultiplier,
        turbulenceMultiplier = turbulenceMultiplier,
        gloss = gloss,
        responseColor = responseColor,
    )
}
