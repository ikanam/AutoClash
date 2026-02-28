package top.jarman.autoclash.ui.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.jarman.autoclash.data.api.ApiClient
import top.jarman.autoclash.data.repository.MihomoRepository
import top.jarman.autoclash.data.repository.SettingsRepository
import top.jarman.autoclash.service.AutomationService

data class SettingsUiState(
    val baseUrl: String = "",
    val secret: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val isServiceRunning: Boolean = false,
    val showNotification: Boolean = true,
    val logEnabled: Boolean = true
)

enum class ConnectionStatus {
    IDLE, TESTING, SUCCESS, FAILED
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private var userManuallyStopped = false

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val url = settingsRepo.apiBaseUrl.first()
            val secret = settingsRepo.apiSecret.first()
            val showNotif = settingsRepo.showNotification.first()
            val logEnabled = settingsRepo.logEnabled.first()
            _uiState.value = _uiState.value.copy(
                baseUrl = url,
                secret = secret,
                showNotification = showNotif,
                logEnabled = logEnabled
            )
            // Auto-connect if there's a saved API URL
            if (url.isNotBlank()) {
                autoConnect(url, secret)
            }

            // Keep notification state updated
            launch {
                settingsRepo.showNotification.collect { show ->
                    _uiState.value = _uiState.value.copy(showNotification = show)
                }
            }

            // Keep log enabled state updated
            launch {
                settingsRepo.logEnabled.collect { enabled ->
                    _uiState.value = _uiState.value.copy(logEnabled = enabled)
                }
            }
        }
    }

    fun toggleNotification(show: Boolean) {
        viewModelScope.launch {
            settingsRepo.setShowNotification(show)
        }
    }

    fun toggleLogEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setLogEnabled(enabled)
        }
    }

    private suspend fun autoConnect(url: String, secret: String) {
        _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.TESTING)
        try {
            val api = ApiClient.getApi(url, secret)
            val repo = MihomoRepository(api)
            val result = repo.testConnection()
            val connected = result.isSuccess
            _uiState.value = _uiState.value.copy(
                connectionStatus = if (connected) ConnectionStatus.SUCCESS else ConnectionStatus.FAILED,
                isServiceRunning = if (connected) isServiceRunning() else false
            )
            if (connected) {
                ensureServiceRunning()
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.FAILED, isServiceRunning = false)
        }
    }

    fun updateBaseUrl(url: String) {
        _uiState.value = _uiState.value.copy(baseUrl = url)
    }

    fun updateSecret(secret: String) {
        _uiState.value = _uiState.value.copy(secret = secret)
    }

    fun saveAndTest() {
        viewModelScope.launch {
            val state = _uiState.value
            settingsRepo.saveApiConfig(state.baseUrl, state.secret)
            ApiClient.clearApi()

            _uiState.value = state.copy(connectionStatus = ConnectionStatus.TESTING)

            try {
                val api = ApiClient.getApi(state.baseUrl, state.secret)
                val repo = MihomoRepository(api)
                val result = repo.testConnection()
                val connected = result.isSuccess
                _uiState.value = _uiState.value.copy(
                    connectionStatus = if (connected) ConnectionStatus.SUCCESS else ConnectionStatus.FAILED
                )
                if (connected) {
                    ensureServiceRunning()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.FAILED)
            }
        }
    }

    fun setServiceRunning(running: Boolean) {
        if (!running && _uiState.value.isServiceRunning) {
            userManuallyStopped = true
        }
        _uiState.value = _uiState.value.copy(isServiceRunning = running)
    }

    private fun ensureServiceRunning() {
        // Don't auto-start if user manually stopped the service
        if (userManuallyStopped) {
            Log.d("SettingsViewModel", "Service auto-start skipped: user manually stopped")
            return
        }
        if (isServiceRunning()) return
        val context = getApplication<Application>()
        val intent = Intent(context, AutomationService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _uiState.value = _uiState.value.copy(isServiceRunning = true)
        } catch (e: Exception) {
            // Service may have been killed or context is invalid
            // This can happen when the app is in background during network changes
            Log.e("SettingsViewModel", "Failed to start foreground service", e)
        }
    }

    private fun isServiceRunning(): Boolean {
        val context = getApplication<Application>()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = activityManager.getRunningServices(Integer.MAX_VALUE)
        return services.any { it.service.className == AutomationService::class.java.name }
    }
}
