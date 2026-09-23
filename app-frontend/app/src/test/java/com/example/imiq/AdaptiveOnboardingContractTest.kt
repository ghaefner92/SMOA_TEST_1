package com.example.imiq

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AdaptiveOnboardingContractTest {

    private val needs =
        mapOf(
            "comfort_physical" to 6f,
            "reliable" to 7f,
            "flex" to 6f,
            "cost" to 7f,
            "safety_crime" to 5f,
            "health_activity" to 5f,
            "time" to 5f,
            "health_infection" to 3f,
            "crowding" to 4f,
            "safety_accident" to 6f,
            "env" to 6f,
        )

    private val valences =
        mapOf(
            "car" to 5f,
            "bike" to 6f,
            "pt" to 4f,
            "walk" to 6f,
        )

    private val startJson =
        """
        {
          "schema_version":
            "adaptive_cognitive_passport_onboarding_1.0",

          "stage":
            "questions",

          "questions": [
            {
              "position": 1,
              "cell_id": "pt__comfort",
              "hotco_mode": "pt",
              "model_need": "comfort"
            },
            {
              "position": 2,
              "cell_id": "walk__comfort",
              "hotco_mode": "walk",
              "model_need": "comfort"
            },
            {
              "position": 3,
              "cell_id": "bike__privacy",
              "hotco_mode": "bike",
              "model_need": "privacy"
            },
            {
              "position": 4,
              "cell_id": "car__reliable",
              "hotco_mode": "car",
              "model_need": "reliable"
            }
          ]
        }
        """.trimIndent()


    @Test
    fun startBuilderMapsUiNeedsToCanonicalAdaptiveNeeds() {

        val raw =
            AdaptiveOnboardingService
                .buildStartJson(
                    "participant-1",
                    needs,
                    valences,
                )

        val root =
            Json.parseToJsonElement(
                raw
            ).jsonObject

        assertEquals(
            AdaptiveOnboardingService.ONBOARDING_SCHEMA,
            root[
                "schema_version"
            ]?.jsonPrimitive?.content,
        )

        val responses =
            root.getValue(
                "responses"
            ).jsonObject

        val mappedNeeds =
            responses.getValue(
                "needs"
            ).jsonObject

        assertEquals(
            11,
            mappedNeeds.size,
        )

        assertEquals(
            6,
            mappedNeeds[
                "pro_env"
            ]?.jsonPrimitive?.intOrNull,
        )

        assertEquals(
            5,
            mappedNeeds[
                "physical"
            ]?.jsonPrimitive?.intOrNull,
        )

        assertEquals(
            4,
            mappedNeeds[
                "privacy"
            ]?.jsonPrimitive?.intOrNull,
        )

        assertEquals(
            6,
            mappedNeeds[
                "autonomy"
            ]?.jsonPrimitive?.intOrNull,
        )

        assertFalse(
            mappedNeeds.containsKey(
                "env"
            )
        )

        assertFalse(
            mappedNeeds.containsKey(
                "health_activity"
            )
        )
    }


    @Test
    fun startBuilderConvertsValenceLikertToMinus3Plus3() {

        val root =
            Json.parseToJsonElement(
                AdaptiveOnboardingService
                    .buildStartJson(
                        "participant-1",
                        needs,
                        valences,
                    )
            ).jsonObject

        val mapped =
            root[
                "responses"
            ]!!.jsonObject[
                "valences"
            ]!!.jsonObject

        assertEquals(
            1,
            mapped[
                "car"
            ]?.jsonPrimitive?.intOrNull,
        )

        assertEquals(
            2,
            mapped[
                "bike"
            ]?.jsonPrimitive?.intOrNull,
        )

        assertEquals(
            0,
            mapped[
                "pt"
            ]?.jsonPrimitive?.intOrNull,
        )
    }


    @Test
    fun parserPreservesFrozenFourQuestionOrder() {

        val questions =
            AdaptiveOnboardingService
                .parseQuestions(
                    startJson
                )

        assertEquals(
            listOf(
                "pt__comfort",
                "walk__comfort",
                "bike__privacy",
                "car__reliable",
            ),
            questions.map {
                it.cellId
            },
        )
    }


    @Test
    fun completeBuilderContainsExactlyFourObservedBeliefs() {

        val raw =
            AdaptiveOnboardingService
                .buildCompleteJson(
                    startJson,
                    mapOf(
                        "pt__comfort" to 1,
                        "walk__comfort" to 3,
                        "bike__privacy" to 5,
                        "car__reliable" to 7,
                    ),
                )

        val root =
            Json.parseToJsonElement(
                raw
            ).jsonObject

        val answers =
            root[
                "answers"
            ]!!.jsonObject

        assertEquals(
            4,
            answers.size,
        )

        assertEquals(
            7,
            answers[
                "car__reliable"
            ]?.jsonPrimitive?.intOrNull,
        )
    }


    @Test
    fun bootstrapRequiresExplicitFourModeAvailability() {

        assertThrows(
            IllegalArgumentException::class.java
        ) {
            AdaptiveOnboardingService
                .buildBootstrapJson(
                    completedJson =
                        """{"stage":"complete"}""",

                    availability =
                        mapOf(
                            "car" to true,
                        ),
                )
        }
    }


    @Test
    fun bootstrapConvertsToleranceRatingsWithoutInferringThem() {

        val raw =
            AdaptiveOnboardingService
                .buildBootstrapJson(
                    completedJson =
                        """{"stage":"complete"}""",

                    availability =
                        mapOf(
                            "car" to true,
                            "bike" to true,
                            "pt" to true,
                            "walk" to true,
                        ),

                    environmentalTolerances =
                        mapOf(
                            "rain" to 1,
                            "crowding" to 4,
                            "darkness" to 7,
                        ),
                )

        val root =
            Json.parseToJsonElement(
                raw
            ).jsonObject

        val tolerance =
            root[
                "environmental_tolerances"
            ]!!.jsonObject

        assertEquals(
            0.0,
            requireNotNull(
                tolerance[
                    "rain"
                ]?.jsonPrimitive?.doubleOrNull
            ),
            1e-12,
        )

        assertEquals(
            0.5,
            requireNotNull(
                tolerance[
                    "crowding"
                ]?.jsonPrimitive?.doubleOrNull
            ),
            1e-12,
        )

        assertEquals(
            1.0,
            requireNotNull(
                tolerance[
                    "darkness"
                ]?.jsonPrimitive?.doubleOrNull
            ),
            1e-12,
        )

        assertTrue(
            tolerance[
                "traffic"
            ] is JsonNull
        )
    }
}
