package top.jarman.autoclash.data.model

import java.util.UUID

/**
 * Types of automation rules
 */
enum class RuleType(val displayName: String) {
    WLAN("WLAN (WiFi)"),
    CARRIER("ISP"),
    FALLBACK("Fallback")
}

/**
 * An automation rule that defines when to switch a proxy group.
 * For FALLBACK type: condition and targetProxy are unused (set to empty string).
 * Fallback-specific fields: testUrl, checkIntervalSecs, retryCount, retryIntervalSecs.
 */
data class AutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val groupName: String,
    val ruleType: RuleType,
    val condition: String, // WLAN: SSID name, CARRIER: ISP name, FALLBACK: unused
    val targetProxy: String, // WLAN/CARRIER: target proxy, FALLBACK: unused
    val enabled: Boolean = true,
    val negate: Boolean = false, // true = match when condition does NOT match (WLAN/CARRIER only)
    val priority: Int = 0, // lower number = higher priority
    // Fallback-specific fields (only used when ruleType == FALLBACK)
    val testUrl: String = "",
    val checkIntervalSecs: Int = 60,
    val retryCount: Int = 1,
    val retryIntervalSecs: Int = 5
)
