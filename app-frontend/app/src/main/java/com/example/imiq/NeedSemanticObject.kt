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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Small semantic pseudo-3D object associated with each mobility need.
 *
 * Important:
 * - selectedValue remains the explicit questionnaire response 1..7.
 * - visual feedback does not estimate emotion, confidence or cognition.
 * - nothing rendered here is fed back into HOTCO.
 */
@Composable
fun NeedSemanticObject(
    profile: NeedVisualProfile,
    selectedValue: Int?,
    modifier: Modifier = Modifier,
) {
    val state =
        resolveNeedResponseVisualState(
            profile = profile,
            response = selectedValue,
        )

    val transition =
        rememberInfiniteTransition(
            label = "need-semantic-object"
        )

    /*
     * Motion cadence belongs to the need identity.
     * Response magnitude does not make the object "more excited".
     */
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
                                    profile.dynamics.driftSeconds
                                        .coerceIn(
                                            6f,
                                            16f,
                                        ) *
                                        1000f
                                )
                                    .roundToInt(),
                            easing = LinearEasing,
                        ),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "need-semantic-phase",
        )

    Canvas(
        modifier = modifier,
    ) {
        val d =
            min(
                size.width,
                size.height,
            )

        if (d <= 0f) {
            return@Canvas
        }

        val center =
            Offset(
                size.width * 0.50f,
                size.height * 0.50f,
            )

        val presence =
            state.scale
                .coerceIn(
                    0.58f,
                    1.10f,
                )

        val responseColor =
            state.responseColor

        val primary =
            lerp(
                profile.primaryColor,
                responseColor,
                0.28f,
            )

        val secondary =
            lerp(
                profile.secondaryColor,
                responseColor,
                0.16f,
            )

        val accent =
            lerp(
                profile.accentColor,
                Color.White,
                0.16f,
            )

        /*
         * Shared atmospheric glow keeps all eleven objects
         * inside the same visual language.
         */
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            responseColor.copy(
                                alpha =
                                    (
                                        state.halo *
                                            0.46f
                                    )
                                        .coerceIn(
                                            0f,
                                            0.30f,
                                        )
                            ),
                            primary.copy(
                                alpha = 0.07f
                            ),
                            Color.Transparent,
                        ),
                    center = center,
                    radius =
                        d *
                            0.55f,
                ),
            radius =
                d *
                    0.55f,
            center = center,
        )

        when (
            profile.avatarStyle
        ) {
            NeedAvatarStyle.ORGANIC_ECOSYSTEM ->
                drawOrganicEcosystem(
                    d = d,
                    center = center,
                    scale = presence,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    phase = phase,
                )

            NeedAvatarStyle.BIOENERGETIC_CELL ->
                drawBioenergeticCell(
                    d = d,
                    center = center,
                    scale = presence,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    phase = phase,
                )

            NeedAvatarStyle.PROTECTED_CORE ->
                drawProtectedCore(
                    d = d,
                    center = center,
                    scale = presence,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    phase = phase,
                )

            NeedAvatarStyle.EXPANSIVE_ORBIT ->
                drawAutonomyOrbit(
                    d = d,
                    center = center,
                    scale = presence,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    phase = phase,
                )

            NeedAvatarStyle.DENSE_CRYSTAL ->
                drawCostCrystal(
                    d = d,
                    center = center,
                    scale = presence,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    phase = phase,
                )

            NeedAvatarStyle.DIRECTIONAL_COMET ->
                drawDirectionalComet(
                    d = d,
                    center = center,
                    scale = presence,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    phase = phase,
                )

            NeedAvatarStyle.FLEXIBLE_SHIELD ->
                drawFlexibleShield(
                    d = d,
                    center = center,
                    scale = presence,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    phase = phase,
                )

            NeedAvatarStyle.SECURITY_RING ->
                drawSecurityRing(
                    d = d,
                    center = center,
                    scale = presence,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    phase = phase,
                )

            NeedAvatarStyle.SOFT_GEL ->
                drawComfortPod(
                    d = d,
                    center = center,
                    scale = presence,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    phase = phase,
                    pulseAmplitude =
                        state.pulseAmplitude,
                )

            NeedAvatarStyle.STABLE_CORE ->
                drawReliabilityDial(
                    d = d,
                    center = center,
                    scale = presence,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    phase = phase,
                )

            NeedAvatarStyle.BIOLOGICAL_MEMBRANE ->
                drawBiologicalMembrane(
                    d = d,
                    center = center,
                    scale = presence,
                    primary = primary,
                    secondary = secondary,
                    accent = accent,
                    phase = phase,
                )
        }
    }
}


