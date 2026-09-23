package com.example.imiq

import com.google.gson.JsonObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface ContextualExplanationApi {
    @POST("api/dyconet/contextual-explanation")
    suspend fun explain(@Body request: JsonObject): ContextualExplanationDto
}

object ContextualExplanationApiService {
    val api: ContextualExplanationApi by lazy {
        Retrofit.Builder()
            .baseUrl(dyconetApiBaseUrl(BuildConfig.DYCONET_BASE_URL))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ContextualExplanationApi::class.java)
    }
}
