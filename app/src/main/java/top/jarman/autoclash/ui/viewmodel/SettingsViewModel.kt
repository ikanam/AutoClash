package top.jarman.autoclash.ui.viewmodel

import android.app.Application
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

data class SettingsUiState(
    val baseUrl: String = "",
    val secret: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val isServiceRunning: Boolean = false
)

enum class ConnectionStatus {
    IDLE, TESTING, SUCCESS, FAILED
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val url = settingsRepo.apiBaseUrl.first()
            val secret = settingsRepo.apiSecret.first()
            _uiState.value = _uiState.value.copy(
                baseUrl = url,
                secret = secret
            )
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
                _uiState.value = _uiState.value.copy(
                    connectionStatus = if (result.isSuccess) ConnectionStatus.SUCCESS else ConnectionStatus.FAILED
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.FAILED)
            }
        }
    }

    fun setServiceRunning(running: Boolean) {
        _uiState.value = _uiState.value.copy(isServiceRunning = running)
    }
}
