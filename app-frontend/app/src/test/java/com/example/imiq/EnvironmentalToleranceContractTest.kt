package com.example.imiq

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentalToleranceContractTest {
    private val needKeys = listOf(
        "comfort_physical", "reliable", "flex", "cost", "safety_crime",
        "health_activity", "time", "health_infection", "crowding",
        "safety_accident", "env",
    )
    private val modes = listOf("walk", "bike", "pt", "car")

    @Test
    fun likertMappingPreservesEndpointsMidpointAndMissing() {
        assertEquals(0.0, likertToTolerance(1)!!, 0.0)
        assertEquals(0.5, likertToTolerance(4)!!, 0.0)
        assertEquals(1.0, likertToTolerance(7)!!, 0.0)
        assertNull(likertToTolerance(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidLikertValueIsRejected() {
        likertToTolerance(8)
    }

    @Test
    fun allFiveCanonicalKeysAreSerializedAndOneMissingRemainsNull() {
        val root = Json.parseToJsonElement(
            surveyJson(
                mapOf(
                    "rain" to 1,
                    "crowding" to 4,
                    "darkness" to 7,
                    "traffic" to null,
                    "temperature" to 5,
                ),
            ),
        ).jsonObject
        val responses = root.getValue("responses").jsonObject
        val tolerances = responses.getValue("environmental_tolerances").jsonObject
        assertEquals(ENVIRONMENTAL_TOLERANCE_KEYS.toSet(), tolerances.keys)
        assertEquals(0.0, tolerances.getValue("rain").jsonPrimitive.double, 0.0)
        assertEquals(0.5, tolerances.getValue("crowding").jsonPrimitive.double, 0.0)
        assertEquals(1.0, tolerances.getValue("darkness").jsonPrimitive.double, 0.0)
        assertTrue(tolerances.getValue("traffic") is JsonNull)
        assertEquals(4.0 / 6.0, tolerances.getValue("temperature").jsonPrimitive.double, 0.0)
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
    fun uiCompletionRequiresAllFiveValidSelections() {
        val complete = ENVIRONMENTAL_TOLERANCE_KEYS.associateWith { 4 }
        assertTrue(hasCompleteEnvironmentalToleranceRatings(complete))
        assertFalse(hasCompleteEnvironmentalToleranceRatings(complete - "rain"))
        assertFalse(hasCompleteEnvironmentalToleranceRatings(complete + ("rain" to 0)))
    }

    @Test
    fun passportValuesRemainAccessibleWithoutCorruption() {
        val passport = """
            {"cognitive_passport":{"schema_version":"2.0","profile":{"environmental_tolerances":{
              "rain":0.0,"crowding":0.5,"darkness":1.0,"traffic":null,"temperature":0.6666666666666666
            }}}}
        """.trimIndent()
        val values = PassportAdapter.environmentalTolerances(passport)
        assertEquals(0.0, values["rain"]!!, 0.0)
        assertEquals(1.0, values["darkness"]!!, 0.0)
        assertNull(values["traffic"])
        assertEquals(2.0 / 3.0, values["temperature"]!!, 0.0)
    }

    @Test
    fun olderPassportWithoutToleranceContractRemainsReadableAsUnknown() {
        val values = PassportAdapter.environmentalTolerances(
            """{"cognitive_passport":{"schema_version":"2.0","profile":{}}}""",
        )
        assertEquals(ENVIRONMENTAL_TOLERANCE_KEYS.toSet(), values.keys)
        assertTrue(values.values.all { it == null })
    }

    private fun surveyJson(tolerances: Map<String, Int?>): String = buildSurveyJson(
        participantId = "test-user",
        needs = needKeys.associateWith { 4f },
        top3 = listOf("cost", "reliable", "time"),
        valences = modes.associateWith { 4f },
        beliefs = modes.associateWith { needKeys.associateWith { 4f } },
        availability = modes.associateWith { true },
        environmentalTolerances = tolerances,
    )
}
