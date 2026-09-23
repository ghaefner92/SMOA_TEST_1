package com.example.imiq

import androidx.compose.ui.graphics.Color

enum class NeedAvatarStyle {
    ORGANIC_ECOSYSTEM,
    BIOENERGETIC_CELL,
    PROTECTED_CORE,
    EXPANSIVE_ORBIT,
    DENSE_CRYSTAL,
    DIRECTIONAL_COMET,
    FLEXIBLE_SHIELD,
    SECURITY_RING,
    SOFT_GEL,
    STABLE_CORE,
    BIOLOGICAL_MEMBRANE,
}

enum class NeedMotionStyle {
    ORGANIC_GROWTH,
    ELASTIC_PULSE,
    QUIET_FLOAT,
    FREE_EXPANSION,
    COMPACT_PULSE,
    DIRECTIONAL_FLOW,
    PROTECTIVE_BREATH,
    PERIPHERAL_ORBIT,
    VISCOUS_DRIFT,
    REGULAR_PULSE,
    MEMBRANE_FLOW,
}

data class NeedVisualDynamics(
    val cohesion: Float = 0.70f,
    val deformation: Float = 0.30f,
    val pulseSeconds: Float = 7.0f,
    val driftSeconds: Float = 12.0f,
    val driftAmplitude: Float = 0.15f,
    val haloStrength: Float = 0.18f,
    val glossStrength: Float = 0.65f,
    val turbulence: Float = 0.20f,
    val anchorX: Float = 0.72f,
    val anchorY: Float = 0.48f,
    val baseScale: Float = 0.72f,
)
data class NeedVisualProfile(
    val key: String,
    val avatarStyle: NeedAvatarStyle,
    val motionStyle: NeedMotionStyle,
    val primaryColor: Color,
    val secondaryColor: Color,
    val accentColor: Color,
    val readabilityDarkness: Float,
    val dynamics: NeedVisualDynamics = NeedVisualDynamics(),
)

object NeedVisualProfiles {

