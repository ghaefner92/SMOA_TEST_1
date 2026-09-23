package com.example.imiq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class MicroQuestionCandidate(
    val questionId: String,
    val trigger: String,
    val kind: String,
    val mode: String?,
    val need: String?,
    val measurementStatus: String,
    val currentRawRating: Int?,
    val usesAdaptiveContract: Boolean,
)

/**
 * Client for deterministic XAI-driven profile refinement.
 *
 * The backend, not GPT, chooses the target. A new Passport is generated only
 * after the user selects a 1..7 response and explicitly confirms the update.
 */
object AdaptiveProfileService {
    private val baseUrl: String = BuildConfig.DYCONET_BASE_URL.trimEnd('/')
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    fun candidate(passportJson: String): MicroQuestionCandidate? = runCatching {
        val cp = Json.parseToJsonElement(passportJson)
            .jsonObject["cognitive_passport"]?.jsonObject
            ?: return@runCatching null
        val adaptive = cp["adaptive_questioning"]?.jsonObject
            ?: return@runCatching null
        if (adaptive["question_needed"]?.jsonPrimitive?.booleanOrNull != true) {
            return@runCatching null
        }
        val candidate = adaptive["candidate"]?.jsonObject ?: return@runCatching null
        if (candidate["scope"]?.jsonPrimitive?.contentOrNull != "profile") {
            return@runCatching null
        }
        if (candidate["update_requires_explicit_user_confirmation"]
                ?.jsonPrimitive?.booleanOrNull != true) {
            return@runCatching null
        }
        val target = candidate["target"]?.jsonObject ?: return@runCatching null

        val kind = target["kind"]?.jsonPrimitive?.contentOrNull
            ?: return@runCatching null
        if (kind !in setOf("need", "belief", "valence")) return@runCatching null

        val rawRating = target["current_raw_rating"]?.jsonPrimitive?.intOrNull
        if (rawRating != null && rawRating !in 1..7) return@runCatching null

        val adaptiveSchema =
            adaptive["schema_version"]?.jsonPrimitive?.contentOrNull ==
                "adaptive_question_engine_1.0"

        val hasAdaptiveExtension =
            cp["adaptive_passport"] != null

        val usesAdaptiveContract =
            adaptiveSchema || hasAdaptiveExtension

        val measurementStatus =
            target["measurement_status"]?.jsonPrimitive?.contentOrNull
                ?: if (rawRating != null) "observed" else return@runCatching null

        if (measurementStatus !in setOf("observed", "estimated")) {
            return@runCatching null
        }

        if (measurementStatus == "observed" && rawRating == null) {
            return@runCatching null
        }

        if (measurementStatus == "estimated" && rawRating != null) {
            return@runCatching null
        }

        MicroQuestionCandidate(
            questionId = candidate["question_id"]?.jsonPrimitive?.contentOrNull
                ?: return@runCatching null,
            trigger = candidate["trigger"]?.jsonPrimitive?.contentOrNull ?: "XAI_RECHECK",
            kind = kind,
            mode = target["mode"]?.jsonPrimitive?.contentOrNull,
            need = target["need"]?.jsonPrimitive?.contentOrNull,
            measurementStatus = measurementStatus,
            currentRawRating = rawRating,
            usesAdaptiveContract = usesAdaptiveContract,
        )
    }.getOrNull()

    internal fun buildUpdateJson(
        passportJson: String,
        candidate: MicroQuestionCandidate,
        rawRating: Int,
    ): String {
        require(rawRating in 1..7) { "Profile re-check response must be from 1 to 7." }
        val previousPassport = Json.parseToJsonElement(passportJson)
        val target: JsonObject = buildJsonObject {
            put("kind", candidate.kind)
            candidate.mode?.let { put("mode", it) }
            candidate.need?.let { put("need", it) }
        }
        return buildJsonObject {
            put(
                "schema_version",
                if (candidate.usesAdaptiveContract)
                    "adaptive_profile_update_1.0"
                else
                    "hotco_ct_profile_update_1.0",
            )
            put("previous_passport", previousPassport)
            put("update", buildJsonObject {
                put("question_id", candidate.questionId)
                put("target", target)
                put("raw_rating", rawRating)
                put("explicit_user_confirmation", true)
            })
        }.toString()
    }

    suspend fun submitExplicitUpdate(
        passportJson: String,
        candidate: MicroQuestionCandidate,
        rawRating: Int,
    ): String = withContext(Dispatchers.IO) {
        val payload = buildUpdateJson(passportJson, candidate, rawRating)

        val endpoint =
            if (candidate.usesAdaptiveContract)
                "/api/dyconet/adaptive-passport/profile-update"
            else
                "/api/dyconet/profile-update"

        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw RuntimeException("Profile update error ${response.code}: $body")
            }
            require(isValidStoredPassportV2(body)) {
                "Server returned an invalid Cognitive Passport after profile update."
            }
            body
        }
    }
}

internal fun microQuestionPrompt(candidate: MicroQuestionCandidate): String {
    val de = LanguageState.current == AppLanguage.DE
    val mode = when (candidate.mode) {
        "car" -> if (de) "Auto" else "car"
        "bike" -> if (de) "Fahrrad" else "cycling"
        "pt" -> if (de) "ÖPNV" else "public transport"
        "walk" -> if (de) "Zufußgehen" else "walking"
        else -> if (de) "dieses Verkehrsmittel" else "this mode"
    }
    val need = when (candidate.need) {
        "pro_env" -> if (de) "Umweltfreundlichkeit" else "eco-friendliness"
        "physical" -> if (de) "körperliche Aktivität" else "physical activity"
        "privacy" -> if (de) "Privatsphäre und Platz" else "privacy and space"
        "autonomy" -> if (de) "Flexibilität und Autonomie" else "flexibility and autonomy"
        "cost" -> if (de) "Kosten" else "cost"
        "speed" -> if (de) "Zeitersparnis" else "time saving"
        "safety_accident" -> if (de) "Verkehrssicherheit" else "traffic safety"
        "safety_crime" -> if (de) "persönliche Sicherheit" else "personal security"
        "comfort" -> if (de) "Komfort" else "comfort"
        "reliable" -> if (de) "Zuverlässigkeit" else "reliability"
        "health_infection" -> if (de) "Gesundheitsschutz" else "health protection"
        else -> if (de) "diesen Aspekt" else "this aspect"
    }
    return when (candidate.kind) {
        "need" -> if (de)
            "Hat sich verändert, wie wichtig dir $need im Allgemeinen ist?"
        else
            "Has how important $need is to you in general changed?"
        "belief" -> if (de)
            "Wie stark verbindest du $mode heute im Allgemeinen mit $need?"
        else
            "How strongly do you currently associate $mode with $need in general?"
        "valence" -> if (de)
            "Wie positiv oder negativ bewertest du $mode heute im Allgemeinen?"
        else
            "How positively or negatively do you currently evaluate $mode in general?"
        else -> if (de)
            "Möchtest du diesen Teil deines Profils erneut bewerten?"
        else
            "Would you like to re-rate this part of your profile?"
    }
}
