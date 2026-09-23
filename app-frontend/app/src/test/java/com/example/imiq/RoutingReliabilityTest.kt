package com.example.imiq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingReliabilityTest {
    private val manual = MobilityReferenceData.Place("Campus", "Magdeburg", 52.13, 11.63)

    @Test
    fun manualOriginRemainsVisibleAndWinsOverGps() {
        val selected = selectOrigin(manual, 52.14 to 11.64)
        assertEquals(OriginKind.MANUAL, selected.kind)
        assertEquals("Campus", selected.label)
        assertEquals(manual.lat to manual.lon, selected.coordinates)
    }

    @Test
    fun switchingFromManualToGpsUsesDeviceOrigin() {
        val selected = selectOrigin(null, 52.14 to 11.64)
        assertEquals(OriginKind.DEVICE, selected.kind)
        assertEquals("Device location", selected.label)
    }

    @Test
    fun noOriginIsExplicit() {
        val selected = selectOrigin(null, null)
        assertEquals(OriginKind.NONE, selected.kind)
        assertNull(selected.coordinates)
    }

    @Test
    fun everyRoutingEntryUsesTheCentralCoveragePolicy() {
        assertTrue(RoutingCoveragePolicy.contains(52.13, 11.63))
        assertFalse(RoutingCoveragePolicy.contains(52.52, 13.40))
        assertFalse(RoutingCoveragePolicy.contains(52.13, 11.90))
    }

    @Test
    fun ranking200WithEmptyRoutesIsNotUsable() {
        assertEquals(
            RouteResultState.NO_USABLE_MODES,
            routeStateAfterRanking(rankingSucceeded = true, hasUsableModes = false, hasGeometry = false),
        )
    }

    @Test
    fun rankingCanSucceedWithoutUsableGeometry() {
        assertEquals(
            RouteResultState.RANKING_WITHOUT_GEOMETRY,
            routeStateAfterRanking(rankingSucceeded = true, hasUsableModes = true, hasGeometry = false),
        )
    }

    @Test
    fun graphHopperFailureIsExposedPerMode() {
        val outcome = GeometryOutcome("bike", GeometryStatus.FAILED, "IOException")
        assertEquals(GeometryStatus.FAILED, outcome.status)
        assertEquals("bike", outcome.modeKey)
    }

    @Test
    fun publicTransportWithoutInternalGeometryStillNeedsExternalNavigation() {
        assertEquals(GeometryStatus.UNSUPPORTED, geometryStatusFor("pt", hasProfile = false, hasPath = false))
        assertTrue(navigationIsUsable(hasInternalInstructions = false, hasExternalHandler = true))
    }

    @Test
    fun unavailableExternalNavigationDisablesStart() {
        assertFalse(navigationIsUsable(hasInternalInstructions = false, hasExternalHandler = false))
    }

    @Test
    fun missingGeometryNeverBecomesAConnectingFallback() {
        assertEquals(RouteResultState.RANKING_WITHOUT_GEOMETRY, routeStateAfterRanking(true, true, false))
    }

    @Test
    fun routeSelectionUsesIdentityInsteadOfModeWhenCandidatesShareAMode() {
        val firstBike = RankedRoute(rank = 1, mode_key = "bike", mode_label = "Cycling")
        val secondBike = RankedRoute(rank = 2, mode_key = "bike", mode_label = "Cycling alternative")

        val selected = selectedRoutingResultById(
            listOf(firstBike, secondBike),
            contextualRouteId(secondBike),
        )

        assertEquals(2, selected?.rank)
        assertEquals("ranked-2-bike", contextualRouteId(selected!!))
    }

    @Test
    fun staleRouteIdentityFallsBackOnlyToTheCurrentRouteList() {
        val current = RankedRoute(rank = 1, mode_key = "walk", mode_label = "Walking")
        val selected = selectedRoutingResultById(listOf(current), "ranked-1-bike")

        assertEquals("walk", selected?.mode_key)
        assertEquals(1, selected?.rank)
    }

    @Test
    fun enrichmentProgressNeverMakesRankingWaitForNarration() {
        assertEquals(
            RoutePipelineStage.COMPANION_PREPARING,
            routePipelineStage(
                RouteResultState.USABLE_GEOMETRY,
                ContextualAnalysisState.Available(emptyMap()),
                narrationLoading = true,
            ),
        )
    }

    @Test
    fun geometryAndContextHaveExplicitProgressStates() {
        assertEquals(
            RoutePipelineStage.MAP_LOADING,
            routePipelineStage(RouteResultState.RANKING_WITHOUT_GEOMETRY, ContextualAnalysisState.NotRequested, false),
        )
        assertEquals(
            RoutePipelineStage.CONDITIONS_LOADING,
            routePipelineStage(RouteResultState.USABLE_GEOMETRY, ContextualAnalysisState.Loading, false),
        )
    }
}
