package com.fanjv.netproxy.feature.apps.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** 跟随主节点的保留出站值；绑定选择它时删除绑定，不写入 bindings。 */
internal const val APP_BINDING_FOLLOW_MAIN = "Proxy"

/** 直连的保留出站值。 */
internal const val APP_BINDING_DIRECT = "direct"

@Serializable
internal data class AppBinding(
    val app: String,
    val outbound: String,
)

@Serializable
internal data class AppProxyConfig(
    val enabled: Boolean = false,
    val mode: String = "blacklist",
    @SerialName("proxy_apps") val proxyApps: List<String> = emptyList(),
    @SerialName("bypass_apps") val bypassApps: List<String> = emptyList(),
    val bindings: List<AppBinding> = emptyList(),
    @Transient val revision: String = "",
)

/** 返回写入指定出站后的绑定列表：同一应用引用只保留一条，跟随主节点即删除该应用的绑定。 */
internal fun List<AppBinding>.withBinding(app: String, outbound: String): List<AppBinding> {
    val kept = filterNot { it.app == app }
    return if (outbound == APP_BINDING_FOLLOW_MAIN) kept else kept + AppBinding(app, outbound)
}
