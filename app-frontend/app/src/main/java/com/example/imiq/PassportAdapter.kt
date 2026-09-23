package com.example.imiq

import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * Adapts Cognitive Passport v2 to the external routing contract.
 *
 * Two scientifically distinct Passport provenance contracts are supported:
 *
 * 1. Strict legacy Passport:
 *    - 11 observed needs
 *    - 44 observed beliefs
 *    - 4 observed valences
 *    - 4 explicit availability declarations
 *    - no belief estimation
 *
 * 2. Adaptive Cognitive Passport:
 *    - 11 observed needs
 *    - 4 observed valences
 *    - 4 explicit availability declarations
 *    - observed + estimator-derived belief cells summing to 44
 *    - explicit adaptive provenance
 *
 * The routing adapter does not convert estimated beliefs into fabricated raw
 * questionnaire answers. Routing receives the already-derived Passport fields
 * it actually consumes: values, availability and mode weights.
 *
 * Availability remains explicit user-declared access and is never inferred
 * from mode weights, valence, frequency, HOTCO output or routing results.
 */
object PassportAdapter {

    private val gson = Gson()

    private val requiredNeeds = listOf(
        "pro_env",
        "physical",
        "privacy",
        "autonomy",
        "cost",
        "speed",
        "safety_accident",
        "safety_crime",
        "comfort",
        "reliable",
        "health_infection",
    )

    private val requiredModes =
        listOf(
            "car",
            "bike",
            "pt",
            "walk",
        )

    private val supportedPassportSchemas =
        setOf(
            "2.0",
            "2.1",
        )

    private val environmentalToleranceKeys =
        listOf(
            "rain",
            "crowding",
            "darkness",
            "traffic",
            "temperature",
        )

    private const val ADAPTIVE_SOURCE_SCHEMA =
        "adaptive_cognitive_passport_extension_1.0"

    private const val ADAPTIVE_POLICY =
        "observed_needs_valences_and_four_beliefs_plus_model_estimated_beliefs"

    /**
     * Read the optional tolerance contract without altering the routing
     * payload.
     *
     * Missing dimensions remain null/unknown.
     */
    fun environmentalTolerances(
        storedJson: String,
    ): Map<String, Double?> {

        val cp =
            cognitivePassport(
                storedJson
            )

        val profile =
            cp.get("profile")
                ?.takeIf {
                    it.isJsonObject
                }
                ?.asJsonObject

        val tolerances =
            profile
                ?.get("environmental_tolerances")
                ?.takeIf {
                    it.isJsonObject
                }
                ?.asJsonObject

        return environmentalToleranceKeys
            .associateWith {
                key ->

                val element =
                    tolerances?.get(
                        key
                    )

                if (
                    element == null ||
                    element.isJsonNull
                ) {
                    null
                } else {

                    val value =
                        runCatching {
                            element.asDouble
                        }.getOrElse {
                            throw IllegalArgumentException(
                                "Environmental tolerance '$key' is not numeric."
                            )
                        }

                    require(
                        value.isFinite() &&
                            value in 0.0..1.0
                    ) {
                        "Environmental tolerance '$key' is outside [0,1]."
                    }

                    value
                }
            }
    }

