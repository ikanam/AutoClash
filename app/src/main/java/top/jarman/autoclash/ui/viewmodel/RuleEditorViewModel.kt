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
import top.jarman.autoclash.data.model.AutomationRule
import top.jarman.autoclash.data.model.RuleType
import top.jarman.autoclash.data.repository.MihomoRepository
import top.jarman.autoclash.data.repository.RuleRepository
import top.jarman.autoclash.data.repository.SettingsRepository

data class RuleEditorUiState(
    val groupName: String = "",
    val currentProxy: String = "",
    val allProxies: List<String> = emptyList(),
    val rules: List<AutomationRule> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddDialog: Boolean = false
)

class RuleEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val ruleRepo = RuleRepository(application)

    private val _uiState = MutableStateFlow(RuleEditorUiState())
    val uiState: StateFlow<RuleEditorUiState> = _uiState.asStateFlow()

    fun loadGroup(groupName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(groupName = groupName, isLoading = true)

            try {
                val baseUrl = settingsRepo.apiBaseUrl.first()
                val secret = settingsRepo.apiSecret.first()
                val api = ApiClient.getApi(baseUrl, secret)
                val repo = MihomoRepository(api)

                val result = repo.getProxyGroup(groupName)
                if (result.isSuccess) {
                    val group = result.getOrNull()!!
                    val rules = ruleRepo.getRulesForGroup(groupName).first()

                    _uiState.value = _uiState.value.copy(
                        currentProxy = group.now ?: "",
                        allProxies = group.all ?: emptyList(),
                        rules = rules,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.exceptionOrNull()?.message
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun addRule(ruleType: RuleType, condition: String, targetProxy: String) {
        viewModelScope.launch {
            val rule = AutomationRule(
                groupName = _uiState.value.groupName,
                ruleType = ruleType,
                condition = condition,
                targetProxy = targetProxy
            )
            ruleRepo.addRule(rule)
            val updatedRules = ruleRepo.getRulesForGroup(_uiState.value.groupName).first()
            _uiState.value = _uiState.value.copy(
                rules = updatedRules,
                showAddDialog = false
            )
        }
    }

    fun toggleRule(rule: AutomationRule) {
        viewModelScope.launch {
            val updated = rule.copy(enabled = !rule.enabled)
            ruleRepo.updateRule(updated)
            val updatedRules = ruleRepo.getRulesForGroup(_uiState.value.groupName).first()
            _uiState.value = _uiState.value.copy(rules = updatedRules)
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            ruleRepo.deleteRule(ruleId)
            val updatedRules = ruleRepo.getRulesForGroup(_uiState.value.groupName).first()
            _uiState.value = _uiState.value.copy(rules = updatedRules)
        }
    }
}
