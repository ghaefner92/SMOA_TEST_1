package com.example.imiq

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// Forward-geocoding for destination/origin entry, via the in-house Simple
// Engine Geocoding Service (Nominatim-backed).
//
// The service searches globally, while the current routing stack only supports
// the Magdeburg region. Phase 2C therefore distinguishes "nothing found" from
// "found, but outside the supported routing area" instead of silently dropping
// all out-of-area candidates.
// ---------------------------------------------------------------------------

data class GeoResult(
    val id: String = "",
    val label: String,
    val sub: String,
    val lat: Double,
    val lon: Double,
    val provenance: String = "REMOTE_GEOCODER",
)

data class GeoSearchResponse(
    val results: List<GeoResult>,
    val outsideCoverageDetected: Boolean,
    val failed: Boolean = false,
)

internal data class FallbackResolution(
    val results: List<GeoResult>,
    val primaryFailed: Boolean,
    val photonAttempted: Boolean,
    val photonFailed: Boolean,
)

/** The complete Photon disclosure contract: no profile or route state. */
internal data class PhotonSearchRequest(
    val query: String,
    val language: String,
    val limit: Int = 10,
)

/**
 * Runs Photon only after the primary provider produced no usable results or
 * failed.  Keeping this policy independent of Retrofit makes its privacy
 * property directly testable.
 */
internal suspend fun resolveWithPhotonFallback(
    primary: suspend () -> List<GeoResult>,
    photon: suspend () -> List<GeoResult>,
): FallbackResolution {
    val primaryResult = runCatching { primary() }
    val primaryResults = primaryResult.getOrDefault(emptyList())
    if (primaryResults.isNotEmpty()) {
        return FallbackResolution(primaryResults, primaryFailed = false, photonAttempted = false, photonFailed = false)
    }
    val photonResult = runCatching { photon() }
    return FallbackResolution(
        results = photonResult.getOrDefault(emptyList()),
        primaryFailed = primaryResult.isFailure,
        photonAttempted = true,
        photonFailed = photonResult.isFailure,
    )
}

private data class GeocodeRequest(
    val address: String,
    val lang: String = "de",
    val limit: Int = 10,
)

private data class GeocodeCandidate(
    val lat: Double? = null,
    val lon: Double? = null,
    val display_name: String? = null,
    val name: String? = null,
    val street: String? = null,
    val housenumber: String? = null,
    val city: String? = null,
    val state: String? = null,
)

private interface GeocodeApi {
    @POST("geocode")
    suspend fun geocode(@Body req: GeocodeRequest): List<GeocodeCandidate>
}

object GeocodingApiService {
    private const val BASE_URL = "https://imiq-public.et.uni-magdeburg.de/api/geocode/"

    // Current routing coverage. Keep this explicit until the routing provider
    // exposes its own coverage endpoint/polygon.
    private val cache = HashMap<String, GeoSearchResponse>()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private val api: GeocodeApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodeApi::class.java)
    }

    private val photonApi: PhotonGeocodeApi by lazy {
        Retrofit.Builder().baseUrl("https://photon.komoot.io/").client(client)
            .addConverterFactory(GsonConverterFactory.create()).build().create(PhotonGeocodeApi::class.java)
    }

    fun isInsideRoutingCoverage(lat: Double, lon: Double): Boolean =
        RoutingCoveragePolicy.contains(lat, lon)

    suspend fun search(query: String): List<GeoResult> = searchWithCoverage(query).results

    suspend fun searchWithCoverage(query: String): GeoSearchResponse {
        val q = query.trim()
        if (q.length < 2) return GeoSearchResponse(emptyList(), outsideCoverageDetected = false)
        val lang = if (LanguageState.current == AppLanguage.DE) "de" else "en"
        val key = "$lang|${q.lowercase()}"
        cache[key]?.let { return it }

        return run {
            // Photon is a privacy-preserving fallback, not a duplicate recipient
            // of every address typed into the app.
            val photonRequest = PhotonSearchRequest(query = q, language = lang)
            val resolution = resolveWithPhotonFallback(
                primary = { api.geocode(GeocodeRequest(address = q, lang = lang)).mapNotNull { it.toGeoResult() } },
                photon = { photonApi.geocode(photonRequest.query, photonRequest.limit, photonRequest.language).features.mapNotNull { it.toGeoResult() } },
            )
            val resolved = resolution.results
                .distinctBy { "${it.lat},${it.lon}" }
            val inArea = resolved
                .filter { isInsideRoutingCoverage(it.lat, it.lon) }
                .take(6)
            GeoSearchResponse(
                results = inArea,
                outsideCoverageDetected = resolved.any { !isInsideRoutingCoverage(it.lat, it.lon) },
                failed = resolution.primaryFailed && resolution.photonFailed,
            ).also { cache[key] = it }
        }
    }

    private fun GeocodeCandidate.toGeoResult(): GeoResult? {
        val la = lat ?: return null
        val lo = lon ?: return null
        val label = name?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(street, housenumber).joinToString(" ").takeIf { it.isNotBlank() }
            ?: display_name?.substringBefore(",")?.takeIf { it.isNotBlank() }
            ?: "Selected place"
        val sub = listOfNotNull(
            street?.takeIf { it.isNotBlank() && it != label },
            city?.takeIf { it.isNotBlank() },
        ).distinct().take(2).joinToString(" · ")
            .ifBlank { state?.takeIf { it.isNotBlank() } ?: "Magdeburg" }
        if (!la.isFinite() || !lo.isFinite() || la !in -90.0..90.0 || lo !in -180.0..180.0) return null
        return GeoResult(id = "remote:${la},${lo}:${label.lowercase()}", label = label, sub = sub, lat = la, lon = lo)
    }

    private fun PhotonFeature.toGeoResult(): GeoResult? {
        val p = properties ?: return null
        val coordinates = geometry?.coordinates ?: return null
        val lon = coordinates.getOrNull(0) ?: return null
        val lat = coordinates.getOrNull(1) ?: return null
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val streetLine = listOfNotNull(p.street, p.housenumber).joinToString(" ").takeIf { it.isNotBlank() }
        val label = p.name?.takeIf { it.isNotBlank() } ?: streetLine ?: "Selected place"
        val sub = listOfNotNull(streetLine?.takeIf { it != label }, p.postcode, p.city).joinToString(" · ").ifBlank { p.state ?: "Magdeburg" }
        return GeoResult("photon:${p.osm_type ?: ""}:${p.osm_id ?: "$lat,$lon"}", label, sub, lat, lon, "REMOTE_GEOCODER_PHOTON")
    }
}

private interface PhotonGeocodeApi {
    @GET("api/")
    suspend fun geocode(@Query("q") query: String, @Query("limit") limit: Int = 10, @Query("lang") language: String): PhotonResponse
}

private data class PhotonResponse(val features: List<PhotonFeature> = emptyList())
private data class PhotonFeature(val properties: PhotonProperties? = null, val geometry: PhotonGeometry? = null)
private data class PhotonGeometry(val coordinates: List<Double> = emptyList())
private data class PhotonProperties(val osm_type: String? = null, val osm_id: Long? = null, val name: String? = null, val street: String? = null, val housenumber: String? = null, val city: String? = null, val postcode: String? = null, val state: String? = null)

internal fun mergeGeoResults(local: List<GeoResult>, remote: List<GeoResult>): List<GeoResult> =
    (local + remote).distinctBy { "${it.lat},${it.lon}" }.take(10)
