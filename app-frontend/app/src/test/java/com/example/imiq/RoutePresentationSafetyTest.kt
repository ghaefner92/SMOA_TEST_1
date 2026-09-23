package com.example.imiq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePresentationSafetyTest {
    @Test
    fun standardSettingsExposeOnlyImplementedCapabilities() {
        assertEquals(setOf(SettingsCapability.LANGUAGE, SettingsCapability.PROFILE), standardSettingsCapabilities)
    }

    @Test
    fun routingLabelsAreNeutralAndDoNotClaimHotcoRanking() {
        assertEquals("Routing result #1", routingResultLabel(1))
        assertFalse(routingResultLabel(1).contains("match", ignoreCase = true))
        assertFalse(routingResultLabel(1).contains("recommend", ignoreCase = true))
    }

    @Test
    fun exploratoryDisclosureKeepsContextualModelSeparateFromRoutingAuthority() {
        assertTrue(EXPLORATORY_CONTEXTUAL_MODEL_DISCLOSURE.contains("exploratory", ignoreCase = true))
        assertTrue(EXPLORATORY_CONTEXTUAL_MODEL_DISCLOSURE.contains("does not change the routing order", ignoreCase = true))
        assertFalse(EXPLORATORY_CONTEXTUAL_MODEL_DISCLOSURE.contains("recommend", ignoreCase = true))
    }

    @Test
    fun explanationAndNarrationAreVisibleOnlyForTheirRoute() {
        val routeA = "ranked-1-bike"
        val routeB = "ranked-2-pt"
        val explanation = RouteScopedState<ContextualExplanationState>(routeA, ContextualExplanationState.Loading)
        val narration = RouteScopedState<ContextualNarrationState>(routeA, ContextualNarrationState.Loading)

        assertTrue(explanation.stateFor(routeA, ContextualExplanationState.NotRequested) is ContextualExplanationState.Loading)
        assertTrue(explanation.stateFor(routeB, ContextualExplanationState.NotRequested) is ContextualExplanationState.NotRequested)
        assertTrue(narration.stateFor(routeA, ContextualNarrationState.NotRequested) is ContextualNarrationState.Loading)
        assertTrue(narration.stateFor(routeB, ContextualNarrationState.NotRequested) is ContextualNarrationState.NotRequested)
    }

    @Test
    fun narrationIsHiddenWhenTheSameRouteReceivesNewEvidence() {
        val narration = RouteScopedState<ContextualNarrationState>("route-a", ContextualNarrationState.Available(ContextualNarrationDto(route_id = "route-a")), "evidence-x")
        assertTrue(narration.stateFor("route-a", "evidence-x", ContextualNarrationState.NotRequested) is ContextualNarrationState.Available)
        assertTrue(narration.stateFor("route-a", "evidence-y", ContextualNarrationState.NotRequested) is ContextualNarrationState.NotRequested)
        // The companion nickname is presentation-only and never appears in this key.
        assertTrue(narration.stateFor("route-a", "evidence-x", ContextualNarrationState.NotRequested) is ContextualNarrationState.Available)
    }

    @Test
    fun narrationContractUsesOnlyRouteAndCanonicalEvidenceIdentity() {
        val narration = ContextualNarrationDto(
            schema_version = DIGITAL_COMPANION_EVIDENCE_SCHEMA_VERSION,
            narration_schema_version = DIGITAL_COMPANION_NARRATION_SCHEMA_VERSION,
            route_id = "route-a",
            evidence_version = "sha256:abc",
            generated_by_llm = true,
        )
        assertEquals(null, narrationContractFailure(narration, "route-a", "sha256:abc"))
        assertEquals(NarrationContractFailure.ROUTE_ID_MISMATCH, narrationContractFailure(narration, "route-b", "sha256:abc"))
        assertEquals(NarrationContractFailure.EVIDENCE_VERSION_MISMATCH, narrationContractFailure(narration, "route-a", "sha256:def"))
        assertEquals(
            NarrationContractFailure.EVIDENCE_SCHEMA_MISMATCH,
            narrationContractFailure(narration.copy(schema_version = "old"), "route-a", "sha256:abc"),
        )
    }

    @Test
    fun narrationRequestPreservesExplicitNullEvidenceFields() {
        val deterministic = ContextualExplanationDto(
            route_id = "route-a",
            evidence_version = "sha256:abc",
            minimized_evidence = mapOf(
                "route_id" to "route-a",
                "rho" to null,
                "uncertainty" to mapOf("minimum_top2_gap" to null),
            ),
        )

        val body = narrationRequestBody(deterministic, "en")

        assertTrue(body.getAsJsonObject("minimized_evidence").has("rho"))
        assertTrue(body.getAsJsonObject("minimized_evidence").get("rho").isJsonNull)
        assertTrue(
            body.getAsJsonObject("minimized_evidence")
                .getAsJsonObject("uncertainty")
                .get("minimum_top2_gap")
                .isJsonNull,
        )
    }

    @Test
    fun productionNarrationRequestLetsBackendRebuildCanonicalEvidence() {
        val deterministic = ContextualExplanationDto(
            route_id = "route-a",
            evidence_version = "sha256:abc",
            minimized_evidence = mapOf("route_id" to "route-a", "rho" to null),
        )
        val deliberation = """{"search_id":"search-a","candidate_results":[]}"""

        val body = narrationRequestBody(deterministic, "en", deliberation)

        assertEquals("search-a", body.getAsJsonObject("deliberation").get("search_id").asString)
        assertFalse(body.has("minimized_evidence"))
        assertEquals("sha256:abc", body.get("evidence_version").asString)
    }

    @Test
    fun coachingFollowUpChangesFocusWithoutChangingEvidenceIdentity() {
        val deterministic = ContextualExplanationDto(
            route_id = "route-a",
            evidence_version = "sha256:abc",
            minimized_evidence = mapOf("route_id" to "route-a"),
        )
        val body = narrationRequestBody(deterministic, "en", explanationType = "WHAT_MATTERS")
        assertEquals("WHAT_MATTERS", body.get("explanation_type").asString)
        assertEquals("sha256:abc", body.get("evidence_version").asString)
        assertEquals(
            listOf("WHY_THIS_TENDENCY", "WHY_NOT_MODE", "WHAT_MATTERS", "WHAT_CHANGED"),
            narrationFollowUpLabels("en").map { it.first },
        )
    }

    @Test
    fun staleAsyncNarrationRequestIsRejectedByGenerationSelectionOrEvidence() {
        assertTrue(narrationRequestIsCurrent(3, 3, "bike", "bike", "sha256:x", "sha256:x"))
        assertFalse(narrationRequestIsCurrent(2, 3, "bike", "bike", "sha256:x", "sha256:x"))
        assertFalse(narrationRequestIsCurrent(3, 3, "bike", "car", "sha256:x", "sha256:x"))
        assertFalse(narrationRequestIsCurrent(3, 3, "bike", "bike", "sha256:x", "sha256:y"))
    }

    @Test
    fun nicknameIsAttributedOnlyToGeneratedNarration() {
        assertEquals("Nova", narrationSpeakerLabel(ContextualNarrationDto(generated_by_llm = true), "Nova"))
        assertEquals("Digital Companion", narrationSpeakerLabel(ContextualNarrationDto(generated_by_llm = false), "Nova"))
    }

    @Test
    fun structuredNarrationExposesEveryBackendField() {
        val sections = structuredNarrationSections(
            ContextualNarrationDto(
                summary = "summary",
                context_effect = "context",
                model_reasoning = listOf("reason"),
                uncertainty = "uncertainty",
                data_quality = "quality",
            ),
        )
        assertEquals(
            listOf("My recommendation", "Why this fits you", "What's happening around you", "How clear this feels", "What I still don't know"),
            sections.map { it.label },
        )
    }

    @Test
    fun missingOptionalNarrationSectionsDoNotPreventSummaryRendering() {
        val sections = structuredNarrationSections(ContextualNarrationDto(summary = "summary"))
        assertEquals(listOf("My recommendation"), sections.map { it.label })
    }

    @Test
    fun companionRecommendationUsesFriendlyModeLabels() {
        assertEquals("Cycling", companionModeLabel("bike", "en"))
        assertEquals("Auto", companionModeLabel("car", "de"))
        assertEquals("Public transport", companionModeLabel("pt_bus_tram", "en"))
    }

    @Test
    fun companionFollowUpsIncludeTheAlternativeQuestion() {
        assertEquals(
            listOf("WHY_THIS_TENDENCY", "WHY_NOT_MODE", "WHAT_MATTERS", "WHAT_CHANGED"),
            narrationFollowUpLabels("en").map { it.first },
        )
    }

    @Test
    fun tendencyAndFreshnessPresentationRespectDeterministicStates() {
        assertEquals("Bike", tendencyPresentation("bike", "CLEAR", "CONTEXTUAL", false).title)
        assertEquals("No clear leading tendency", tendencyPresentation("bike", "NEAR_TIE", "CONTEXTUAL", true).title)
        assertEquals("Current", freshnessLabel("CURRENT"))
        assertEquals("Updated recently", freshnessLabel("RECENT"))
        assertEquals("Older observation", freshnessLabel("STALE"))
        assertEquals("", freshnessLabel("UNKNOWN"))
    }

    @Test
    fun routeProgressKeepsRoutingAndOptionalWorkDistinct() {
        assertEquals(
            "Route options are ready. Drawing route lines.",
            routeProgressMessage(RouteResultState.RANKING_WITHOUT_GEOMETRY, ContextualAnalysisState.NotRequested, false),
        )
        assertEquals(
            "Route options are ready. Checking current conditions.",
            routeProgressMessage(RouteResultState.USABLE_GEOMETRY, ContextualAnalysisState.Loading, false),
        )
        assertEquals(
            "Route options are ready. Your Digital Companion is putting the explanation into words.",
            routeProgressMessage(RouteResultState.USABLE_GEOMETRY, ContextualAnalysisState.Available(emptyMap()), true),
        )
    }

    @Test
    fun contextualFailureHasStableCompanionSurfaceMessage() {
        val message = contextualCompanionUnavailableMessage(ContextualAnalysisState.Unavailable("internal"))
        assertTrue(message.contains("unavailable", ignoreCase = true))
        assertTrue(message.contains("routing result", ignoreCase = true))
        assertFalse(message.contains("internal", ignoreCase = true))
    }

    @Test
    fun contextStatesRemainDistinctAndNeverFallBackToLow() {
        fun stressor(status: String) = ContextNormalizedStressorDto(name = "rain", value = null, status = status)

        assertEquals("Unknown", contextValueLabel(stressor("UNKNOWN")))
        assertEquals("No data", contextValueLabel(stressor("EMPTY")))
        assertEquals("Temporarily unavailable", contextValueLabel(stressor("UNAVAILABLE")))
        assertEquals("Data error", contextValueLabel(stressor("ERROR")))
        assertEquals("No data", contextValueLabel(null))
        assertFalse(contextValueLabel(stressor("UNKNOWN")).equals("Low", ignoreCase = true))
        assertFalse(contextValueLabel(stressor("UNAVAILABLE")).equals("Low", ignoreCase = true))
    }

    @Test
    fun observedMeasurementsAndModelPressureUseDifferentTerminology() {
        val temperature = ContextObservationDto(variable = "temperature", raw_value = "18.4", numeric_value = 18.4, unit = "CEL", status = "OBSERVED")
        val comfort = ContextNormalizedStressorDto(name = "temperature", value = 0.0, status = "DERIVED")

        assertEquals("18.4 °C", observedContextValueLabel("temperature", temperature))
        assertEquals("No data", observedContextValueLabel("temperature", temperature.copy(unit = null)))
        assertEquals("No pressure", modelPressureLabel(comfort))
        assertFalse(observedContextValueLabel("temperature", temperature).contains("Low", ignoreCase = true))
        assertEquals("Not determined", modelPressureLabel(ContextNormalizedStressorDto(name = "temperature", value = null, status = "UNKNOWN")))
    }

    @Test
    fun contextStatusChipsHaveExplicitDistinctLabelsForEveryState() {
        val labels = ContextPresentationState.entries.map { it.userLabel() }
        assertEquals(ContextPresentationState.entries.size, labels.toSet().size)
        assertEquals("Available", ContextPresentationState.AVAILABLE.userLabel())
        assertEquals("Partial", ContextPresentationState.PARTIAL.userLabel())
        assertEquals("Unknown", ContextPresentationState.UNKNOWN.userLabel())
        assertEquals("No data", ContextPresentationState.NO_DATA.userLabel())
        assertEquals("Temporarily unavailable", ContextPresentationState.UNAVAILABLE.userLabel())
        assertEquals("Data error", ContextPresentationState.ERROR.userLabel())
    }

    @Test
    fun androidContextQueryUsesOnlyTheValidatedWeatherSource() {
        assertEquals(listOf("weather"), ContextualQueryConfiguration().entity_families)
    }

    @Test
    fun safeErrorsNeverExposeExceptionDetails() {
        RouteUserError.entries.forEach { error ->
            val message = error.userMessage()
            assertFalse(message.contains("Exception"))
            assertFalse(message.contains("Http"))
            assertFalse(message.contains("UnknownHost"))
        }
    }

    @Test
    fun secondaryFailuresDoNotInvalidateRankedRoutes() {
        val routes = listOf(
            RankedRoute(rank = 1, mode_key = "bike", available = true),
            RankedRoute(rank = 2, mode_key = "pt", available = true),
        )
        assertEquals(routes, routesWithContextPreservingOrder(routes, ContextualAnalysisState.Unavailable("safe")).map { it.first })
        assertEquals(RouteResultState.RANKING_WITHOUT_GEOMETRY, routeStateAfterRanking(true, true, false))
        assertTrue(ContextualExplanationState.Unavailable(RouteUserError.EXPLANATION.userMessage()) is ContextualExplanationState.Unavailable)
        assertTrue(ContextualNarrationState.Unavailable(RouteUserError.NARRATION.userMessage()) is ContextualNarrationState.Unavailable)
    }

    @Test
    fun selectingAnAlternativeKeepsExternalOrderAndChangesOnlySelectedPresentation() {
        val routes = listOf(
            RankedRoute(rank = 1, mode_key = "bike", available = true),
            RankedRoute(rank = 2, mode_key = "car", available = true),
            RankedRoute(rank = 3, mode_key = "pt", available = true),
        )

        assertEquals(listOf("bike", "car", "pt"), routes.map { it.mode_key })
        assertEquals("car", selectedRoutingResult(routes, "car")?.mode_key)
        assertEquals(listOf("bike", "car", "pt"), routes.map { it.mode_key })
        assertEquals("bike", selectedRoutingResult(routes, "missing")?.mode_key)
    }
}
