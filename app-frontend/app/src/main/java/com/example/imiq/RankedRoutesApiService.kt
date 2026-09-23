package com.example.imiq

import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Client for Modou's routing engine (Simple_engine):
//   POST https://imiq-app.et.uni-magdeburg.de/api/routing/ranked-routes
// Returns ranked mode options + per-mode value-fit breakdowns (the "why this
// mode"), but NO map geometry — polylines come from GraphHopper separately.
// The `cognitive_passport` field is the strict 11-need v4.3 object from
// PassportAdapter. Deploy a routing-engine build that accepts all eleven need
// axes and the four user-reported availability flags before this client.
// ---------------------------------------------------------------------------

data class GeoPoint2(val lat: Double, val lon: Double)

data class RankedRoutesRequest(
    val cognitive_passport: JsonObject,
    val start: GeoPoint2,
    val stop: GeoPoint2,
    val datetime: String? = null,
    val max_walk_m: Int = 500,
    val include_unavailable: Boolean = true
)

data class RankedRoutesResponse(
    val status: String? = null,
    val agent: EngineAgent? = null,
    val straight_line_distance_m: Double? = null,
    val best_route: RankedRoute? = null,
    val routes: List<RankedRoute> = emptyList()
)

data class EngineAgent(
    val id: String? = null,
    val available_modes: List<String> = emptyList(),
    val top_values: List<DimensionScore> = emptyList()
)

data class RankedRoute(
    val rank: Int = 0,
    val mode_key: String = "",
    val mode_label: String = "",
    val available: Boolean = false,
    val availability_reason: String? = null,
    val score: RouteScore? = null,
    val summary: RouteSummary? = null,
    val top_matching_values: List<DimensionScore> = emptyList(),
    val top_conflicting_values: List<DimensionScore> = emptyList(),
    val dimension_scores: List<DimensionScore> = emptyList(),
    val legs: List<RouteLeg> = emptyList()
)

data class RouteScore(val utility: Double = 0.0)

data class RouteSummary(
    val duration_seconds: Double = 0.0,
    val distance_meters: Double = 0.0,
    val transfers: Int = 0
)

// `top_values` items carry {dimension, weight}; dimension_scores / matching /
// conflicting carry {dimension, agent_weight, mode_fit, contribution}. One DTO
// covers both — Gson populates whatever the payload provides.
data class DimensionScore(
    val dimension: String = "",
    val weight: Double = 0.0,
    val agent_weight: Double = 0.0,
    val mode_fit: Double = 0.0,
    val contribution: Double = 0.0
)

data class RouteLeg(
    val step: Int = 0,
    val mode: String = "",
    val from_name: String = "",
    val to_name: String = "",
    val distance_meters: Double = 0.0,
    val duration_seconds: Double = 0.0,
    val stops: List<String> = emptyList()
)

interface RankedRoutesApi {
    @POST("api/routing/ranked-routes")
    suspend fun rankedRoutes(@Body req: RankedRoutesRequest): RankedRoutesResponse
}

object RankedRoutesApiService {
    private const val BASE_URL = "https://imiq-app.et.uni-magdeburg.de/"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val api: RankedRoutesApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RankedRoutesApi::class.java)
    }
}
