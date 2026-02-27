package top.jarman.autoclash.data.repository

import top.jarman.autoclash.data.api.MihomoApi
import top.jarman.autoclash.data.api.ProxyDetail
import top.jarman.autoclash.data.api.SwitchProxyRequest

class MihomoRepository(private val api: MihomoApi) {

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
     * Switch the selected proxy in a group
     */
    suspend fun switchProxy(groupName: String, proxyName: String): Result<Unit> {
        return try {
            val response = api.switchProxy(groupName, SwitchProxyRequest(proxyName))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("API error: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
