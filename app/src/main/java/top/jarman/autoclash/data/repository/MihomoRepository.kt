package top.jarman.autoclash.data.repository

import top.jarman.autoclash.data.api.MihomoApi
import top.jarman.autoclash.data.api.ProxyDetail
import top.jarman.autoclash.data.api.SwitchProxyRequest
import kotlin.math.pow

class MihomoRepository(private val api: MihomoApi) {

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 500L
    }

    /**
     * Get all Selector-type proxy groups, ordered by config file order
     */
    suspend fun getSelectGroups(): Result<List<ProxyDetail>> {
        return try {
            val response = api.getProxies()
            if (response.isSuccessful) {
                val proxies = response.body()?.proxies ?: emptyMap()

                // Use GLOBAL group's "all" list to determine config order
                val globalOrder = proxies["GLOBAL"]?.all ?: emptyList()

                val selectGroups = proxies.values.filter {
                    it.type.equals("Selector", ignoreCase = true) && it.name != "GLOBAL"
                }

                // Sort by position in GLOBAL's all list; unknown items go to end
                val sorted = selectGroups.sortedBy { group ->
                    val index = globalOrder.indexOf(group.name)
                    if (index >= 0) index else Int.MAX_VALUE
                }

                Result.success(sorted)
            } else {
                Result.failure(Exception("API error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a single proxy group's detail
     */
    suspend fun getProxyGroup(name: String): Result<ProxyDetail> {
        return try {
            val response = api.getProxyGroup(name)
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Empty response"))
            } else {
                Result.failure(Exception("API error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Switch the selected proxy in a group with retry mechanism
     */
    suspend fun switchProxy(groupName: String, proxyName: String): Result<Unit> {
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                val response = api.switchProxy(groupName, SwitchProxyRequest(proxyName))
                if (response.isSuccessful) {
                    if (attempt > 1) {
                        // Log retry success
                        println("MihomoRepository: switchProxy succeeded on attempt $attempt")
                    }
                    return Result.success(Unit)
                } else {
                    lastException = Exception("API error: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                lastException = e
            }

            // Exponential backoff before retry
            if (attempt < MAX_RETRIES) {
                val delayMs = INITIAL_DELAY_MS * 2.0.pow(attempt - 1).toLong()
                kotlinx.coroutines.delay(delayMs)
            }
        }

        return Result.failure(lastException ?: Exception("Unknown error"))
    }

    /**
     * Test API connection
     */
    suspend fun testConnection(): Result<Boolean> {
        return try {
            val response = api.getProxies()
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("API error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
