package top.jarman.autoclash.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.jarman.autoclash.data.model.AutomationRule

private val Context.rulesDataStore: DataStore<Preferences> by preferencesDataStore(name = "automation_rules")

class RuleRepository(private val context: Context) {

    companion object {
        private val KEY_RULES = stringPreferencesKey("rules_json")
        private val gson = Gson()
    }

    val rules: Flow<List<AutomationRule>> = context.rulesDataStore.data.map { prefs ->
        val json = prefs[KEY_RULES] ?: "[]"
        val type = object : TypeToken<List<AutomationRule>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    }

    suspend fun saveRules(rules: List<AutomationRule>) {
        context.rulesDataStore.edit { prefs ->
            prefs[KEY_RULES] = gson.toJson(rules)
        }
    }

    suspend fun addRule(rule: AutomationRule) {
        context.rulesDataStore.edit { prefs ->
            val json = prefs[KEY_RULES] ?: "[]"
            val type = object : TypeToken<List<AutomationRule>>() {}.type
            val existing: MutableList<AutomationRule> = gson.fromJson(json, type) ?: mutableListOf()
            existing.add(rule)
            prefs[KEY_RULES] = gson.toJson(existing)
        }
    }

    suspend fun updateRule(rule: AutomationRule) {
        context.rulesDataStore.edit { prefs ->
            val json = prefs[KEY_RULES] ?: "[]"
            val type = object : TypeToken<List<AutomationRule>>() {}.type
            val existing: MutableList<AutomationRule> = gson.fromJson(json, type) ?: mutableListOf()
            val index = existing.indexOfFirst { it.id == rule.id }
            if (index >= 0) {
                existing[index] = rule
            }
            prefs[KEY_RULES] = gson.toJson(existing)
        }
    }

    suspend fun deleteRule(ruleId: String) {
        context.rulesDataStore.edit { prefs ->
            val json = prefs[KEY_RULES] ?: "[]"
            val type = object : TypeToken<List<AutomationRule>>() {}.type
            val existing: MutableList<AutomationRule> = gson.fromJson(json, type) ?: mutableListOf()
            existing.removeAll { it.id == ruleId }
            prefs[KEY_RULES] = gson.toJson(existing)
        }
    }

    fun getRulesForGroup(groupName: String): Flow<List<AutomationRule>> {
        return rules.map { list -> list.filter { it.groupName == groupName } }
    }
}
