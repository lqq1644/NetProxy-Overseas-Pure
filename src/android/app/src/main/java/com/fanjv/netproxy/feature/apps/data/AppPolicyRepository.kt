package com.fanjv.netproxy.feature.apps.data

import com.fanjv.netproxy.feature.apps.model.AppProxyConfig
import com.fanjv.netproxy.feature.settings.data.ConfigRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** 分应用代理策略的数据入口。 */
internal class AppPolicyRepository(
    private val configs: ConfigRepository
) {
    private val json = Json { encodeDefaults = true }

    suspend fun config(): AppProxyConfig {
        val snapshot = configs.readSnapshot("inbound/app")
        val app = json.parseToJsonElement(snapshot.content).jsonObject.getValue("app").jsonObject
        // bindings 是可选字段：旧模块文件没有它时按空绑定处理，出现时必须与四个既有字段一起出现。
        require(app.keys.containsAll(setOf("enabled", "mode", "proxy_apps", "bypass_apps")) &&
            app.keys.all { it in setOf("enabled", "mode", "proxy_apps", "bypass_apps", "bindings") }) {
            "模块返回的应用策略字段不完整"
        }
        return json.decodeFromJsonElement(AppProxyConfig.serializer(), app)
            .copy(revision = snapshot.revision)
    }

    suspend fun apply(config: AppProxyConfig): AppProxyConfig {
        val content = json.encodeToString(mapOf("app" to config))
        val revision = configs.apply("inbound/app", content, config.revision)
        return config.copy(revision = revision)
    }
}
