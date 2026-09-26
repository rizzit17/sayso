package com.samsung.prism.teachable.ai

import android.content.Context
import android.content.SharedPreferences

class GeminiConfigStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiKey: String?
        get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value?.trim()).apply()
        }

    var selectedModel: String
        get() = prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) {
            prefs.edit().putString(KEY_SELECTED_MODEL, value.trim()).apply()
        }

    var cachedAvailableModels: List<String>
        get() {
            val str = prefs.getString(KEY_CACHED_MODELS, null) ?: return SUPPORTED_MODELS
            return str.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { SUPPORTED_MODELS }
        }
        set(value) {
            prefs.edit().putString(KEY_CACHED_MODELS, value.joinToString(",")).apply()
        }

    var isGenAiEnabled: Boolean
        get() = prefs.getBoolean(KEY_GEN_AI_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_GEN_AI_ENABLED, value).apply()
        }

    var lastValidationStatus: String
        get() = prefs.getString(KEY_LAST_VALIDATION_STATUS, "Not Tested") ?: "Not Tested"
        set(value) {
            prefs.edit().putString(KEY_LAST_VALIDATION_STATUS, value).apply()
        }

    var lastValidationTimestamp: Long
        get() = prefs.getLong(KEY_LAST_VALIDATION_TIME, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_VALIDATION_TIME, value).apply()
        }

    fun hasValidKey(): Boolean {
        val key = apiKey
        return !key.isNullOrBlank() && isGenAiEnabled
    }

    fun clearKey() {
        prefs.edit()
            .remove(KEY_API_KEY)
            .remove(KEY_CACHED_MODELS)
            .putString(KEY_LAST_VALIDATION_STATUS, "Key Cleared")
            .putLong(KEY_LAST_VALIDATION_TIME, System.currentTimeMillis())
            .apply()
    }

    fun saveKey(key: String) {
        apiKey = key
        lastValidationStatus = "Saved"
        lastValidationTimestamp = System.currentTimeMillis()
    }

    companion object {
        private const val PREFS_NAME = "sayso_gemini_config"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_SELECTED_MODEL = "gemini_selected_model"
        private const val KEY_CACHED_MODELS = "gemini_cached_models"
        private const val KEY_GEN_AI_ENABLED = "gemini_gen_ai_enabled"
        private const val KEY_LAST_VALIDATION_STATUS = "gemini_last_val_status"
        private const val KEY_LAST_VALIDATION_TIME = "gemini_last_val_time"

        const val DEFAULT_MODEL = "gemini-2.0-flash"
        val SUPPORTED_MODELS = listOf(
            "gemini-2.0-flash",
            "gemini-1.5-flash-latest",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-2.5-flash"
        )
    }
}