/* ============================================================
 * RELIABILITY
 * Stable glass dial / clock
 * ============================================================ */

private fun DrawScope.drawReliabilityDial(
    d: Float,
    center: Offset,
    scale: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
    phase: Float,
) {
    val radius =
        d *
            0.33f *
            scale

    drawCircle(
        brush =
            Brush.radialGradient(
                colorStops =
                    arrayOf(
                        0.00f to
                            Color.White.copy(
                                alpha = 0.90f
                            ),

                        0.18f to
                            accent.copy(
                                alpha = 0.62f
                            ),

                        0.58f to
                            primary.copy(
                                alpha = 0.56f
                            ),

                        1.00f to
                            secondary.copy(
                                alpha = 0.18f
                            ),
                    ),
                center =
                    Offset(
                        center.x -
                            radius * 0.28f,
                        center.y -
                            radius * 0.32f,
                    ),
                radius =
                    radius *
                        1.30f,
            ),
        radius = radius,
        center = center,
    )

    drawCircle(
        color =
            Color.White.copy(
                alpha = 0.56f
            ),
        radius =
            radius *
                0.88f,
        center = center,
        style =
            Stroke(
                width =
                    d *
                        0.017f
            ),
    )

    repeat(12) { index ->
        val angle =
            index *
                2f *
                PI.toFloat() /
                12f

        drawLine(
            color =
                Color.White.copy(
                    alpha = 0.48f
                ),
            start =
                pointOnCircle(
                    center,
                    radius * 0.67f,
                    angle,
                ),
            end =
                pointOnCircle(
                    center,
                    radius * 0.79f,
                    angle,
                ),
            strokeWidth =
                d *
                    0.009f,
            cap = StrokeCap.Round,
        )
    }

    val handAngle =
        phase *
            2f *
            PI.toFloat() -
            PI.toFloat() *
            0.5f

    drawLine(
        color =
            Color.White.copy(
                alpha = 0.88f
            ),
        start = center,
        end =
            pointOnCircle(
                center,
                radius * 0.53f,
                handAngle,
            ),
        strokeWidth =
            d *
                0.019f,
        cap = StrokeCap.Round,
    )

    drawCircle(
        color = Color.White,
        radius =
            radius *
                0.065f,
        center = center,
    )
}


/* ============================================================
 * AUTONOMY
 * Compass / open orbital navigation system
 * ============================================================ */

