package top.jarman.autoclash.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.jarman.autoclash.data.api.ApiClient
import top.jarman.autoclash.data.repository.LogRepository
import top.jarman.autoclash.data.repository.MihomoRepository
import top.jarman.autoclash.data.repository.RuleRepository
import top.jarman.autoclash.data.repository.SettingsRepository
import top.jarman.autoclash.ui.MainActivity

class AutomationService : Service() {

    companion object {
        private const val TAG = "AutomationService"
        private const val CHANNEL_ID = "auto_clash_service"
        private const val NOTIFICATION_ID = 1
        const val ACTION_REFRESH_NOTIFICATION = "top.jarman.autoclash.action.REFRESH_NOTIFICATION"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var ruleEngine: RuleEngine
    private var networkReceiver: NetworkReceiver? = null
    private var isNotificationEnabled: Boolean = true

    @Volatile
    private var notificationContentText: String = "自动策略切换服务运行中"

    @Volatile
    private var notificationBigText: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AutomationService created")

        // Log service start
        val logRepo = LogRepository(applicationContext)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (logRepo.isLogEnabled()) {
                logRepo.i(TAG, "自动化服务已启动")
            }
        }

        ruleEngine = RuleEngine(applicationContext)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        registerNetworkReceiver()

        // Start periodic rule check via WorkManager (backup when broadcast receivers don't work)
        startPeriodicRuleCheck()

        val settingsRepo = SettingsRepository(applicationContext)

        // Listen for notification setting changes
        serviceScope.launch {
            settingsRepo.showNotification.collect { show ->
                isNotificationEnabled = show
                if (show) {
                    androidx.core.app.ServiceCompat.startForeground(
                        this@AutomationService,
                        NOTIFICATION_ID,
                        createNotification(),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        else 0
                    )
                } else {
                    androidx.core.app.ServiceCompat.stopForeground(
                        this@AutomationService,
                        androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE
                    )
                }
            }
        }

        // Run initial evaluation (only refresh notification when rules actually switched)
        serviceScope.launch {
            val switchedCount = ruleEngine.evaluateRules()
            if (switchedCount > 0) {
                refreshAndPublishNotificationStatus()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        if (intent?.action == ACTION_REFRESH_NOTIFICATION) {
            serviceScope.launch {
                refreshAndPublishNotificationStatus()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AutomationService destroyed")

        // Log service stop
        val logRepo = LogRepository(applicationContext)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (logRepo.isLogEnabled()) {
                logRepo.i(TAG, "自动化服务已停止")
            }
        }

        unregisterNetworkReceiver()
        cancelPeriodicRuleCheck()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoClash 后台服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "自动策略切换服务运行中"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoClash")
            .setContentText(notificationContentText)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)

        notificationBigText?.takeIf { it.isNotBlank() }?.let {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(it))
        }

        return builder.build()
    }

    private suspend fun refreshAndPublishNotificationStatus() {
        runCatching {
            val status = buildNotificationStatusText()
            notificationContentText = status.first
            notificationBigText = status.second
        }.onFailure {
            Log.w(TAG, "Failed to refresh notification status", it)
        }

        if (isNotificationEnabled) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, createNotification())
        }
    }

    private suspend fun buildNotificationStatusText(): Pair<String, String?> {
        val ruleRepo = RuleRepository(applicationContext)
        val settingsRepo = SettingsRepository(applicationContext)

        val groupNames = ruleRepo.rules.first()
            .filter { it.enabled }
            .map { it.groupName }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        if (groupNames.isEmpty()) {
            return "自动策略切换服务运行中（暂无自动策略组）" to null
        }

        val apiBaseUrl = settingsRepo.apiBaseUrl.first()
        val apiSecret = settingsRepo.apiSecret.first()
        val api = ApiClient.getApi(apiBaseUrl, apiSecret)
        val repo = MihomoRepository(api)

        val lines = groupNames.map { groupName ->
            val current = repo.getProxyGroup(groupName).getOrNull()?.now ?: "未知"
            "$groupName: $current"
        }

        val summary = if (lines.size == 1) {
            lines.first()
        } else {
            "${lines.size}个自动策略组，${lines.first()}"
        }

        return summary to lines.joinToString("\n")
    }

    @Suppress("deprecation")
    private fun registerNetworkReceiver() {
        networkReceiver = NetworkReceiver()
        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        registerReceiver(networkReceiver, filter)
        Log.d(TAG, "Network receiver registered")
    }

    private fun unregisterNetworkReceiver() {
        networkReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver", e)
            }
        }
        networkReceiver = null
    }

    /**
     * Start periodic rule check via WorkManager.
     * This serves as a backup when dynamic BroadcastReceivers don't work in the background.
     * Minimum interval is 15 minutes per WorkManager requirements.
     */
    private fun startPeriodicRuleCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Schedule periodic work every 15 minutes (WorkManager minimum)
        val periodicWork = PeriodicWorkRequestBuilder<RuleCheckWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(RuleCheckWorker.WORK_NAME)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            RuleCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )

        Log.d(TAG, "Periodic rule check scheduled (every 15 minutes)")
    }

    /**
     * Cancel periodic rule check when service is destroyed.
     */
    private fun cancelPeriodicRuleCheck() {
        WorkManager.getInstance(applicationContext).cancelUniqueWork(RuleCheckWorker.WORK_NAME)
        Log.d(TAG, "Periodic rule check cancelled")
    }
}
