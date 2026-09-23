package com.example.imiq

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingCoveragePolicyTest {
    @Test
    fun magdeburgCoordinatesAreInsideCurrentCoverage() {
        assertTrue(GeocodingApiService.isInsideRoutingCoverage(52.1305, 11.6276))
    }

    @Test
    fun outsideRegionIsNotPresentedAsRoutable() {
        assertFalse(GeocodingApiService.isInsideRoutingCoverage(52.5200, 13.4050))
    }
}
