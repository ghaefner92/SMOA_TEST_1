package com.example.imiq

import com.google.gson.JsonObject

const val CONTEXTUAL_DELIBERATION_REQUEST_SCHEMA = "contextual-deliberation-request-v1"
const val CONTEXTUAL_DELIBERATION_RESPONSE_SCHEMA = "contextual-deliberation-response-v1"
const val CONTEXTUAL_XAI_SCHEMA_VERSION = "contextual-xai-v1"
const val DIGITAL_COMPANION_EVIDENCE_SCHEMA_VERSION = "digital-companion-evidence-v2"
const val DIGITAL_COMPANION_NARRATION_SCHEMA_VERSION = "digital-companion-narration-v2"

data class ContextCoordinate(val latitude: Double, val longitude: Double)

data class ContextRouteSummary(
    val duration_seconds: Double,
    val distance_meters: Double,
    val transfers: Int,
)

data class ContextCandidateLeg(
    val segment_id: String,
    val mode: String,
    val start: ContextCoordinate,
    val end: ContextCoordinate,
    val duration_seconds: Double,
    val distance_meters: Double,
    val geometry: List<ContextCoordinate>? = null,
    val source_metadata: Map<String, Any?> = emptyMap(),
)

data class ContextualCandidateRoute(
    val route_id: String,
    val origin: ContextCoordinate,
    val destination: ContextCoordinate,
    val summary: ContextRouteSummary,
    val legs: List<ContextCandidateLeg>,
    val geometry: List<ContextCoordinate>? = null,
    val requested_at: String,
    val source_metadata: Map<String, Any?>,
    val transport_modes: List<String>,
)

data class ContextualQueryConfiguration(
    val query_orion: Boolean = true,
    // Weather is the only currently validated live source for the Android flow.
    // Keep other Orion families out of the request until their route semantics
    // and presentation are deliberately introduced.
    val entity_families: List<String> = listOf("weather"),
    val radius_meters: Double = 500.0,
    val limit: Int = 100,
)

data class ContextualDeliberationRequestDto(
    val schema_version: String = CONTEXTUAL_DELIBERATION_REQUEST_SCHEMA,
    val search_id: String,
    val timestamp: String,
    val cognitive_passport: JsonObject,
    val tolerance_profile: Map<String, Double?>,
    val candidate_routes: List<ContextualCandidateRoute>,
    val contextual_query: ContextualQueryConfiguration = ContextualQueryConfiguration(),
)

data class ContextualHotcoSummaryDto(
    val final_action_activations: Map<String, Double> = emptyMap(),
    val probabilities: Map<String, Double> = emptyMap(),
    val winner: String = "",
    val context_metadata: Map<String, Any?> = emptyMap(),
    val process_diagnostics: Map<String, Any?> = emptyMap(),
    val node_activations: Map<String, Any?> = emptyMap(),
    val ambiguity_state: String = "UNKNOWN",
)

data class ContextObservationDto(
    val source: String = "",
    val variable: String = "",
    val raw_value: Any? = null,
    val numeric_value: Double? = null,
    val unit: String? = null,
    val timestamp: String? = null,
    val status: String = "UNKNOWN",
    val quality: Double? = null,
    val confidence: Double? = null,
    val source_entity_id: String? = null,
    val spatial_metadata: Map<String, Any?>? = null,
)

data class ContextNormalizedStressorDto(
    val name: String = "",
    val value: Double? = null,
    val status: String = "UNKNOWN",
    val source_variables: List<String> = emptyList(),
    val normalization_metadata: Map<String, Any?>? = null,
    val confidence: Double? = null,
    val quality: Double? = null,
    val timestamp: String? = null,
)

/** Backend-authored deterministic current-condition contract. */
data class ContextFactDto(
    val variable: String = "",
    val label: String = "",
    val display_value: String = "",
    val status: String = "UNKNOWN",
    val freshness: String = "UNKNOWN",
    val source_confidence: String = "UNKNOWN",
    val observed_at: String? = null,
    val displayable: Boolean = true,
    val eligible_for_model: Boolean = false,
)