    /**
     * Returns the routing-engine Passport:
     *
     * {
     *   id,
     *   values {11},
     *   beliefs {owns_car, owns_bike, has_pt_access},
     *   availability {4},
     *   mode_weights {4}
     * }
     *
     * Full 4x11 HOTCO beliefs are deliberately not reconstructed here.
     */
    fun toEnginePassport(
        storedJson: String,
    ): JsonObject {

        val cp =
            cognitivePassport(
                storedJson
            )

        val schemaVersion =
            cp.stringOrNull(
                "schema_version"
            )

        require(
            schemaVersion in
                supportedPassportSchemas
        ) {
            "Routing requires a supported Cognitive Passport v2 schema (2.0 or 2.1)."
        }

        validateInputProvenance(
            cp
        )

        val profile =
            cp.requiredObject(
                "profile"
            )

        val needs =
            profile.requiredObject(
                "needs"
            )

        val availability =
            profile.requiredObject(
                "availability"
            )

        val routing =
            cp.requiredObject(
                "routing_parameters"
            )

        val modeWeights =
            routing.requiredObject(
                "mode_weights"
            )

        val elevenValues =
            JsonObject().apply {

                requiredNeeds.forEach {
                    key ->

                    val value =
                        needs.requiredNumber(
                            key
                        )

                    require(
                        value in 0.0..1.0
                    ) {
                        "Cognitive Passport need '$key' is outside [0,1]."
                    }

                    addProperty(
                        key,
                        value,
                    )
                }
            }

        val availabilityOut =
            JsonObject().apply {

                requiredModes.forEach {
                    key ->

                    addProperty(
                        key,
                        availability.requiredBoolean(
                            key
                        ),
                    )
                }
            }

        require(
            requiredModes.any {
                key ->
                availability.requiredBoolean(
                    key
                )
            }
        ) {
            "Routing requires at least one explicitly available mode."
        }

        val weightsOut =
            JsonObject().apply {

                requiredModes.forEach {
                    key ->

                    val value =
                        modeWeights.requiredNumber(
                            key
                        )

                    require(
                        value in 0.0..1.0
                    ) {
                        "Cognitive Passport mode weight '$key' is outside [0,1]."
                    }

                    addProperty(
                        key,
                        value,
                    )
                }
            }

        /*
         * These three legacy routing fields express structural access only.
         * They must not be interpreted as HOTCO beliefs or preference.
         */
        val beliefs =
            JsonObject().apply {

                addProperty(
                    "owns_car",
                    availability.requiredBoolean(
                        "car"
                    ),
                )

                addProperty(
                    "owns_bike",
                    availability.requiredBoolean(
                        "bike"
                    ),
                )

                addProperty(
                    "has_pt_access",
                    availability.requiredBoolean(
                        "pt"
                    ),
                )
            }

        val id =
            cp.stringOrNull(
                "agent_id"
            )
                ?: error(
                    "Cognitive Passport v2 is missing agent_id."
                )

        return JsonObject().apply {

            addProperty(
                "id",
                id,
            )

            add(
                "values",
                elevenValues,
            )

            add(
                "beliefs",
                beliefs,
            )

            add(
                "availability",
                availabilityOut,
            )

            add(
                "mode_weights",
                weightsOut,
            )
        }
    }

    /**
     * Validate provenance without conflating the strict and adaptive
     * scientific contracts.
     */
    private fun validateInputProvenance(
        cp: JsonObject,
    ) {

        val provenance =
            cp.requiredObject(
                "input_provenance"
            )

        val sourceSchema =
            provenance.stringOrNull(
                "source_schema"
            )

        if (
            sourceSchema ==
            ADAPTIVE_SOURCE_SCHEMA
        ) {

            validateAdaptiveProvenance(
                cp,
                provenance,
            )

        } else {

            validateStrictProvenance(
                provenance
            )
        }
    }

    private fun validateStrictProvenance(
        provenance: JsonObject,
    ) {

        require(
            provenance.stringOrNull(
                "policy"
            ) ==
                "current_user_responses_only"
        ) {
            "Strict routing Passport requires current-user-only input provenance."
        }

        require(
            !provenance.requiredBoolean(
                "imputation_used"
            )
        ) {
            "Strict routing Passport rejects missing-value substitution."
        }

        require(
            !provenance.requiredBoolean(
                "population_or_synthetic_values_used"
            )
        ) {
            "Strict routing Passport rejects population or synthetic values."
        }

        val observed =
            provenance.requiredObject(
                "observed_counts"
            )

        require(
            observed.requiredNumber(
                "needs"
            ) == 11.0 &&
                observed.requiredNumber(
                    "beliefs"
                ) == 44.0 &&
                observed.requiredNumber(
                    "valences"
                ) == 4.0 &&
                observed.requiredNumber(
                    "availability"
                ) == 4.0
        ) {
            "Strict routing Passport requires all 63 observed questionnaire inputs."
        }
    }

