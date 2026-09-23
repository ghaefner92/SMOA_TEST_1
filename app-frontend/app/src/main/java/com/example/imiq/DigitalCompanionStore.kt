package com.example.imiq

/** Presentation-only companion identity. Never participates in Passport/HOTCO/routing. */
object DigitalCompanionStore {
    private const val KEY_NAME = "digital_companion_name"
    private val invalid = Regex("[\\p{Cntrl}\\r\\n]")

    fun displayName(): String = TokenManager.companionName()?.ifBlank { null } ?: "Digital Companion"

    fun normalize(value: String): String? {
        val normalized = value.trim()
        if (normalized.isEmpty()) return null
        require(normalized.length in 1..24 && !invalid.containsMatchIn(normalized)) { "Invalid companion name" }
        return normalized
    }

    fun save(value: String) = TokenManager.setCompanionName(normalize(value))
    fun reset() = TokenManager.setCompanionName(null)
    internal const val preferenceKey = KEY_NAME
}