data class ContextSegmentDto(
    val segment_id: String = "",
    val mode: String = "",
    val start: ContextCoordinate? = null,
    val end: ContextCoordinate? = null,
    val duration_seconds: Double? = null,
    val distance_meters: Double? = null,
    val geometry: Any? = null,
    val raw_observations: List<ContextObservationDto> = emptyList(),
    val normalized_stressors: List<ContextNormalizedStressorDto> = emptyList(),
    val context_facts: List<ContextFactDto> = emptyList(),
    val contextual_data_completeness: Map<String, Any?> = emptyMap(),
)

data class ContextRouteContextDto(
    val route_id: String = "",
    val requested_at: String = "",
    val origin: ContextCoordinate? = null,
    val destination: ContextCoordinate? = null,
    val total_distance_meters: Double? = null,
    val total_duration_seconds: Double? = null,
    val segments: List<ContextSegmentDto> = emptyList(),
    val raw_observations: List<ContextObservationDto> = emptyList(),
    val normalized_stressors: List<ContextNormalizedStressorDto> = emptyList(),
    val context_facts: List<ContextFactDto> = emptyList(),
    val mode_specific_stressors: Map<String, List<ContextNormalizedStressorDto>> = emptyMap(),
    val contextual_data_completeness: Map<String, Any?> = emptyMap(),
    val source_metadata: Map<String, Any?> = emptyMap(),
)

data class ContextEffectiveExposureDto(
    val stressor: String = "",
    val context_value: Double? = null,
    val tolerance: Double? = null,
    val effective_value: Double? = null,
    val source_scope: String = "",
    val mode: String? = null,
    val status: String = "UNKNOWN",
    val contextual_coverage: Double? = null,
    val source_normalization_metadata: Map<String, Any?> = emptyMap(),
    val formula: String = "",
    val warnings: List<String> = emptyList(),
)

data class ContextContributionDto(
    val stressor: String = "",
    val target_node: String = "",
    val target_family: String = "",
    val mode: String? = null,
    val effective_exposure: Double = 0.0,
    val coefficient: Double = 0.0,
    val contribution: Double = 0.0,
    val source_scope: String = "",
    val source_normalization_metadata: Map<String, Any?> = emptyMap(),
    val tolerance_source: String? = null,
    val warnings: List<String> = emptyList(),
)

data class ContextPerturbationDto(
    val route_id: String = "",
    val normalization_version: String = "",
    val perturbation_version: String = "",
    val effective_exposures: List<ContextEffectiveExposureDto> = emptyList(),
    val need_perturbations: Map<String, Double> = emptyMap(),
    val action_perturbations: Map<String, Double> = emptyMap(),
    val valence_perturbations: Map<String, Double> = emptyMap(),
    val contributions: List<ContextContributionDto> = emptyList(),
    val missing_stressors: List<String> = emptyList(),
    val ignored_context_variables: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val data_coverage: Map<String, Any?> = emptyMap(),
)

data class ContextDifferenceDto(
    val final_action_activations: Map<String, Double> = emptyMap(),
    val probabilities: Map<String, Double> = emptyMap(),
)

data class ContextualCandidateResultDto(
    val route_id: String = "",
    val route_context: ContextRouteContextDto? = null,
    val context_perturbation: ContextPerturbationDto? = null,
    val hotco: ContextualHotcoSummaryDto = ContextualHotcoSummaryDto(),
    val difference_from_baseline: ContextDifferenceDto? = null,
    val source_status: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
)

data class ContextualDeliberationResponseDto(
    val schema_version: String = CONTEXTUAL_DELIBERATION_RESPONSE_SCHEMA,
    val search_id: String = "",
    val timestamp: String = "",
    val baseline: ContextualHotcoSummaryDto? = null,
    val candidate_results: List<ContextualCandidateResultDto> = emptyList(),
    val model_version: String = "",
    val normalization_version: String = "",
    val perturbation_version: String = "",
    val warnings: List<String> = emptyList(),
)

