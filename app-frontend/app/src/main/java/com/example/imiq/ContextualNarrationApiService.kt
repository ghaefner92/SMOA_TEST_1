package com.example.imiq

import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ContextualNarrationApi {
    @POST("api/dyconet/contextual-explanation/narrate")
    suspend fun narrate(@Body request: JsonObject): ContextualNarrationDto
}

object ContextualNarrationApiService {
    // Narration is secondary to route choice. It has room for a valid provider
    // response, but cannot leave its non-blocking panel loading indefinitely.
    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 15L
    private const val CALL_TIMEOUT_SECONDS = 35L

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    val api: ContextualNarrationApi by lazy {
        Retrofit.Builder()
            .baseUrl(dyconetApiBaseUrl(BuildConfig.DYCONET_BASE_URL))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ContextualNarrationApi::class.java)
    }
}