private fun DrawScope.drawAutonomyOrbit(
    d: Float,
    center: Offset,
    scale: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
    phase: Float,
) {
    val coreRadius =
        d *
            0.13f *
            scale

    val orbitRadius =
        d *
            0.31f *
            scale

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White,
                        accent.copy(
                            alpha = 0.80f
                        ),
                        primary.copy(
                            alpha = 0.42f
                        ),
                    ),
                center =
                    Offset(
                        center.x -
                            coreRadius * 0.25f,
                        center.y -
                            coreRadius * 0.30f,
                    ),
                radius =
                    coreRadius *
                        1.35f,
            ),
        radius = coreRadius,
        center = center,
    )

    drawArc(
        color =
            accent.copy(
                alpha = 0.58f
            ),
        startAngle = -48f,
        sweepAngle = 232f,
        useCenter = false,
        topLeft =
            Offset(
                center.x -
                    orbitRadius,
                center.y -
                    orbitRadius,
            ),
        size =
            Size(
                orbitRadius * 2f,
                orbitRadius * 2f,
            ),
        style =
            Stroke(
                width =
                    d *
                        0.016f,
                cap = StrokeCap.Round,
            ),
    )

    drawArc(
        color =
            secondary.copy(
                alpha = 0.34f
            ),
        startAngle = 145f,
        sweepAngle = 145f,
        useCenter = false,
        topLeft =
            Offset(
                center.x -
                    orbitRadius * 0.72f,
                center.y -
                    orbitRadius * 0.72f,
            ),
        size =
            Size(
                orbitRadius * 1.44f,
                orbitRadius * 1.44f,
            ),
        style =
            Stroke(
                width =
                    d *
                        0.010f,
                cap = StrokeCap.Round,
            ),
    )

    val angle1 =
        phase *
            2f *
            PI.toFloat()

    val angle2 =
        -phase *
            2f *
            PI.toFloat() +
            1.5f

    val node1 =
        pointOnCircle(
            center,
            orbitRadius,
            angle1,
        )

    val node2 =
        pointOnCircle(
            center,
            orbitRadius * 0.72f,
            angle2,
        )

    drawGlowNode(
        d = d,
        position = node1,
        radiusFraction = 0.050f,
        accent = accent,
    )

    drawGlowNode(
        d = d,
        position = node2,
        radiusFraction = 0.035f,
        accent = secondary,
    )

    val pointer =
        Path().apply {
            moveTo(
                center.x,
                center.y -
                    coreRadius *
                    1.42f,
            )

            lineTo(
                center.x +
                    coreRadius *
                    0.42f,
                center.y +
                    coreRadius *
                    0.55f,
            )

            lineTo(
                center.x,
                center.y +
                    coreRadius *
                    0.18f,
            )

            lineTo(
                center.x -
                    coreRadius *
                    0.42f,
                center.y +
                    coreRadius *
                    0.55f,
            )

            close()
        }

    drawPath(
        path = pointer,
        brush =
            Brush.verticalGradient(
                colors =
                    listOf(
                        Color.White,
                        accent.copy(
                            alpha = 0.45f
                        ),
                    )
            ),
    )
}


/* ============================================================
 * COMFORT
 * Soft gel / padded pod
 * ============================================================ */

private fun DrawScope.drawComfortPod(
    d: Float,
    center: Offset,
    scale: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
    phase: Float,
    pulseAmplitude: Float,
) {
    val breathing =
        1f +
            sin(
                phase *
                    2f *
                    PI.toFloat()
            ) *
            pulseAmplitude *
            0.36f

    val width =
        d *
            0.69f *
            scale *
            breathing

    val height =
        width *
            0.62f

    drawOval(
        brush =
            Brush.radialGradient(
                colorStops =
                    arrayOf(
                        0.00f to
                            Color.White.copy(
                                alpha = 0.94f
                            ),

                        0.20f to
                            accent.copy(
                                alpha = 0.72f
                            ),

                        0.55f to
                            primary.copy(
                                alpha = 0.58f
                            ),

                        1.00f to
                            secondary.copy(
                                alpha = 0.18f
                            ),
                    ),
                center =
                    Offset(
                        center.x -
                            width * 0.18f,
                        center.y -
                            height * 0.34f,
                    ),
                radius =
                    width *
                        0.70f,
            ),
        topLeft =
            Offset(
                center.x -
                    width * 0.50f,
                center.y -
                    height * 0.50f,
            ),
        size =
            Size(
                width,
                height,
            ),
    )

    drawArc(
        color =
            Color.White.copy(
                alpha = 0.38f
            ),
        startAngle = 16f,
        sweepAngle = 148f,
        useCenter = false,
        topLeft =
            Offset(
                center.x -
                    width * 0.30f,
                center.y -
                    height * 0.27f,
            ),
        size =
            Size(
                width * 0.60f,
                height * 0.54f,
            ),
        style =
            Stroke(
                width =
                    d *
                        0.017f,
                cap = StrokeCap.Round,
            ),
    )

    drawOval(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White.copy(
                            alpha = 0.48f
                        ),
                        accent.copy(
                            alpha = 0.16f
                        ),
                        Color.Transparent,
                    ),
                center = center,
                radius =
                    width *
                        0.27f,
            ),
        topLeft =
            Offset(
                center.x -
                    width * 0.23f,
                center.y -
                    height * 0.21f,
            ),
        size =
            Size(
                width * 0.46f,
                height * 0.42f,
            ),
    )
}