data class ContextualDriverDto(
    val fact_type: String? = null,
    val stressor: String? = null,
    val target: String? = null,
    val target_family: String? = null,
    val mode: String? = null,
    val context_value: Double? = null,
    val tolerance: Double? = null,
    val effective_exposure: Double? = null,
    val coefficient: Double? = null,
    val xi: Double? = null,
)

data class ContextualDriversDto(
    val all: List<ContextualDriverDto> = emptyList(),
    val top_positive: List<ContextualDriverDto> = emptyList(),
    val top_negative: List<ContextualDriverDto> = emptyList(),
)

data class ContextualDataQualityDto(
    val context_status: String? = null,
    val missing_stressors: List<String> = emptyList(),
    val missing_tolerances: List<String> = emptyList(),
    val ignored_context_variables: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
)

data class ContextualExplanationDto(
    val schema_version: String = "",
    val route_id: String = "",
    val baseline_tendency: String = "",
    val contextual_tendency: String = "",
    val contextual_probabilities: Map<String, Double> = emptyMap(),
    val leader_changed: Boolean = false,
    val ambiguity_state: String = "UNKNOWN",
    val contextual_drivers: ContextualDriversDto = ContextualDriversDto(),
    val need_drivers: List<ContextualDriverDto> = emptyList(),
    val valence_drivers: List<ContextualDriverDto> = emptyList(),
    val supporting_constraints: List<Map<String, Any?>> = emptyList(),
    val opposing_constraints: List<Map<String, Any?>> = emptyList(),
    val uncertainty: Map<String, Any?> = emptyMap(),
    val data_quality: ContextualDataQualityDto = ContextualDataQualityDto(),
    val counterfactual_readiness: Map<String, Any?> = emptyMap(),
    val summary: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val minimized_evidence: Map<String, Any?> = emptyMap(),
    val evidence_version: String = "",
)

data class ContextualNarrationDto(
    val schema_version: String = "",
    val narration_schema_version: String = "",
    val route_id: String = "",
    val language: String = "",
    val explanation_type: String = "",
    val title: String = "",
    val summary: String = "",
    val context_effect: String = "",
    val model_reasoning: List<String> = emptyList(),
    val uncertainty: String = "",
    val data_quality: String = "",
    val evidence_version: String = "",
    val generated_by_llm: Boolean = false,
    val status: String = "",
    val availability_status: String = "",
    val warnings: List<String> = emptyList(),
    val recommendation_mode: String? = null,
    val recommendation_status: String = "UNKNOWN",
    val selected_route_alignment: String = "UNKNOWN",
)

sealed interface ContextualNarrationState {
    data object NotRequested : ContextualNarrationState
    data object Loading : ContextualNarrationState
    data class Available(val narration: ContextualNarrationDto) : ContextualNarrationState
    data class Fallback(val narration: ContextualNarrationDto) : ContextualNarrationState
    data class Unavailable(val reason: String? = null) : ContextualNarrationState
}

sealed interface ContextualExplanationState {
    data object NotRequested : ContextualExplanationState
    data object Loading : ContextualExplanationState
    data class Available(val explanation: ContextualExplanationDto) : ContextualExplanationState
    data class Unavailable(val reason: String? = null) : ContextualExplanationState
}

data class ContextModeMapping(val canonicalMode: String, val warning: String? = null)

fun contextualModeFor(routingMode: String): ContextModeMapping {
    val normalized = routingMode.trim().lowercase()
    val canonical = when (normalized) {
        "walk", "walking", "foot" -> "walk"
        "bike", "bicycle", "cycling" -> "bike"
        "bike_sharing", "bikeshare" -> "bikeshare"
        "pt", "bus", "tram", "bus_tram", "pt_bus_tram" -> "pt_bus_tram"
        "train", "rail" -> "train"
        "car", "driving", "car_driver" -> "car_driver"
        "carsharing", "car_sharing" -> "carsharing"
        "escooter", "e_scooter" -> "escooter"
        else -> normalized.ifBlank { "unknown" }
    }
    return if (normalized in knownRoutingAliases) ContextModeMapping(canonical)
    else ContextModeMapping(canonical, "No canonical contextual mapping exists for routing mode '$routingMode'; it was preserved.")
}

