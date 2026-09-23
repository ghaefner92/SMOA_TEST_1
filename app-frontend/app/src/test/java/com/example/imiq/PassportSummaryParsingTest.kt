package com.example.imiq

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PassportSummaryParsingTest {
    @Test
    fun nullLastUpdateEventRepresentsInitialRevision() {
        val lineage = Json.parseToJsonElement(
            """{"revision":1,"last_update_event":null}"""
        ).jsonObject

        assertNull(lastUpdateEventOrNull(lineage))
    }

    @Test
    fun objectLastUpdateEventIsReturned() {
        val lineage = Json.parseToJsonElement(
            """{"revision":2,"last_update_event":{"delta_raw_rating":1,"value_changed":true}}"""
        ).jsonObject

        val event = lastUpdateEventOrNull(lineage)

        assertEquals(1, event?.get("delta_raw_rating")?.jsonPrimitive?.intOrNull)
        assertEquals(true, event?.get("value_changed")?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun missingLastUpdateEventIsAccepted() {
        val lineage = Json.parseToJsonElement("""{"revision":1}""").jsonObject

        assertNull(lastUpdateEventOrNull(lineage))
    }
}
