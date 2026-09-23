package com.example.imiq

/** Keeps the existing deterministic rank-one parsing available to legacy tests/UI. */
object RouteExplainerService {
    fun engineTopMode(options: String): String? = options.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("-") }
        ?.removePrefix("-")
        ?.trim()
        ?.substringBefore('|')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}
