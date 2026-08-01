package com.dermoai.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "dermoai_prefs")

/**
 * User-facing preferences stored via DataStore.
 */
@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    val isOnboarded: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDED] ?: false
    }

    val activeUserId: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_USER_ID]
    }

    val darkModeEnabled: Flow<Boolean?> = dataStore.data.map { prefs ->
        prefs[KEY_DARK_MODE]
    }

    val dynamicColorEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: false
    }

    val languageCode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "en"
    }

    val envAlertsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ENV_ALERTS] ?: true
    }

    suspend fun setOnboarded(value: Boolean) {
        dataStore.edit { it[KEY_ONBOARDED] = value }
    }

    suspend fun setActiveUserId(userId: String?) {
        dataStore.edit {
            if (userId == null) it.remove(KEY_USER_ID) else it[KEY_USER_ID] = userId
        }
    }

    suspend fun setDarkMode(enabled: Boolean?) {
        dataStore.edit {
            if (enabled == null) it.remove(KEY_DARK_MODE) else it[KEY_DARK_MODE] = enabled
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setLanguage(code: String) {
        dataStore.edit { it[KEY_LANGUAGE] = code }
        // Sync to SharedPreferences for attachBaseContext (sync read)
        context.getSharedPreferences("dermoai_prefs_sync", android.content.Context.MODE_PRIVATE)
            .edit().putString("language", code).apply()
    }

    suspend fun setEnvAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENV_ALERTS] = enabled }
    }

    companion object {
        private val KEY_ONBOARDED = booleanPreferencesKey("is_onboarded")
        private val KEY_USER_ID = stringPreferencesKey("active_user_id")
        private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_LANGUAGE = stringPreferencesKey("language_code")
        private val KEY_ENV_ALERTS = booleanPreferencesKey("env_alerts_enabled")
    }
}