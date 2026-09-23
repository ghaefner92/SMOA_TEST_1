package com.example.imiq

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader

/**
 * Local GLB representation of the seven discrete valence states.
 *
 * The stored questionnaire value remains null or an exact Int in 1..7.
 * Continuous weights exist only in this presentation component.
 */
@Composable
fun Valence3DFace(
    selectedValue: Int?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    transportMode: String? = null,
) {
    require(selectedValue == null || selectedValue in 1..7)
    require(transportMode == null || transportMode in setOf("walk", "bike", "pt", "car"))

    if (!enabled || LocalInspectionMode.current) {
        ValenceHumanFace(
            selectedValue = selectedValue,
            modifier = modifier,
        )
        return
    }

    val targetValence = selectedValue?.let { (it - 4) / 3f } ?: 0f
    val visualValence by animateFloatAsState(
        targetValue = targetValence,
        animationSpec = tween(durationMillis = 360),
        label = "valence3DExpression",
    )

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val environment = rememberEnvironment(
        environmentLoader = environmentLoader,
        isOpaque = false,
    )

    val characterAnimation = when (transportMode) {
        "walk" -> "walk"
        "bike" -> "bike_ride"
        "car" -> "drive"
        "pt" -> "transit_sit"
        null -> null
        else -> null
    }
    val assetFileLocation = if (transportMode == null) {
        "models/valence_face.glb"
    } else {
        "models/imiq_character.glb"
    }

    val modelInstance = rememberModelInstance(
        modelLoader = modelLoader,
        assetFileLocation = assetFileLocation,
    )

    if (modelInstance == null) {
        ValenceHumanFace(
            selectedValue = selectedValue,
            modifier = modifier,
        )
        return
    }

    val cameraNode = rememberCameraNode(engine) {
        position = Position(
            x = 0f,
            y = 0f,
            z = if (transportMode == null) 3.15f else 4.65f,
        )
        lookAt(
            Position(
                x = 0f,
                y = 0f,
                z = 0f,
            )
        )
    }

    val negativeWeight = (-visualValence).coerceIn(0f, 1f)
    val positiveWeight = visualValence.coerceIn(0f, 1f)

    SceneView(
        modifier = modifier,
        // TextureView participates in the card's Compose translation, rotation and clipping.
        surfaceType = SurfaceType.TextureSurface,
        engine = engine,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        environment = environment,
        isOpaque = false,
        cameraNode = cameraNode,
        cameraManipulator = null,
        onGestureListener = null,
    ) {
        ModelNode(
            modelInstance = modelInstance,
            autoAnimate = transportMode != null,
            animationName = characterAnimation,
            animationLoop = true,
            scaleToUnits = if (transportMode == null) 1.70f else 2.25f,
            position = Position(
                x = 0f,
                y = if (transportMode == null) -0.02f else 0f,
                z = 0f,
            ),
            isEditable = false,
        ) {
            val faceNode = parentNode as ModelNode
            // The DSL's `apply` runs only at creation. Publish every animated expression
            // after composition, while the DSL retains ownership of the same native node.
            SideEffect {
                faceNode.setMorphWeights(
                    weights = floatArrayOf(
                        negativeWeight,
                        positiveWeight,
                    ),
                    offset = 0,
                )
            }
        }
    }
}
