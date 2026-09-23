package com.example.imiq

/**
 * Session-scoped manual trip origin used only when the user cannot or does not
 * want to use device location. It is never written into the Cognitive Passport.
 *
 * The manual origin is deliberately separate from structural mode availability
 * and from HOTCO-CT. It only supplies the spatial start point to routing.
 */
object TripOriginStore {
    @Volatile
    private var manual: MobilityReferenceData.Place? = null

    fun setManual(place: MobilityReferenceData.Place) {
        manual = place
    }

    fun manualOrigin(): MobilityReferenceData.Place? = manual

    fun clearManual() {
        manual = null
    }
}