/* ============================================================
 * ENVIRONMENT
 * Organic micro-ecosystem / leaf
 * ============================================================ */

private fun DrawScope.drawOrganicEcosystem(
    d: Float,
    center: Offset,
    scale: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
    phase: Float,
) {
    val radius =
        d *
            0.32f *
            scale

    val sway =
        sin(
            phase *
                2f *
                PI.toFloat()
        ) *
            d *
            0.025f

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White.copy(
                            alpha = 0.78f
                        ),
                        accent.copy(
                            alpha = 0.40f
                        ),
                        primary.copy(
                            alpha = 0.26f
                        ),
                        Color.Transparent,
                    ),
                center = center,
                radius =
                    radius *
                        1.30f,
            ),
        radius =
            radius *
                1.20f,
        center = center,
    )

    drawLine(
        color =
            Color.White.copy(
                alpha = 0.66f
            ),
        start =
            Offset(
                center.x,
                center.y +
                    radius * 0.65f,
            ),
        end =
            Offset(
                center.x + sway,
                center.y -
                    radius * 0.62f,
            ),
        strokeWidth =
            d *
                0.018f,
        cap = StrokeCap.Round,
    )

    val leftLeaf =
        leafPath(
            cx =
                center.x -
                    radius * 0.13f +
                    sway * 0.35f,
            cy =
                center.y -
                    radius * 0.15f,
            width =
                radius *
                    0.80f,
            height =
                radius *
                    0.48f,
            direction = -1f,
        )

    val rightLeaf =
        leafPath(
            cx =
                center.x +
                    radius * 0.18f +
                    sway * 0.55f,
            cy =
                center.y +
                    radius * 0.12f,
            width =
                radius *
                    0.72f,
            height =
                radius *
                    0.44f,
            direction = 1f,
        )

    drawPath(
        path = leftLeaf,
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White.copy(
                            alpha = 0.76f
                        ),
                        accent.copy(
                            alpha = 0.64f
                        ),
                        primary.copy(
                            alpha = 0.72f
                        ),
                    ),
                center =
                    Offset(
                        center.x -
                            radius * 0.26f,
                        center.y -
                            radius * 0.28f,
                    ),
                radius =
                    radius *
                        0.72f,
            ),
    )

    drawPath(
        path = rightLeaf,
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White.copy(
                            alpha = 0.68f
                        ),
                        secondary.copy(
                            alpha = 0.68f
                        ),
                        primary.copy(
                            alpha = 0.64f
                        ),
                    ),
                center =
                    Offset(
                        center.x +
                            radius * 0.28f,
                        center.y -
                            radius * 0.02f,
                    ),
                radius =
                    radius *
                        0.70f,
            ),
    )

    repeat(3) { index ->
        val angle =
            phase *
                2f *
                PI.toFloat() +
                index *
                    2f *
                    PI.toFloat() /
                    3f

        drawGlowNode(
            d = d,
            position =
                pointOnCircle(
                    center,
                    radius * 0.92f,
                    angle,
                ),
            radiusFraction = 0.025f,
            accent = accent,
        )
    }
}


/* ============================================================
 * PHYSICAL ACTIVITY
 * Bioenergetic cell / kinetic organism
 * ============================================================ */

