package com.example.imiq

/** Only settings with current, implemented behavior belong in the standard Settings screen. */
enum class SettingsCapability { LANGUAGE, PROFILE }

val standardSettingsCapabilities: Set<SettingsCapability> = setOf(
    SettingsCapability.LANGUAGE,
    SettingsCapability.PROFILE,
)