private val knownRoutingAliases = setOf(
    "walk", "walking", "foot", "bike", "bicycle", "cycling", "bike_sharing",
    "bikeshare", "pt", "bus", "tram", "bus_tram", "pt_bus_tram", "train",
    "rail", "car", "driving", "car_driver", "carsharing", "car_sharing",
    "escooter", "e_scooter",
)

fun contextualRouteId(route: RankedRoute): String =
    "ranked-${route.rank}-${route.mode_key.trim().lowercase().ifBlank { "unknown" }}"

fun RankedRoute.toContextualCandidateRoute(
    origin: Pair<Double, Double>,
    destination: MobilityReferenceData.Place,
    requestedAt: String,
    geometry: List<Pair<Double, Double>>? = null,
    geometryProvenance: String = if (geometry.isNullOrEmpty()) "ORIGIN_DESTINATION_APPROXIMATION" else "ROUTE_RECONSTRUCTION",
): ContextualCandidateRoute {
    val originPoint = ContextCoordinate(origin.first, origin.second)
    val destinationPoint = ContextCoordinate(destination.lat, destination.lon)
    val routeMode = contextualModeFor(mode_key)
    val adaptedLegs = if (legs.isEmpty()) {
        listOf(
            ContextCandidateLeg(
                segment_id = "${contextualRouteId(this)}-segment-1",
                mode = routeMode.canonicalMode,
                start = originPoint,
                end = destinationPoint,
                duration_seconds = summary?.duration_seconds ?: 0.0,
                distance_meters = summary?.distance_meters ?: 0.0,
                source_metadata = mapOf("coordinate_source" to "route_endpoints"),
            ),
        )
    } else {
        legs.mapIndexed { index, leg ->
            val mode = contextualModeFor(leg.mode.ifBlank { mode_key })
            ContextCandidateLeg(
                segment_id = "${contextualRouteId(this)}-segment-${index + 1}",
                mode = mode.canonicalMode,
                start = originPoint,
                end = destinationPoint,
                duration_seconds = leg.duration_seconds,
                distance_meters = leg.distance_meters,
                source_metadata = mapOf(
                    "coordinate_source" to "route_endpoints_external_leg_coordinates_unavailable",
                    "from_name" to leg.from_name,
                    "to_name" to leg.to_name,
                    "stops" to leg.stops,
                    "mode_mapping_warning" to mode.warning,
                ),
            )
        }
    }

    val modes = adaptedLegs.map { it.mode }.ifEmpty { listOf(routeMode.canonicalMode) }
    return ContextualCandidateRoute(
        route_id = contextualRouteId(this),
        origin = originPoint,
        destination = destinationPoint,
        summary = ContextRouteSummary(
            duration_seconds = summary?.duration_seconds ?: 0.0,
            distance_meters = summary?.distance_meters ?: 0.0,
            transfers = summary?.transfers ?: 0,
        ),
        legs = adaptedLegs,
        geometry = geometry?.map { ContextCoordinate(it.first, it.second) },
        requested_at = requestedAt,
        source_metadata = mapOf(
            "provider" to "external_ranked_routes",
            "routing_rank" to rank,
            "routing_mode" to mode_key,
            "mode_mapping_warning" to routeMode.warning,
            "leg_coordinate_limitation" to "external_route_legs_do_not_supply_coordinates",
            "geometry_provenance" to geometryProvenance,
            "context_spatial_fidelity" to if (geometry.isNullOrEmpty()) "LOW" else "MEDIUM",
        ),
        transport_modes = modes.distinct(),
    )
}

