package top.jarman.autoclash.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import top.jarman.autoclash.data.repository.LogRepository

/**
 * WorkManager Worker that periodically checks network state and evaluates rules.
 * This serves as a backup mechanism when dynamic BroadcastReceivers don't work
 * in the background (Android 8.0+ background execution limits).
 */
class RuleCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "RuleCheckWorker"
        const val WORK_NAME = "rule_check_work"
    }

    private val logRepository: LogRepository? by lazy {
        try {
            LogRepository(applicationContext)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting periodic rule check")

        try {
            val ruleEngine = RuleEngine(applicationContext)

            // Evaluate all rules (both WLAN and CARRIER)
            ruleEngine.evaluateRules()

            Log.d(TAG, "Periodic rule check completed")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during periodic rule check", e)
            return Result.retry()
        }
    }
}
