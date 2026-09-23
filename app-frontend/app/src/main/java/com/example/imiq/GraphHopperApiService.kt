package com.example.imiq

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// GraphHopper geometry source (the routing engine returns none):
//   GET https://imiq-app.et.uni-magdeburg.de/api/graphhopper/route
//       ?point=lat,lon&point=lat,lon&profile=car|bike|foot&points_encoded=false
// Verified live 2026-06-04. Engine mode_key maps 1:1 to GH profile for the
// three routable modes (car/bike/foot). With points_encoded=false the polyline
// arrives as a GeoJSON LineString: coordinates are [lon, lat] pairs.
// ---------------------------------------------------------------------------

data class GhResponse(val paths: List<GhPath> = emptyList())

data class GhPath(
    val distance: Double = 0.0,            // metres
    val time: Long = 0L,                   // milliseconds
    val bbox: List<Double> = emptyList(),  // [minLon, minLat, maxLon, maxLat]
    val points: GhPoints? = null,
    val instructions: List<GhInstruction> = emptyList()
)

data class GhPoints(
    val coordinates: List<List<Double>> = emptyList()  // [[lon, lat], ...]
)

data class GhInstruction(
    val text: String = "",
    val distance: Double = 0.0,
    val time: Long = 0L,
    val street_name: String = ""
)

interface GraphHopperApi {
    @GET("api/graphhopper/route")
    suspend fun route(
        @Query("point") points: List<String>,           // ["lat,lon", "lat,lon"]
        @Query("profile") profile: String,
        @Query("points_encoded") pointsEncoded: Boolean = false,
        @Query("locale") locale: String = "en"           // GraphHopper localizes turn instructions
    ): GhResponse
}

object GraphHopperApiService {
    private const val BASE_URL = "https://imiq-app.et.uni-magdeburg.de/"
    // Geometry is supplemental: ranked routes must never wait through a long
    // map-provider outage. Individual mode requests already run in parallel.
    private const val CONNECT_TIMEOUT_SECONDS = 8L
    private const val READ_TIMEOUT_SECONDS = 12L
    private const val CALL_TIMEOUT_SECONDS = 15L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    val api: GraphHopperApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GraphHopperApi::class.java)
    }

    /** "lat,lon" point string in the order GraphHopper expects. */
    fun point(lat: Double, lon: Double): String = "$lat,$lon"

    /** Maps an engine mode_key to a supported GraphHopper profile, or null if not routable. */
    fun profileForMode(modeKey: String): String? = when (modeKey.lowercase()) {
        "car" -> "car"
        "bike" -> "bike"
        "foot", "walk" -> "foot"
        else -> null   // pt / bike_pt / car_pt have no GraphHopper geometry
    }
}
