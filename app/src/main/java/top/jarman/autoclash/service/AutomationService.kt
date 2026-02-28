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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import top.jarman.autoclash.data.repository.LogRepository
import top.jarman.autoclash.ui.MainActivity

class AutomationService : Service() {

    companion object {
        private const val TAG = "AutomationService"
        private const val CHANNEL_ID = "auto_clash_service"
        private const val NOTIFICATION_ID = 1
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var ruleEngine: RuleEngine
    private var networkReceiver: NetworkReceiver? = null

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

        val settingsRepo = top.jarman.autoclash.data.repository.SettingsRepository(applicationContext)

        // Listen for notification setting changes
        serviceScope.launch {
            settingsRepo.showNotification.collect { show ->
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

        // Run initial evaluation
        serviceScope.launch {
            ruleEngine.evaluateRules()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoClash")
            .setContentText("自动策略切换服务运行中")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
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
}
