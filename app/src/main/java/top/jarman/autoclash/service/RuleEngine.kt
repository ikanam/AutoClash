package top.jarman.autoclash.service

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.first
import top.jarman.autoclash.data.api.ApiClient
import top.jarman.autoclash.data.model.RuleType
import top.jarman.autoclash.data.repository.LogLevel
import top.jarman.autoclash.data.repository.LogRepository
import top.jarman.autoclash.data.repository.MihomoRepository
import top.jarman.autoclash.data.repository.RuleRepository
import top.jarman.autoclash.data.repository.SettingsRepository

class RuleEngine(private val context: Context) {

    companion object {
        private const val TAG = "RuleEngine"
    }

    private val settingsRepo = SettingsRepository(context)
    private val ruleRepo = RuleRepository(context)
    private val logRepo = LogRepository(context)

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

            Log.d(TAG, "Evaluating ${rules.size} rules. SSID=$currentSsid, Carrier=$currentCarrier")

            for (rule in rules) {
                val matches = when (rule.ruleType) {
                    RuleType.WLAN -> currentSsid != null && currentSsid == rule.condition
                    RuleType.CARRIER -> currentCarrier != null && currentCarrier.contains(rule.condition, ignoreCase = true)
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

    private suspend fun evaluateRulesByType(type: RuleType) {
        try {
            val baseUrl = settingsRepo.apiBaseUrl.first()
            val secret = settingsRepo.apiSecret.first()

            if (baseUrl.isBlank()) {
                Log.w(TAG, "[${type.displayName}] API not configured, skip")
                return
            }

            // Check if logging is enabled
            val isLoggingEnabled = logRepo.isLogEnabled()

            val api = ApiClient.getApi(baseUrl, secret)
            val repo = MihomoRepository(api)
            val allRules = ruleRepo.rules.first()
            val rules = allRules.filter { it.enabled && it.ruleType == type }.sortedBy { it.priority }

            Log.i(TAG, "========== 开始评估 ${type.displayName} 规则 ==========")
            if (isLoggingEnabled) {
                logRepo.i(TAG, "开始评估 ${type.displayName} 规则，总规则数: ${allRules.size}, 启用规则数: ${rules.size}")
            }

            if (rules.isEmpty()) {
                Log.i(TAG, "没有启用的 ${type.displayName} 规则，跳过")
                if (isLoggingEnabled) {
                    logRepo.i(TAG, "没有启用的 ${type.displayName} 规则，跳过评估")
                }
                return
            }

            val hasWlanRules = rules.any { it.ruleType == RuleType.WLAN }
            val hasCarrierRules = rules.any { it.ruleType == RuleType.CARRIER }

            val currentSsid = if (hasWlanRules) getCurrentSsid() else null
            val currentCarrier = if (hasCarrierRules) getCurrentCarrier() else null

            Log.i(TAG, "当前环境: SSID=[$currentSsid], 运营商=[$currentCarrier]")
            if (isLoggingEnabled) {
                logRepo.i(TAG, "当前环境 - SSID: [$currentSsid], 运营商: [$currentCarrier]")
            }

            // Group rules by proxy group, first-match-wins per group
            val rulesByGroup = rules.groupBy { it.groupName }
            val matchedGroups = mutableSetOf<String>()

            for (rule in rules) {
                // Skip if this group already had a match
                if (rule.groupName in matchedGroups) {
                    Log.d(TAG, "  跳过 [${rule.groupName}] 的规则 (该组已命中)")
                    continue
                }

                val negateLabel = if (rule.negate) " [取反]" else ""
                Log.d(TAG, "检查规则 #${rule.priority}: type=${rule.ruleType}$negateLabel, condition=[${rule.condition}], group=${rule.groupName}, target=${rule.targetProxy}")

                val rawMatch = when (type) {
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
                }

                val matches = if (rule.negate) !rawMatch else rawMatch
                if (rule.negate) {
                    Log.d(TAG, "  取反后: $matches")
                }

                if (matches) {
                    matchedGroups.add(rule.groupName)
                    Log.i(TAG, "✅ 规则命中! 切换 [${rule.groupName}] -> [${rule.targetProxy}] (后续该组规则将跳过)")
                    val result = repo.switchProxy(rule.groupName, rule.targetProxy)
                    if (result.isSuccess) {
                        Log.i(TAG, "✅ 切换成功: ${rule.groupName} -> ${rule.targetProxy}")
                        if (isLoggingEnabled) {
                            logRepo.i(TAG, "✅ 策略组切换成功: ${rule.groupName} -> ${rule.targetProxy}")
                        }
                    } else {
                        Log.e(TAG, "❌ 切换失败: ${result.exceptionOrNull()?.message}")
                        if (isLoggingEnabled) {
                            logRepo.e(TAG, "❌ 策略组切换失败: ${rule.groupName} -> ${rule.targetProxy}, 错误: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    Log.d(TAG, "  ❌ 规则未命中")
                }
            }
            Log.i(TAG, "========== ${type.displayName} 规则评估完毕 (${matchedGroups.size} 个组命中) ==========")
            if (isLoggingEnabled) {
                logRepo.i(TAG, "${type.displayName} 规则评估完毕，${matchedGroups.size} 个策略组已切换")
            }
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

    /**
     * Get current network ISP by querying public IP info.
     * Works for both WiFi and cellular - detects actual broadband provider.
     */
    private suspend fun getCurrentCarrier(): String? {
        val maxRetries = 3
        val retryDelayMs = 2000L
        var attempt = 0

        while (attempt < maxRetries) {
            try {
                val url = java.net.URL("http://ip-api.com/json/?fields=isp")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val body = connection.inputStream.bufferedReader().readText()
                    connection.disconnect()
                    // Response: {"isp":"China Telecom"}
                    val isp = org.json.JSONObject(body).optString("isp", "")
                    val carrier = normalizeCarrier(isp)
                    Log.i(TAG, "当前网络 ISP: [$isp] -> 运营商: [$carrier]")
                    return carrier.takeIf { it.isNotBlank() }
                } else {
                    connection.disconnect()
                    Log.w(TAG, "IP lookup failed: HTTP $responseCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting ISP via IP lookup on attempt ${attempt + 1}", e)
            }
            
            attempt++
            if (attempt < maxRetries) {
                Log.w(TAG, "ISP 检查失败，将在 ${retryDelayMs / 1000} 秒后重试 (当前重试次数: $attempt/$maxRetries)")
                kotlinx.coroutines.delay(retryDelayMs)
            }
        }
        Log.e(TAG, "ISP lookup failed after $maxRetries attempts")
        return null
    }

    /**
     * Normalize English ISP name from ip-api.com to canonical carrier name.
     * - 电信: Chinanet / China Telecom
     * - 移动: China Mobile
     * - 联通: China Unicom
     */
    private fun normalizeCarrier(isp: String): String {
        return when {
            isp.contains("Chinanet", ignoreCase = true) ||
            isp.contains("China Telecom", ignoreCase = true) -> "中国电信"
            isp.contains("China Mobile", ignoreCase = true) -> "中国移动"
            isp.contains("China Unicom", ignoreCase = true) -> "中国联通"
            else -> isp
        }
    }
}
