package top.jarman.autoclash.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_API_BASE_URL = stringPreferencesKey("api_base_url")
        private val KEY_API_SECRET = stringPreferencesKey("api_secret")
        private val KEY_SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        private val KEY_HAS_SHOWN_ISP_WARNING = booleanPreferencesKey("has_shown_isp_warning")
    }

    val apiBaseUrl: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_API_BASE_URL] ?: "http://127.0.0.1:9090"
    }

    val apiSecret: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_API_SECRET] ?: ""
    }

    val showNotification: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_SHOW_NOTIFICATION] ?: true
    }

    val hasShownIspWarning: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_HAS_SHOWN_ISP_WARNING] ?: false
    }

    suspend fun saveApiConfig(baseUrl: String, secret: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_API_BASE_URL] = baseUrl
            prefs[KEY_API_SECRET] = secret
        }
    }

    suspend fun setShowNotification(show: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SHOW_NOTIFICATION] = show
        }
    }

    suspend fun setHasShownIspWarning(shown: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_HAS_SHOWN_ISP_WARNING] = shown
        }
    }
}
