package com.example.imiq

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import dev.andrefrelicot.papershaders.shaders.MetaballsAgsl

/**
 * GPU-rendered affective visual feedback for an explicit 1..7 response.
 *
 * selectedValue remains the questionnaire's exact discrete response.
 * None of the visual properties are fed back into HOTCO.
 */
@Composable
fun ReactiveMetaballsBackground(
    profile: NeedVisualProfile,
    selectedValue: Int?,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        LivingPlasmaBackground(
            selectedValue = selectedValue,
            modifier = modifier,
        )
        return
    }

    ReactiveMetaballsApi33(
        profile = profile,
        selectedValue = selectedValue,
        modifier = modifier,
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ReactiveMetaballsApi33(
    profile: NeedVisualProfile,
    selectedValue: Int?,
    modifier: Modifier,
) {
    val runtimeShader = remember {
        RuntimeShader(MetaballsAgsl)
    }

    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = runtimeShader
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    val noiseBitmap = remember(context) {
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ps_noise,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            },
        )
    }

    val noiseShader = remember(noiseBitmap) {
        BitmapShader(
            noiseBitmap,
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP,
        ).apply {
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
    }

    var frameMs by remember {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }

        while (true) {
            withFrameNanos { now ->
                frameMs =
                    (now - start) /
                        1_000_000f
            }
        }
    }
    val palette =
        remember(selectedValue) {
            metaballPalette(selectedValue)
        }

    val response =
        selectedValue?.coerceIn(1, 7)

    val responseNorm =
        if (response == null)
            0.50f
        else
            (response - 1) / 6f

    val dynamics =
        profile.dynamics

    /*
     * Need identity now changes the actual geometry of the field.
     *
     * Reliability:
     *   high cohesion, low deformation/turbulence
     *
     * Autonomy:
     *   lower cohesion, high deformation/turbulence
     *
     * Comfort:
     *   large soft forms with restrained movement
     */

    val blobSize =
        (
            0.42f +
                dynamics.cohesion * 0.25f +
                dynamics.baseScale * 0.16f -
                dynamics.turbulence * 0.12f -
                dynamics.deformation * 0.08f +
                responseNorm * 0.12f
        )
            .coerceIn(
                0.40f,
                0.88f,
            )

    val count =
        (
            6.0f +
                dynamics.turbulence * 18.0f +
                dynamics.deformation * 8.0f +
                (1.0f - dynamics.cohesion) * 6.0f +
                responseNorm * 2.0f
        )
            .coerceIn(
                6.0f,
                22.0f,
            )

    val rhythmFactor =
        (
            7.5f /
                dynamics.pulseSeconds
                    .coerceAtLeast(1.0f)
        )
            .coerceIn(
                0.65f,
                1.35f,
            )

    val speed =
        (
            (
                0.14f +
                    4.2f /
                    dynamics.driftSeconds
                        .coerceAtLeast(1.0f) +
                    dynamics.turbulence * 0.30f +
                    dynamics.deformation * 0.18f
            ) *
                rhythmFactor
        )
            .coerceIn(
                0.20f,
                0.90f,
            )

    val fieldScale =
        (
            0.88f +
                dynamics.deformation * 0.20f +
                dynamics.driftAmplitude * 0.22f
        )
            .coerceIn(
                0.88f,
                1.12f,
            )

    val fieldOffsetX =
        (
            (dynamics.anchorX - 0.50f) *
                0.40f
        )
            .coerceIn(
                -0.20f,
                0.20f,
            )

    val fieldOffsetY =
        (
            (dynamics.anchorY - 0.50f) *
                0.30f
        )
            .coerceIn(
                -0.15f,
                0.15f,
            )

    Canvas(
        modifier = modifier,
    ) {
        val colors =
            FloatArray(8 * 4)

        palette.forEachIndexed { index, color ->
            if (index < 8) {
                colors[index * 4] = color.r
                colors[index * 4 + 1] = color.g
                colors[index * 4 + 2] = color.b
                colors[index * 4 + 3] = color.a
            }
        }

        /*
         * Global Paper-Shaders uniforms.
         */
        runtimeShader.setFloatUniform(
            "u_resolution",
            this.size.width,
            this.size.height,
        )

        runtimeShader.setFloatUniform(
            "u_pixelRatio",
            density,
        )

        runtimeShader.setFloatUniform(
            "u_time",
            frameMs * 0.001f * speed,
        )

        // Cover the whole portrait card rather than fitting a square inside it.
        runtimeShader.setFloatUniform("u_fit", 2f)
        runtimeShader.setFloatUniform("u_scale", fieldScale)
        runtimeShader.setFloatUniform("u_rotation", 0f)

        runtimeShader.setFloatUniform("u_originX", 0.5f)
        runtimeShader.setFloatUniform("u_originY", 0.5f)

        runtimeShader.setFloatUniform("u_offsetX", fieldOffsetX)
        runtimeShader.setFloatUniform("u_offsetY", fieldOffsetY)

        runtimeShader.setFloatUniform("u_worldWidth", 0f)
        runtimeShader.setFloatUniform("u_worldHeight", 0f)

        runtimeShader.setFloatUniform(
            "u_imageAspectRatio",
            1f,
        )

        /*
         * Noise texture used by the original Metaballs shader.
         */
        runtimeShader.setInputBuffer(
            "u_noiseTexture",
            noiseShader,
        )

        runtimeShader.setFloatUniform(
            "u_noiseTextureSize",
            noiseBitmap.width.toFloat(),
            noiseBitmap.height.toFloat(),
        )

        /*
         * Transparent background: the blobs coexist with the white card.
         */
        runtimeShader.setFloatUniform(
            "u_colorBack",
            1f,
            1f,
            1f,
            if (response == null) 0.015f else 0.035f,
        )

        runtimeShader.setFloatUniform(
            "u_colors",
            colors,
        )

        runtimeShader.setFloatUniform(
            "u_colorsCount",
            palette.size.toFloat(),
        )

        runtimeShader.setFloatUniform(
            "u_size",
            blobSize,
        )

        runtimeShader.setFloatUniform(
            "u_count",
            count,
        )

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(
                0f,
                0f,
                this.size.width,
                this.size.height,
                paint,
            )
        }
    }
}

