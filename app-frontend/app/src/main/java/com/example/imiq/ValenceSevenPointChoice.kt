package com.example.imiq

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Discrete seven-point affective choice.
 *
 * Questionnaire contract remains Int 1..7.
 * Numerical values are intentionally not shown to participants.
 */
@Composable
fun ValenceSevenPointChoice(
    selectedValue: Int?,
    de: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(
        selectedValue == null ||
            selectedValue in 1..7
    )

    val primaryText =
        Color(0xFF24262B)

    val secondaryText =
        Color(0xFF66686E)

    Column(
        modifier = modifier,
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    0.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            (1..7).forEach { value ->
                val label = valenceSemanticLabel(value, de)

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .semantics { contentDescription = label }
                            .selectable(
                                selected = selectedValue == value,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) },
                            ),
                    contentAlignment =
                        Alignment.Center,
                ) {
                    ValenceMicroFace(
                        value = value,
                        selected =
                            selectedValue ==
                                value,
                    )
                }
            }
        }

        Spacer(
            Modifier.height(
                8.dp
            )
        )

        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            text =
                selectedValue?.let {
                    valenceSemanticLabel(
                        value = it,
                        de = de,
                    )
                }
                    ?: if (de)
                        "Wähle, wie es sich für dich anfühlt"
                    else
                        "Choose how it feels to you",

            fontFamily =
                PassportBodyFontFamily,

            fontWeight =
                if (selectedValue == null)
                    FontWeight.Normal
                else
                    FontWeight.SemiBold,

            fontSize =
                16.sp,

            letterSpacing =
                0.sp,

            color =
                if (selectedValue == null)
                    secondaryText
                else
                    primaryText,
        )

        Spacer(
            Modifier.height(
                4.dp
            )
        )

        Text(
            text =
                if (de)
                    "unangenehm  ·  neutral  ·  angenehm"
                else
                    "unpleasant  ·  neutral  ·  pleasant",

            fontFamily =
                PassportBodyFontFamily,

            fontWeight =
                FontWeight.Normal,

            fontSize =
                11.sp,

            letterSpacing =
                0.3.sp,

            color =
                secondaryText.copy(
                    alpha = 0.72f
                ),
        )
    }
}


