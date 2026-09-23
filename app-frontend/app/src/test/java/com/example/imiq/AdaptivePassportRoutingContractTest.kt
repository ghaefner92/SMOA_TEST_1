package com.example.imiq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePassportRoutingContractTest {

    private fun adaptivePassport(
        observedBeliefs: Int = 4,
        estimatedBeliefs: Int = 40,
        fabricatedRawBeliefs: Int = 0,
    ): String =
        """
        {
          "cognitive_passport": {
            "schema_version": "2.0",
            "agent_id": "adaptive-test-user",

            "input_provenance": {
              "source_schema": "adaptive_cognitive_passport_extension_1.0",
              "policy": "observed_needs_valences_and_four_beliefs_plus_model_estimated_beliefs",

              "imputation_used": true,
              "belief_estimation_used": true,
              "population_trained_estimator_used": true,
              "population_or_synthetic_values_used": true,

              "observed_counts": {
                "needs": 11,
                "beliefs": $observedBeliefs,
                "valences": 4,
                "availability": 4,
                "environmental_tolerances": 5
              },

              "estimated_counts": {
                "needs": 0,
                "beliefs": $estimatedBeliefs,
                "valences": 0,
                "availability": 0
              }
            },

            "adaptive_passport": {
              "schema_version": "adaptive_cognitive_passport_extension_1.0",
              "model_version": "v1",
              "belief_cells_total": 44,
              "belief_cells_observed": $observedBeliefs,
              "belief_cells_estimated": $estimatedBeliefs,
              "fabricated_raw_beliefs": $fabricatedRawBeliefs,
              "initial_question_policy": "fixed_train_derived_four_item_calibration",
              "status": "experimental_internal_validation"
            },

            "profile": {
              "needs": {
                "pro_env": 0.25,
                "physical": 0.50,
                "privacy": 0.75,
                "autonomy": 1.00,
                "cost": 0.50,
                "speed": 0.75,
                "safety_accident": 1.00,
                "safety_crime": 0.50,
                "comfort": 0.75,
                "reliable": 1.00,
                "health_infection": 0.25
              },

              "availability": {
                "car": true,
                "bike": true,
                "pt": true,
                "walk": true
              },

              "environmental_tolerances": {
                "rain": 0.5,
                "crowding": 0.5,
                "darkness": 0.5,
                "traffic": 0.5,
                "temperature": 0.5
              }
            },

            "routing_parameters": {
              "mode_weights": {
                "car": 0.20,
                "bike": 0.30,
                "pt": 0.25,
                "walk": 0.25
              }
            }
          }
        }
        """.trimIndent()

    @Test
    fun adaptiveFourObservedFortyEstimatedPassportBuildsRoutingPayload() {

        val routed =
            PassportAdapter.toEnginePassport(
                adaptivePassport()
            )

        assertEquals(
            "adaptive-test-user",
            routed.get("id").asString,
        )

        val values =
            routed.getAsJsonObject("values")

        assertEquals(
            11,
            values.size(),
        )

        assertEquals(
            0.25,
            values.get("pro_env").asDouble,
            1e-12,
        )

        val availability =
            routed.getAsJsonObject("availability")

        assertEquals(
            4,
            availability.size(),
        )

        assertTrue(
            availability.get("car").asBoolean
        )

        assertTrue(
            availability.get("bike").asBoolean
        )

        assertTrue(
            availability.get("pt").asBoolean
        )

        assertTrue(
            availability.get("walk").asBoolean
        )

        val modeWeights =
            routed.getAsJsonObject("mode_weights")

        assertEquals(
            4,
            modeWeights.size(),
        )

        assertEquals(
            0.30,
            modeWeights.get("bike").asDouble,
            1e-12,
        )

        /*
         * Routing receives only its historical structural-access aliases.
         * It must not receive reconstructed/fabricated raw 4x11 beliefs.
         */
        val routingBeliefs =
            routed.getAsJsonObject("beliefs")

        assertEquals(
            3,
            routingBeliefs.size(),
        )

        assertTrue(
            routingBeliefs.get("owns_car").asBoolean
        )

        assertTrue(
            routingBeliefs.get("owns_bike").asBoolean
        )

        assertTrue(
            routingBeliefs.get("has_pt_access").asBoolean
        )

        assertFalse(
            routed.has("raw_beliefs")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun adaptivePassportRejectsBeliefProvenanceThatDoesNotAccountForAll44Cells() {

        PassportAdapter.toEnginePassport(
            adaptivePassport(
                observedBeliefs = 4,
                estimatedBeliefs = 39,
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun adaptivePassportRejectsFabricatedRawBeliefs() {

        PassportAdapter.toEnginePassport(
            adaptivePassport(
                fabricatedRawBeliefs = 1,
            )
        )
    }
}