private data class PlasmaColor(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float,
)

private fun metaballPalette(
    selectedValue: Int?,
): List<PlasmaColor> =
    when (selectedValue?.coerceIn(1, 7)) {
        1 ->
            listOf(
                rgb(0xE53935, 0.88f),
                rgb(0xFF6B45, 0.78f),
                rgb(0xFFB15C, 0.62f),
                rgb(0xC62828, 0.76f),
                rgb(0xFFE0D5, 0.50f),
            )

        2 ->
            listOf(
                rgb(0xF05A47, 0.88f),
                rgb(0xFF7A47, 0.80f),
                rgb(0xFFB65E, 0.65f),
                rgb(0xD74735, 0.74f),
                rgb(0xFFE5D8, 0.50f),
            )

        3 ->
            listOf(
                rgb(0xF59E42, 0.88f),
                rgb(0xFFBF55, 0.78f),
                rgb(0xFF7657, 0.65f),
                rgb(0xE78432, 0.75f),
                rgb(0xFFF0D7, 0.52f),
            )

        4 ->
            listOf(
                rgb(0x9B8FD8, 0.88f),
                rgb(0xC987E8, 0.78f),
                rgb(0x6E8CE8, 0.70f),
                rgb(0xE3B5FF, 0.58f),
                rgb(0x775FD1, 0.72f),
            )

        5 ->
            listOf(
                rgb(0x58B4E8, 0.90f),
                rgb(0x59D9DB, 0.78f),
                rgb(0x7B8FFF, 0.70f),
                rgb(0xAFEAF4, 0.60f),
                rgb(0x4389DA, 0.75f),
            )

        6 ->
            listOf(
                rgb(0x318CE7, 0.92f),
                rgb(0x41C7E8, 0.80f),
                rgb(0x655CE8, 0.72f),
                rgb(0xA6E6FF, 0.62f),
                rgb(0x2565D8, 0.78f),
            )

        7 ->
            listOf(
                rgb(0x1769D2, 0.94f),
                rgb(0x20BDEB, 0.84f),
                rgb(0x534BE8, 0.78f),
                rgb(0x8EEBFF, 0.68f),
                rgb(0x1549C5, 0.82f),
            )

        else ->
            listOf(
                rgb(0x9B8FD8, 0.26f),
                rgb(0x58B4E8, 0.22f),
                rgb(0xC987E8, 0.20f),
            )
    }

private fun rgb(
    hex: Int,
    alpha: Float,
): PlasmaColor =
    PlasmaColor(
        r = ((hex shr 16) and 0xFF) / 255f,
        g = ((hex shr 8) and 0xFF) / 255f,
        b = (hex and 0xFF) / 255f,
        a = alpha,
    )




