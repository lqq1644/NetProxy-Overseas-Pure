package com.fanjv.netproxy.feature.scenes

import com.fanjv.netproxy.feature.apps.model.AppBinding
import com.fanjv.netproxy.feature.apps.model.AppProxyConfig
import com.fanjv.netproxy.feature.scenes.data.decodeScenes
import com.fanjv.netproxy.feature.scenes.data.encodeScenes
import com.fanjv.netproxy.feature.scenes.model.SceneSnapshot
import com.fanjv.netproxy.feature.scenes.model.sanitizedSceneBindings
import com.fanjv.netproxy.feature.scenes.model.sceneModeLabel
import com.fanjv.netproxy.feature.scenes.model.selectionNodeTag
import com.fanjv.netproxy.feature.scenes.model.validSceneBindings
import com.fanjv.netproxy.feature.scenes.model.withScene
import org.junit.Assert.assertEquals
import org.junit.Test

class ScenesTest {
    private val scene = SceneSnapshot(
        name = "日常",
        mode = "Rule",
        appConfig = AppProxyConfig(
            enabled = true,
            mode = "whitelist",
            proxyApps = listOf("0:com.example.app"),
            bindings = listOf(AppBinding("0:com.example.app", "China")),
        ),
        selectionGroupId = "g1",
        selectionNodeTag = "China",
    )

    @Test fun encodeDecodeRoundTrip() {
        assertEquals(listOf(scene), decodeScenes(encodeScenes(listOf(scene))))
    }

    @Test fun decodeCorruptIsEmpty() {
        assertEquals(emptyList<SceneSnapshot>(), decodeScenes("{not json"))
        assertEquals(emptyList<SceneSnapshot>(), decodeScenes(null))
        assertEquals(emptyList<SceneSnapshot>(), decodeScenes("  "))
    }

    @Test fun sanitizeDropsFollowMain() {
        val bindings = listOf(
            AppBinding("0:a", "Proxy"),
            AppBinding("0:b", "China"),
        )
        assertEquals(listOf(AppBinding("0:b", "China")), bindings.sanitizedSceneBindings())
    }

    @Test fun validBindingsKeepDirectAndExistingTags() {
        val bindings = listOf(
            AppBinding("0:a", "China"),
            AppBinding("0:b", "direct"),
            AppBinding("0:c", "Gone"),
        )
        val (kept, skipped) = bindings.validSceneBindings(setOf("China", "JP"))
        assertEquals(
            listOf(AppBinding("0:a", "China"), AppBinding("0:b", "direct")),
            kept,
        )
        assertEquals(1, skipped)
    }

    @Test fun withSceneReplacesInPlaceOtherwiseAppends() {
        val renamed = scene.copy(mode = "Proxy")
        assertEquals(listOf(renamed), listOf(scene).withScene(renamed))
        assertEquals(listOf(scene, renamed.copy(name = "出差")), listOf(scene).withScene(renamed.copy(name = "出差")))
    }

    @Test fun sceneModeLabelCollapsesSplitModes() {
        assertEquals("SPLIT", sceneModeLabel("Rule", "SPLIT", "PROXY", "DIRECT"))
        assertEquals("SPLIT", sceneModeLabel("RuleAllowAds", "SPLIT", "PROXY", "DIRECT"))
        assertEquals("PROXY", sceneModeLabel("Proxy", "SPLIT", "PROXY", "DIRECT"))
        assertEquals("DIRECT", sceneModeLabel("Direct", "SPLIT", "PROXY", "DIRECT"))
        assertEquals("Custom", sceneModeLabel("Custom", "SPLIT", "PROXY", "DIRECT"))
    }

    @Test fun selectionNodeTagParsing() {
        assertEquals("", selectionNodeTag("urltest", "g1", "g1/China"))
        assertEquals("China", selectionNodeTag("manual", "g1", "g1/China"))
        assertEquals("", selectionNodeTag("manual", "g1", "g2/China"))
        assertEquals("", selectionNodeTag("manual", "g1", "China"))
    }
}
