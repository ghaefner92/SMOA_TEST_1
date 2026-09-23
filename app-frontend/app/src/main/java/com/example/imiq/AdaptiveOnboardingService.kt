package com.example.imiq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class AdaptiveOnboardingQuestion(
    val position: Int,
    val cellId: String,
    val mode: String,
    val need: String,
)

object AdaptiveOnboardingService {

    const val ONBOARDING_SCHEMA =
        "adaptive_cognitive_passport_onboarding_1.0"

    const val BOOTSTRAP_SCHEMA =
        "adaptive_hotco_bootstrap_1.0"

    private val baseUrl =
        BuildConfig.DYCONET_BASE_URL.trimEnd('/')

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    private val JSON_MEDIA =
        "application/json; charset=utf-8".toMediaType()

    /*
     * Explicit bridge from the current questionnaire vocabulary
     * to the canonical Adaptive Passport / HOTCO vocabulary.
     *
     * No value is inferred here.
     */
    internal val UI_TO_ADAPTIVE_NEED = mapOf(
        "env" to "pro_env",
        "health_activity" to "physical",
        "crowding" to "privacy",
        "flex" to "autonomy",
        "cost" to "cost",
        "time" to "speed",
        "safety_accident" to "safety_accident",
        "safety_crime" to "safety_crime",
        "comfort_physical" to "comfort",
        "reliable" to "reliable",
        "health_infection" to "health_infection",
    )

    private val MODES =
        listOf(
            "car",
            "bike",
            "pt",
            "walk",
        )

    internal fun buildStartJson(
        participantId: String,
        needs: Map<String, Float>,
        valences: Map<String, Float>,
    ): String {

        val agentId =
            participantId.trim()

        require(
            agentId.isNotEmpty()
        ) {
            "Participant identifier is required."
        }

        require(
            UI_TO_ADAPTIVE_NEED.keys.all {
                key -> needs[key]?.let {
                    it.isFinite() &&
                        it % 1f == 0f &&
                        it in 1f..7f
                } == true
            }
        ) {
            "All 11 need ratings must be explicit integers from 1 to 7."
        }

        require(
            MODES.all {
                mode -> valences[mode]?.let {
                    it.isFinite() &&
                        it % 1f == 0f &&
                        it in 1f..7f
                } == true
            }
        ) {
            "All four valence ratings must be explicit integers from 1 to 7."
        }

        return buildJsonObject {

            put(
                "schema_version",
                ONBOARDING_SCHEMA,
            )

            put(
                "agent_id",
                agentId,
            )

            put(
                "responses",
                buildJsonObject {

                    put(
                        "needs",
                        buildJsonObject {

                            UI_TO_ADAPTIVE_NEED
                                .forEach {
                                        (uiKey, adaptiveKey) ->

                                    put(
                                        adaptiveKey,
                                        requireNotNull(
                                            needs[
                                                uiKey
                                            ]
                                        ).toInt(),
                                    )
                                }
                        },
                    )

                    /*
                     * Existing Android UX uses 1..7.
                     * Adaptive backend expects -3..+3.
                     */
                    put(
                        "valences",
                        buildJsonObject {

                            MODES.forEach {
                                    mode ->

                                put(
                                    mode,
                                    requireNotNull(
                                        valences[
                                            mode
                                        ]
                                    ).toInt() - 4,
                                )
                            }
                        },
                    )
                },
            )
        }.toString()
    }


    internal fun parseQuestions(
        startJson: String,
    ): List<AdaptiveOnboardingQuestion> {

        val root =
            Json.parseToJsonElement(
                startJson
            ).jsonObject

        require(
            root[
                "schema_version"
            ]?.jsonPrimitive?.contentOrNull
                == ONBOARDING_SCHEMA
        )

        require(
            root[
                "stage"
            ]?.jsonPrimitive?.contentOrNull
                == "questions"
        )

        val questions =
            root[
                "questions"
            ]?.jsonArray
                ?: error(
                    "Adaptive start response has no questions."
                )

        require(
            questions.size == 4
        ) {
            "Adaptive Passport v1 must return exactly four initial questions."
        }

        val parsed =
            questions.map {
                element ->

                val q =
                    element.jsonObject

                AdaptiveOnboardingQuestion(
                    position =
                        q[
                            "position"
                        ]?.jsonPrimitive?.intOrNull
                            ?: error(
                                "Question position missing."
                            ),

                    cellId =
                        q[
                            "cell_id"
                        ]?.jsonPrimitive?.contentOrNull
                            ?: error(
                                "Question cell_id missing."
                            ),

                    mode =
                        q[
                            "hotco_mode"
                        ]?.jsonPrimitive?.contentOrNull
                            ?: error(
                                "Question mode missing."
                            ),

                    need =
                        q[
                            "model_need"
                        ]?.jsonPrimitive?.contentOrNull
                            ?: error(
                                "Question need missing."
                            ),
                )
            }

        require(
            parsed.map {
                it.position
            } == listOf(
                1,
                2,
                3,
                4,
            )
        )

        require(
            parsed.map {
                it.cellId
            }.toSet().size == 4
        )

        return parsed
    }