private fun DrawScope.drawBioenergeticCell(
    d: Float,
    center: Offset,
    scale: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
    phase: Float,
) {
    val radius =
        d *
            0.30f *
            scale

    val pulse =
        1f +
            sin(
                phase *
                    2f *
                    PI.toFloat()
            ) *
            0.06f

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White.copy(
                            alpha = 0.90f
                        ),
                        accent.copy(
                            alpha = 0.66f
                        ),
                        primary.copy(
                            alpha = 0.48f
                        ),
                        secondary.copy(
                            alpha = 0.16f
                        ),
                    ),
                center =
                    Offset(
                        center.x -
                            radius * 0.20f,
                        center.y -
                            radius * 0.24f,
                    ),
                radius =
                    radius *
                        1.10f,
            ),
        radius =
            radius *
                pulse,
        center = center,
    )

    drawCircle(
        color =
            Color.White.copy(
                alpha = 0.44f
            ),
        radius =
            radius *
                0.70f,
        center = center,
        style =
            Stroke(
                width =
                    d *
                        0.014f
            ),
    )

    repeat(6) { index ->
        val angle =
            phase *
                PI.toFloat() *
                2f +
                index *
                    PI.toFloat() /
                    3f

        val node =
            pointOnCircle(
                center,
                radius * 0.82f,
                angle,
            )

        drawLine(
            color =
                accent.copy(
                    alpha = 0.32f
                ),
            start = center,
            end = node,
            strokeWidth =
                d *
                    0.008f,
            cap = StrokeCap.Round,
        )

        drawGlowNode(
            d = d,
            position = node,
            radiusFraction = 0.030f,
            accent =
                if (
                    index % 2 == 0
                )
                    accent
                else
                    secondary,
        )
    }

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White,
                        accent.copy(
                            alpha = 0.76f
                        ),
                        Color.Transparent,
                    ),
                center = center,
                radius =
                    radius *
                        0.34f,
            ),
        radius =
            radius *
                0.34f,
        center = center,
    )
}


/* ============================================================
 * PRIVACY
 * Protected inner core / capsule
 * ============================================================ */

private fun DrawScope.drawProtectedCore(
    d: Float,
    center: Offset,
    scale: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
    phase: Float,
) {
    val radius =
        d *
            0.31f *
            scale

    val breathing =
        1f +
            sin(
                phase *
                    2f *
                    PI.toFloat()
            ) *
            0.025f

    drawCircle(
        color =
            primary.copy(
                alpha = 0.22f
            ),
        radius =
            radius *
                1.02f *
                breathing,
        center = center,
    )

    drawCircle(
        color =
            accent.copy(
                alpha = 0.48f
            ),
        radius =
            radius *
                0.82f,
        center = center,
        style =
            Stroke(
                width =
                    d *
                        0.020f
            ),
    )

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White.copy(
                            alpha = 0.96f
                        ),
                        accent.copy(
                            alpha = 0.72f
                        ),
                        primary.copy(
                            alpha = 0.52f
                        ),
                        secondary.copy(
                            alpha = 0.18f
                        ),
                    ),
                center =
                    Offset(
                        center.x -
                            radius * 0.10f,
                        center.y -
                            radius * 0.13f,
                    ),
                radius =
                    radius *
                        0.56f,
            ),
        radius =
            radius *
                0.45f,
        center = center,
    )

    drawArc(
        color =
            Color.White.copy(
                alpha = 0.32f
            ),
        startAngle = 205f,
        sweepAngle = 195f,
        useCenter = false,
        topLeft =
            Offset(
                center.x -
                    radius,
                center.y -
                    radius,
            ),
        size =
            Size(
                radius * 2f,
                radius * 2f,
            ),
        style =
            Stroke(
                width =
                    d *
                        0.012f,
                cap = StrokeCap.Round,
            ),
    )
}


/* ============================================================
 * COST
 * Dense faceted crystal
 * ============================================================ */

