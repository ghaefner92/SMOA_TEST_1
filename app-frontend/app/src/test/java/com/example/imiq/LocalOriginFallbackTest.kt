package com.example.imiq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalOriginFallbackTest {
    @Test
    fun knownMagdeburgLandmarksHaveStableFallbackResults() {
        assertEquals("Hauptbahnhof", localFallbackSearch("Hauptbahnhof").single().label)
        assertEquals("Hauptbahnhof", localFallbackSearch("Magdeburg Hauptbahnhof").single().label)
        assertEquals("Alter Markt", localFallbackSearch("Alter Markt").single().label)
        assertEquals("Allee-Center", localFallbackSearch("Allee Center").single().label)
    }

    @Test
    fun unrelatedOutOfAreaQueryDoesNotResolveToMagdeburg() {
        assertTrue(localFallbackSearch("Berlin Hauptbahnhof").isEmpty())
    }
}
