package dev.agentone.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurityManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "agentone_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(providerId: String, apiKey: String) {
        prefs.edit().putString("api_key_$providerId", apiKey).apply()
    }

    fun getApiKey(providerId: String): String? {
        return prefs.getString("api_key_$providerId", null)
    }

    fun deleteApiKey(providerId: String) {
        prefs.edit().remove("api_key_$providerId").apply()
    }

    fun setMemoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("memory_enabled", enabled).apply()
    }

    fun isMemoryEnabled(): Boolean = prefs.getBoolean("memory_enabled", true)

    fun setAutoApproveLowRisk(enabled: Boolean) {
        prefs.edit().putBoolean("auto_approve_low_risk", enabled).apply()
    }

    fun isAutoApproveLowRisk(): Boolean = prefs.getBoolean("auto_approve_low_risk", false)

    fun setOnboardingComplete() {
        prefs.edit().putBoolean("onboarding_complete", true).apply()
    }

    fun isOnboardingComplete(): Boolean = prefs.getBoolean("onboarding_complete", false)

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
