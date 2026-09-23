package com.example.imiq

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.GsonBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

@Composable
fun RouteResultsScreen(
    destination: MobilityReferenceData.Place = MobilityReferenceData.destination,
    onBackClick: () -> Unit = {}
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val locationHelper = remember { LocationHelper(context) }
    val screenScope = rememberCoroutineScope()

    // Ranking and all displayed scores come from the live routing engine using
    // the user's validated passport. Failures remain visible; no sample profile
    // or synthetic route scores replace missing data.
    var engineRoutes by remember { mutableStateOf<List<RankedRoute>>(emptyList()) }
    var origin by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var originLabel by remember { mutableStateOf(s.yourLocation) }
    // Geometry and every secondary state are keyed by route identity rather
    // than mode. External routing may later return two candidates for one
    // mode; a mode key alone is not safe for async association.
    var geometry by remember { mutableStateOf<Map<String, RouteGeo>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var routeState by remember { mutableStateOf(RouteResultState.LOADING) }
    var geometryOutcomes by remember { mutableStateOf<Map<String, GeometryOutcome>>(emptyMap()) }
    var mapAvailable by remember { mutableStateOf<Boolean?>(null) }
    var contextualState by remember {
        mutableStateOf<ContextualAnalysisState>(ContextualAnalysisState.NotRequested)
    }
    var contextualResponse by remember { mutableStateOf<ContextualDeliberationResponseDto?>(null) }
    var explanationState by remember { mutableStateOf<RouteScopedState<ContextualExplanationState>?>(null) }
    var narrationState by remember { mutableStateOf<RouteScopedState<ContextualNarrationState>?>(null) }
    var searchAttempt by remember { mutableIntStateOf(0) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var contextRequestVersion by remember { mutableIntStateOf(0) }
    var explanationRequestVersion by remember { mutableIntStateOf(0) }
    var narrationRequestVersion by remember { mutableIntStateOf(0) }
    var selectedRouteId by remember { mutableStateOf<String?>(null) }
    var selectedRouteIdForAsync by remember { mutableStateOf<String?>(null) }
    var contextJob by remember { mutableStateOf<Job?>(null) }
    var explanationJob by remember { mutableStateOf<Job?>(null) }
    var narrationJob by remember { mutableStateOf<Job?>(null) }

    fun requestContextualAnalysis(
        ranked: List<RankedRoute>,
        start: Pair<Double, Double>,
        passportJson: String,
        storedPassport: JsonObject,
        generation: Int,
        geometryByRouteId: Map<String, List<Pair<Double, Double>>>,
    ) {
        contextJob?.cancel()
        val requestVersion = ++contextRequestVersion
        contextualState = ContextualAnalysisState.Loading
        val startedAtMs = SystemClock.elapsedRealtime()
        Log.d("RouteResults", "PIPELINE_CONTEXT_START routes=${ranked.size} generation=$generation geometry_routes=${geometryByRouteId.size}")
        contextJob = screenScope.launch {
            val requestedAt = Instant.now().toString()
            val routeIds = ranked.map(::contextualRouteId)
            val searchId = UUID.randomUUID().toString()
            try {
                val tolerances = runCatching { PassportAdapter.environmentalTolerances(passportJson) }
                    .getOrElse { ENVIRONMENTAL_TOLERANCE_KEYS.associateWith { null } }
                val response = ContextualDeliberationApiService.api.deliberate(
                    ContextualDeliberationRequestDto(
                        search_id = searchId,
                        timestamp = requestedAt,
                        cognitive_passport = storedPassport,
                        tolerance_profile = tolerances,
                        candidate_routes = ranked.map { route ->
                            route.toContextualCandidateRoute(
                                start, destination, requestedAt,
                                geometry = geometryByRouteId[contextualRouteId(route)],
                            )
                        },
                        contextual_query = ContextualQueryConfiguration(query_orion = true),
                    ),
                )
                Log.d("RouteResults", "PIPELINE_CONTEXT_COMPLETE candidates=${response.candidate_results.size} generation=$generation duration_ms=${SystemClock.elapsedRealtime() - startedAtMs}")
                if (generation == searchGeneration && requestVersion == contextRequestVersion) {
                    contextualResponse = response
                    contextualState = contextualStateFrom(routeIds, response, expectedSearchId = searchId)
                    // Context may have changed while the route identity stayed the
                    // same. XAI/narration are evidence-bound and must be requested
                    // again rather than presented as current.
                    explanationState = null
                    narrationState = null
                    explanationRequestVersion++
                    narrationRequestVersion++
                }
            } catch (e: CancellationException) {
                Log.d("RouteResults", "PIPELINE_CONTEXT_CANCELLED generation=$generation duration_ms=${SystemClock.elapsedRealtime() - startedAtMs}")
                throw e
            } catch (e: Exception) {
                Log.w("RouteResults", "CONTEXT_PROVIDER_UNAVAILABLE generation=$generation error_type=${e::class.simpleName} duration_ms=${SystemClock.elapsedRealtime() - startedAtMs}")
                if (generation == searchGeneration && requestVersion == contextRequestVersion) {
                    contextualState = ContextualAnalysisState.Unavailable(RouteUserError.CONTEXT.userMessage(LanguageState.current == AppLanguage.DE))
                }
            }
        }
    }

    fun requestNarration(
        routeId: String,
        deterministic: ContextualExplanationDto,
        selectedRouteIdAtRequest: String,
        deliberation: String,
        explanationType: String = "WHY_THIS_TENDENCY",
    ) {
        narrationJob?.cancel()
        val requestVersion = ++narrationRequestVersion
        val evidenceVersion = deterministic.evidence_version
        if (!evidenceVersion.startsWith("sha256:")) {
            Log.w("RouteResults", "NARRATION_SCHEMA_MISMATCH route_id=$routeId evidence=invalid generation=$requestVersion")
            narrationState = RouteScopedState(
                routeId,
                ContextualNarrationState.Unavailable(RouteUserError.NARRATION.userMessage()),
                evidenceVersion,
            )
            return
        }
        narrationState = RouteScopedState(routeId, ContextualNarrationState.Loading, evidenceVersion)
        val narrationLanguage = if (LanguageState.current == AppLanguage.DE) "de" else "en"
        val startedAtMs = SystemClock.elapsedRealtime()
        Log.d("RouteResults", "PIPELINE_NARRATION_START route_id=$routeId language=$narrationLanguage evidence=${evidenceVersionPrefix(evidenceVersion)} generation=$requestVersion")
        narrationJob = screenScope.launch {
            try {
                val narrationBody = narrationRequestBody(deterministic, narrationLanguage, deliberation, explanationType)
                val narration = ContextualNarrationApiService.api.narrate(narrationBody)
                Log.d("RouteResults", "PIPELINE_NARRATION_COMPLETE route_id=${narration.route_id} status=${narration.status} generated_by_llm=${narration.generated_by_llm} evidence=${evidenceVersionPrefix(narration.evidence_version)} generation=$requestVersion duration_ms=${SystemClock.elapsedRealtime() - startedAtMs}")
                val contractFailure = narrationContractFailure(narration, routeId, evidenceVersion)
                if (contractFailure != null) {
                    Log.w(
                        "RouteResults",
                        "${contractFailure.name} expected_route=$routeId actual_route=${narration.route_id.take(120)} " +
                                "expected_schema=$DIGITAL_COMPANION_EVIDENCE_SCHEMA_VERSION actual_schema=${narration.schema_version.take(80)} " +
                                "expected_evidence=${evidenceVersionPrefix(evidenceVersion)} actual_evidence=${evidenceVersionPrefix(narration.evidence_version)} " +
                                "generation=$requestVersion",
                    )
                    if (requestVersion == narrationRequestVersion && selectedRouteIdForAsync == selectedRouteIdAtRequest) {
                        narrationState = RouteScopedState(routeId, ContextualNarrationState.Unavailable(RouteUserError.NARRATION.userMessage()), evidenceVersion)
                    }
                    return@launch
                }
                if (narrationRequestIsCurrent(
                        requestGeneration = requestVersion,
                        currentGeneration = narrationRequestVersion,
                        requestedSelection = selectedRouteIdAtRequest,
                        currentSelection = selectedRouteIdForAsync,
                        requestedEvidenceVersion = evidenceVersion,
                        currentEvidenceVersion = (explanationState?.state as? ContextualExplanationState.Available)?.explanation?.evidence_version,
                    )
                ) {
                    narrationState = RouteScopedState(
                        routeId,
                        if (narration.generated_by_llm) ContextualNarrationState.Available(narration)
                        else ContextualNarrationState.Fallback(narration),
                        evidenceVersion,
                    )
                }
            } catch (e: CancellationException) {
                Log.d("RouteResults", "PIPELINE_NARRATION_CANCELLED route_id=$routeId generation=$requestVersion duration_ms=${SystemClock.elapsedRealtime() - startedAtMs}")
                throw e
            } catch (e: Exception) {
                Log.w(
                    "RouteResults",
                    "${narrationExceptionCategory(e)} route_id=$routeId evidence=${evidenceVersionPrefix(evidenceVersion)} " +
                            "generation=$requestVersion error_type=${e::class.simpleName}",
                )
                if (requestVersion == narrationRequestVersion && selectedRouteIdForAsync == selectedRouteIdAtRequest) {
                    narrationState = RouteScopedState(routeId, ContextualNarrationState.Unavailable(RouteUserError.NARRATION.userMessage()), evidenceVersion)
                }
            }
        }
    }

    fun requestDeterministicExplanation(
        routeId: String,
        deliberation: String,
        selectedRouteIdAtRequest: String,
        narrateAfterEvidence: Boolean = false,
    ) {
        explanationJob?.cancel()
        val requestVersion = ++explanationRequestVersion
        explanationState = RouteScopedState(routeId, ContextualExplanationState.Loading)
        narrationState = null
        val startedAtMs = SystemClock.elapsedRealtime()
        Log.d("RouteResults", "PIPELINE_XAI_START route_id=$routeId generation=$requestVersion")
        explanationJob = screenScope.launch {
            try {
                val body = JsonObject().apply {
                    add("deliberation", JsonParser.parseString(deliberation))
                    addProperty("route_id", routeId)
                }
                val deterministic = ContextualExplanationApiService.api.explain(body)
                if (deterministic.route_id != routeId) {
                    Log.w("RouteResults", "NARRATION_ROUTE_ID_MISMATCH expected_route=$routeId actual_route=${deterministic.route_id.take(120)} generation=$requestVersion")
                    if (requestVersion == explanationRequestVersion) {
                        explanationState = RouteScopedState(routeId, ContextualExplanationState.Unavailable(RouteUserError.EXPLANATION.userMessage()))
                    }
                    return@launch
                }
                if (deterministic.schema_version != CONTEXTUAL_XAI_SCHEMA_VERSION || !deterministic.evidence_version.startsWith("sha256:")) {
                    Log.w("RouteResults", "NARRATION_SCHEMA_MISMATCH route_id=$routeId evidence=${evidenceVersionPrefix(deterministic.evidence_version)} generation=$requestVersion")
                    if (requestVersion == explanationRequestVersion) {
                        explanationState = RouteScopedState(routeId, ContextualExplanationState.Unavailable(RouteUserError.EXPLANATION.userMessage()))
                    }
                    return@launch
                }
                if (requestVersion != explanationRequestVersion || selectedRouteIdForAsync != selectedRouteIdAtRequest) return@launch
                explanationState = RouteScopedState(routeId, ContextualExplanationState.Available(deterministic))
                Log.d("RouteResults", "PIPELINE_XAI_COMPLETE route_id=$routeId generation=$requestVersion duration_ms=${SystemClock.elapsedRealtime() - startedAtMs}")
                if (narrateAfterEvidence) requestNarration(routeId, deterministic, selectedRouteIdAtRequest, deliberation)
            } catch (e: CancellationException) {
                Log.d("RouteResults", "PIPELINE_XAI_CANCELLED route_id=$routeId generation=$requestVersion duration_ms=${SystemClock.elapsedRealtime() - startedAtMs}")
                throw e
            } catch (e: Exception) {
                Log.w("RouteResults", "XAI_UNAVAILABLE route_id=$routeId generation=$requestVersion error_type=${e::class.simpleName} duration_ms=${SystemClock.elapsedRealtime() - startedAtMs}")
                if (requestVersion == explanationRequestVersion) {
                    explanationState = RouteScopedState(routeId, ContextualExplanationState.Unavailable(RouteUserError.EXPLANATION.userMessage()))
                }
            }
        }
    }

    LaunchedEffect(destination, searchAttempt) {
        val generation = ++searchGeneration
        // A new destination starts a fresh episode. Do not let a previous
        // route/context/XAI result remain visible while this search loads.
        engineRoutes = emptyList()
        origin = null
        geometry = emptyMap()
        geometryOutcomes = emptyMap()
        mapAvailable = null
        loaded = false
        loadError = null
        routeState = RouteResultState.LOADING
        contextualState = ContextualAnalysisState.NotRequested
        contextualResponse = null
        explanationState = null
        narrationState = null
        selectedRouteId = null
        selectedRouteIdForAsync = null
        contextJob?.cancel()
        explanationJob?.cancel()
        narrationJob?.cancel()
        contextRequestVersion++
        explanationRequestVersion++
        narrationRequestVersion++
        // A manually chosen origin is an explicit routing input. Only fall
        // back to the device location when no manual origin has been selected.
        val loc = TripOriginStore.manualOrigin()?.let { it.lat to it.lon }
            ?: try { locationHelper.getCurrentLocation() } catch (e: Exception) { null }
        if (loc == null) {
            loadError = s.routeErrorLocation
            routeState = RouteResultState.RANKING_UNAVAILABLE
            loaded = true
            return@LaunchedEffect
        }
        if (!RoutingCoveragePolicy.contains(loc.first, loc.second) ||
            !RoutingCoveragePolicy.contains(destination.lat, destination.lon)
        ) {
            loadError = if (LanguageState.current == AppLanguage.DE)
                "Start oder Ziel liegt außerhalb des unterstützten Routing-Gebiets."
            else "The start or destination is outside the supported routing area."
            routeState = RouteResultState.COVERAGE_UNAVAILABLE
            loaded = true
            return@LaunchedEffect
        }
        val start = loc
        origin = start
        originLabel = TripOriginStore.manualOrigin()?.label ?: s.yourLocation

        val passportJson = PassportStore.load()
        if (passportJson == null) {
            loadError = s.routeErrorPassport
            routeState = RouteResultState.RANKING_UNAVAILABLE
            loaded = true
            return@LaunchedEffect
        }
        val passport = try {
            PassportAdapter.toEnginePassport(passportJson)
        } catch (_: Exception) {
            loadError = s.routeErrorPassport
            routeState = RouteResultState.RANKING_UNAVAILABLE
            loaded = true
            return@LaunchedEffect
        }
        val storedPassport = try {
            JsonParser.parseString(passportJson).asJsonObject
        } catch (_: Exception) {
            loadError = s.routeErrorPassport
            routeState = RouteResultState.RANKING_UNAVAILABLE
            loaded = true
            return@LaunchedEffect
        }
        val rankingStartedAtMs = SystemClock.elapsedRealtime()
        val ranked = try {
            fetchRankedRoutes(passport, start, destination)
        } catch (_: Exception) {
            loadError = RouteUserError.RANKING.userMessage(LanguageState.current == AppLanguage.DE)
            routeState = RouteResultState.RANKING_UNAVAILABLE
            loaded = true
            return@LaunchedEffect
        }
        Log.d("RouteResults", "PIPELINE_RANKING_COMPLETE routes=${ranked.size} generation=$generation duration_ms=${SystemClock.elapsedRealtime() - rankingStartedAtMs}")
        if (ranked.isEmpty() || ranked.none { it.available }) {
            loadError = if (LanguageState.current == AppLanguage.DE) "Keine nutzbaren Verkehrsmittel gefunden." else "No usable transport modes were returned."
            routeState = RouteResultState.NO_USABLE_MODES
            loaded = true
            return@LaunchedEffect
        }
        engineRoutes = ranked
        routeState = RouteResultState.RANKING_WITHOUT_GEOMETRY
        loaded = true
        selectedRouteId = contextualRouteId(ranked.first { it.available })

        // Contextual evidence is independent from routing. Existing routes are
        // visible and remain usable while this request runs or fails.
        val geometryStartedAtMs = SystemClock.elapsedRealtime()
        Log.d("RouteResults", "PIPELINE_GEOMETRY_START routes=${ranked.size} generation=$generation")
        val geometryResult = fetchRouteGeometry(start, destination, ranked)
        if (generation != searchGeneration) return@LaunchedEffect
        geometryOutcomes = geometryResult.outcomes
        geometry = geometryResult.routes
        routeState = routeStateAfterRanking(
            rankingSucceeded = true,
            hasUsableModes = ranked.any { it.available },
            hasGeometry = geometryResult.routes.isNotEmpty(),
        )
        Log.d(
            "RouteResults",
            "PIPELINE_GEOMETRY_COMPLETE routes=${geometryResult.routes.size} " +
                    "generation=$generation " +
                    "duration_ms=${SystemClock.elapsedRealtime() - geometryStartedAtMs}"
        )

        geometryResult.outcomes.forEach { (routeId, outcome) ->
            Log.d(
                "RouteResults",
                "PIPELINE_GEOMETRY_ROUTE " +
                        "route_id=$routeId " +
                        "mode=${outcome.modeKey} " +
                        "status=${outcome.status}"
            )
        }

// Audit the reconstructed GraphHopper geometry against the route summary
// returned by the external routing service.
        logRouteGeometryAudit(
            rankedRoutes = ranked,
            geometryResult = geometryResult,
            requestedStart = start,
            requestedDestination = destination.lat to destination.lon,
        )

// GraphHopper reconstructs a path from the ranked candidate's endpoints
// and mode. It never changes ranked-routes order, but gives context a
// route-shaped trajectory rather than an origin/destination midpoint.
        requestContextualAnalysis(
            ranked,
            start,
            passportJson,
            storedPassport,
            generation,
            ranked.associate { route ->
                contextualRouteId(route) to
                        (geometryResult.routes[contextualRouteId(route)]?.polyline ?: emptyList())
            },
        )

    }

    LaunchedEffect(loaded) {
        if (loaded) {
            delay(10_000)
            if (mapAvailable == null) {
                mapAvailable = false
                routeState = RouteResultState.MAP_UNAVAILABLE
            }
        }
    }

    if (!loaded || engineRoutes.isEmpty()) {
        RouteLoadingOrError(error = loadError, onBackClick = onBackClick, onRetry = { searchAttempt++ })
        return
    }

    // Cards: ranking/utility/chips from the engine. GraphHopper provides only
    // a reconstructed line and directions; the summary follows routing rank 1.
    // The external routing service owns candidate ranking and its reported
    // duration/distance. GraphHopper supplies only a visual reconstruction and
    // directions; replacing the summary made one candidate appear to change
    // after it had already been ranked.
    val displayRoutes = engineRoutes.filter { it.available }.ifEmpty { engineRoutes }
    val firstRoutingResult = engineRoutes.firstOrNull { it.available } ?: engineRoutes.first()
    val routeOrigin = requireNotNull(origin) { "A measured origin is required for live routing." }

    LaunchedEffect(firstRoutingResult) {
        if (selectedRouteId == null || displayRoutes.none { contextualRouteId(it) == selectedRouteId }) {
            selectedRouteId = contextualRouteId(firstRoutingResult)
        }
    }
    var showDirections by remember { mutableStateOf(false) }
    val selectedRoute = requireNotNull(selectedRoutingResultById(displayRoutes, selectedRouteId))
    val activeRouteId = contextualRouteId(selectedRoute)
    val selectedMode = selectedRoute.mode_key
    var showContextDetails by remember(activeRouteId) { mutableStateOf(false) }

    val accent = Mob.modeColor(selectedMode)
    val routePolyline = geometry[activeRouteId]?.polyline ?: emptyList()

    // Deterministic XAI and its bounded plain-language narration are generated
    // once context exists for the selected route. The key prevents Compose
    // recomposition from issuing duplicate provider requests.
    LaunchedEffect(activeRouteId, contextualResponse?.search_id) {
        selectedRouteIdForAsync = activeRouteId
        explanationJob?.cancel()
        narrationJob?.cancel()
        explanationState = null
        narrationState = null
        explanationRequestVersion++
        narrationRequestVersion++
        val response = contextualResponse ?: return@LaunchedEffect
        val routeId = activeRouteId
        if (response.candidate_results.any { it.route_id == routeId }) {
            requestDeterministicExplanation(
                routeId = routeId,
                deliberation = GsonBuilder().create().toJson(response),
                selectedRouteIdAtRequest = activeRouteId,
                narrateAfterEvidence = true,
            )
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // Keep the map prominent while allowing the result sheet to own the
        // comparison and explanatory hierarchy below it.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.44f)
        ) {
            MobMap(
                modifier = Modifier.fillMaxSize(),
                route = routePolyline,
                origin = routeOrigin,
                destination = destination.lat to destination.lon,
                routeColor = accent,
                onMapReady = { mapAvailable = true },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.IconButton(onClick = onBackClick, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(12.dp))
                ImiqCard(modifier = Modifier.weight(1f), style = ImiqCardStyle.Emphasized) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(originLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp).size(16.dp))
                        Text(destination.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Scrollable results sheet. Route cards preserve the engine order; the
        // selected card only controls map/detail presentation.
        Column(
            Modifier
                .fillMaxWidth()
                .weight(0.56f)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ImiqSpacing.md)
            ) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
                Spacer(Modifier.height(ImiqSpacing.md))

                SectionHeader(
                    title = "Routing results",
                    subtitle = "Results are ordered by the routing service",
                )

                val pipelineStage = routePipelineStage(
                    routeState = routeState,
                    contextState = contextualState,
                    narrationLoading = (narrationState?.state as? ContextualNarrationState.Loading) != null,
                )
                Spacer(Modifier.height(ImiqSpacing.xs))
                RoutePipelineStatus(
                    stage = pipelineStage,
                    de = LanguageState.current == AppLanguage.DE,
                )

                if (routeState != RouteResultState.USABLE_GEOMETRY) {
                    Text(
                        when (routeState) {
                            RouteResultState.RANKING_WITHOUT_GEOMETRY -> if (geometryOutcomes.isEmpty()) {
                                if (LanguageState.current == AppLanguage.DE) "Rangfolge verfügbar. Routenlinien werden geladen." else "Ranking available. Drawing route lines."
                            } else {
                                if (LanguageState.current == AppLanguage.DE) "Rangfolge verfügbar, aber keine nutzbare Routenlinie." else "Ranking available, but route geometry is unavailable."
                            }
                            RouteResultState.MAP_UNAVAILABLE -> if (LanguageState.current == AppLanguage.DE) "Die Karte ist nicht verfügbar." else "The map is unavailable."
                            else -> if (LanguageState.current == AppLanguage.DE) "Für diese Fahrt ist keine nutzbare Route verfügbar." else "No usable route is available for this trip."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(ImiqSpacing.sm))

                displayRoutes.forEach { route ->
                    RouteResultCard(
                        route = route,
                        geometryOutcome = geometryOutcomes[contextualRouteId(route)],
                        geometryLoading = routeState == RouteResultState.RANKING_WITHOUT_GEOMETRY && geometryOutcomes.isEmpty(),
                        contextualAssessment = contextualState.assessmentFor(route),
                        selected = contextualRouteId(route) == activeRouteId,
                        onClick = { selectedRouteId = contextualRouteId(route) }
                    )
                    Spacer(Modifier.height(ImiqSpacing.xs))
                }
                Spacer(Modifier.height(ImiqSpacing.md))
                SelectedRouteDetail(
                    route = selectedRoute,
                    assessment = contextualState.assessmentFor(selectedRoute),
                    contextualState = contextualState,
                    showContextDetails = showContextDetails,
                    onToggleContextDetails = { showContextDetails = !showContextDetails },
                    onRetryContext = {
                        PassportStore.load()?.let { currentPassport ->
                            runCatching { JsonParser.parseString(currentPassport).asJsonObject }.getOrNull()?.let { stored ->
                                requestContextualAnalysis(
                                    engineRoutes, routeOrigin, currentPassport, stored, searchGeneration,
                                    engineRoutes.associate { route -> contextualRouteId(route) to (geometry[contextualRouteId(route)]?.polyline ?: emptyList()) },
                                )
                            }
                        }
                    },
                )
                Spacer(Modifier.height(ImiqSpacing.sm))
                ContextualExplanationPanel(
                    response = contextualResponse,
                    routeId = contextualRouteId(selectedRoute),
                    contextualState = contextualState,
                    state = explanationState,
                    narrationState = narrationState,
                    onRequestNarration = { routeId, deterministic, explanationType ->
                        requestNarration(routeId, deterministic, routeId, GsonBuilder().create().toJson(contextualResponse), explanationType)
                    },
                    onRequest = { routeId, deliberation -> requestDeterministicExplanation(routeId, deliberation, routeId, narrateAfterEvidence = true) },
                )
                Spacer(Modifier.height(ImiqSpacing.md))
            }

            // Sticky Start button
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = ImiqSpacing.md, vertical = ImiqSpacing.sm)
            ) {
                val durationText = selectedRoute.summary?.let {
                    " · ${MobilityReferenceData.formatDuration(it.duration_seconds)}"
                }.orEmpty()
                PrimaryActionButton(
                    text = "Open directions · ${s.modeLabels[selectedRoute.mode_key.lowercase()] ?: selectedRoute.mode_label}$durationText",
                    icon = Icons.Default.Navigation,
                    enabled = navigationIsUsable(
                        hasInternalInstructions = geometry[activeRouteId]?.instructions?.isNotEmpty() == true,
                        hasExternalHandler = hasExternalNavigationHandler(context, routeOrigin, destination, selectedMode),
                    ),
                    onClick = {
                        // In-app turn-by-turn for the routable modes; transit (or no
                        // step data) hands off to Google Maps instead.
                        val steps = geometry[activeRouteId]?.instructions ?: emptyList()
                        if (selectedMode.equals("pt", true) || steps.isEmpty())
                            launchNavigation(context, routeOrigin, destination, selectedMode)
                        else showDirections = true
                    }
                )
                if (!navigationIsUsable(
                        geometry[activeRouteId]?.instructions?.isNotEmpty() == true,
                        hasExternalNavigationHandler(context, routeOrigin, destination, selectedMode),
                    )) {
                    Text(
                        if (LanguageState.current == AppLanguage.DE) "Navigation für dieses Ergebnis ist nicht verfügbar." else "Navigation is unavailable for this result.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (showDirections) {
            DirectionsSheet(
                modeKey = selectedMode,
                modeLabel = s.modeLabels[selectedMode.lowercase()] ?: selectedRoute.mode_label,
                originLabel = originLabel,
                destination = destination,
                summary = selectedRoute.summary,
                steps = geometry[activeRouteId]?.instructions ?: emptyList(),
                onOpenMaps = { showDirections = false; launchNavigation(context, routeOrigin, destination, selectedMode) },
                onClose = { showDirections = false },
            )
        }
        if (mapAvailable == false) {
            Text(
                if (LanguageState.current == AppLanguage.DE) "Karte konnte nicht geladen werden." else "Map could not be loaded.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Ranking from the engine + geometry from GraphHopper.
//  - fetchRankedRoutes: POST the strict 11-need passport + start/stop to the
//    ranked-routes engine; returns its ranked List<RankedRoute> (utility,
//    value-fit, summary). Errors remain errors; no demo response replaces them.
//  - fetchRouteGeometry: one GraphHopper path per routable mode (parallel), for
//    the map polyline + a distance/time that matches the drawn line. A mode with
//    no GraphHopper profile (e.g. pt) or a failed call is omitted. GraphHopper
//    returns [lon, lat]; MobMap wants (lat, lon).
// ---------------------------------------------------------------------------
private enum class GeometrySource {
    GRAPHHOPPER_RECONSTRUCTED,
}

private enum class GeometryFidelity {
    RECONSTRUCTED,
}

private data class RouteGeo(
    val polyline: List<Pair<Double, Double>>,
    val summary: RouteSummary,
    val instructions: List<GhInstruction> = emptyList(),
    val source: GeometrySource = GeometrySource.GRAPHHOPPER_RECONSTRUCTED,
    val fidelity: GeometryFidelity = GeometryFidelity.RECONSTRUCTED,
)

private data class GeometryFetchResult(
    val routes: Map<String, RouteGeo>,
    val outcomes: Map<String, GeometryOutcome>,
)

private fun relativeDifferencePercent(
    reference: Double?,
    reconstructed: Double,
): Double? {
    if (reference == null || reference <= 0.0) return null
    return ((reconstructed - reference) / reference) * 100.0
}

private fun pointDistanceMeters(
    a: Pair<Double, Double>,
    b: Pair<Double, Double>,
): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(a.first)
    val lat2 = Math.toRadians(b.first)
    val deltaLat = Math.toRadians(b.first - a.first)
    val deltaLon = Math.toRadians(b.second - a.second)

    val h =
        kotlin.math.sin(deltaLat / 2.0) * kotlin.math.sin(deltaLat / 2.0) +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(deltaLon / 2.0) * kotlin.math.sin(deltaLon / 2.0)

    val c = 2.0 * kotlin.math.atan2(
        kotlin.math.sqrt(h),
        kotlin.math.sqrt(1.0 - h),
    )

    return earthRadiusMeters * c
}

private fun polylineLengthMeters(
    points: List<Pair<Double, Double>>,
): Double {
    if (points.size < 2) return 0.0

    return points.zipWithNext().sumOf { (a, b) ->
        pointDistanceMeters(a, b)
    }
}
private fun auditValue(value: Double?): String =
    value?.let {
        java.lang.String.format(
            java.util.Locale.US,
            "%.1f",
            it,
        )
    } ?: "NA"

private fun auditCoordinate(value: Double?): String =
    value?.let {
        java.lang.String.format(
            java.util.Locale.US,
            "%.6f",
            it,
        )
    } ?: "NA"

private fun logRouteGeometryAudit(
    rankedRoutes: List<RankedRoute>,
    geometryResult: GeometryFetchResult,
    requestedStart: Pair<Double, Double>,
    requestedDestination: Pair<Double, Double>,
) {
    rankedRoutes.forEach { route ->
        val routeId = contextualRouteId(route)
        val reconstructed = geometryResult.routes[routeId]
        val outcome = geometryResult.outcomes[routeId]

        if (reconstructed == null) {
            Log.d(
                "RouteGeometryAudit",
                "ROUTE_GEOMETRY_AUDIT " +
                        "route_id=$routeId " +
                        "rank=${route.rank} " +
                        "mode=${route.mode_key} " +
                        "status=${outcome?.status ?: "UNKNOWN"} " +
                        "routing_distance_m=${auditValue(route.summary?.distance_meters)} " +
                        "routing_duration_s=${auditValue(route.summary?.duration_seconds)} " +
                        "gh_distance_m=NA gh_duration_s=NA polyline_points=0 " +
                        "distance_delta_pct=NA duration_delta_pct=NA " +
                        "source=NONE fidelity=NONE",
            )
            return@forEach
        }

        val routingDistance = route.summary?.distance_meters
        val routingDuration = route.summary?.duration_seconds
        val ghDistance = reconstructed.summary.distance_meters
        val ghDuration = reconstructed.summary.duration_seconds
        val polylineDistance = polylineLengthMeters(reconstructed.polyline)
        val ghStart = reconstructed.polyline.firstOrNull()
        val ghDestination = reconstructed.polyline.lastOrNull()
        val startOffset = ghStart?.let { pointDistanceMeters(requestedStart, it) }
        val destinationOffset = ghDestination?.let {
            pointDistanceMeters(requestedDestination, it)
        }

        val polylineDistanceDelta = relativeDifferencePercent(
            reference = ghDistance,
            reconstructed = polylineDistance,
        )
        val distanceDelta = relativeDifferencePercent(
            reference = routingDistance,
            reconstructed = ghDistance,
        )

        val durationDelta = relativeDifferencePercent(
            reference = routingDuration,
            reconstructed = ghDuration,
        )

        Log.d(
            "RouteGeometryAudit",
            "ROUTE_GEOMETRY_AUDIT " +
                    "route_id=$routeId " +
                    "rank=${route.rank} " +
                    "mode=${route.mode_key} " +
                    "status=${outcome?.status ?: "UNKNOWN"} " +
                    "routing_distance_m=${auditValue(routingDistance)} " +
                    "routing_duration_s=${auditValue(routingDuration)} " +
                    "gh_distance_m=${auditValue(ghDistance)} " +
                    "gh_duration_s=${auditValue(ghDuration)} " +
                    "polyline_points=${reconstructed.polyline.size} " +
                    "polyline_distance_m=${auditValue(polylineDistance)} " +
                    "polyline_distance_delta_pct=${auditValue(polylineDistanceDelta)} " +
                    "distance_delta_pct=${auditValue(distanceDelta)} " +
                    "duration_delta_pct=${auditValue(durationDelta)} " +
                    "requested_start_lat=${auditCoordinate(requestedStart.first)} " +
                    "requested_start_lon=${auditCoordinate(requestedStart.second)} " +
                    "gh_start_lat=${auditCoordinate(ghStart?.first)} " +
                    "gh_start_lon=${auditCoordinate(ghStart?.second)} " +
                    "start_offset_m=${auditValue(startOffset)} " +
                    "requested_destination_lat=${auditCoordinate(requestedDestination.first)} " +
                    "requested_destination_lon=${auditCoordinate(requestedDestination.second)} " +
                    "gh_destination_lat=${auditCoordinate(ghDestination?.first)} " +
                    "gh_destination_lon=${auditCoordinate(ghDestination?.second)} " +
                    "destination_offset_m=${auditValue(destinationOffset)} " +
                    "source=${reconstructed.source.name} " +
                    "fidelity=${reconstructed.fidelity.name}",
        )
    }
}

@Composable
private fun RouteLoadingOrError(error: String?, onBackClick: () -> Unit, onRetry: () -> Unit) {
    val s = LocalStrings.current
    Box(
        modifier = Modifier.fillMaxSize().background(Mob.bg),
        contentAlignment = Alignment.Center,
    ) {
        if (error == null) {
            AnalyzingRow()
        } else {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(42.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    s.routeErrorTitle,
                    color = Mob.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    color = Mob.textSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                MobButton(text = s.tryAgain, onClick = onRetry)
                Spacer(Modifier.height(10.dp))
                Text(
                    s.back,
                    color = Mob.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onBackClick() },
                )
            }
        }
    }
}

// Hands off to Google Maps for real turn-by-turn navigation in the chosen mode.
// IMIQ picks the best mode for the user's cognitive passport; Maps drives the trip.
// Uses the universal Maps "directions" URL so it works for car/bike/walk AND transit,
// opening the Maps app if installed (else the browser). Never throws.
private fun launchNavigation(
    context: android.content.Context,
    origin: Pair<Double, Double>,
    destination: MobilityReferenceData.Place,
    modeKey: String,
) : Boolean {
    val intent = navigationIntent(origin, destination, modeKey)
    return runCatching {
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}

private fun navigationIntent(
    origin: Pair<Double, Double>,
    destination: MobilityReferenceData.Place,
    modeKey: String,
): Intent {
    val url = "https://www.google.com/maps/dir/?api=1" +
            "&origin=${origin.first},${origin.second}" +
            "&destination=${destination.lat},${destination.lon}" +
            "&travelmode=${googleTravelMode(modeKey)}"
    return Intent(Intent.ACTION_VIEW, Uri.parse(url))
}

private fun hasExternalNavigationHandler(
    context: android.content.Context,
    origin: Pair<Double, Double>,
    destination: MobilityReferenceData.Place,
    modeKey: String,
): Boolean = navigationIntent(origin, destination, modeKey).resolveActivity(context.packageManager) != null

private fun googleTravelMode(modeKey: String): String = when (modeKey.lowercase()) {
    "car", "car_pt" -> "driving"
    "bike", "bike_pt" -> "bicycling"
    "foot", "walk" -> "walking"
    "pt" -> "transit"
    else -> "driving"
}

private suspend fun fetchRankedRoutes(
    passport: JsonObject,
    start: Pair<Double, Double>,
    destination: MobilityReferenceData.Place
): List<RankedRoute> = RankedRoutesApiService.api.rankedRoutes(
    RankedRoutesRequest(
        cognitive_passport = passport,
        start = GeoPoint2(start.first, start.second),
        stop = GeoPoint2(destination.lat, destination.lon),
        datetime = null,
        include_unavailable = false
    )
).routes

private suspend fun fetchRouteGeometry(
    start: Pair<Double, Double>,
    destination: MobilityReferenceData.Place,
    routes: List<RankedRoute>,
): GeometryFetchResult = coroutineScope {
    val results = routes.distinctBy(::contextualRouteId).map { route ->
        async {
            val routeId = contextualRouteId(route)
            val mode = route.mode_key
            val profile = GraphHopperApiService.profileForMode(mode)
            if (profile == null) {
                return@async routeId to (null to GeometryOutcome(mode, GeometryStatus.UNSUPPORTED))
            }
            try {
                val resp = GraphHopperApiService.api.route(
                    points = listOf(
                        GraphHopperApiService.point(start.first, start.second),
                        GraphHopperApiService.point(destination.lat, destination.lon)
                    ),
                    profile = profile,
                    locale = if (LanguageState.current == AppLanguage.DE) "de" else "en"
                )
                val path = resp.paths.firstOrNull()
                    ?: return@async routeId to (null to GeometryOutcome(mode, GeometryStatus.EMPTY, "No path returned"))
                val poly = path.points?.coordinates
                    ?.mapNotNull { if (it.size >= 2) it[1] to it[0] else null }
                    ?: emptyList()
                if (poly.isEmpty()) routeId to (null to GeometryOutcome(mode, GeometryStatus.EMPTY, "Empty polyline"))
                else routeId to (RouteGeo(poly, RouteSummary(path.time / 1000.0, path.distance, 0), path.instructions) to GeometryOutcome(mode, GeometryStatus.AVAILABLE))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                routeId to (null to GeometryOutcome(mode, GeometryStatus.FAILED, e::class.simpleName))
            }
        }
    }.awaitAll()
    GeometryFetchResult(
        routes = results.mapNotNull { (mode, pair) -> pair.first?.let { mode to it } }.toMap(),
        outcomes = results.associate { it.first to it.second.second },
    )
}

@Composable
private fun RoutingResultHero(route: RankedRoute) {
    val s = LocalStrings.current
    val accent = Mob.modeColor(route.mode_key)
    MobGlassCard(color = accent.copy(alpha = 0.10f), border = accent.copy(alpha = 0.35f), radius = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Directions, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                routingResultLabel(route.rank, LanguageState.current == AppLanguage.DE).uppercase(),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MobilityReferenceData.modeIcon(route.mode_key), null, tint = accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                s.modeLabels[route.mode_key.lowercase()] ?: route.mode_label,
                color = Mob.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun AnalyzingRow() {
    val s = LocalStrings.current
    val t = rememberInfiniteTransition(label = "an")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "a")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Sensors, null, tint = Mob.primary.copy(alpha = a), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            s.analyzing,
            color = Mob.textPrimary.copy(alpha = a), fontSize = 14.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ContextualStatusBanner(state: ContextualAnalysisState, onRetry: () -> Unit) {
    val s = LocalStrings.current
    val text = when (state) {
        ContextualAnalysisState.NotRequested -> return
        ContextualAnalysisState.Loading -> s.contextualLoading
        is ContextualAnalysisState.Available -> s.contextualAvailable
        is ContextualAnalysisState.Partial -> s.contextualPartial
        is ContextualAnalysisState.Unavailable -> RouteUserError.CONTEXT.userMessage(LanguageState.current == AppLanguage.DE)
    }
    when (state) {
        ContextualAnalysisState.Loading -> InlineLoadingIndicator(text)
        is ContextualAnalysisState.Unavailable -> InlineErrorState(
            message = text,
            retryLabel = if (LanguageState.current == AppLanguage.DE) "Erneut versuchen" else "Retry",
            onRetry = onRetry,
        )
        is ContextualAnalysisState.Available, is ContextualAnalysisState.Partial -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ImiqSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContextStatusChip(if (state is ContextualAnalysisState.Available) ContextPresentationState.AVAILABLE else ContextPresentationState.PARTIAL)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ContextualAnalysisState.NotRequested -> Unit
    }
}

@Composable
private fun ContextualExplanationPanel(
    response: ContextualDeliberationResponseDto?,
    routeId: String,
    contextualState: ContextualAnalysisState,
    state: RouteScopedState<ContextualExplanationState>?,
    narrationState: RouteScopedState<ContextualNarrationState>?,
    onRequestNarration: (String, ContextualExplanationDto, String) -> Unit,
    onRequest: (String, String) -> Unit,
) {
    val s = LocalStrings.current
    val companionName = DigitalCompanionStore.displayName()
    var showLimitations by remember(routeId) { mutableStateOf(false) }
    var showResearchDetails by remember(routeId) { mutableStateOf(false) }
    var dismissedNarrationEvidence by remember(routeId) { mutableStateOf<String?>(null) }
    var showNarrationPopup by remember(routeId) { mutableStateOf(false) }
    val hasSelectedContext = response?.candidate_results?.any { it.route_id == routeId } == true
    if (!hasSelectedContext) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Mob.surfaceHi).padding(12.dp),
        ) {
            Text("Digital Companion", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(ImiqSpacing.xs))
            Text("Current conditions", style = MaterialTheme.typography.labelLarge)
            Text(
                contextualCompanionUnavailableMessage(contextualState),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(ImiqSpacing.sm))
            Text("Digital Companion tendency", style = MaterialTheme.typography.labelLarge)
            Text("Unavailable until deterministic contextual evidence is available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CompanionRequestCard(unavailableMessage = "An explanation requires deterministic route evidence.")
        }
        return
    }
    val explanation = state.stateFor(routeId, ContextualExplanationState.NotRequested)
    val narrationEvidenceVersion = (explanation as? ContextualExplanationState.Available)?.explanation?.evidence_version
    val narration = narrationState.stateFor(routeId, narrationEvidenceVersion, ContextualNarrationState.NotRequested)
    // Open as soon as deterministic evidence is ready, including loading/error
    // states. Closing it never automatically reopens the same evidence.
    LaunchedEffect(routeId, narrationEvidenceVersion, narration is ContextualNarrationState.Available) {
        // Generate automatically, but do not cover already usable route cards
        // with a loading dialog. Open the animated Companion only once its
        // validated narration is ready.
        showNarrationPopup = narrationEvidenceVersion != null &&
                narration is ContextualNarrationState.Available &&
                dismissedNarrationEvidence != narrationEvidenceVersion
    }
    if (showNarrationPopup && narrationEvidenceVersion != null) {
        CompanionNarrationDialog(
            companionName = companionName,
            state = narration,
            onRetry = {
                (explanation as? ContextualExplanationState.Available)?.let { onRequestNarration(routeId, it.explanation, "WHY_THIS_TENDENCY") }
            },
            onFollowUp = { explanationType ->
                (explanation as? ContextualExplanationState.Available)?.let { onRequestNarration(routeId, it.explanation, explanationType) }
            },
            onDismiss = {
                showNarrationPopup = false
                dismissedNarrationEvidence = narrationEvidenceVersion
            },
        )
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Mob.surfaceHi).padding(12.dp),
    ) {
        when (explanation) {
            ContextualExplanationState.NotRequested -> {
                CompanionRequestCard(loading = true)
            }
            ContextualExplanationState.Loading -> Text(s.explanationLoading, color = Mob.textSecondary, fontSize = 11.sp)
            is ContextualExplanationState.Unavailable -> InlineErrorState(
                message = explanation.reason ?: RouteUserError.EXPLANATION.userMessage(LanguageState.current == AppLanguage.DE),
                retryLabel = "Retry",
                onRetry = { onRequest(routeId, GsonBuilder().create().toJson(response)) },
            )
            is ContextualExplanationState.Available -> {
                val evidence = explanation.explanation
                when (narration) {
                    ContextualNarrationState.NotRequested -> CompanionRequestCard(loading = true)
                    ContextualNarrationState.Loading -> CompanionRequestCard(loading = true)
                    is ContextualNarrationState.Available -> {
                        CompanionRequestCard(
                            unavailableMessage = "Your Digital Companion explanation is ready.",
                            onRequest = { showNarrationPopup = true },
                            actionLabel = "Open explanation",
                        )
                    }
                    is ContextualNarrationState.Fallback -> CompanionRequestCard(
                        unavailableMessage = "Digital Companion explanation is temporarily unavailable. Deterministic evidence remains available below.",
                        onRequest = { onRequestNarration(routeId, evidence, "WHY_THIS_TENDENCY") },
                    )
                    is ContextualNarrationState.Unavailable -> CompanionRequestCard(
                        unavailableMessage = narration.reason ?: "Digital Companion explanation is temporarily unavailable.",
                        onRequest = { onRequestNarration(routeId, evidence, "WHY_THIS_TENDENCY") },
                    )
                }
                androidx.compose.material3.TextButton(onClick = { showLimitations = !showLimitations }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showLimitations) "Hide data limitations" else "Data limitations")
                }
                if (showLimitations) {
                    val missing = evidence.data_quality.missing_stressors
                    Text(
                        if (missing.isEmpty()) "No additional data limitations were reported." else "Unavailable or incomplete: ${missing.joinToString().replace('_', ' ')}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.TextButton(onClick = { showResearchDetails = !showResearchDetails }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showResearchDetails) "Hide research details" else "Research details")
                }
                if (showResearchDetails) {
                    evidence.summary.take(2).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "The Digital Companion uses exploratory HOTCO-CT-based analysis. Routing determines route order; this analysis does not change it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompanionNarrationDialog(
    companionName: String,
    state: ContextualNarrationState,
    onRetry: () -> Unit,
    onFollowUp: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    fun closeAnimated() {
        visible = false
        scope.launch {
            delay(180)
            onDismiss()
        }
    }
    Dialog(
        onDismissRequest = ::closeAnimated,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) + scaleIn(tween(260), initialScale = .90f),
            exit = fadeOut(tween(160)) + scaleOut(tween(180), targetScale = .94f),
        ) {
            if (state is ContextualNarrationState.Available) {
                CompanionNarrationCard(
                    companionName = companionName,
                    narration = state.narration,
                    onClose = ::closeAnimated,
                    onFollowUp = onFollowUp,
                    modifier = Modifier.fillMaxWidth(.92f).heightIn(max = 680.dp),
                )
            } else {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth(.92f),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp,
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                            Text("Digital Companion", modifier = Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleLarge)
                            androidx.compose.material3.IconButton(onClick = ::closeAnimated) {
                                Icon(Icons.Default.Close, "Close explanation")
                            }
                        }
                        if (state is ContextualNarrationState.Loading || state is ContextualNarrationState.NotRequested) {
                            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Putting the comparison into words…", style = MaterialTheme.typography.titleMedium)
                            Text("You can close this card and continue exploring your routes. Your explanation will remain available here.")
                        } else {
                            Text("The explanation couldn't be completed.", style = MaterialTheme.typography.titleMedium)
                            Text("Your route and the comparison are still available. You can retry the explanation.")
                            androidx.compose.material3.Button(onClick = onRetry) { Text("Try again") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionNarrationCard(
    companionName: String,
    narration: ContextualNarrationDto,
    onClose: (() -> Unit)? = null,
    onFollowUp: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .22f), RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Digital Companion", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (narration.generated_by_llm) narrationSpeakerLabel(narration, companionName) else "System explanation",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onClose != null) {
                androidx.compose.material3.IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close explanation")
                }
            }
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (narration.language == "de")
                    "Persönliche Orientierung aus deinen Prioritäten. Die Reihenfolge der Routen bleibt unverändert."
                else
                    "Personal guidance from your priorities. Routing order stays unchanged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (narration.recommendation_status == "RECOMMENDED" && !narration.recommendation_mode.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(if (narration.language == "de") "Meine Empfehlung" else "My recommendation", style = MaterialTheme.typography.labelLarge)
                    Text(companionModeLabel(narration.recommendation_mode, narration.language), style = MaterialTheme.typography.headlineSmall)
                    if (narration.selected_route_alignment == "DIFFERENT_FROM_RECOMMENDATION") {
                        Text(
                            if (narration.language == "de") "Die Route, die du gerade ansiehst, ist eine andere Option."
                            else "The route you are viewing is a different option.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (narration.recommendation_status == "NO_CLEAR_CHOICE") {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (narration.language == "de") "Heute gibt es keine eindeutig beste Option."
                    else "There is no single clear choice today.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (narration.title.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(narration.title, style = MaterialTheme.typography.titleSmall)
            }
            structuredNarrationSections(narration).forEachIndexed { index, section ->
                Spacer(Modifier.height(if (index == 0) 12.dp else 10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (index == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(section.label, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(section.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onFollowUp != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    if (narration.language == "de") "Frag mich weiter" else "Ask me more",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    narrationFollowUpLabels(narration.language).forEach { (type, label) ->
                        androidx.compose.material3.AssistChip(
                            onClick = { onFollowUp(type) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionRequestCard(
    loading: Boolean = false,
    unavailableMessage: String? = null,
    onRequest: (() -> Unit)? = null,
    actionLabel: String = "Try again",
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = ImiqSpacing.xs)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))
            .padding(ImiqSpacing.sm),
    ) {
        Text("Digital Companion", style = MaterialTheme.typography.labelLarge)
        when {
            loading -> Text("Preparing an explanation…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            unavailableMessage != null -> {
                Text(unavailableMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onRequest != null) {
                    androidx.compose.material3.TextButton(onClick = onRequest, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
                }
            }
            else -> Text("An explanation will appear after the comparison.", style = MaterialTheme.typography.bodySmall)
        }
    }
}


private val observedContextVariables = listOf("temperature", "daylight", "crowding")
private val compactContextStressors = listOf("rain", "temperature", "darkness")

private fun contextStressorLabel(name: String): String = when (name) {
    "rain" -> "Rain"
    "temperature" -> "Temperature"
    "windSpeed" -> "Wind"
    "traffic" -> "Traffic"
    "darkness" -> "Darkness"
    "crowding" -> "Crowding"
    else -> name.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
private fun ContextEvidenceBlock(assessment: ContextualRouteAssessment) {
    val routeContext = assessment.routeContext
    val perturbation = assessment.contextPerturbation
    if (routeContext == null && perturbation == null && assessment.sourceStatus.isEmpty()) return
    var expanded by remember(assessment.routeId) { mutableStateOf(false) }
    val stressors = routeContext?.normalized_stressors.orEmpty()
        .associateBy { it.name }
    val observations = routeContext?.raw_observations.orEmpty()
        .associateBy { it.variable }
    val facts = routeContext?.context_facts.orEmpty().associateBy { it.variable }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("Observed context", color = Mob.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        observedContextVariables.forEach { variable ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(contextStressorLabel(variable), color = Mob.textMuted, fontSize = 9.sp)
                Text(
                    facts[variable]?.display_value ?: observedContextValueLabel(observation = observations[variable], variable = variable, sourceStatus = assessment.sourceStatus["weather"], de = LanguageState.current == AppLanguage.DE),
                    color = Mob.textSecondary,
                    fontSize = 9.sp,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text("Model context pressure", color = Mob.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        compactContextStressors.forEach { name ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(contextStressorLabel(name), color = Mob.textMuted, fontSize = 9.sp)
                Text(
                    modelPressureLabel(stressors[name], assessment.sourceStatus[name], LanguageState.current == AppLanguage.DE),
                    color = Mob.textSecondary,
                    fontSize = 9.sp,
                )
            }
        }
        Text(
            if (assessment.availability == ContextAvailability.PARTIAL) "Data: Partial" else "Data: Available",
            color = Mob.textMuted,
            fontSize = 9.sp,
        )
        androidx.compose.material3.TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(if (expanded) "Hide research details" else "View research details") }
        if (expanded) {
            Text("Context data sources", color = Mob.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            assessment.sourceStatus.toSortedMap().forEach { (source, status) ->
                val label = when (status.uppercase()) {
                    "OBSERVED" -> "Available"
                    "EMPTY" -> "No data"
                    "UNAVAILABLE" -> "Unavailable"
                    "MALFORMED" -> "Unusable"
                    else -> "Unknown"
                }
                Text("${contextStressorLabel(source)}: $label", color = Mob.textMuted, fontSize = 9.sp)
            }
            perturbation?.effective_exposures
                ?.filter { it.source_scope == "route" }
                ?.distinctBy { it.stressor }
                ?.forEach { exposure ->
                    val effective = exposure.effective_value?.let { contextBucket(it) } ?: "Unknown"
                    val tolerance = exposure.tolerance?.let { "%.2f".format(it) } ?: "Unknown"
                    Text(
                        "${contextStressorLabel(exposure.stressor)} exposure: ${exposure.context_value?.let { contextBucket(it) } ?: "Unknown"}; tolerance: $tolerance; effective pressure: $effective",
                        color = Mob.textMuted,
                        fontSize = 9.sp,
                    )
                }
            perturbation?.need_perturbations?.maxByOrNull { kotlin.math.abs(it.value) }?.let {
                Text("Strongest need pressure: ${it.key} ${"%.2f".format(it.value)}", color = Mob.textMuted, fontSize = 9.sp)
            }
            perturbation?.valence_perturbations?.filter { it.value != 0.0 }?.maxByOrNull { kotlin.math.abs(it.value) }?.let {
                Text("Strongest mode pressure: ${it.key} ${"%.2f".format(it.value)}", color = Mob.textMuted, fontSize = 9.sp)
            }
            assessment.warnings.take(2).forEach { warning ->
                Text("Context data is incomplete: ${friendlyContextWarning(warning)}", color = Mob.textMuted, fontSize = 9.sp)
            }
        }
    }
}

private fun contextBucket(value: Double): String = when {
    value < 0.25 -> "Low"
    value < 0.50 -> "Moderate"
    value < 0.75 -> "High"
    else -> "Very high"
}

private fun friendlyContextWarning(value: String): String = when {
    value.contains("unavailable", ignoreCase = true) -> "some sources were unavailable"
    value.contains("UNKNOWN", ignoreCase = true) -> "some dimensions remain unknown"
    else -> "some contextual details are limited"
}

@Composable
private fun RouteResultCard(
    route: RankedRoute,
    geometryOutcome: GeometryOutcome?,
    geometryLoading: Boolean,
    contextualAssessment: ContextualRouteAssessment?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val s = LocalStrings.current
    val accent = Mob.modeColor(route.mode_key)
    ImiqCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { this.selected = selected; stateDescription = if (selected) "Selected route" else "Routing result" },
        style = if (selected) ImiqCardStyle.Selected else ImiqCardStyle.Default,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleGlyph(MobilityReferenceData.modeIcon(route.mode_key), accent, diameter = 48.dp)
            Spacer(Modifier.width(ImiqSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(s.modeLabels[route.mode_key.lowercase()] ?: route.mode_label, style = MaterialTheme.typography.titleMedium)
                Text(
                    route.summary?.let {
                        "${MobilityReferenceData.formatDuration(it.duration_seconds)} · ${MobilityReferenceData.formatDistance(it.distance_meters)}"
                    } ?: "—",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (route.summary?.transfers ?: 0 > 0) {
                    Text("${route.summary?.transfers} transfers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    buildString {
                        append(routingResultLabel(route.rank, LanguageState.current == AppLanguage.DE))
                        if (selected) {
                            append(" · ")
                            append(if (LanguageState.current == AppLanguage.DE) "Auf der Karte" else "Shown on map")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }
            contextualAssessment?.let { assessment ->
                ContextStatusChip(
                    if (assessment.availability == ContextAvailability.PARTIAL) ContextPresentationState.PARTIAL else ContextPresentationState.AVAILABLE,
                    modifier = Modifier.padding(start = ImiqSpacing.xs),
                )
            }
        }
        if (geometryOutcome != null && geometryOutcome.status != GeometryStatus.AVAILABLE) {
            Spacer(Modifier.height(ImiqSpacing.xs))
            Text(
                if (geometryOutcome.status == GeometryStatus.UNSUPPORTED) "Map line unavailable for this mode." else RouteUserError.GEOMETRY.userMessage(LanguageState.current == AppLanguage.DE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (geometryLoading) {
            Spacer(Modifier.height(ImiqSpacing.xs))
            Text(
                if (LanguageState.current == AppLanguage.DE) "Routenlinie wird geladen…" else "Drawing route line…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A compact progress strip makes optional enrichment visible without replacing
 * already usable route cards with a spinner. The stages are presentation-only.
 */
@Composable
private fun RoutePipelineStatus(stage: RoutePipelineStage, de: Boolean) {
    val labels = if (de) {
        listOf("Routen", "Karte", "Bedingungen", "Companion")
    } else {
        listOf("Routes", "Map", "Conditions", "Companion")
    }
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    val stageLabel = routePipelineStageLabel(stage, de)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { stateDescription = stageLabel },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEachIndexed { index, label ->
                val completed = index < stage.completedSteps
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (completed) activeColor else inactiveColor),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (completed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        AnimatedContent(targetState = stageLabel, label = "routePipelineStatus") { status ->
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectedRouteDetail(
    route: RankedRoute,
    assessment: ContextualRouteAssessment?,
    contextualState: ContextualAnalysisState,
    showContextDetails: Boolean,
    onToggleContextDetails: () -> Unit,
    onRetryContext: () -> Unit,
) {
    ImiqCard(style = ImiqCardStyle.Emphasized, modifier = Modifier.fillMaxWidth()) {
        SectionHeader("Selected route", subtitle = route.mode_label)
        Spacer(Modifier.height(ImiqSpacing.sm))
        when (contextualState) {
            ContextualAnalysisState.Loading -> InlineLoadingIndicator("Loading context")
            is ContextualAnalysisState.Unavailable -> InlineErrorState(
                message = RouteUserError.CONTEXT.userMessage(LanguageState.current == AppLanguage.DE),
                severity = InlineErrorSeverity.Warning,
                retryLabel = "Retry",
                onRetry = onRetryContext,
            )
            else -> assessment?.let { RouteContextSummary(it, showContextDetails, onToggleContextDetails) }
        }
        if (assessment != null) {
            Spacer(Modifier.height(ImiqSpacing.md))
            ModelTendencyCard(assessment)
        }
    }
}

@Composable
private fun RouteContextSummary(
    assessment: ContextualRouteAssessment,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val facts = assessment.routeContext?.context_facts.orEmpty().associateBy { it.variable }
    SectionHeader(
        "Current conditions",
        trailing = { ContextStatusChip(if (assessment.availability == ContextAvailability.PARTIAL) ContextPresentationState.PARTIAL else ContextPresentationState.AVAILABLE) },
    )
    listOf("temperature", "daylight", "rain", "wind", "crowding", "traffic").forEach { name ->
        val value = when (name) {
            "temperature", "daylight", "crowding", "rain", "wind" -> facts[name]?.let { fact -> listOf(fact.display_value, freshnessLabel(fact.freshness)).filter { it.isNotBlank() }.joinToString(" · ") } ?: "No data"
            "traffic" -> "Unavailable"
            else -> "No data"
        }
        Row(Modifier.fillMaxWidth().padding(top = ImiqSpacing.xxs), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(contextStressorLabel(if (name == "daylight") "darkness" else name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (assessment.availability == ContextAvailability.PARTIAL) Text("Some contextual information is unavailable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    androidx.compose.material3.TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(if (expanded) "Hide context details" else "View context details")
    }
    if (expanded) ContextEvidenceBlock(assessment)
}

@Composable
private fun ModelTendencyCard(assessment: ContextualRouteAssessment) {
    SectionHeader("Digital Companion tendency", subtitle = "Exploratory contextual analysis")
    Spacer(Modifier.height(ImiqSpacing.xs))
    Text("Baseline tendency", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(assessment.baselineWinner.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(ImiqSpacing.xxs))
    val presentation = tendencyPresentation(assessment.contextualWinner, assessment.hotco.ambiguity_state, "CONTEXTUAL", assessment.tendencyChanged)
    Text("With current context", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(presentation.title, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(ImiqSpacing.xxs))
    Text(
        presentation.detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(ImiqSpacing.xs))
    Text(
        EXPLORATORY_CONTEXTUAL_MODEL_DISCLOSURE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------------------------------------------------------------------
// In-app turn-by-turn directions, from GraphHopper's instructions. Shown for the
// routable modes (car/bike/walk); a button hands off to Google Maps for live nav.
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionsSheet(
    modeKey: String,
    modeLabel: String,
    originLabel: String,
    destination: MobilityReferenceData.Place,
    summary: RouteSummary?,
    steps: List<GhInstruction>,
    onOpenMaps: () -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalStrings.current
    val accent = Mob.modeColor(modeKey)
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Mob.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleGlyph(MobilityReferenceData.modeIcon(modeKey), accent, diameter = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.directionsTitle, color = Mob.textMuted, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                    Text(modeLabel, color = Mob.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    summary?.let {
                        "${MobilityReferenceData.formatDuration(it.duration_seconds)} · ${MobilityReferenceData.formatDistance(it.distance_meters)}"
                    } ?: "—",
                    color = Mob.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("$originLabel → ${destination.label}", color = Mob.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Mob.border))

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                steps.forEachIndexed { i, step ->
                    StepRow(i + 1, step.text, step.distance, accent)
                    if (i < steps.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Mob.border.copy(alpha = 0.5f)))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            MobButton(s.openInMaps, leadingIcon = Icons.Default.Map, color = accent, onClick = onOpenMaps)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StepRow(num: Int, text: String, distanceMeters: Double, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text("$num", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text.ifBlank { "—" }, color = Mob.textPrimary, fontSize = 14.sp, lineHeight = 19.sp)
            if (distanceMeters >= 1.0) {
                Text(MobilityReferenceData.formatDistance(distanceMeters), color = Mob.textMuted, fontSize = 12.sp)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07090D, heightDp = 820)
@Composable
private fun RouteResultsPreview() {
    RouteResultsScreen()
}
