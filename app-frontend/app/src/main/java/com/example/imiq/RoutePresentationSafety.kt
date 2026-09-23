package com.example.imiq

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Presentation-only interpretations of contextual-source status. These values
 * never change contextual data, HOTCO output, or routing order.
 */
enum class ContextPresentationState {
    AVAILABLE,
    PARTIAL,
    UNKNOWN,
    NO_DATA,
    UNAVAILABLE,
    ERROR,
}

fun contextPresentationState(
    stressor: ContextNormalizedStressorDto?,
    sourceStatus: String? = null,
): ContextPresentationState {
    if (stressor?.value != null) return ContextPresentationState.AVAILABLE

    fun statusState(status: String?): ContextPresentationState? = when (status?.trim()?.uppercase()) {
        "PARTIAL" -> ContextPresentationState.PARTIAL
        "UNKNOWN" -> ContextPresentationState.UNKNOWN
        "EMPTY", "NO_DATA", "NO DATA" -> ContextPresentationState.NO_DATA
        "UNAVAILABLE" -> ContextPresentationState.UNAVAILABLE
        "ERROR", "FAILED", "MALFORMED" -> ContextPresentationState.ERROR
        else -> null
    }

    return statusState(stressor?.status)
        ?: statusState(sourceStatus)
        ?: ContextPresentationState.NO_DATA
}

fun ContextPresentationState.userLabel(de: Boolean = false): String = when (this) {
    ContextPresentationState.AVAILABLE -> if (de) "Verfügbar" else "Available"
    ContextPresentationState.PARTIAL -> if (de) "Teilweise verfügbar" else "Partial"
    ContextPresentationState.UNKNOWN -> if (de) "Unbekannt" else "Unknown"
    ContextPresentationState.NO_DATA -> if (de) "Keine Daten" else "No data"
    ContextPresentationState.UNAVAILABLE -> if (de) "Vorübergehend nicht verfügbar" else "Temporarily unavailable"
    ContextPresentationState.ERROR -> if (de) "Datenfehler" else "Data error"
}

fun contextValueLabel(
    stressor: ContextNormalizedStressorDto?,
    sourceStatus: String? = null,
    de: Boolean = false,
): String {
    val value = stressor?.value
    if (value == null) return contextPresentationState(stressor, sourceStatus).userLabel(de)
    return when {
        value < 0.25 -> if (de) "Niedrig" else "Low"
        value < 0.50 -> if (de) "Mittel" else "Moderate"
        value < 0.75 -> if (de) "Hoch" else "High"
        else -> if (de) "Sehr hoch" else "Very high"
    }
}

/**
 * Formats an Orion observation as the measurement it is, rather than as a
 * HOTCO pressure. Keep this separate from [modelPressureLabel].
 */
fun observedContextValueLabel(
    variable: String,
    observation: ContextObservationDto?,
    sourceStatus: String? = null,
    de: Boolean = false,
): String {
    val raw = observation?.raw_value
    if (raw == null) return contextPresentationState(null, sourceStatus).userLabel(de)
    val value = raw.toString()
    return when (variable) {
        "temperature" -> if (observation?.unit?.trim()?.lowercase() in setOf("c", "°c", "celsius", "degree celsius", "cel")) "$value °C" else contextPresentationState(null, sourceStatus).userLabel(de)
        "rain", "windSpeed" -> contextPresentationState(null, sourceStatus).userLabel(de)
        else -> value
    }
}

fun freshnessLabel(freshness: String): String = when (freshness.uppercase()) {
    "CURRENT" -> "Current"
    "RECENT" -> "Updated recently"
    "STALE" -> "Older observation"
    else -> ""
}

data class TendencyPresentation(val title: String, val detail: String)

fun tendencyPresentation(mode: String, ambiguityState: String, source: String, leaderChanged: Boolean): TendencyPresentation {
    if (ambiguityState in setOf("NEAR_TIE", "UNRESOLVED")) {
        return TendencyPresentation("No clear leading tendency", "The exploratory analysis currently shows similar support for the leading alternatives.")
    }
    val label = mode.replace('_', ' ').replaceFirstChar { it.uppercase() }
    val prefix = if (source == "BASELINE") "Baseline analysis" else "Contextual analysis"
    return TendencyPresentation(label, if (leaderChanged) "$prefix changes the numerical leader." else "$prefix shows a clear leading tendency.")
}

