package top.jarman.autoclash.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NetworkReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NetworkReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("deprecation")
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Network change detected: ${intent.action}")

        val ruleEngine = RuleEngine(context.applicationContext)

        when (intent.action) {
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                scope.launch {
                    ruleEngine.evaluateWlanRules()
                }
            }
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                scope.launch {
                    ruleEngine.evaluateCarrierRules()
                }
            }
        }
    }
}