private fun DrawScope.drawCostCrystal(
    d: Float,
    center: Offset,
    scale: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
    phase: Float,
) {
    val radius =
        d *
            0.30f *
            scale

    val floatOffset =
        sin(
            phase *
                2f *
                PI.toFloat()
        ) *
            d *
            0.012f

    val c =
        Offset(
            center.x,
            center.y +
                floatOffset,
        )

    val top =
        Offset(
            c.x,
            c.y -
                radius,
        )

    val rightTop =
        Offset(
            c.x +
                radius * 0.72f,
            c.y -
                radius * 0.28f,
        )

    val rightBottom =
        Offset(
            c.x +
                radius * 0.54f,
            c.y +
                radius * 0.64f,
        )

    val bottom =
        Offset(
            c.x,
            c.y +
                radius,
        )

    val leftBottom =
        Offset(
            c.x -
                radius * 0.54f,
            c.y +
                radius * 0.64f,
        )

    val leftTop =
        Offset(
            c.x -
                radius * 0.72f,
            c.y -
                radius * 0.28f,
        )

    val crystal =
        Path().apply {
            moveTo(
                top.x,
                top.y,
            )
            lineTo(
                rightTop.x,
                rightTop.y,
            )
            lineTo(
                rightBottom.x,
                rightBottom.y,
            )
            lineTo(
                bottom.x,
                bottom.y,
            )
            lineTo(
                leftBottom.x,
                leftBottom.y,
            )
            lineTo(
                leftTop.x,
                leftTop.y,
            )
            close()
        }

    drawPath(
        path = crystal,
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White.copy(
                            alpha = 0.92f
                        ),
                        accent.copy(
                            alpha = 0.72f
                        ),
                        primary.copy(
                            alpha = 0.72f
                        ),
                        secondary.copy(
                            alpha = 0.36f
                        ),
                    ),
                center =
                    Offset(
                        c.x -
                            radius * 0.22f,
                        c.y -
                            radius * 0.30f,
                    ),
                radius =
                    radius *
                        1.45f,
            ),
    )

    listOf(
        top,
        rightTop,
        rightBottom,
        bottom,
        leftBottom,
        leftTop,
    ).forEach { point ->
        drawLine(
            color =
                Color.White.copy(
                    alpha = 0.26f
                ),
            start = c,
            end = point,
            strokeWidth =
                d *
                    0.009f,
            cap = StrokeCap.Round,
        )
    }

    drawLine(
        color =
            Color.White.copy(
                alpha = 0.48f
            ),
        start = leftTop,
        end = rightBottom,
        strokeWidth =
            d *
                0.009f,
        cap = StrokeCap.Round,
    )
}


/* ============================================================
 * TIME SAVING
 * Directional comet / chronodynamic cue
 * ============================================================ */

private fun DrawScope.drawDirectionalComet(
    d: Float,
    center: Offset,
    scale: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
    phase: Float,
) {
    val radius =
        d *
            0.18f *
            scale

    val drift =
        sin(
            phase *
                2f *
                PI.toFloat()
        ) *
            d *
            0.018f

    val head =
        Offset(
            center.x +
                d * 0.13f +
                drift,
            center.y -
                d * 0.10f,
        )

    val tailStartX =
        center.x -
            d * 0.35f

    repeat(3) { index ->
        val offset =
            (
                index -
                    1
            ) *
                d *
                0.085f

        drawLine(
            color =
                when (index) {
                    0 ->
                        secondary.copy(
                            alpha = 0.18f
                        )

                    1 ->
                        accent.copy(
                            alpha = 0.42f
                        )

                    else ->
                        primary.copy(
                            alpha = 0.24f
                        )
                },
            start =
                Offset(
                    tailStartX,
                    head.y +
                        offset +
                        d * 0.13f,
                ),
            end =
                Offset(
                    head.x -
                        radius * 0.55f,
                    head.y +
                        offset * 0.22f,
                ),
            strokeWidth =
                d *
                    (
                        0.012f +
                            index *
                                0.004f
                    ),
            cap = StrokeCap.Round,
        )
    }

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White,
                        accent.copy(
                            alpha = 0.84f
                        ),
                        primary.copy(
                            alpha = 0.64f
                        ),
                        secondary.copy(
                            alpha = 0.16f
                        ),
                    ),
                center =
                    Offset(
                        head.x -
                            radius * 0.26f,
                        head.y -
                            radius * 0.28f,
                    ),
                radius =
                    radius *
                        1.45f,
            ),
        radius = radius,
        center = head,
    )

    val arrow =
        Path().apply {
            moveTo(
                head.x +
                    radius * 1.45f,
                head.y,
            )

            lineTo(
                head.x +
                    radius * 0.52f,
                head.y -
                    radius * 0.42f,
            )

            lineTo(
                head.x +
                    radius * 0.52f,
                head.y +
                    radius * 0.42f,
            )

            close()
        }

    drawPath(
        path = arrow,
        color =
            Color.White.copy(
                alpha = 0.74f
            ),
    )
}


