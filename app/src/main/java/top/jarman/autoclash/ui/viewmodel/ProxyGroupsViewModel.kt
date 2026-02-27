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
import top.jarman.autoclash.data.api.ProxyDetail
import top.jarman.autoclash.data.model.AutomationRule
import top.jarman.autoclash.data.repository.MihomoRepository
import top.jarman.autoclash.data.repository.RuleRepository
import top.jarman.autoclash.data.repository.SettingsRepository

data class ProxyGroupsUiState(
    val isLoading: Boolean = false,
    val groups: List<ProxyGroupItem> = emptyList(),
    val error: String? = null
)

data class ProxyGroupItem(
    val name: String,
    val currentProxy: String,
    val allProxies: List<String>,
    val ruleCount: Int = 0
)

class ProxyGroupsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val ruleRepo = RuleRepository(application)

    private val _uiState = MutableStateFlow(ProxyGroupsUiState())
    val uiState: StateFlow<ProxyGroupsUiState> = _uiState.asStateFlow()

    init {
        loadGroups()
    }

    fun loadGroups() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val baseUrl = settingsRepo.apiBaseUrl.first()
                val secret = settingsRepo.apiSecret.first()

                if (baseUrl.isBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "请先配置 API 地址"
                    )
                    return@launch
                }

                val api = ApiClient.getApi(baseUrl, secret)
                val repo = MihomoRepository(api)
                val result = repo.getSelectGroups()

                if (result.isSuccess) {
                    val groups = result.getOrDefault(emptyList())
                    val rules = ruleRepo.rules.first()

                    val items = groups.map { group ->
                        ProxyGroupItem(
                            name = group.name,
                            currentProxy = group.now ?: "无",
                            allProxies = group.all ?: emptyList(),
                            ruleCount = rules.count { it.groupName == group.name }
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        groups = items
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message ?: "未知错误"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "网络错误"
                )
            }
        }
    }
}
