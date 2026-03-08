package top.jarman.autoclash.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.jarman.autoclash.data.repository.LogRepository

class NetworkReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NetworkReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var logRepository: LogRepository? = null
    private var currentJob: Job? = null

    @Suppress("deprecation")
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Network change detected: ${intent.action}")

        // Cancel any previous evaluation job to ensure latest network change takes priority
        currentJob?.cancel()
        Log.d(TAG, "Cancelled previous evaluation job")

        // Initialize log repository lazily
        if (logRepository == null) {
            logRepository = LogRepository(context.applicationContext)
        }

        val ruleEngine = RuleEngine(context.applicationContext)

        when (intent.action) {
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                // WiFi state changes (connect/disconnect) - evaluate both WLAN and CARRIER rules
                // WiFi disconnect may trigger network transition to cellular
                currentJob = scope.launch {
                    logRepository?.i(TAG, "检测到 WiFi 网络变化")
                    val switched = ruleEngine.evaluateWlanRules() + ruleEngine.evaluateCarrierRules()
                    if (switched > 0) {
                        requestNotificationRefresh(context)
                    }
                }
            }
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                currentJob = scope.launch {
                    logRepository?.i(TAG, "检测到网络变化")
                    val switched = ruleEngine.evaluateWlanRules() + ruleEngine.evaluateCarrierRules()
                    if (switched > 0) {
                        requestNotificationRefresh(context)
                    }
                }
            }
        }
    }

    private fun requestNotificationRefresh(context: Context) {
        val intent = Intent(context, AutomationService::class.java).apply {
            action = AutomationService.ACTION_REFRESH_NOTIFICATION
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request notification refresh", e)
        }
    }
}
