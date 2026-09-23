package com.example.imiq

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Compatibility contract for previously stored strict HOTCO-CT passports.
 *
 * The active onboarding is adaptive. These utilities deliberately contain no
 * UI or network code, and remain only to validate historical passports and
 * their contract fixtures.
 */
private val strictNeedKeys = listOf(
    "comfort_physical", "reliable", "flex", "cost", "safety_crime",
    "health_activity", "time", "health_infection", "crowding",
    "safety_accident", "env",
)

private val strictModeKeys = listOf("walk", "bike", "pt", "car")

internal val ENVIRONMENTAL_TOLERANCE_KEYS = listOf(
    "rain", "crowding", "darkness", "traffic", "temperature",
)

internal fun hasCompleteEnvironmentalToleranceRatings(answers: Map<String, Int>): Boolean =
    ENVIRONMENTAL_TOLERANCE_KEYS.all { answers[it] in 1..7 }

/** Builds the retired strict request schema for contract compatibility tests. */
internal fun buildSurveyJson(
    participantId: String,
    needs: Map<String, Float>,
    top3: List<String>,
    valences: Map<String, Float>,
    beliefs: Map<String, Map<String, Float>>,
    availability: Map<String, Boolean>,
    environmentalTolerances: Map<String, Int?> = emptyMap(),
): String {
    val agentId = participantId.trim()
    require(agentId.isNotEmpty()) { "A user-provided participant identifier is required." }
    val needKeys = strictNeedKeys.toSet()
    val modeKeys = strictModeKeys.toSet()

    fun Float.isIntegerRating(range: ClosedFloatingPointRange<Float>): Boolean =
        isFinite() && this in range && this % 1f == 0f

    fun ratingIssues(
        answers: Map<String, Float>,
        requiredKeys: Set<String>,
        range: ClosedFloatingPointRange<Float>,
    ): Pair<List<String>, List<String>> {
        val missing = requiredKeys.filter { answers[it] == null }.sorted()
        val invalid = requiredKeys.filter { key ->
            answers[key]?.let { !it.isIntegerRating(range) } == true
        }.sorted()
        return missing to invalid
    }

    val (missingNeeds, invalidNeeds) = ratingIssues(needs, needKeys, 1f..7f)
    require(missingNeeds.isEmpty() && invalidNeeds.isEmpty()) {
        "All 11 need ratings must be explicitly selected from 1 to 7. " +
            listOfNotNull(
                missingNeeds.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Missing: ", postfix = "."),
                invalidNeeds.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Invalid: ", postfix = "."),
            ).joinToString(" ")
    }

    require(top3.size == 3 && top3.distinct().size == 3 && top3.all { it in needKeys }) {
        "Exactly three distinct, recognized ranked priorities are required."
    }
    require(modeKeys.all { it in availability } && modeKeys.any { availability[it] == true }) {
        "All four explicit availability responses and at least one available mode are required."
    }

    val beliefIssues = modeKeys.flatMap { mode ->
        val (missing, invalid) = ratingIssues(beliefs[mode].orEmpty(), needKeys, 1f..7f)
        buildList {
            if (missing.isNotEmpty()) add("$mode missing ${missing.joinToString()}")
            if (invalid.isNotEmpty()) add("$mode invalid ${invalid.joinToString()}")
        }
    }
    require(beliefIssues.isEmpty()) {
        "All 44 need-mode ratings must be explicitly selected from 1 to 7. ${beliefIssues.joinToString("; ")}"
    }

    val (missingValences, invalidValences) = ratingIssues(valences, modeKeys, 1f..7f)
    require(missingValences.isEmpty() && invalidValences.isEmpty()) {
        "All four valence ratings must be explicitly selected from 1 to 7."
    }

    return buildJsonObject {
        put("schema_version", "hotco_ct_input_2.1")
        put("agent_id", agentId)
        put("responses", buildJsonObject {
            put("needs", buildJsonObject {
                strictNeedKeys.forEach { put(it, requireNotNull(needs[it]).toInt()) }
            })
            put("availability", buildJsonObject {
                strictModeKeys.forEach { put(it, requireNotNull(availability[it])) }
            })
            put("beliefs", buildJsonObject {
                strictModeKeys.forEach { mode ->
                    put(mode, buildJsonObject {
                        strictNeedKeys.forEach { need ->
                            put(need, requireNotNull(beliefs[mode]?.get(need)).toInt())
                        }
                    })
                }
            })
            put("valences", buildJsonObject {
                strictModeKeys.forEach { put(it, requireNotNull(valences[it]).toInt() - 4) }
            })
            put("top_needs_ranking", buildJsonArray { top3.forEach { add(JsonPrimitive(it)) } })
            put("environmental_tolerances", buildJsonObject {
                ENVIRONMENTAL_TOLERANCE_KEYS.forEach { key ->
                    val tolerance = likertToTolerance(environmentalTolerances[key])
                    if (tolerance == null) put(key, JsonNull) else put(key, JsonPrimitive(tolerance))
                }
            })
        })
    }.toString()
}

internal fun likertToTolerance(value: Int?): Double? {
    if (value == null) return null
    require(value in 1..7) { "Environmental tolerance response must be within 1..7." }
    return (value - 1) / 6.0
}

internal fun isValidPassportV2(passportJson: String): Boolean {
    return try {
        val cp = Json.parseToJsonElement(passportJson).jsonObject["cognitive_passport"]?.jsonObject
            ?: return false
        if (cp["schema_version"]?.jsonPrimitive?.contentOrNull != "2.0") return false
        val provenance = cp["input_provenance"]?.jsonObject ?: return false
        if (provenance["source_schema"]?.jsonPrimitive?.contentOrNull != "hotco_ct_input_2.1") return false
        if (provenance["policy"]?.jsonPrimitive?.contentOrNull != "current_user_responses_only") return false
        if (provenance["imputation_used"]?.jsonPrimitive?.booleanOrNull != false) return false
        if (provenance["population_or_synthetic_values_used"]?.jsonPrimitive?.booleanOrNull != false) return false
        val availability = provenance["availability_resolution"]?.jsonObject ?: return false
        if (availability["policy"]?.jsonPrimitive?.contentOrNull != "explicit_user_declared_access_only") return false
        if (availability["external_provider_data_used"]?.jsonPrimitive?.booleanOrNull != false) return false
        if (availability["frequency_or_preference_inference_used"]?.jsonPrimitive?.booleanOrNull != false) return false
        val counts = provenance["observed_counts"]?.jsonObject ?: return false
        if (counts["needs"]?.jsonPrimitive?.intOrNull != 11 || counts["beliefs"]?.jsonPrimitive?.intOrNull != 44 ||
            counts["valences"]?.jsonPrimitive?.intOrNull != 4 || counts["availability"]?.jsonPrimitive?.intOrNull != 4) return false
        val deliberation = cp["deliberation"]?.jsonObject ?: return false
        !deliberation["terminal_tendency"]?.jsonPrimitive?.contentOrNull.isNullOrBlank() &&
            deliberation["terminal_margin"]?.jsonPrimitive?.doubleOrNull != null &&
            deliberation["comparative_readout"]?.jsonObject?.size == 4
    } catch (_: Exception) {
        false
    }
}
