package com.fanjv.netproxy.feature.apps.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProxyConfigTest {
    private val json = Json { encodeDefaults = true }

    private fun decode(content: String): AppProxyConfig =
        json.decodeFromJsonElement(AppProxyConfig.serializer(), json.parseToJsonElement(content))

    @Test fun legacyJsonWithoutBindingsDecodesToEmptyBindings() {
        val config = decode(
            """{"enabled":true,"mode":"whitelist","proxy_apps":["0:com.example.client"],"bypass_apps":[]}"""
        )
        assertTrue(config.enabled)
        assertEquals("whitelist", config.mode)
        assertEquals(listOf("0:com.example.client"), config.proxyApps)
        assertEquals(emptyList<AppBinding>(), config.bindings)
    }

    @Test fun jsonWithBindingsDecodesEntries() {
        val config = decode(
            """{"enabled":true,"mode":"whitelist","proxy_apps":[],"bypass_apps":[],""" +
                """"bindings":[{"app":"0:com.tmri.app.main","outbound":"China"},""" +
                """{"app":"10:com.example.client","outbound":"direct"}]}"""
        )
        assertEquals(
            listOf(
                AppBinding("0:com.tmri.app.main", "China"),
                AppBinding("10:com.example.client", "direct"),
            ),
            config.bindings
        )
    }

    @Test fun encodingKeepsBindingsAndRoundTrips() {
        val config = AppProxyConfig(
            enabled = true,
            mode = "whitelist",
            proxyApps = listOf("0:com.tmri.app.main"),
            bindings = listOf(AppBinding("0:com.tmri.app.main", "China")),
        )
        val encoded = json.encodeToString(mapOf("app" to config))
        val app = json.parseToJsonElement(encoded).jsonObject.getValue("app")
        assertEquals(config, json.decodeFromJsonElement(AppProxyConfig.serializer(), app))
    }

    @Test fun withBindingAddsNewEntry() {
        val updated = emptyList<AppBinding>().withBinding("0:com.example.a", "China")
        assertEquals(listOf(AppBinding("0:com.example.a", "China")), updated)
    }

    @Test fun withBindingOverwritesSameAppAndKeepsOthers() {
        val bindings = listOf(
            AppBinding("0:com.example.a", "China"),
            AppBinding("0:com.example.b", "JP"),
        )
        val updated = bindings.withBinding("0:com.example.a", "HK")
        assertEquals(
            listOf(AppBinding("0:com.example.b", "JP"), AppBinding("0:com.example.a", "HK")),
            updated
        )
    }

    @Test fun withBindingFollowMainRemovesEntry() {
        val bindings = listOf(
            AppBinding("0:com.example.a", "China"),
            AppBinding("0:com.example.b", "JP"),
        )
        assertEquals(
            listOf(AppBinding("0:com.example.b", "JP")),
            bindings.withBinding("0:com.example.a", APP_BINDING_FOLLOW_MAIN)
        )
        assertEquals(
            emptyList<AppBinding>(),
            emptyList<AppBinding>().withBinding("0:com.example.a", APP_BINDING_FOLLOW_MAIN)
        )
    }

    @Test fun withBindingDirectStoresReservedOutbound() {
        val updated = emptyList<AppBinding>().withBinding("0:com.example.a", APP_BINDING_DIRECT)
        assertEquals(listOf(AppBinding("0:com.example.a", "direct")), updated)
    }
}
