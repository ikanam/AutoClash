package top.jarman.autoclash.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

private val Context.logDataStore: DataStore<Preferences> by preferencesDataStore(name = "log_settings")

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    fun toFormattedString(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = dateFormat.format(Date(timestamp))
        return "$date [${level.name}] $tag: $message"
    }
}

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

class LogRepository(private val context: Context) {

    companion object {
        private val KEY_LOG_ENABLED = booleanPreferencesKey("log_enabled")
        private const val MAX_LOG_LINES = 1000
        private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    }

    val logEnabled: Flow<Boolean> = context.logDataStore.data.map { prefs ->
        prefs[KEY_LOG_ENABLED] ?: true
    }

    suspend fun isLogEnabled(): Boolean {
        return context.logDataStore.data.first()[KEY_LOG_ENABLED] ?: true
    }

    suspend fun setLogEnabled(enabled: Boolean) {
        context.logDataStore.edit { prefs ->
            prefs[KEY_LOG_ENABLED] = enabled
        }
    }

    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        logQueue.offer(entry)

        // Keep only recent logs in memory
        while (logQueue.size > MAX_LOG_LINES) {
            logQueue.poll()
        }

        // Also write to file
        writeToFile(entry)
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    private fun writeToFile(entry: LogEntry) {
        try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val logFile = File(logDir, "autoclash_${dateFormat.format(Date())}.log")

            logFile.appendText(entry.toFormattedString() + "\n")

            // Clean old log files (keep last 7 days)
            cleanOldLogs(logDir)
        } catch (e: Exception) {
            // Silently fail - logging should not break the app
        }
    }

    private fun cleanOldLogs(logDir: File) {
        try {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            logDir.listFiles()?.forEach { file ->
                if (file.lastModified() < sevenDaysAgo) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Silently fail
        }
    }

    fun getLogFiles(): List<File> {
        val logDir = File(context.filesDir, "logs")
        return if (logDir.exists()) {
            logDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }

    fun getAllLogs(): String {
        val logs = StringBuilder()
        getLogFiles().forEach { file ->
            logs.append(file.readText())
        }
        return logs.toString()
    }

    fun getRecentLogs(maxLines: Int = 100): String {
        val logs = StringBuilder()
        getLogFiles().take(3).forEach { file ->
            logs.append(file.readText())
        }

        // Return only recent lines
        val allLines = logs.toString().lines()
        return allLines.takeLast(maxLines).joinToString("\n")
    }

    fun clearLogs() {
        getLogFiles().forEach { it.delete() }
        logQueue.clear()
    }
}