/* ============================================================
 * TRAFFIC SAFETY
 * Flexible protective shield
 * ============================================================ */

private fun DrawScope.drawFlexibleShield(
    d: Float,
    center: Offset,
    scale: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
    phase: Float,
) {
    val radius =
        d *
            0.30f *
            scale

    val breath =
        1f +
            sin(
                phase *
                    2f *
                    PI.toFloat()
            ) *
            0.025f

    val r =
        radius *
            breath

    val shield =
        Path().apply {
            moveTo(
                center.x,
                center.y -
                    r
            )

            cubicTo(
                center.x +
                    r * 0.82f,
                center.y -
                    r * 0.74f,
                center.x +
                    r * 0.78f,
                center.y +
                    r * 0.24f,
                center.x,
                center.y +
                    r
            )

            cubicTo(
                center.x -
                    r * 0.78f,
                center.y +
                    r * 0.24f,
                center.x -
                    r * 0.82f,
                center.y -
                    r * 0.74f,
                center.x,
                center.y -
                    r
            )

            close()
        }

    drawPath(
        path = shield,
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White.copy(
                            alpha = 0.88f
                        ),
                        accent.copy(
                            alpha = 0.64f
                        ),
                        primary.copy(
                            alpha = 0.56f
                        ),
                        secondary.copy(
                            alpha = 0.20f
                        ),
                    ),
                center =
                    Offset(
                        center.x -
                            r * 0.23f,
                        center.y -
                            r * 0.34f,
                    ),
                radius =
                    r *
                        1.45f,
            ),
    )

    drawPath(
        path = shield,
        color =
            Color.White.copy(
                alpha = 0.44f
            ),
        style =
            Stroke(
                width =
                    d *
                        0.015f
            ),
    )

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White,
                        accent.copy(
                            alpha = 0.72f
                        ),
                        Color.Transparent,
                    ),
                center = center,
                radius =
                    r *
                        0.31f,
            ),
        radius =
            r *
                0.31f,
        center = center,
    )
}


/* ============================================================
 * PERSONAL SECURITY
 * Perimeter/security ring
 * ============================================================ */

private fun DrawScope.drawSecurityRing(
    d: Float,
    center: Offset,
    scale: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
    phase: Float,
) {
    val radius =
        d *
            0.31f *
            scale

    val rotation =
        phase *
            360f

    repeat(3) { index ->
        val r =
            radius *
                (
                    1f -
                        index *
                            0.18f
                )

        drawArc(
            color =
                when (index) {
                    0 ->
                        accent.copy(
                            alpha = 0.56f
                        )

                    1 ->
                        primary.copy(
                            alpha = 0.42f
                        )

                    else ->
                        secondary.copy(
                            alpha = 0.28f
                        )
                },
            startAngle =
                rotation *
                    (
                        if (
                            index % 2 == 0
                        )
                            1f
                        else
                            -1f
                    ) +
                    index *
                        74f,
            sweepAngle =
                205f -
                    index *
                        16f,
            useCenter = false,
            topLeft =
                Offset(
                    center.x - r,
                    center.y - r,
                ),
            size =
                Size(
                    r * 2f,
                    r * 2f,
                ),
            style =
                Stroke(
                    width =
                        d *
                            (
                                0.017f -
                                    index *
                                        0.003f
                            ),
                    cap = StrokeCap.Round,
                ),
        )
    }

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White,
                        accent.copy(
                            alpha = 0.74f
                        ),
                        primary.copy(
                            alpha = 0.42f
                        ),
                    ),
                center =
                    Offset(
                        center.x -
                            radius * 0.06f,
                        center.y -
                            radius * 0.08f,
                    ),
                radius =
                    radius *
                        0.36f,
            ),
        radius =
            radius *
                0.25f,
        center = center,
    )

    repeat(4) { index ->
        val angle =
            phase *
                2f *
                PI.toFloat() +
                index *
                    PI.toFloat() /
                    2f

        drawGlowNode(
            d = d,
            position =
                pointOnCircle(
                    center,
                    radius * 0.91f,
                    angle,
                ),
            radiusFraction = 0.025f,
            accent = accent,
        )
    }
}


