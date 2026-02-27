package top.jarman.autoclash.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
    }

    val apiBaseUrl: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_API_BASE_URL] ?: ""
    }

    val apiSecret: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_API_SECRET] ?: ""
    }

    suspend fun saveApiConfig(baseUrl: String, secret: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_API_BASE_URL] = baseUrl
            prefs[KEY_API_SECRET] = secret
        }
    }
}