/** Labels the normalized contextual quantity that HOTCO receives, never a raw observation. */
fun modelPressureLabel(
    stressor: ContextNormalizedStressorDto?,
    sourceStatus: String? = null,
    de: Boolean = false,
): String {
    val value = stressor?.value
    if (value == null) {
        return when (contextPresentationState(stressor, sourceStatus)) {
            ContextPresentationState.UNKNOWN -> if (de) "Nicht bestimmbar" else "Not determined"
            else -> contextPresentationState(stressor, sourceStatus).userLabel(de)
        }
    }
    return when {
        value < 0.25 -> if (de) "Keine Belastung" else "No pressure"
        value < 0.50 -> if (de) "Geringe Belastung" else "Light pressure"
        value < 0.75 -> if (de) "Mäßige Belastung" else "Moderate pressure"
        else -> if (de) "Hohe Belastung" else "High pressure"
    }
}

enum class RouteUserError {
    RANKING,
    CONTEXT,
    GEOMETRY,
    EXPLANATION,
    NARRATION,
}

fun RouteUserError.userMessage(de: Boolean = false): String = when (this) {
    RouteUserError.RANKING -> if (de) "Routen konnten nicht geladen werden. Bitte versuche es erneut." else "Routes couldn't be loaded. Please try again."
    RouteUserError.CONTEXT -> if (de) "Kontextdaten sind vorübergehend nicht verfügbar. Deine Routen bleiben nutzbar." else "Context data is temporarily unavailable. Your routing results are still usable."
    RouteUserError.GEOMETRY -> if (de) "Die Routenlinie konnte nicht geladen werden, aber das Routing-Ergebnis bleibt verfügbar." else "The route line couldn't be loaded, but the routing result is still available."
    RouteUserError.EXPLANATION -> if (de) "Die Erklärung konnte nicht geladen werden. Versuche es erneut." else "The explanation couldn't be loaded. Try again."
    RouteUserError.NARRATION -> if (de) "Die Erklärung des Digital Companion ist vorübergehend nicht verfügbar." else "The Digital Companion explanation is temporarily unavailable."
}

/** A route identity prevents an asynchronous response for route A appearing on route B. */
data class RouteScopedState<T>(val routeId: String, val state: T, val evidenceVersion: String? = null)

fun <T> RouteScopedState<T>?.stateFor(routeId: String, idle: T): T =
    if (this?.routeId == routeId) this.state else idle

fun <T> RouteScopedState<T>?.stateFor(routeId: String, evidenceVersion: String?, idle: T): T =
    if (this?.routeId == routeId && this.evidenceVersion == evidenceVersion) this.state else idle

enum class NarrationContractFailure {
    EVIDENCE_SCHEMA_MISMATCH,
    NARRATION_SCHEMA_MISMATCH,
    ROUTE_ID_MISMATCH,
    EVIDENCE_VERSION_MISMATCH,
}

fun narrationContractFailure(
    response: ContextualNarrationDto,
    expectedRouteId: String,
    expectedEvidenceVersion: String,
): NarrationContractFailure? = when {
    response.schema_version != DIGITAL_COMPANION_EVIDENCE_SCHEMA_VERSION -> NarrationContractFailure.EVIDENCE_SCHEMA_MISMATCH
    response.narration_schema_version != DIGITAL_COMPANION_NARRATION_SCHEMA_VERSION -> NarrationContractFailure.NARRATION_SCHEMA_MISMATCH
    response.route_id != expectedRouteId -> NarrationContractFailure.ROUTE_ID_MISMATCH
    response.evidence_version != expectedEvidenceVersion -> NarrationContractFailure.EVIDENCE_VERSION_MISMATCH
    else -> null
}

fun evidenceVersionPrefix(value: String): String =
    value.takeIf { it.startsWith("sha256:") }?.take(18) ?: "invalid"

/**
 * Re-serializes the exact backend evidence document without dropping explicit
 * nulls. Those nulls are part of the canonical evidence hash returned by the
 * backend, so omitting them in transit would make otherwise unchanged evidence
 * fail narration validation.
 */
