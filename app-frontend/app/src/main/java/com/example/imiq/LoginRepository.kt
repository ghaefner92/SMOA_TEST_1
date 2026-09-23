package com.example.imiq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class LoginRequest(val code: String)

@Serializable
data class LoginResponse(val token: String)

class LoginRepository {
    private val baseUrl = "https://imiq-public.et.uni-magdeburg.de"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun login(code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val loginRequest = LoginRequest(code = code)
            val requestBody = json.encodeToString(
                LoginRequest.serializer(),
                loginRequest
            ).toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/api/dayplanner/login")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val loginResponse = json.decodeFromString<LoginResponse>(responseBody)
                    Result.success(loginResponse.token)
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("Login failed with code: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}