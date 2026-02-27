package top.jarman.autoclash.data.api

import com.google.gson.annotations.SerializedName

/**
 * Response from GET /proxies
 */
data class ProxiesResponse(
    @SerializedName("proxies") val proxies: Map<String, ProxyDetail>
)

/**
 * Detail of a single proxy or proxy group
 */
data class ProxyDetail(
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("now") val now: String? = null,
    @SerializedName("all") val all: List<String>? = null,
    @SerializedName("history") val history: List<ProxyHistory>? = null,
    @SerializedName("udp") val udp: Boolean = false,
    @SerializedName("xudp") val xudp: Boolean = false
)

data class ProxyHistory(
    @SerializedName("time") val time: String,
    @SerializedName("delay") val delay: Int
)

/**
 * Request body for PUT /proxies/{name}
 */
data class SwitchProxyRequest(
    @SerializedName("name") val name: String
)