@Composable
private fun ValenceMicroFace(
    value: Int,
    selected: Boolean,
) {
    val affect =
        when (value) {
            1 -> -1.00f
            2 -> -0.67f
            3 -> -0.33f
            4 -> 0.00f
            5 -> 0.33f
            6 -> 0.67f
            7 -> 1.00f
            else -> 0.00f
        }

    val negative =
        Color(0xFFE15E55)

    val neutral =
        Color(0xFF9587C8)

    val positive =
        Color(0xFF3D9DCE)

    val affectColor =
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

    val scale by
        animateFloatAsState(
            targetValue =
                if (selected)
                    1.08f
                else
                    1f,
            animationSpec =
                tween(
                    durationMillis = 220
                ),
            label = "microFaceScale",
        )

    val borderColor by
        animateColorAsState(
            targetValue =
                if (selected)
                    affectColor
                else
                    Color(0xFFD8D4CE),
            animationSpec =
                tween(
                    durationMillis = 220
                ),
            label = "microFaceBorder",
        )

    val backgroundColor by
        animateColorAsState(
            targetValue =
                if (selected)
                    affectColor.copy(
                        alpha = 0.13f
                    )
                else
                    Color(0xFFF7F4EE),
            animationSpec =
                tween(
                    durationMillis = 220
                ),
            label = "microFaceBackground",
        )

    Box(
        modifier =
            Modifier
                .size(
                    38.dp
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(
                    color =
                        backgroundColor,
                    shape =
                        CircleShape,
                )
                .border(
                    width =
                        if (selected)
                            2.dp
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

        Canvas(
            modifier =
                Modifier
                    .size(
                        31.dp
                    )
        ) {
            val w =
                size.width

            val h =
                size.height

            val center =
                Offset(
                    w * 0.50f,
                    h * 0.51f,
                )

            /*
             * Eyes
             */
            val eyeY =
                h * 0.39f

            val eyeOffset =
                w * 0.17f

            val eyeRadius =
                w *
                    (
                        0.035f +
                            affect
                                .coerceAtLeast(
                                    0f
                                ) *
                                0.004f
                    )

            drawCircle(
                color =
                    Color(0xFF38363C),
                radius =
                    eyeRadius,
                center =
                    Offset(
                        center.x -
                            eyeOffset,
                        eyeY,
                    ),
            )

            drawCircle(
                color =
                    Color(0xFF38363C),
                radius =
                    eyeRadius,
                center =
                    Offset(
                        center.x +
                            eyeOffset,
                        eyeY,
                    ),
            )

            /*
             * Brows remain subtle.
             */
            val browTilt =
                affect *
                    h *
                    0.018f

            drawLine(
                color =
                    Color(0xFF555159)
                        .copy(
                            alpha = 0.58f
                        ),
                start =
                    Offset(
                        center.x -
                            eyeOffset -
                            w * 0.065f,
                        eyeY -
                            h * 0.10f +
                            browTilt,
                    ),
                end =
                    Offset(
                        center.x -
                            eyeOffset +
                            w * 0.065f,
                        eyeY -
                            h * 0.10f -
                            browTilt,
                    ),
                strokeWidth =
                    w * 0.025f,
                cap =
                    StrokeCap.Round,
            )

            drawLine(
                color =
                    Color(0xFF555159)
                        .copy(
                            alpha = 0.58f
                        ),
                start =
                    Offset(
                        center.x +
                            eyeOffset -
                            w * 0.065f,
                        eyeY -
                            h * 0.10f -
                            browTilt,
                    ),
                end =
                    Offset(
                        center.x +
                            eyeOffset +
                            w * 0.065f,
                        eyeY -
                            h * 0.10f +
                            browTilt,
                    ),
                strokeWidth =
                    w * 0.025f,
                cap =
                    StrokeCap.Round,
            )

            /*
             * Mouth curvature is the dominant valence cue.
             */
            val mouthY =
                h * 0.68f

            val mouthHalfWidth =
                w *
                    (
                        0.17f +
                            abs(affect) *
                                0.025f
                    )

            val curve =
                affect *
                    h *
                    0.115f

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
                            curve,
                        center.x +
                            mouthHalfWidth,
                        mouthY,
                    )
                }

            drawPath(
                path =
                    mouth,
                color =
                    Color(0xFF4A4146),
                style =
                    Stroke(
                        width =
                            w * 0.035f,
                        cap =
                            StrokeCap.Round,
                    ),
            )

            /*
             * Tiny affective accent.
             */
            drawCircle(
                color =
                    affectColor.copy(
                        alpha =
                            if (selected)
                                0.20f
                            else
                                0.08f
                    ),
                radius =
                    w * 0.055f,
                center =
                    Offset(
                        center.x -
                            w * 0.22f,
                        h * 0.56f,
                    ),
            )

            drawCircle(
                color =
                    affectColor.copy(
                        alpha =
                            if (selected)
                                0.20f
                            else
                                0.08f
                    ),
                radius =
                    w * 0.055f,
                center =
                    Offset(
                        center.x +
                            w * 0.22f,
                        h * 0.56f,
                    ),
            )
        }
    }
}


private fun valenceSemanticLabel(
    value: Int,
    de: Boolean,
): String =
    if (de) {
        when (value) {
            1 ->
                "Sehr unangenehm"

            2 ->
                "Unangenehm"

            3 ->
                "Eher unangenehm"

            4 ->
                "Neutral"

            5 ->
                "Eher angenehm"

            6 ->
                "Angenehm"

            7 ->
                "Sehr angenehm"

            else ->
                ""
        }
    } else {
        when (value) {
            1 ->
                "Very unpleasant"

            2 ->
                "Unpleasant"

            3 ->
                "Slightly unpleasant"

            4 ->
                "Neutral"

            5 ->
                "Slightly pleasant"

            6 ->
                "Pleasant"

            7 ->
                "Very pleasant"

            else ->
                ""
        }
    }


