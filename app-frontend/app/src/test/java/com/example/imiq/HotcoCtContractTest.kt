package com.example.imiq

import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.JsonParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotcoCtContractTest {
    private val needKeys = listOf(
        "comfort_physical", "reliable", "flex", "cost", "safety_crime",
        "health_activity", "time", "health_infection", "crowding",
        "safety_accident", "env",
    )
    private val modes = listOf("walk", "bike", "pt", "car")

    @Test
    fun questionnaireBuilderEmitsEveryObservedInputWithoutFrequencyLeakage() {
        val needs = needKeys.associateWith { 4f }
        val beliefs = modes.associateWith { needKeys.associateWith { 4f } }
        val valences = modes.associateWith { 4f }
        val availability = modes.associateWith { true }

        val raw = buildSurveyJson(
            participantId = "test-user",
            needs = needs,
            top3 = listOf("cost", "reliable", "time"),
            valences = valences,
            beliefs = beliefs,
            availability = availability,
        )
        val root = Json.parseToJsonElement(raw).jsonObject
        assertEquals("hotco_ct_input_2.1", root["schema_version"]?.jsonPrimitive?.content)
        assertEquals("test-user", root["agent_id"]?.jsonPrimitive?.content)
        assertFalse(root.containsKey("APP"))
        assertFalse(root.containsKey("MOBIL"))

        val responses = root.getValue("responses").jsonObject
        assertEquals(11, responses.getValue("needs").jsonObject.size)
        assertEquals(
            44,
            responses.getValue("beliefs").jsonObject.values.sumOf { it.jsonObject.size },
        )
        assertEquals(4, responses.getValue("valences").jsonObject.size)
        assertEquals(4, responses.getValue("availability").jsonObject.size)
        assertFalse(responses.containsKey("frequencies"))
    }

    @Test
    fun questionnaireBuilderAcceptsSnapshottedComposeStateAndEmitsOnlyCanonicalAnswers() {
        val needsState = mutableStateMapOf<String, Float>().apply {
            needKeys.forEach { put(it, 4f) }
            put("ui_only_transient_key", 7f)
        }
        val beliefState = mutableStateMapOf<String, MutableMap<String, Float>>().apply {
            modes.forEach { mode ->
                put(mode, mutableStateMapOf<String, Float>().apply {
                    needKeys.forEach { put(it, 4f) }
                })
            }
        }

        val raw = buildSurveyJson(
            participantId = "compose-state-user",
            needs = needsState.toMap(),
            top3 = listOf("cost", "reliable", "time"),
            valences = modes.associateWith { 4f },
            beliefs = beliefState.mapValues { (_, values) -> values.toMap() },
            availability = modes.associateWith { true },
        )

        val responses = Json.parseToJsonElement(raw)
            .jsonObject.getValue("responses").jsonObject
        assertEquals(11, responses.getValue("needs").jsonObject.size)
        assertFalse(responses.getValue("needs").jsonObject.containsKey("ui_only_transient_key"))
        assertFalse(responses.containsKey("frequencies"))
    }

    @Test
    fun questionnaireBuilderIdentifiesTheMissingNeed() {
        val missingKey = "health_infection"
        val error = runCatching {
            buildSurveyJson(
                participantId = "test-user",
                needs = needKeys.filterNot { it == missingKey }.associateWith { 4f },
                top3 = listOf("cost", "reliable", "time"),
                valences = modes.associateWith { 4f },
                beliefs = modes.associateWith { needKeys.associateWith { 4f } },
                availability = modes.associateWith { true },
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains(missingKey))
    }

    @Test
    fun storedPassportRequiresPhase1AvailabilityProvenance() {
        val valid = """
            {
              "cognitive_passport": {
                "schema_version": "2.0",
                "input_provenance": {
                  "source_schema": "hotco_ct_input_2.1",
                  "policy": "current_user_responses_only",
                  "imputation_used": false,
                  "population_or_synthetic_values_used": false,
                  "observed_counts": {"needs":11,"beliefs":44,"valences":4,"availability":4},
                  "availability_resolution": {
                    "policy": "explicit_user_declared_access_only",
                    "external_provider_data_used": false,
                    "frequency_or_preference_inference_used": false
                  }
                },
                "deliberation": {
                  "terminal_tendency": "bike",
                  "terminal_margin": 0.12,
                  "comparative_readout": {"car":0.1,"bike":0.4,"pt":0.2,"walk":0.3}
                }
              }
            }
        """.trimIndent()
        assertTrue(isValidPassportV2(valid))
        assertFalse(isValidPassportV2(valid.replace("\"imputation_used\": false", "\"imputation_used\": true")))
        assertFalse(isValidPassportV2(valid.replace("\"external_provider_data_used\": false", "\"external_provider_data_used\": true")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun questionnaireBuilderRejectsOneMissingBelief() {
        val needs = needKeys.associateWith { 4f }
        val beliefs = modes.associateWith { mode ->
            needKeys.filterNot { mode == "bike" && it == "cost" }.associateWith { 4f }
        }
        buildSurveyJson(
            participantId = "test-user",
            needs = needs,
            top3 = listOf("cost", "reliable", "time"),
            valences = modes.associateWith { 4f },
            beliefs = beliefs,
            availability = modes.associateWith { true },
        )
    }

    @Test
    fun routingAdapterUsesElevenNeedsAndReportedAvailability() {
        val needsJson = listOf(
            "pro_env", "physical", "privacy", "autonomy", "cost", "speed",
            "safety_accident", "safety_crime", "comfort", "reliable", "health_infection",
        ).joinToString(",") { "\"$it\":0.5" }
        val stored = """
            {
              "cognitive_passport": {
                "schema_version": "2.0",
                "agent_id": "test-user",
                "input_provenance": {
                  "policy": "current_user_responses_only",
                  "imputation_used": false,
                  "population_or_synthetic_values_used": false,
                  "observed_counts": {"needs":11,"beliefs":44,"valences":4,"availability":4}
                },
                "profile": {
                  "needs": {$needsJson},
                  "availability": {"car":false,"bike":true,"pt":true,"walk":true}
                },
                "routing_parameters": {
                  "mode_weights": {"car":0.1,"bike":0.4,"pt":0.2,"walk":0.3}
                }
              }
            }
        """.trimIndent()

        val adapted = PassportAdapter.toEnginePassport(stored)
        assertEquals(11, adapted.getAsJsonObject("values").size())
        assertFalse(adapted.getAsJsonObject("beliefs").get("owns_car").asBoolean)
        assertTrue(adapted.getAsJsonObject("beliefs").get("owns_bike").asBoolean)
        assertEquals(4, adapted.getAsJsonObject("availability").size())
        assertEquals(4, adapted.getAsJsonObject("mode_weights").size())
        JsonParser.parseString(adapted.toString())
    }
}
