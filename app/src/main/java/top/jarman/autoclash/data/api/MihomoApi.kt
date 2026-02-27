package top.jarman.autoclash.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Mihomo RESTful API service interface
 */
interface MihomoApi {

    /**
     * Get all proxies and proxy groups
     */
    @GET("proxies")
    suspend fun getProxies(): Response<ProxiesResponse>

    /**
     * Get a single proxy group detail
     */
    @GET("proxies/{name}")
    suspend fun getProxyGroup(@Path("name") name: String): Response<ProxyDetail>

    /**
* Switch the selected proxy in a Selector group
*/
@PUT("proxies/{name}")
suspend fun switchProxy(
    @Path("name") groupName: String,
    @Body request: SwitchProxyRequest
): Response<Unit>
}
