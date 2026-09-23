package com.example.imiq

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ContextualDeliberationApi {
    @POST("api/dyconet/contextual-deliberation")
    suspend fun deliberate(
        @Body request: ContextualDeliberationRequestDto,
    ): ContextualDeliberationResponseDto
}

internal fun dyconetApiBaseUrl(configuredUrl: String): String =
    configuredUrl.trimEnd('/') + "/"

object ContextualDeliberationApiService {
    private val gson = GsonBuilder().serializeNulls().create()
    // Context is optional relative to routing. Bound a full route-context
    // request so a provider outage transitions to the existing safe UI state.
    private const val CONNECT_TIMEOUT_SECONDS = 8L
    private const val READ_TIMEOUT_SECONDS = 25L
    private const val WRITE_TIMEOUT_SECONDS = 15L
    private const val CALL_TIMEOUT_SECONDS = 30L

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    val api: ContextualDeliberationApi by lazy {
        Retrofit.Builder()
            .baseUrl(dyconetApiBaseUrl(BuildConfig.DYCONET_BASE_URL))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ContextualDeliberationApi::class.java)
    }
}