enum class ContextAvailability { AVAILABLE, PARTIAL }

data class ContextualRouteAssessment(
    val routeId: String,
    val baselineWinner: String,
    val contextualWinner: String,
    val contextualProbability: Double?,
    val availability: ContextAvailability,
    val routeContext: ContextRouteContextDto? = null,
    val contextPerturbation: ContextPerturbationDto? = null,
    val differenceFromBaseline: ContextDifferenceDto? = null,
    val sourceStatus: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList(),
    val hotco: ContextualHotcoSummaryDto = ContextualHotcoSummaryDto(),
) {
    val tendencyChanged: Boolean
        get() = baselineWinner.isNotBlank() && contextualWinner.isNotBlank() &&
            baselineWinner != contextualWinner
}

sealed interface ContextualAnalysisState {
    data object NotRequested : ContextualAnalysisState
    data object Loading : ContextualAnalysisState
    data class Available(val byRouteId: Map<String, ContextualRouteAssessment>) : ContextualAnalysisState
    data class Partial(val byRouteId: Map<String, ContextualRouteAssessment>) : ContextualAnalysisState
    data class Unavailable(val reason: String? = null) : ContextualAnalysisState
}

fun contextualStateFrom(
    expectedRouteIds: List<String>,
    response: ContextualDeliberationResponseDto,
    expectedSearchId: String? = null,
): ContextualAnalysisState {
    if (response.schema_version != CONTEXTUAL_DELIBERATION_RESPONSE_SCHEMA) {
        return ContextualAnalysisState.Unavailable("Unsupported contextual response schema")
    }
    if (expectedSearchId != null && response.search_id != expectedSearchId) {
        return ContextualAnalysisState.Unavailable("Contextual response search ID mismatch")
    }
    val baseline = response.baseline ?: return ContextualAnalysisState.Unavailable("Missing baseline")
    if (baseline.winner.isBlank()) {
        return ContextualAnalysisState.Unavailable("Missing baseline model tendency")
    }
    val byId = response.candidate_results.associateBy { it.route_id }
    val assessments = linkedMapOf<String, ContextualRouteAssessment>()
    var partial = response.warnings.isNotEmpty()
    for (routeId in expectedRouteIds) {
        val result = byId[routeId]
        if (result == null) {
            partial = true
            continue
        }
        if (result.hotco.winner.isBlank()) {
            partial = true
            continue
        }
        val sourcePartial = result.warnings.isNotEmpty() ||
            result.source_status.any { (source, status) ->
                source != "supplied" && status != "OBSERVED"
            }
        partial = partial || sourcePartial
        assessments[routeId] = ContextualRouteAssessment(
            routeId = routeId,
            baselineWinner = baseline.winner,
            contextualWinner = result.hotco.winner,
            contextualProbability = result.hotco.probabilities[result.hotco.winner],
            availability = if (sourcePartial) ContextAvailability.PARTIAL else ContextAvailability.AVAILABLE,
            routeContext = result.route_context,
            contextPerturbation = result.context_perturbation,
            differenceFromBaseline = result.difference_from_baseline,
            sourceStatus = result.source_status,
            warnings = result.warnings,
            hotco = result.hotco,
        )
    }
    if (assessments.isEmpty()) return ContextualAnalysisState.Unavailable("No matching candidate results")
    return if (partial) ContextualAnalysisState.Partial(assessments)
    else ContextualAnalysisState.Available(assessments)
}

fun ContextualAnalysisState.assessmentFor(route: RankedRoute): ContextualRouteAssessment? =
    when (this) {
        is ContextualAnalysisState.Available -> byRouteId[contextualRouteId(route)]
        is ContextualAnalysisState.Partial -> byRouteId[contextualRouteId(route)]
        else -> null
    }

fun routesWithContextPreservingOrder(
    routes: List<RankedRoute>,
    state: ContextualAnalysisState,
): List<Pair<RankedRoute, ContextualRouteAssessment?>> =
    routes.map { it to state.assessmentFor(it) }
