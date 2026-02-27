package top.jarman.autoclash.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.first
import top.jarman.autoclash.data.api.ApiClient
import top.jarman.autoclash.data.model.AutomationRule
import top.jarman.autoclash.data.model.RuleType
import top.jarman.autoclash.data.repository.MihomoRepository
import top.jarman.autoclash.data.repository.RuleRepository
import top.jarman.autoclash.data.repository.SettingsRepository
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RuleEngine(private val context: Context) {

    companion object {
        private const val TAG = "RuleEngine"
    }

    private val settingsRepo = SettingsRepository(context)
    private val ruleRepo = RuleRepository(context)

    /**
     * Evaluate all enabled rules and execute matching ones
     */
    suspend fun evaluateRules() {
        try {
            val baseUrl = settingsRepo.apiBaseUrl.first()
            val secret = settingsRepo.apiSecret.first()

            if (baseUrl.isBlank()) {
                Log.w(TAG, "API not configured, skipping rule evaluation")
                return
            }

            val api = ApiClient.getApi(baseUrl, secret)
            val repo = MihomoRepository(api)
            val rules = ruleRepo.rules.first().filter { it.enabled }

            val currentSsid = getCurrentSsid()
            val currentCarrier = getCurrentCarrier()
            val currentTime = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                LocalTime.now()
            } else {
                null
            }

            Log.d(TAG, "Evaluating ${rules.size} rules. SSID=$currentSsid, Carrier=$currentCarrier, Time=$currentTime")

            for (rule in rules) {
                val matches = when (rule.ruleType) {
                    RuleType.WLAN -> currentSsid != null && currentSsid == rule.condition
                    RuleType.CARRIER -> currentCarrier != null && currentCarrier.contains(rule.condition, ignoreCase = true)
                    RuleType.TIME -> currentTime != null && isInTimeRange(currentTime, rule.condition)
                }

                if (matches) {
                    Log.i(TAG, "Rule matched: ${rule.ruleType} [${rule.condition}] -> switching ${rule.groupName} to ${rule.targetProxy}")
                    val result = repo.switchProxy(rule.groupName, rule.targetProxy)
                    if (result.isSuccess) {
                        Log.i(TAG, "Successfully switched ${rule.groupName} to ${rule.targetProxy}")
                    } else {
                        Log.e(TAG, "Failed to switch proxy: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating rules", e)
        }
    }

    /**
     * Evaluate only WLAN rules (triggered by WiFi change)
     */
    suspend fun evaluateWlanRules() {
        evaluateRulesByType(RuleType.WLAN)
    }

    /**
     * Evaluate only CARRIER rules (triggered by network change)
     */
    suspend fun evaluateCarrierRules() {
        evaluateRulesByType(RuleType.CARRIER)
    }

    /**
     * Evaluate only TIME rules (triggered by alarm)
     */
    suspend fun evaluateTimeRules() {
        evaluateRulesByType(RuleType.TIME)
    }

    private suspend fun evaluateRulesByType(type: RuleType) {
        try {
            val baseUrl = settingsRepo.apiBaseUrl.first()
            val secret = settingsRepo.apiSecret.first()

            if (baseUrl.isBlank()) {
                Log.w(TAG, "[${type.displayName}] API not configured, skip")
                return
            }

            val api = ApiClient.getApi(baseUrl, secret)
            val repo = MihomoRepository(api)
            val allRules = ruleRepo.rules.first()
            val rules = allRules.filter { it.enabled && it.ruleType == type }

            Log.i(TAG, "========== 开始评估 ${type.displayName} 规则 ==========")
            Log.i(TAG, "总规则数: ${allRules.size}, ${type.displayName} 启用规则数: ${rules.size}")

            if (rules.isEmpty()) {
                Log.i(TAG, "没有启用的 ${type.displayName} 规则，跳过")
                return
            }

            val currentSsid = getCurrentSsid()
            val currentCarrier = getCurrentCarrier()
            val currentTime = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                LocalTime.now()
            } else {
                null
            }

            Log.i(TAG, "当前环境: SSID=[$currentSsid], 运营商=[$currentCarrier], 时间=[$currentTime]")

            for (rule in rules) {
                Log.d(TAG, "检查规则: type=${rule.ruleType}, condition=[${rule.condition}], group=${rule.groupName}, target=${rule.targetProxy}")

                val matches = when (type) {
                    RuleType.WLAN -> {
                        val result = currentSsid != null && currentSsid == rule.condition
                        Log.d(TAG, "  WLAN匹配: 当前SSID=[$currentSsid] vs 规则条件=[${rule.condition}] -> $result")
                        result
                    }
                    RuleType.CARRIER -> {
                        val result = currentCarrier != null && currentCarrier.contains(rule.condition, ignoreCase = true)
                        Log.d(TAG, "  运营商匹配: 当前运营商=[$currentCarrier] vs 规则条件=[${rule.condition}] -> $result")
                        result
                    }
                    RuleType.TIME -> {
                        val result = currentTime != null && isInTimeRange(currentTime, rule.condition)
                        Log.d(TAG, "  时间匹配: 当前时间=[$currentTime] vs 规则条件=[${rule.condition}] -> $result")
                        result
                    }
                }

                if (matches) {
                    Log.i(TAG, "✅ 规则命中! 切换 [${rule.groupName}] -> [${rule.targetProxy}]")
                    val result = repo.switchProxy(rule.groupName, rule.targetProxy)
                    if (result.isSuccess) {
                        Log.i(TAG, "✅ 切换成功: ${rule.groupName} -> ${rule.targetProxy}")
                    } else {
                        Log.e(TAG, "❌ 切换失败: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    Log.d(TAG, "  ❌ 规则未命中")
                }
            }
            Log.i(TAG, "========== ${type.displayName} 规则评估完毕 ==========")
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating $type rules", e)
        }
    }

    @Suppress("deprecation")
    private fun getCurrentSsid(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val rawSsid = info.ssid
            Log.d(TAG, "WiFi connectionInfo: ssid=[$rawSsid], networkId=${info.networkId}, supplicantState=${info.supplicantState}")

            if (rawSsid == null || rawSsid == "<unknown ssid>" || rawSsid == "0x") {
                Log.w(TAG, "SSID 无法获取 (rawSsid=[$rawSsid])，可能需要授予位置权限")
                null
            } else {
                val cleanSsid = rawSsid.removePrefix("\"").removeSuffix("\"")
                Log.i(TAG, "当前 WiFi SSID: [$cleanSsid]")
                cleanSsid
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SSID", e)
            null
        }
    }

    private fun getCurrentCarrier(): String? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.networkOperatorName.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting carrier", e)
            null
        }
    }

    private fun isInTimeRange(now: LocalTime, rangeStr: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return false
            val parts = rangeStr.split("-")
            if (parts.size != 2) return false
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val start = LocalTime.parse(parts[0].trim(), formatter)
            val end = LocalTime.parse(parts[1].trim(), formatter)

            if (start <= end) {
                now in start..end
            } else {
                // Crosses midnight (e.g., 22:00-06:00)
                now >= start || now <= end
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing time range: $rangeStr", e)
            false
        }
    }
}