fun narrationRequestBody(
    deterministic: ContextualExplanationDto,
    language: String,
    deliberation: String? = null,
    explanationType: String = "WHY_THIS_TENDENCY",
): JsonObject = JsonObject().apply {
    addProperty("route_id", deterministic.route_id)
    addProperty("language", language)
    addProperty("explanation_type", explanationType)
    addProperty("evidence_version", deterministic.evidence_version)
    if (deliberation != null) {
        add("deliberation", JsonParser.parseString(deliberation))
    } else {
        // Compatibility path for older backends and isolated contract tests.
        add(
            "minimized_evidence",
            JsonParser.parseString(
                GsonBuilder().serializeNulls().create().toJson(deterministic.minimized_evidence),
            ),
        )
    }
}

fun narrationExceptionCategory(error: Throwable): String = when (error::class.simpleName) {
    "JsonDataException", "JsonIOException", "JsonParseException", "JsonSyntaxException", "MalformedJsonException" -> "NARRATION_DESERIALIZATION_ERROR"
    "HttpException", "IOException", "SocketTimeoutException", "UnknownHostException" -> "NARRATION_PROVIDER_UNAVAILABLE"
    else -> "NARRATION_VALIDATION_FAILED"
}

fun narrationRequestIsCurrent(
    requestGeneration: Int,
    currentGeneration: Int,
    requestedSelection: String,
    currentSelection: String?,
    requestedEvidenceVersion: String,
    currentEvidenceVersion: String?,
): Boolean = requestGeneration == currentGeneration &&
    requestedSelection == currentSelection &&
    requestedEvidenceVersion == currentEvidenceVersion

fun narrationSpeakerLabel(narration: ContextualNarrationDto, nickname: String): String =
    if (narration.generated_by_llm) nickname else "Digital Companion"

data class NarrationPresentationSection(val label: String, val text: String)

fun structuredNarrationSections(narration: ContextualNarrationDto): List<NarrationPresentationSection> = buildList {
    val de = narration.language == "de"
    if (narration.summary.isNotBlank()) add(NarrationPresentationSection(if (de) "Meine Empfehlung" else "My recommendation", narration.summary))
    if (narration.model_reasoning.isNotEmpty()) add(NarrationPresentationSection(if (de) "Warum das zu dir passt" else "Why this fits you", narration.model_reasoning.joinToString("\n") { "• $it" }))
    if (narration.context_effect.isNotBlank()) add(NarrationPresentationSection(if (de) "Was heute mit hineinspielt" else "What's happening around you", narration.context_effect))
    if (narration.uncertainty.isNotBlank()) add(NarrationPresentationSection(if (de) "Wie eindeutig es ist" else "How clear this feels", narration.uncertainty))
    if (narration.data_quality.isNotBlank()) add(NarrationPresentationSection(if (de) "Was ich noch nicht weiß" else "What I still don't know", narration.data_quality))
}

fun narrationFollowUpLabels(language: String): List<Pair<String, String>> = if (language == "de") {
    listOf(
        "WHY_THIS_TENDENCY" to "Warum diese Wahl?",
        "WHY_NOT_MODE" to "Warum nicht die andere Option?",
        "WHAT_MATTERS" to "Was zählt für mich?",
        "WHAT_CHANGED" to "Was ist heute anders?",
    )
} else {
    listOf(
        "WHY_THIS_TENDENCY" to "Why this choice?",
        "WHY_NOT_MODE" to "Why not the other option?",
        "WHAT_MATTERS" to "What matters to me?",
        "WHAT_CHANGED" to "What changed today?",
    )
}

fun companionModeLabel(mode: String?, language: String): String = when (mode?.lowercase()) {
    "bike", "bicycle", "bikeshare" -> if (language == "de") "Fahrrad" else "Cycling"
    "car", "car_driver", "car_passenger" -> if (language == "de") "Auto" else "Driving"
    "walk", "walking", "foot" -> if (language == "de") "Zu Fuß" else "Walking"
    "pt", "pt_bus_tram", "train" -> if (language == "de") "Öffentlicher Verkehr" else "Public transport"
    else -> mode?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: ""
}