    internal fun buildCompleteJson(
        startJson: String,
        answers: Map<String, Int>,
    ): String {

        val start =
            Json.parseToJsonElement(
                startJson
            )

        val expected =
            parseQuestions(
                startJson
            ).map {
                it.cellId
            }.toSet()

        require(
            answers.keys == expected
        ) {
            "Answers must contain exactly the four questions returned by start."
        }

        require(
            answers.values.all {
                it in 1..7
            }
        )

        return buildJsonObject {

            put(
                "schema_version",
                ONBOARDING_SCHEMA,
            )

            put(
                "start",
                start,
            )

            put(
                "answers",
                buildJsonObject {

                    expected.forEach {
                            cellId ->

                        put(
                            cellId,
                            requireNotNull(
                                answers[
                                    cellId
                                ]
                            ),
                        )
                    }
                },
            )
        }.toString()
    }


    internal fun buildBootstrapJson(
        completedJson: String,
        availability: Map<String, Boolean>,
        environmentalTolerances: Map<String, Int?> =
            emptyMap(),
    ): String {

        require(
            MODES.all {
                it in availability
            }
        ) {
            "Availability must explicitly cover all four modes."
        }

        require(
            availability.values.any {
                it
            }
        ) {
            "At least one mode must be available."
        }

        environmentalTolerances
            .values
            .filterNotNull()
            .forEach {
                require(
                    it in 1..7
                )
            }

        return buildJsonObject {

            put(
                "schema_version",
                BOOTSTRAP_SCHEMA,
            )

            put(
                "completed_passport",
                Json.parseToJsonElement(
                    completedJson
                ),
            )

            put(
                "availability",
                buildJsonObject {

                    MODES.forEach {
                            mode ->

                        put(
                            mode,
                            requireNotNull(
                                availability[
                                    mode
                                ]
                            ),
                        )
                    }
                },
            )

            if (
                environmentalTolerances
                    .isNotEmpty()
            ) {

                put(
                    "environmental_tolerances",
                    buildJsonObject {

                        ENVIRONMENTAL_TOLERANCE_KEYS
                            .forEach {
                                    key ->

                                val rating =
                                    environmentalTolerances[
                                        key
                                    ]

                                if (
                                    rating == null
                                ) {
                                    put(
                                        key,
                                        JsonNull,
                                    )
                                } else {
                                    put(
                                        key,
                                        (
                                            rating - 1
                                        ) / 6.0,
                                    )
                                }
                            }
                    },
                )
            }
        }.toString()
    }


    private suspend fun post(
        path: String,
        json: String,
    ): String =
        withContext(
            Dispatchers.IO
        ) {

            val request =
                Request.Builder()
                    .url(
                        "$baseUrl$path"
                    )
                    .post(
                        json.toRequestBody(
                            JSON_MEDIA
                        )
                    )
                    .build()

            client
                .newCall(
                    request
                )
                .execute()
                .use {
                    response ->

                    val body =
                        response.body
                            ?.string()
                            ?: ""

                    if (
                        !response.isSuccessful
                    ) {
                        throw RuntimeException(
                            "Adaptive Passport error " +
                                "${response.code}: $body"
                        )
                    }

                    body
                }
        }


    suspend fun start(
        participantId: String,
        needs: Map<String, Float>,
        valences: Map<String, Float>,
    ): String {

        val body =
            post(
                "/api/dyconet/adaptive-passport/start",
                buildStartJson(
                    participantId,
                    needs,
                    valences,
                ),
            )

        /*
         * Parsing is also validation of the server contract.
         */
        parseQuestions(
            body
        )

        return body
    }


