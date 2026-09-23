package com.example.imiq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class DigitalCompanionStoreTest {
    @Test fun normalizes_valid_unicode_name() {
        assertEquals("Nova Lumi", DigitalCompanionStore.normalize("  Nova Lumi  "))
    }

    @Test fun blank_name_means_default_identity() {
        assertNull(DigitalCompanionStore.normalize("   "))
    }

    @Test fun rejects_control_newline_and_overlong_names() {
        listOf("Nova\nMobi", "Nova\u0000", "x".repeat(25)).forEach {
            try { DigitalCompanionStore.normalize(it); fail("Expected invalid nickname") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