fun contextualCompanionUnavailableMessage(state: ContextualAnalysisState): String = when (state) {
    ContextualAnalysisState.Loading -> "Contextual analysis is loading."
    is ContextualAnalysisState.Unavailable -> "Contextual analysis is currently unavailable. Your routing result remains valid."
    ContextualAnalysisState.NotRequested -> "Contextual analysis is not available for this routing result."
    is ContextualAnalysisState.Available,
    is ContextualAnalysisState.Partial -> "Contextual analysis is not available for the selected route."
}

fun routingResultLabel(rank: Int, de: Boolean = false): String =
    if (de) "Routing-Ergebnis #$rank" else "Routing result #$rank"

/**
 * Presentation-only progress for a route result. It deliberately describes
 * optional enrichment after routing rather than implying that the user must
 * wait for context or narration before a route can be used.
 */
enum class RoutePipelineStage(val completedSteps: Int) {
    ROUTES_READY(1),
    MAP_LOADING(1),
    CONDITIONS_LOADING(2),
    COMPANION_PREPARING(3),
    READY(4),
}

fun routePipelineStage(
    routeState: RouteResultState,
    contextState: ContextualAnalysisState,
    narrationLoading: Boolean,
): RoutePipelineStage = when {
    routeState == RouteResultState.RANKING_WITHOUT_GEOMETRY -> RoutePipelineStage.MAP_LOADING
    contextState is ContextualAnalysisState.Loading -> RoutePipelineStage.CONDITIONS_LOADING
    narrationLoading -> RoutePipelineStage.COMPANION_PREPARING
    else -> RoutePipelineStage.READY
}

fun routePipelineStageLabel(stage: RoutePipelineStage, de: Boolean = false): String = when (stage) {
    RoutePipelineStage.ROUTES_READY -> if (de) "Routenoptionen bereit" else "Route options ready"
    RoutePipelineStage.MAP_LOADING -> if (de) "Routenlinien werden geladen" else "Drawing route lines"
    RoutePipelineStage.CONDITIONS_LOADING -> if (de) "Aktuelle Bedingungen werden geprüft" else "Checking current conditions"
    RoutePipelineStage.COMPANION_PREPARING -> if (de) "Digital Companion formuliert die Erklärung" else "Digital Companion is preparing the explanation"
    RoutePipelineStage.READY -> if (de) "Route und Kontext bereit" else "Route and context ready"
}

/** A visible, non-blocking progression: routing remains usable throughout. */
fun routeProgressMessage(
    routeState: RouteResultState,
    contextState: ContextualAnalysisState,
    narrationLoading: Boolean,
    de: Boolean = false,
): String? = when {
    routeState == RouteResultState.RANKING_WITHOUT_GEOMETRY ->
        if (de) "Routenoptionen sind bereit. Routenlinien werden geladen." else "Route options are ready. Drawing route lines."
    contextState is ContextualAnalysisState.Loading ->
        if (de) "Routenoptionen sind bereit. Aktuelle Bedingungen werden geprüft." else "Route options are ready. Checking current conditions."
    narrationLoading ->
        if (de) "Routenoptionen sind bereit. Dein Digital Companion formuliert die Erklärung." else "Route options are ready. Your Digital Companion is putting the explanation into words."
    else -> null
}

/** Scientific boundary for the exploratory contextual layer; it never changes routing rank. */
const val EXPLORATORY_CONTEXTUAL_MODEL_DISCLOSURE =
    "Contextual HOTCO-CT is an exploratory model-based analysis. It does not change the routing order."

/** Selection is presentation-only: it never sorts, filters, or refetches routing results. */
fun selectedRoutingResult(routes: List<RankedRoute>, selectedMode: String): RankedRoute? =
    routes.firstOrNull { it.mode_key == selectedMode } ?: routes.firstOrNull()

/**
 * Route selection is keyed by the backend-derived route identity.  A mode is
 * presentation metadata only: two future candidates can share a mode without
 * sharing map geometry, context, XAI, or a pending narration request.
 */
fun selectedRoutingResultById(routes: List<RankedRoute>, selectedRouteId: String?): RankedRoute? =
    routes.firstOrNull { contextualRouteId(it) == selectedRouteId } ?: routes.firstOrNull()
