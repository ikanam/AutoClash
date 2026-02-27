package top.jarman.autoclash.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimeAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimeAlarmReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Time alarm triggered")

        val ruleEngine = RuleEngine(context.applicationContext)
        scope.launch {
            ruleEngine.evaluateTimeRules()
        }
    }
}