    private val profiles =
        listOf(
            NeedVisualProfile(
                key = "pro_env",
                avatarStyle = NeedAvatarStyle.ORGANIC_ECOSYSTEM,
                motionStyle = NeedMotionStyle.ORGANIC_GROWTH,
                primaryColor = Color(0xFF24BFA3),
                secondaryColor = Color(0xFF45D9D0),
                accentColor = Color(0xFF9CF4D8),
                readabilityDarkness = 0.42f,
            ),

            NeedVisualProfile(
                key = "physical",
                avatarStyle = NeedAvatarStyle.BIOENERGETIC_CELL,
                motionStyle = NeedMotionStyle.ELASTIC_PULSE,
                primaryColor = Color(0xFF35CFA7),
                secondaryColor = Color(0xFF7DE38D),
                accentColor = Color(0xFFAEF4D3),
                readabilityDarkness = 0.40f,
            ),

            NeedVisualProfile(
                key = "privacy",
                avatarStyle = NeedAvatarStyle.PROTECTED_CORE,
                motionStyle = NeedMotionStyle.QUIET_FLOAT,
                primaryColor = Color(0xFF5447A8),
                secondaryColor = Color(0xFF8676D8),
                accentColor = Color(0xFFC3B7FF),
                readabilityDarkness = 0.54f,
            ),

            NeedVisualProfile(
                key = "autonomy",
                avatarStyle = NeedAvatarStyle.EXPANSIVE_ORBIT,
                motionStyle = NeedMotionStyle.FREE_EXPANSION,
                primaryColor = Color(0xFF356FE5),
                secondaryColor = Color(0xFF745BE7),
                accentColor = Color(0xFF71D5F4),
                readabilityDarkness = 0.45f,
                dynamics =
                    NeedVisualDynamics(
                        cohesion = 0.48f,
                        deformation = 0.62f,
                        pulseSeconds = 8.5f,
                        driftSeconds = 10.0f,
                        driftAmplitude = 0.22f,
                        haloStrength = 0.22f,
                        glossStrength = 0.66f,
                        turbulence = 0.28f,
                        anchorX = 0.70f,
                        anchorY = 0.50f,
                        baseScale = 0.78f,
                    ),
            ),

            NeedVisualProfile(
                key = "cost",
                avatarStyle = NeedAvatarStyle.DENSE_CRYSTAL,
                motionStyle = NeedMotionStyle.COMPACT_PULSE,
                primaryColor = Color(0xFFD88932),
                secondaryColor = Color(0xFFE26C4F),
                accentColor = Color(0xFFFFC875),
                readabilityDarkness = 0.48f,
            ),

            NeedVisualProfile(
                key = "speed",
                avatarStyle = NeedAvatarStyle.DIRECTIONAL_COMET,
                motionStyle = NeedMotionStyle.DIRECTIONAL_FLOW,
                primaryColor = Color(0xFF3B9CEB),
                secondaryColor = Color(0xFF67D5F5),
                accentColor = Color(0xFFD8F7FF),
                readabilityDarkness = 0.43f,
            ),

            NeedVisualProfile(
                key = "safety_accident",
                avatarStyle = NeedAvatarStyle.FLEXIBLE_SHIELD,
                motionStyle = NeedMotionStyle.PROTECTIVE_BREATH,
                primaryColor = Color(0xFF315A95),
                secondaryColor = Color(0xFF287E8E),
                accentColor = Color(0xFFA7E2E6),
                readabilityDarkness = 0.52f,
            ),

            NeedVisualProfile(
                key = "safety_crime",
                avatarStyle = NeedAvatarStyle.SECURITY_RING,
                motionStyle = NeedMotionStyle.PERIPHERAL_ORBIT,
                primaryColor = Color(0xFF3D477C),
                secondaryColor = Color(0xFF566E99),
                accentColor = Color(0xFF8FD4E8),
                readabilityDarkness = 0.56f,
            ),

            NeedVisualProfile(
                key = "comfort",
                avatarStyle = NeedAvatarStyle.SOFT_GEL,
                motionStyle = NeedMotionStyle.VISCOUS_DRIFT,
                primaryColor = Color(0xFF78BEE8),
                secondaryColor = Color(0xFF9A8ED8),
                accentColor = Color(0xFFC8F1F4),
                readabilityDarkness = 0.40f,
                dynamics =
                    NeedVisualDynamics(
                        cohesion = 0.78f,
                        deformation = 0.30f,
                        pulseSeconds = 9.0f,
                        driftSeconds = 13.0f,
                        driftAmplitude = 0.10f,
                        haloStrength = 0.20f,
                        glossStrength = 0.58f,
                        turbulence = 0.10f,
                        anchorX = 0.68f,
                        anchorY = 0.53f,
                        baseScale = 0.76f,
                    ),
            ),

            NeedVisualProfile(
                key = "reliable",
                avatarStyle = NeedAvatarStyle.STABLE_CORE,
                motionStyle = NeedMotionStyle.REGULAR_PULSE,
                primaryColor = Color(0xFF437AD4),
                secondaryColor = Color(0xFF8178C8),
                accentColor = Color(0xFFDCE8FF),
                readabilityDarkness = 0.48f,
                dynamics =
                    NeedVisualDynamics(
                        cohesion = 0.94f,
                        deformation = 0.10f,
                        pulseSeconds = 7.5f,
                        driftSeconds = 14.0f,
                        driftAmplitude = 0.05f,
                        haloStrength = 0.24f,
                        glossStrength = 0.82f,
                        turbulence = 0.06f,
                        anchorX = 0.72f,
                        anchorY = 0.47f,
                        baseScale = 0.68f,
                    ),
            ),

            NeedVisualProfile(
                key = "health_infection",
                avatarStyle = NeedAvatarStyle.BIOLOGICAL_MEMBRANE,
                motionStyle = NeedMotionStyle.MEMBRANE_FLOW,
                primaryColor = Color(0xFF31AFA8),
                secondaryColor = Color(0xFF58CFC7),
                accentColor = Color(0xFFB0F0E6),
                readabilityDarkness = 0.46f,
            ),
        )

    private val byKey =
        profiles.associateBy {
            it.key
        }

    /*
     * UI questionnaire keys are intentionally kept unchanged because they
     * belong to the existing profile/backend contract.
     *
     * This alias layer only resolves the corresponding visual identity.
     */
    private val visualKeyAliases =
        mapOf(
            "comfort_physical" to "comfort",
            "flex" to "autonomy",
            "health_activity" to "physical",
            "time" to "speed",
            "crowding" to "privacy",
            "env" to "pro_env",
        )

    fun forKey(key: String): NeedVisualProfile {
        val visualKey =
            visualKeyAliases[key]
                ?: key

        return byKey[visualKey]
            ?: profiles.first {
                it.key == "reliable"
            }
    }
}


