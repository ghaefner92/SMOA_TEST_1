package com.example.imiq

/** A single source of truth for the currently supported routing rectangle. */
object RoutingCoveragePolicy {
    const val MIN_LAT = 52.00
    const val MAX_LAT = 52.25
    const val MIN_LON = 11.45
    const val MAX_LON = 11.80

    fun contains(lat: Double, lon: Double): Boolean =
        lat in MIN_LAT..MAX_LAT && lon in MIN_LON..MAX_LON
}

enum class OriginKind { DEVICE, MANUAL, NONE }

data class OriginSelection(
    val coordinates: Pair<Double, Double>?,
    val label: String,
    val kind: OriginKind,
)

fun selectOrigin(
    manual: MobilityReferenceData.Place?,
    measured: Pair<Double, Double>?,
    deviceLabel: String = "Device location",
): OriginSelection = when {
    manual != null -> OriginSelection(manual.lat to manual.lon, manual.label, OriginKind.MANUAL)
    measured != null -> OriginSelection(measured, deviceLabel, OriginKind.DEVICE)
    else -> OriginSelection(null, "No starting point", OriginKind.NONE)
}

enum class RouteResultState {
    LOADING,
    COVERAGE_UNAVAILABLE,
    RANKING_UNAVAILABLE,
    NO_USABLE_MODES,
    RANKING_WITHOUT_GEOMETRY,
    USABLE_GEOMETRY,
    MAP_UNAVAILABLE,
    NAVIGATION_UNAVAILABLE,
}

enum class GeometryStatus {
    AVAILABLE,
    UNSUPPORTED,
    EMPTY,
    FAILED,
}

data class GeometryOutcome(
    val modeKey: String,
    val status: GeometryStatus,
    val detail: String? = null,
)

fun geometryStatusFor(modeKey: String, hasProfile: Boolean, hasPath: Boolean): GeometryStatus = when {
    !hasProfile -> GeometryStatus.UNSUPPORTED
    !hasPath -> GeometryStatus.EMPTY
    else -> GeometryStatus.AVAILABLE
}

fun navigationIsUsable(hasInternalInstructions: Boolean, hasExternalHandler: Boolean): Boolean =
    hasInternalInstructions || hasExternalHandler

fun routeStateAfterRanking(
    rankingSucceeded: Boolean,
    hasUsableModes: Boolean,
    hasGeometry: Boolean,
): RouteResultState = when {
    !rankingSucceeded -> RouteResultState.RANKING_UNAVAILABLE
    !hasUsableModes -> RouteResultState.NO_USABLE_MODES
    !hasGeometry -> RouteResultState.RANKING_WITHOUT_GEOMETRY
    else -> RouteResultState.USABLE_GEOMETRY
}
