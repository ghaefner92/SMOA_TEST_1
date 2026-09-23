package com.example.imiq

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualDeliberationContractTest {
    private val destination = MobilityReferenceData.Place("Station", "Magdeburg", 52.13, 11.64)
    private val origin = 52.12 to 11.62
    private val requestedAt = "2026-09-12T10:00:00Z"

    @Test
    fun rankedRouteMapsToBackendCandidateContract() {
        val route = route(1, "bike", 0.81).copy(
            summary = RouteSummary(600.0, 2500.0, 0),
            legs = listOf(RouteLeg(1, "bicycle", "A", "B", 2500.0, 600.0)),
        )
        val candidate = route.toContextualCandidateRoute(
            origin,
            destination,
            requestedAt,
            geometry = listOf(origin, destination.lat to destination.lon),
        )

        assertEquals("ranked-1-bike", candidate.route_id)
        assertEquals(ContextCoordinate(52.12, 11.62), candidate.origin)
        assertEquals(ContextCoordinate(52.13, 11.64), candidate.destination)
        assertEquals(600.0, candidate.summary.duration_seconds, 0.0)
        assertEquals(2500.0, candidate.summary.distance_meters, 0.0)
        assertEquals(listOf("bike"), candidate.transport_modes)
        assertEquals("bike", candidate.legs.single().mode)
        assertEquals(2, candidate.geometry?.size)
        assertEquals(
            "route_endpoints_external_leg_coordinates_unavailable",
            candidate.legs.single().source_metadata["coordinate_source"],
        )
    }

    @Test
    fun routingModesUseOneCanonicalMappingAndUnknownIsPreserved() {
        val expected = mapOf(
            "walking" to "walk",
            "foot" to "walk",
            "bicycle" to "bike",
            "bike_sharing" to "bikeshare",
            "tram" to "pt_bus_tram",
            "bus" to "pt_bus_tram",
            "rail" to "train",
            "car" to "car_driver",
            "car_sharing" to "carsharing",
            "e_scooter" to "escooter",
        )
        expected.forEach { (routing, canonical) ->
            assertEquals(canonical, contextualModeFor(routing).canonicalMode)
            assertNull(contextualModeFor(routing).warning)
        }
        val future = contextualModeFor("hoverboard")
        assertEquals("hoverboard", future.canonicalMode)
        assertTrue(future.warning?.contains("preserved") == true)
    }

    @Test
    fun requestContainsPassportTolerancesCandidatesAndNoCredential() {
        val request = ContextualDeliberationRequestDto(
            search_id = "search-1",
            timestamp = requestedAt,
            cognitive_passport = JsonObject().apply { addProperty("schema_version", "2.0") },
            tolerance_profile = mapOf(
                "rain" to 0.0,
                "crowding" to 0.5,
                "darkness" to 1.0,
                "traffic" to null,
                "temperature" to 2.0 / 3.0,
            ),
            candidate_routes = listOf(route(1, "bike", 0.8).toContextualCandidateRoute(origin, destination, requestedAt)),
        )
        val json = GsonBuilder().serializeNulls().create().toJson(request)
        assertTrue(json.contains("\"tolerance_profile\""))
        assertTrue(json.contains("\"traffic\":null"))
        assertTrue(json.contains("\"query_orion\":true"))
        assertFalse(json.contains("api_key", ignoreCase = true))
        assertFalse(json.contains("x-api-key", ignoreCase = true))
    }

    @Test
    fun contextualClientUsesConfiguredDyconetBaseUrl() {
        assertEquals("http://127.0.0.1:8077/", dyconetApiBaseUrl("http://127.0.0.1:8077"))
        assertEquals("https://example.test/base/", dyconetApiBaseUrl("https://example.test/base/"))
    }

    @Test
    fun reversedContextResponseMatchesByIdWithoutReorderingOrRescoring() {
        val routes = listOf(route(1, "bike", 0.8), route(2, "pt", 0.6))
        val originalScores = routes.map { it.score?.utility }
        val response = ContextualDeliberationResponseDto(
            schema_version = CONTEXTUAL_DELIBERATION_RESPONSE_SCHEMA,
            search_id = "search-1",
            baseline = hotco("bike"),
            candidate_results = listOf(
                candidateResult(routes[1], "pt"),
                candidateResult(routes[0], "bike"),
            ),
        )
        val state = contextualStateFrom(routes.map(::contextualRouteId), response)
        val merged = routesWithContextPreservingOrder(routes, state)

        assertEquals(listOf("bike", "pt"), merged.map { it.first.mode_key })
        assertEquals(originalScores, merged.map { it.first.score?.utility })
        assertFalse(merged[0].second!!.tendencyChanged)
        assertTrue(merged[1].second!!.tendencyChanged)
        assertEquals("pt", merged[1].second!!.contextualWinner)
    }

    @Test
    fun twoCandidateNetworkResponseParsesBaselineAndContextLeadersByRouteId() {
        val json = """
            {
              "schema_version":"contextual-deliberation-response-v1",
              "search_id":"search-1",
              "baseline":{"winner":"bike","probabilities":{"bike":0.7,"pt":0.3}},
              "candidate_results":[
                {"route_id":"ranked-1-bike","hotco":{"winner":"bike","probabilities":{"bike":0.65}},"source_status":{"weather":"OBSERVED"}},
                {"route_id":"ranked-2-pt","hotco":{"winner":"pt","probabilities":{"pt":0.6}},"source_status":{"weather":"OBSERVED"}}
              ]
            }
        """.trimIndent()
        val response = GsonBuilder().create().fromJson(
            json,
            ContextualDeliberationResponseDto::class.java,
        )
        val routes = listOf(route(1, "bike", 0.8), route(2, "pt", 0.6))
        val state = contextualStateFrom(routes.map(::contextualRouteId), response)

        assertEquals("bike", state.assessmentFor(routes[0])?.baselineWinner)
        assertFalse(state.assessmentFor(routes[0])!!.tendencyChanged)
        assertTrue(state.assessmentFor(routes[1])!!.tendencyChanged)
        assertEquals(routes, routesWithContextPreservingOrder(routes, state).map { it.first })
    }

    @Test
    fun fullContextualEvidenceSurvivesJsonParsingAndUnknownStaysUnknown() {
        val json = """
            {
              "schema_version":"contextual-deliberation-response-v1",
              "search_id":"search-1",
              "timestamp":"2026-09-12T08:00:00Z",
              "model_version":"hotco_ct_v4.3",
              "normalization_version":"context-normalization-v1",
              "perturbation_version":"context-perturbation-v1",
              "baseline":{"winner":"bike","probabilities":{"bike":0.7}},
              "candidate_results":[
                {"route_id":"ranked-1-bike",
                 "route_context":{"route_id":"ranked-1-bike","requested_at":"2026-09-12T08:00:00Z",
                   "origin":{"latitude":52.13,"longitude":11.63},"destination":{"latitude":52.14,"longitude":11.64},
                   "segments":[],"raw_observations":[],"normalized_stressors":[{"name":"rain","value":null,"status":"UNKNOWN"}],"mode_specific_stressors":{},"source_metadata":{}},
                 "context_perturbation":{"route_id":"ranked-1-bike","normalization_version":"context-normalization-v1","perturbation_version":"context-perturbation-v1",
                   "effective_exposures":[{"stressor":"rain","context_value":null,"tolerance":0.4,"effective_value":null,"source_scope":"route","status":"UNKNOWN"}],
                   "need_perturbations":{"comfort":0.0},"action_perturbations":{"bike":0.0},"valence_perturbations":{"valence_bike":0.0},"contributions":[],"missing_stressors":["rain"],"ignored_context_variables":[],"warnings":[],"data_coverage":{}},
                 "difference_from_baseline":{"final_action_activations":{"bike":0.0},"probabilities":{"bike":0.0}},
                 "hotco":{"winner":"bike","probabilities":{"bike":0.7}},"source_status":{"weather":"OBSERVED"}}
              ]
            }
        """.trimIndent()
        val response = GsonBuilder().create().fromJson(json, ContextualDeliberationResponseDto::class.java)
        val route = route(1, "bike", 0.8)
        val state = contextualStateFrom(listOf(contextualRouteId(route)), response)
        val assessment = (state as ContextualAnalysisState.Available).byRouteId.values.single()
        assertEquals("UNKNOWN", assessment.routeContext!!.normalized_stressors.single().status)
        assertNull(assessment.routeContext.normalized_stressors.single().value)
        assertNotNull(assessment.contextPerturbation)
        assertNotNull(assessment.differenceFromBaseline)
        assertEquals("context-normalization-v1", response.normalization_version)
        assertEquals("context-perturbation-v1", response.perturbation_version)
    }

    @Test
    fun loadingAndFailureNeverRemoveExistingRoutes() {
        val routes = listOf(route(1, "bike", 0.8), route(2, "pt", 0.6))
        assertEquals(routes, routesWithContextPreservingOrder(routes, ContextualAnalysisState.Loading).map { it.first })
        assertEquals(
            routes,
            routesWithContextPreservingOrder(routes, ContextualAnalysisState.Unavailable("timeout")).map { it.first },
        )
    }

    @Test
    fun partialSourcesProducePartialStateAndMissingResultDoesNotShiftByPosition() {
        val routes = listOf(route(1, "bike", 0.8), route(2, "pt", 0.6))
        val response = ContextualDeliberationResponseDto(
            schema_version = CONTEXTUAL_DELIBERATION_RESPONSE_SCHEMA,
            search_id = "search-1",
            baseline = hotco("bike"),
            candidate_results = listOf(
                candidateResult(routes[1], "pt", mapOf("weather" to "OBSERVED", "traffic" to "UNAVAILABLE")),
            ),
        )
        val state = contextualStateFrom(routes.map(::contextualRouteId), response)
        assertTrue(state is ContextualAnalysisState.Partial)
        assertNull(state.assessmentFor(routes[0]))
        assertEquals(ContextAvailability.PARTIAL, state.assessmentFor(routes[1])?.availability)
    }

    @Test
    fun emptyOptionalSuppliedChannelDoesNotMakeCompleteOrionDataPartial() {
        val route = route(1, "bike", 0.8)
        val response = ContextualDeliberationResponseDto(
            schema_version = CONTEXTUAL_DELIBERATION_RESPONSE_SCHEMA,
            search_id = "search-1",
            baseline = hotco("bike"),
            candidate_results = listOf(
                candidateResult(route, "bike", mapOf("supplied" to "EMPTY", "weather" to "OBSERVED")),
            ),
        )
        val state = contextualStateFrom(listOf(contextualRouteId(route)), response)
        assertTrue(state is ContextualAnalysisState.Available)
        assertEquals(ContextAvailability.AVAILABLE, state.assessmentFor(route)?.availability)
    }

    @Test
    fun unsupportedResponseSchemaFallsBackWithoutTouchingRoutes() {
        val route = route(1, "bike", 0.8)
        val response = ContextualDeliberationResponseDto(
            schema_version = "future-contextual-schema",
            search_id = "search-1",
            baseline = hotco("bike"),
            candidate_results = listOf(candidateResult(route, "bike")),
        )

        val state = contextualStateFrom(listOf(contextualRouteId(route)), response)
        assertTrue(state is ContextualAnalysisState.Unavailable)
        assertEquals(listOf(route), routesWithContextPreservingOrder(listOf(route), state).map { it.first })
    }

    @Test
    fun responseFromAnotherSearchIsRejected() {
        val route = route(1, "bike", 0.8)
        val response = ContextualDeliberationResponseDto(
            schema_version = CONTEXTUAL_DELIBERATION_RESPONSE_SCHEMA,
            search_id = "stale-search",
            baseline = hotco("bike"),
            candidate_results = listOf(candidateResult(route, "bike")),
        )

        val state = contextualStateFrom(
            listOf(contextualRouteId(route)),
            response,
            expectedSearchId = "current-search",
        )
        assertTrue(state is ContextualAnalysisState.Unavailable)
    }

    @Test
    fun structuredExplanationParsesRouteEvidenceWithoutInternalCausalClaims() {
        val json = """
            {
              "schema_version":"contextual-xai-v1",
              "route_id":"ranked-1-bike",
              "baseline_tendency":"bike",
              "contextual_tendency":"pt",
              "leader_changed":true,
              "contextual_drivers":{"top_positive":[{"stressor":"rain","target":"need_comfort","xi":0.2}],"top_negative":[]},
              "data_quality":{"context_status":"PARTIAL","missing_stressors":["traffic"]},
              "summary":["The simulated model tendency changed from bike to pt compared with the no-context simulation."]
            }
        """.trimIndent()
        val explanation = GsonBuilder().create().fromJson(json, ContextualExplanationDto::class.java)
        assertEquals("ranked-1-bike", explanation.route_id)
        assertTrue(explanation.leader_changed)
        assertEquals(0.2, explanation.contextual_drivers.top_positive.single().xi ?: -1.0, 0.0)
        assertEquals("PARTIAL", explanation.data_quality.context_status)
        assertTrue(explanation.summary.single().contains("simulated model tendency"))
    }

    @Test
    fun backendContextFactsAreTheCurrentConditionsSourceOfTruth() {
        val json = """{"route_id":"ranked-1-bike","context_facts":[{"variable":"temperature","label":"Temperature","display_value":"18 °C","status":"AVAILABLE","freshness":"CURRENT","source_confidence":"CONFIRMED"},{"variable":"daylight","label":"Light conditions","display_value":"Daylight","status":"AVAILABLE","freshness":"CURRENT","source_confidence":"STRONGLY_INFERRED"},{"variable":"crowding","label":"Crowding","display_value":"No data","status":"UNKNOWN","freshness":"UNKNOWN","source_confidence":"UNKNOWN"}]}"""
        val routeContext = GsonBuilder().create().fromJson(json, ContextRouteContextDto::class.java)
        assertEquals(listOf("18 °C", "Daylight", "No data"), routeContext.context_facts.map { it.display_value })
        assertEquals(listOf("AVAILABLE", "AVAILABLE", "UNKNOWN"), routeContext.context_facts.map { it.status })
    }

    @Test
    fun narrationResponsePreservesEvidenceIdentityAndLanguage() {
        val json = """
            {"schema_version":"digital-companion-evidence-v2","narration_schema_version":"digital-companion-narration-v2","route_id":"ranked-1-bike","language":"de","explanation_type":"WHY_THIS_TENDENCY","title":"Meine Einschätzung","summary":"Meine Empfehlung ist Fahrrad.","context_effect":"Kontextdaten","model_reasoning":[],"uncertainty":"Teilweise","data_quality":"Teilweise verfügbar","evidence_version":"sha256:abc","generated_by_llm":true,"status":"available","availability_status":"AVAILABLE"}
        """.trimIndent()
        val narration = GsonBuilder().create().fromJson(json, ContextualNarrationDto::class.java)
        assertEquals("ranked-1-bike", narration.route_id)
        assertEquals("de", narration.language)
        assertTrue(narration.generated_by_llm)
        assertEquals("digital-companion-evidence-v2", narration.schema_version)
        assertEquals("digital-companion-narration-v2", narration.narration_schema_version)
        assertEquals("sha256:abc", narration.evidence_version)
    }

    private fun route(rank: Int, mode: String, utility: Double) = RankedRoute(
        rank = rank,
        mode_key = mode,
        mode_label = mode,
        available = true,
        score = RouteScore(utility),
        summary = RouteSummary(600.0, 2000.0, 0),
    )

    private fun hotco(winner: String) = ContextualHotcoSummaryDto(
        probabilities = mapOf("bike" to if (winner == "bike") 0.7 else 0.3, "pt" to if (winner == "pt") 0.7 else 0.3),
        winner = winner,
    )

    private fun candidateResult(
        route: RankedRoute,
        winner: String,
        sources: Map<String, String> = mapOf("weather" to "OBSERVED"),
    ) = ContextualCandidateResultDto(
        route_id = contextualRouteId(route),
        hotco = hotco(winner),
        source_status = sources,
    )
}
