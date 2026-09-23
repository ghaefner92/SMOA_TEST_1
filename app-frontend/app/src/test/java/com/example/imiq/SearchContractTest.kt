package com.example.imiq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class SearchContractTest {
    @Test fun localResultsMergeImmediatelyWithoutDuplicates() {
        val local = GeoResult("local:1", "Alter Markt", "Magdeburg", 52.13, 11.64, "LOCAL_FALLBACK")
        val duplicate = GeoResult("remote:1", "Alter Markt", "Magdeburg", 52.13, 11.64)
        val address = GeoResult("remote:2", "Breiter Weg 1", "39104 Magdeburg", 52.12, 11.63)
        assertEquals(listOf(local, address), mergeGeoResults(listOf(local), listOf(duplicate, address)))
    }

    @Test fun fallbackSupportsLandmarksButDoesNotPretendToBeGeneralGeocoding() {
        assertTrue(localFallbackSearch("Hauptbahnhof Magdeburg").isNotEmpty())
        assertTrue(localFallbackSearch("Breiter Weg 1 39104 Magdeburg").isEmpty())
        assertTrue(localFallbackSearch("Berlin Hauptbahnhof").isEmpty())
    }

    @Test fun selectedPlaceRetainsGeocoderIdentityAndCoordinates() {
        val result = GeoResult("remote:station", "Hauptbahnhof", "Magdeburg", 52.1303, 11.6259)
        val place = MobilityReferenceData.Place(result.label, result.sub, result.lat, result.lon, result.id, result.provenance)

        assertEquals("remote:station", place.id)
        assertEquals(52.1303, place.lat, 0.000001)
        assertEquals(11.6259, place.lon, 0.000001)
    }

    @Test fun successfulInHouseSearchDoesNotDiscloseQueryToPhoton() = runBlocking {
        var primaryCalls = 0
        var photonCalls = 0
        val inHouse = GeoResult("primary:1", "Breiter Weg 1", "Magdeburg", 52.12, 11.63)

        val resolution = resolveWithPhotonFallback(
            primary = { primaryCalls++; listOf(inHouse) },
            photon = { photonCalls++; listOf(GeoResult("photon:1", "Other", "Magdeburg", 52.13, 11.64)) },
        )

        assertEquals(1, primaryCalls)
        assertEquals(0, photonCalls)
        assertFalse(resolution.photonAttempted)
        assertEquals(listOf(inHouse), resolution.results)
    }

    @Test fun emptyInHouseSearchUsesPhotonOnce() = runBlocking {
        var photonCalls = 0
        val photon = GeoResult("photon:1", "Hauptbahnhof", "Magdeburg", 52.13, 11.63)

        val resolution = resolveWithPhotonFallback(
            primary = { emptyList() },
            photon = { photonCalls++; listOf(photon) },
        )

        assertEquals(1, photonCalls)
        assertTrue(resolution.photonAttempted)
        assertEquals(listOf(photon), resolution.results)
    }

    @Test fun failedInHouseSearchUsesPhotonOnceAndDoubleFailureIsBounded() = runBlocking {
        var photonCalls = 0
        val fallback = resolveWithPhotonFallback(
            primary = { throw IllegalStateException("primary unavailable") },
            photon = { photonCalls++; listOf(GeoResult("photon:1", "Alter Markt", "Magdeburg", 52.13, 11.64)) },
        )
        assertEquals(1, photonCalls)
        assertTrue(fallback.primaryFailed)
        assertFalse(fallback.photonFailed)

        val unavailable = resolveWithPhotonFallback(
            primary = { throw IllegalStateException("primary unavailable") },
            photon = { throw IllegalStateException("photon unavailable") },
        )
        assertTrue(unavailable.primaryFailed)
        assertTrue(unavailable.photonAttempted)
        assertTrue(unavailable.photonFailed)
        assertTrue(unavailable.results.isEmpty())
    }

    @Test fun photonDisclosureContractContainsOnlySearchParameters() {
        val request = PhotonSearchRequest(query = "Breiter Weg 1", language = "de")

        assertEquals("Breiter Weg 1", request.query)
        assertEquals("de", request.language)
        assertEquals(10, request.limit)
    }
}
