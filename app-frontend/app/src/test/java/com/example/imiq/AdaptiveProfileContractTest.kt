package com.example.imiq

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveProfileContractTest {
    private val passport = """
        {
          "cognitive_passport": {
            "schema_version": "2.0",
            "lineage": {
              "passport_id": "p1",
              "revision": 1,
              "parent_passport_id": null,
              "measurement_history": []
            },
            "adaptive_questioning": {
              "schema_version": "1.0",
              "question_needed": true,
              "candidate": {
                "question_id": "mq_123",
                "trigger": "PERSISTENT_RIVALRY",
                "scope": "profile",
                "update_requires_explicit_user_confirmation": true,
                "target": {
                  "kind": "need",
                  "need": "cost",
                  "current_raw_rating": 5
                }
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun candidateParserReturnsOnlyBackendStructuredQuestion() {
        val candidate = AdaptiveProfileService.candidate(passport)
        assertNotNull(candidate)
        assertEquals("mq_123", candidate?.questionId)
        assertEquals("need", candidate?.kind)
        assertEquals("cost", candidate?.need)
        assertEquals("observed", candidate?.measurementStatus)
        assertEquals(5, candidate?.currentRawRating)
        assertEquals(false, candidate?.usesAdaptiveContract)
    }

    @Test
    fun updateBuilderCarriesPreviousPassportAndExplicitConfirmation() {
        val candidate = requireNotNull(AdaptiveProfileService.candidate(passport))
        val raw = AdaptiveProfileService.buildUpdateJson(passport, candidate, 6)
        val root = Json.parseToJsonElement(raw).jsonObject
        assertEquals(
            "hotco_ct_profile_update_1.0",
            root["schema_version"]?.jsonPrimitive?.content,
        )
        assertTrue(root.containsKey("previous_passport"))
        val update = root.getValue("update").jsonObject
        assertEquals("mq_123", update["question_id"]?.jsonPrimitive?.content)
        assertEquals(6, update["raw_rating"]?.jsonPrimitive?.intOrNull)
        assertEquals(true, update["explicit_user_confirmation"]?.jsonPrimitive?.booleanOrNull)
        val target = update.getValue("target").jsonObject
        assertEquals("need", target["kind"]?.jsonPrimitive?.content)
        assertEquals("cost", target["need"]?.jsonPrimitive?.content)
    }

    @Test
    fun candidateParserRejectsQuestionWithoutExplicitUpdateGuard() {
        val unsafe = passport.replace(
            "\"update_requires_explicit_user_confirmation\": true,",
            "\"update_requires_explicit_user_confirmation\": false,",
        )
        assertEquals(null, AdaptiveProfileService.candidate(unsafe))
    }

    @Test
    fun candidateParserAcceptsEstimatedAdaptiveBeliefWithoutFakeRawRating() {
        val adaptivePassport = """
            {
              "cognitive_passport": {
                "schema_version": "2.0",
                "adaptive_passport": {
                  "schema_version": "adaptive_cognitive_passport_extension_1.0"
                },
                "adaptive_questioning": {
                  "schema_version": "adaptive_question_engine_1.0",
                  "question_needed": true,
                  "candidate": {
                    "question_id": "amq_123",
                    "trigger": "LEADERSHIP_REVERSAL",
                    "scope": "profile",
                    "update_requires_explicit_user_confirmation": true,
                    "target": {
                      "kind": "belief",
                      "mode": "car",
                      "need": "cost",
                      "measurement_status": "estimated",
                      "question_intent": "measure_estimated_construct"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val candidate =
            AdaptiveProfileService.candidate(adaptivePassport)

        assertNotNull(candidate)
        assertEquals("belief", candidate?.kind)
        assertEquals("car", candidate?.mode)
        assertEquals("cost", candidate?.need)
        assertEquals("estimated", candidate?.measurementStatus)
        assertEquals(null, candidate?.currentRawRating)
        assertEquals(true, candidate?.usesAdaptiveContract)
    }

    @Test
    fun adaptiveUpdateBuilderUsesAdaptiveProfileUpdateSchema() {
        val adaptivePassport = """
            {
              "cognitive_passport": {
                "schema_version": "2.0",
                "adaptive_passport": {
                  "schema_version": "adaptive_cognitive_passport_extension_1.0"
                },
                "adaptive_questioning": {
                  "schema_version": "adaptive_question_engine_1.0",
                  "question_needed": true,
                  "candidate": {
                    "question_id": "amq_456",
                    "trigger": "LEADERSHIP_REVERSAL",
                    "scope": "profile",
                    "update_requires_explicit_user_confirmation": true,
                    "target": {
                      "kind": "belief",
                      "mode": "bike",
                      "need": "privacy",
                      "measurement_status": "estimated"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val candidate =
            requireNotNull(
                AdaptiveProfileService.candidate(adaptivePassport)
            )

        val raw =
            AdaptiveProfileService.buildUpdateJson(
                adaptivePassport,
                candidate,
                6,
            )

        val root =
            Json.parseToJsonElement(raw).jsonObject

        assertEquals(
            "adaptive_profile_update_1.0",
            root["schema_version"]?.jsonPrimitive?.content,
        )

        val update =
            root.getValue("update").jsonObject

        assertEquals(
            true,
            update["explicit_user_confirmation"]
                ?.jsonPrimitive
                ?.booleanOrNull,
        )
    }
}