    suspend fun complete(
        startJson: String,
        answers: Map<String, Int>,
    ): String {

        val body =
            post(
                "/api/dyconet/adaptive-passport/complete",
                buildCompleteJson(
                    startJson,
                    answers,
                ),
            )

        val root =
            Json.parseToJsonElement(
                body
            ).jsonObject

        require(
            root[
                "schema_version"
            ]?.jsonPrimitive?.contentOrNull
                == ONBOARDING_SCHEMA
        )

        require(
            root[
                "stage"
            ]?.jsonPrimitive?.contentOrNull
                == "complete"
        )

        require(
            root[
                "ready_for_hotco"
            ]?.jsonPrimitive?.booleanOrNull
                == true
        )

        return body
    }


    suspend fun bootstrap(
        completedJson: String,
        availability: Map<String, Boolean>,
        environmentalTolerances: Map<String, Int?> =
            emptyMap(),
    ): String {

        val body =
            post(
                "/api/dyconet/adaptive-passport/bootstrap",
                buildBootstrapJson(
                    completedJson,
                    availability,
                    environmentalTolerances,
                ),
            )

        require(
            isValidAdaptivePassportV2(
                body
            )
        ) {
            "Server returned an invalid Adaptive Cognitive Passport."
        }

        return body
    }
}


/*
 * Validation specifically for Adaptive Cognitive Passports.
 *
 * This is deliberately separate from isValidPassportV2(), because the
 * strict 44-observed-belief contract and the adaptive mixed-provenance
 * contract make different scientific claims.
 */
internal fun isValidAdaptivePassportV2(
    passportJson: String,
): Boolean {
    return try {

        val cp =
            Json.parseToJsonElement(
                passportJson
            )
                .jsonObject[
                    "cognitive_passport"
                ]
                ?.jsonObject
                ?: return false

        if (
            cp[
                "schema_version"
            ]?.jsonPrimitive?.contentOrNull
                != "2.0"
        ) {
            return false
        }

        if (
            cp[
                "adaptive_passport"
            ]?.jsonObject
                == null
        ) {
            return false
        }

        val provenance =
            cp[
                "input_provenance"
            ]?.jsonObject
                ?: return false

        if (
            provenance[
                "source_schema"
            ]?.jsonPrimitive?.contentOrNull
                !=
                "adaptive_cognitive_passport_extension_1.0"
        ) {
            return false
        }

        if (
            provenance[
                "belief_estimation_used"
            ]?.jsonPrimitive?.booleanOrNull
                != true
        ) {
            return false
        }

        val observed =
            provenance[
                "observed_counts"
            ]?.jsonObject
                ?: return false

        val estimated =
            provenance[
                "estimated_counts"
            ]?.jsonObject
                ?: return false

        if (
            observed[
                "needs"
            ]?.jsonPrimitive?.intOrNull
                != 11
        ) {
            return false
        }

        if (
            observed[
                "valences"
            ]?.jsonPrimitive?.intOrNull
                != 4
        ) {
            return false
        }

        if (
            observed[
                "availability"
            ]?.jsonPrimitive?.intOrNull
                != 4
        ) {
            return false
        }

        val observedBeliefs =
            observed[
                "beliefs"
            ]?.jsonPrimitive?.intOrNull
                ?: return false

        val estimatedBeliefs =
            estimated[
                "beliefs"
            ]?.jsonPrimitive?.intOrNull
                ?: return false

        if (
            observedBeliefs < 4
            || estimatedBeliefs < 0
            || observedBeliefs +
                estimatedBeliefs != 44
        ) {
            return false
        }

        val availabilityResolution =
            provenance[
                "availability_resolution"
            ]?.jsonObject
                ?: return false

        if (
            availabilityResolution[
                "policy"
            ]?.jsonPrimitive?.contentOrNull
                !=
                "explicit_user_declared_access_only"
        ) {
            return false
        }

        val lineage =
            cp[
                "lineage"
            ]?.jsonObject
                ?: return false

        if (
            (
                lineage[
                    "revision"
                ]?.jsonPrimitive?.intOrNull
                    ?: 0
            ) < 1
        ) {
            return false
        }

        val deliberation =
            cp[
                "deliberation"
            ]?.jsonObject
                ?: return false

        if (
            deliberation[
                "terminal_tendency"
            ]?.jsonPrimitive?.contentOrNull
                .isNullOrBlank()
        ) {
            return false
        }

        if (
            deliberation[
                "comparative_readout"
            ]?.jsonObject
                ?.size != 4
        ) {
            return false
        }

        true

    } catch (
        _: Exception
    ) {
        false
    }
}


internal fun isValidStoredPassportV2(
    passportJson: String,
): Boolean =
    isValidPassportV2(
        passportJson
    ) ||
        isValidAdaptivePassportV2(
            passportJson
        )