    private fun validateAdaptiveProvenance(
        cp: JsonObject,
        provenance: JsonObject,
    ) {

        require(
            provenance.stringOrNull(
                "policy"
            ) ==
                ADAPTIVE_POLICY
        ) {
            "Adaptive routing Passport has an unsupported provenance policy."
        }

        require(
            provenance.requiredBoolean(
                "belief_estimation_used"
            )
        ) {
            "Adaptive routing Passport must declare belief estimation."
        }

        val adaptive =
            cp.requiredObject(
                "adaptive_passport"
            )

        require(
            adaptive.stringOrNull(
                "schema_version"
            ) ==
                ADAPTIVE_SOURCE_SCHEMA
        ) {
            "Adaptive routing Passport is missing the expected adaptive extension."
        }

        require(
            adaptive.requiredNumber(
                "fabricated_raw_beliefs"
            ) == 0.0
        ) {
            "Adaptive routing Passport must not contain fabricated raw beliefs."
        }

        val observed =
            provenance.requiredObject(
                "observed_counts"
            )

        val estimated =
            provenance.requiredObject(
                "estimated_counts"
            )

        require(
            observed.requiredNumber(
                "needs"
            ) == 11.0
        ) {
            "Adaptive routing Passport requires 11 observed needs."
        }

        require(
            observed.requiredNumber(
                "valences"
            ) == 4.0
        ) {
            "Adaptive routing Passport requires four observed valences."
        }

        require(
            observed.requiredNumber(
                "availability"
            ) == 4.0
        ) {
            "Adaptive routing Passport requires four explicit availability declarations."
        }

        val observedBeliefs =
            observed.requiredNumber(
                "beliefs"
            )

        val estimatedBeliefs =
            estimated.requiredNumber(
                "beliefs"
            )

        require(
            observedBeliefs >= 4.0 &&
                estimatedBeliefs >= 0.0 &&
                observedBeliefs +
                estimatedBeliefs ==
                44.0
        ) {
            "Adaptive routing Passport belief provenance must account for all 44 cells."
        }
    }

    private fun cognitivePassport(
        storedJson: String,
    ): JsonObject {

        val root =
            gson.fromJson(
                storedJson,
                JsonObject::class.java,
            )

        return if (
            root.has(
                "cognitive_passport"
            ) &&
            root.get(
                "cognitive_passport"
            ).isJsonObject
        ) {
            root.getAsJsonObject(
                "cognitive_passport"
            )
        } else {
            root
        }
    }

    private fun JsonObject.requiredObject(
        key: String,
    ): JsonObject {

        require(
            has(key) &&
                get(key).isJsonObject
        ) {
            "Cognitive Passport is missing object '$key'."
        }

        return getAsJsonObject(
            key
        )
    }

    private fun JsonObject.requiredNumber(
        key: String,
    ): Double {

        require(
            has(key) &&
                !get(key).isJsonNull
        ) {
            "Cognitive Passport is missing '$key'."
        }

        return runCatching {
            get(key).asDouble
        }.getOrElse {
            throw IllegalArgumentException(
                "Cognitive Passport field '$key' is not numeric."
            )
        }
    }

    private fun JsonObject.requiredBoolean(
        key: String,
    ): Boolean {

        require(
            has(key) &&
                !get(key).isJsonNull
        ) {
            "Cognitive Passport is missing '$key'."
        }

        return runCatching {
            get(key).asBoolean
        }.getOrElse {
            throw IllegalArgumentException(
                "Cognitive Passport field '$key' is not boolean."
            )
        }
    }

    private fun JsonObject.stringOrNull(
        key: String,
    ): String? =
        if (
            has(key) &&
            !get(key).isJsonNull
        ) {
            runCatching {
                get(key).asString
            }.getOrNull()
        } else {
            null
        }
}
