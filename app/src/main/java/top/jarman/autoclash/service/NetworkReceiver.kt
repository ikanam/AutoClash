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
import top.jarman.autoclash.data.repository.LogLevel

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
                currentJob = scope.launch {
                    logRepository?.i(TAG, "检测到 WiFi 网络变化")
                    ruleEngine.evaluateWlanRules()
                }
            }
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                currentJob = scope.launch {
                    logRepository?.i(TAG, "检测到移动网络变化")
                    ruleEngine.evaluateCarrierRules()
                }
            }
        }
    }
}
