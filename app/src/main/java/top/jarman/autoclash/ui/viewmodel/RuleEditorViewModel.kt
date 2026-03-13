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
import top.jarman.autoclash.service.RuleEngine

data class RuleDialogResult(
    val ruleType: RuleType,
    val condition: String,
    val targetProxy: String,
    val negate: Boolean,
    val testUrl: String = "",
    val checkIntervalSecs: Int = 60,
    val retryCount: Int = 1,
    val retryIntervalSecs: Int = 5
)

data class RuleEditorUiState(
    val groupName: String = "",
    val currentProxy: String = "",
    val allProxies: List<String> = emptyList(),
    val rules: List<AutomationRule> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddDialog: Boolean = false,
    val editingRule: AutomationRule? = null,
    val hasShownIspWarning: Boolean = false
)

class RuleEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val ruleRepo = RuleRepository(application)
    private val ruleEngine = RuleEngine(application)

    private val _uiState = MutableStateFlow(RuleEditorUiState())
    val uiState: StateFlow<RuleEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.hasShownIspWarning.collect { hasShown ->
                _uiState.value = _uiState.value.copy(hasShownIspWarning = hasShown)
            }
        }
    }

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
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingRule = null)
    }

    fun showEditDialog(rule: AutomationRule) {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingRule = rule)
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false, editingRule = null)
    }

    fun addRule(result: RuleDialogResult) {
        viewModelScope.launch {
            val currentRules = ruleRepo.getRulesForGroup(_uiState.value.groupName).first()

            // Conflict check: FALLBACK cannot coexist with WLAN/CARRIER and vice versa
            val conflictMsg = checkConflict(result.ruleType, currentRules, editingId = null)
            if (conflictMsg != null) {
                // Conflict detected — the dialog already shows this; addRule should not be called
                // in this case, but guard here anyway
                return@launch
            }

            val maxPriority = currentRules.maxOfOrNull { it.priority } ?: -1
            val rule = AutomationRule(
                groupName = _uiState.value.groupName,
                ruleType = result.ruleType,
                condition = result.condition,
                targetProxy = result.targetProxy,
                negate = result.negate,
                priority = maxPriority + 1,
                testUrl = result.testUrl,
                checkIntervalSecs = result.checkIntervalSecs,
                retryCount = result.retryCount,
                retryIntervalSecs = result.retryIntervalSecs
            )
            ruleRepo.addRule(rule)
            refreshRules()
            ruleEngine.evaluateRules()
        }
    }

    fun updateRule(result: RuleDialogResult) {
        val editing = _uiState.value.editingRule ?: return
        viewModelScope.launch {
            val currentRules = ruleRepo.getRulesForGroup(_uiState.value.groupName).first()
            val conflictMsg = checkConflict(result.ruleType, currentRules, editingId = editing.id)
            if (conflictMsg != null) return@launch

            val updated = editing.copy(
                ruleType = result.ruleType,
                condition = result.condition,
                targetProxy = result.targetProxy,
                negate = result.negate,
                testUrl = result.testUrl,
                checkIntervalSecs = result.checkIntervalSecs,
                retryCount = result.retryCount,
                retryIntervalSecs = result.retryIntervalSecs
            )
            ruleRepo.updateRule(updated)
            refreshRules()
            ruleEngine.evaluateRules()
        }
    }

    /**
     * Returns a conflict error message if [newType] cannot be added given [existingRules].
     * [editingId] is excluded from the check (allow editing a rule to the same type).
     */
    fun checkConflict(newType: RuleType, existingRules: List<AutomationRule>, editingId: String?): String? {
        val others = existingRules.filter { it.id != editingId }
        return when {
            newType == RuleType.FALLBACK && others.any { it.ruleType != RuleType.FALLBACK } ->
                "Fallback 规则不能与 WiFi/ISP 规则共存，请先删除已有规则"
            newType != RuleType.FALLBACK && others.any { it.ruleType == RuleType.FALLBACK } ->
                "已有 Fallback 规则，不能添加 ${newType.displayName} 规则，请先删除 Fallback 规则"
            else -> null
        }
    }

    private suspend fun refreshRules() {
        val updatedRules = ruleRepo.getRulesForGroup(_uiState.value.groupName).first()
            .sortedBy { it.priority }
        _uiState.value = _uiState.value.copy(
            rules = updatedRules,
            showAddDialog = false,
            editingRule = null
        )
    }

    fun toggleRule(rule: AutomationRule) {
        viewModelScope.launch {
            val updated = rule.copy(enabled = !rule.enabled)
            ruleRepo.updateRule(updated)
            refreshRules()
            // Re-evaluate affected group rules
            ruleEngine.evaluateRules()
        }
    }

    fun deleteRule(ruleId: String) {
        viewModelScope.launch {
            ruleRepo.deleteRule(ruleId)
            refreshRules()
            // Re-evaluate affected group rules
            ruleEngine.evaluateRules()
        }
    }

    fun moveRuleUp(rule: AutomationRule) {
        viewModelScope.launch {
            val rules = _uiState.value.rules
            val index = rules.indexOf(rule)
            if (index <= 0) return@launch
            val above = rules[index - 1]
            // Swap priorities
            ruleRepo.updateRule(rule.copy(priority = above.priority))
            ruleRepo.updateRule(above.copy(priority = rule.priority))
            refreshRules()
            // Re-evaluate affected group rules
            ruleEngine.evaluateRules()
        }
    }

    fun reorderRules(reorderedRules: List<AutomationRule>) {
        viewModelScope.launch {
            reorderedRules.forEachIndexed { index, rule ->
                if (rule.priority != index) {
                    ruleRepo.updateRule(rule.copy(priority = index))
                }
            }
            refreshRules()
            // Re-evaluate affected group rules
            ruleEngine.evaluateRules()
        }
    }

    fun markIspWarningAsShown() {
        viewModelScope.launch {
            settingsRepo.setHasShownIspWarning(true)
        }
    }
}
