package top.jarman.autoclash.data.model

import java.util.UUID

/**
 * Types of automation rules
 */
enum class RuleType(val displayName: String) {
    WLAN("WLAN (WiFi)"),
    TIME("定时切换"),
    CARRIER("运营商")
}

/**
 * An automation rule that defines when to switch a proxy group
 */
data class AutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val groupName: String,
    val ruleType: RuleType,
    val condition: String, // WLAN: SSID name, TIME: "HH:mm-HH:mm", CARRIER: carrier name
    val targetProxy: String,
    val enabled: Boolean = true
)
