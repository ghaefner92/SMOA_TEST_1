package com.example.imiq

import android.content.Context
import android.content.SharedPreferences
import java.io.File

object TokenManager {
    private const val PREF_NAME = "imiq_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_PROFILE_COMPLETED = "profile_completed"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_COMPANION_NAME = "digital_companion_name"

    private var sharedPreferences: SharedPreferences? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(token: String) {
        sharedPreferences?.edit()?.putString(KEY_TOKEN, token)?.apply()
    }

    fun getToken(): String? {
        return sharedPreferences?.getString(KEY_TOKEN, null)
    }

    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    // ========== QUESTIONNAIRE STATE ==========

    fun markProfileCompleted(participantId: String) {
        require(participantId.isNotBlank()) { "participantId must be user-provided" }
        sharedPreferences?.edit()?.apply {
            putString(KEY_USER_NAME, participantId)
            putBoolean(KEY_PROFILE_COMPLETED, true)
            apply()
        }
    }

    fun getUserName(): String? = sharedPreferences?.getString(KEY_USER_NAME, null)

    fun isProfileCompleted(): Boolean = sharedPreferences?.getBoolean(KEY_PROFILE_COMPLETED, false) ?: false

    fun clearProfile() {
        sharedPreferences?.edit()?.apply {
            remove(KEY_USER_NAME)
            remove(KEY_PROFILE_COMPLETED)
            apply()
        }
        // Also delete cognitive passport file
        appContext?.let {
            val file = File(it.filesDir, "cognitive_passport.json")
            if (file.exists()) file.delete()
        }
    }

    // ========== APP LANGUAGE ==========

    fun getLanguage(): String? = sharedPreferences?.getString(KEY_LANGUAGE, null)

    fun setLanguage(code: String) {
        sharedPreferences?.edit()?.putString(KEY_LANGUAGE, code)?.apply()
    }

    fun companionName(): String? = sharedPreferences?.getString(KEY_COMPANION_NAME, null)

    fun setCompanionName(value: String?) {
        sharedPreferences?.edit()?.apply {
            if (value == null) remove(KEY_COMPANION_NAME) else putString(KEY_COMPANION_NAME, value)
            apply()
        }
    }
}
