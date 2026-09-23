package com.example.imiq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteExplanationPolicyTest {
    @Test
    fun engineRankOneOwnsTheModeSelection() {
        val options = """
            - bike | autonomy/reliable | 14 min, 4.2 km
            - pt | reliable/comfort | 18 min, 4.8 km
            - car | speed/privacy | 12 min, 5.0 km
        """.trimIndent()
        assertEquals("bike", RouteExplainerService.engineTopMode(options))
    }

    @Test
    fun missingEngineRankingProducesNoMode() {
        assertNull(RouteExplainerService.engineTopMode("No ranked options"))
    }

    @Test
    fun manualOriginStoreIsExplicitAndClearable() {
        val place = MobilityReferenceData.Place("Test start", "Magdeburg", 52.13, 11.63)
        TripOriginStore.setManual(place)
        assertEquals(place, TripOriginStore.manualOrigin())
        TripOriginStore.clearManual()
        assertNull(TripOriginStore.manualOrigin())
    }
}