/* ============================================================
 * HEALTH PROTECTION
 * Biological membrane
 * ============================================================ */

private fun DrawScope.drawBiologicalMembrane(
    d: Float,
    center: Offset,
    scale: Float,
    primary: Color,
    secondary: Color,
    accent: Color,
    phase: Float,
) {
    val radius =
        d *
            0.31f *
            scale

    val membranePulse =
        1f +
            sin(
                phase *
                    2f *
                    PI.toFloat()
            ) *
            0.035f

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White.copy(
                            alpha = 0.84f
                        ),
                        accent.copy(
                            alpha = 0.52f
                        ),
                        primary.copy(
                            alpha = 0.34f
                        ),
                        secondary.copy(
                            alpha = 0.14f
                        ),
                    ),
                center =
                    Offset(
                        center.x -
                            radius * 0.18f,
                        center.y -
                            radius * 0.20f,
                    ),
                radius =
                    radius *
                        1.24f,
            ),
        radius =
            radius *
                membranePulse,
        center = center,
    )

    drawCircle(
        color =
            Color.White.copy(
                alpha = 0.52f
            ),
        radius =
            radius *
                0.82f,
        center = center,
        style =
            Stroke(
                width =
                    d *
                        0.016f
            ),
    )

    drawCircle(
        color =
            accent.copy(
                alpha = 0.30f
            ),
        radius =
            radius *
                0.61f,
        center = center,
        style =
            Stroke(
                width =
                    d *
                        0.010f
            ),
    )

    repeat(8) { index ->
        val angle =
            phase *
                2f *
                PI.toFloat() *
                0.45f +
                index *
                    2f *
                    PI.toFloat() /
                    8f

        val position =
            pointOnCircle(
                center,
                radius * 0.84f,
                angle,
            )

        drawCircle(
            color =
                Color.White.copy(
                    alpha = 0.68f
                ),
            radius =
                d *
                    0.018f,
            center = position,
        )
    }

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White,
                        accent.copy(
                            alpha = 0.64f
                        ),
                        Color.Transparent,
                    ),
                center = center,
                radius =
                    radius *
                        0.34f,
            ),
        radius =
            radius *
                0.34f,
        center = center,
    )
}


/* ============================================================
 * Shared helpers
 * ============================================================ */

private fun DrawScope.drawGlowNode(
    d: Float,
    position: Offset,
    radiusFraction: Float,
    accent: Color,
) {
    val radius =
        d *
            radiusFraction

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White,
                        accent.copy(
                            alpha = 0.74f
                        ),
                        Color.Transparent,
                    ),
                center = position,
                radius =
                    radius *
                        2.4f,
            ),
        radius =
            radius *
                2.4f,
        center = position,
    )

    drawCircle(
        color = Color.White,
        radius = radius,
        center = position,
    )
}


private fun pointOnCircle(
    center: Offset,
    radius: Float,
    angle: Float,
): Offset =
    Offset(
        x =
            center.x +
                cos(angle) *
                radius,

        y =
            center.y +
                sin(angle) *
                radius,
    )


private fun leafPath(
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    direction: Float,
): Path =
    Path().apply {
        moveTo(
            cx,
            cy,
        )

        cubicTo(
            cx +
                width *
                0.32f *
                direction,
            cy -
                height *
                0.62f,

            cx +
                width *
                0.82f *
                direction,
            cy -
                height *
                0.30f,

            cx +
                width *
                direction,
            cy,
        )

        cubicTo(
            cx +
                width *
                0.70f *
                direction,
            cy +
                height *
                0.56f,

            cx +
                width *
                0.24f *
                direction,
            cy +
                height *
                0.54f,

            cx,
            cy,
        )

        close()
    }
