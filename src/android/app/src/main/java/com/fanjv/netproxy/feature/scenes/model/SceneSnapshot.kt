package com.fanjv.netproxy.feature.scenes.model

import com.fanjv.netproxy.feature.apps.model.APP_BINDING_DIRECT
import com.fanjv.netproxy.feature.apps.model.APP_BINDING_FOLLOW_MAIN
import com.fanjv.netproxy.feature.apps.model.AppBinding
import com.fanjv.netproxy.feature.apps.model.AppProxyConfig
import kotlinx.serialization.Serializable

/**
 * 自定义场景：一次保存的完整分流快照，恢复时原样写回。
 *
 * mode 是出站模式的内核值；appConfig 是整份分应用配置（含白名单成员与绑定）；
 * selectionNodeTag 为空表示自动选择，否则为该分组内的节点 tag。
 */
@Serializable
internal data class SceneSnapshot(
    val name: String,
    val mode: String,
    val appConfig: AppProxyConfig = AppProxyConfig(),
    val selectionGroupId: String = "",
    val selectionNodeTag: String = "",
)

/** 保存时清洗绑定：跟随主节点本来就不写入 bindings，出现时按无绑定处理。 */
internal fun List<AppBinding>.sanitizedSceneBindings(): List<AppBinding> =
    filterNot { it.outbound == APP_BINDING_FOLLOW_MAIN }

/** 恢复时过滤绑定：只保留出站仍存在的绑定，返回（保留的绑定，跳过的条数）。 */
internal fun List<AppBinding>.validSceneBindings(validNodeTags: Set<String>): Pair<List<AppBinding>, Int> {
    val kept = filter { it.outbound == APP_BINDING_DIRECT || it.outbound in validNodeTags }
    return kept to (size - kept.size)
}

/** 场景模式在界面上的显示文案：规则与规则（允许广告）同属应用分流一档。 */
internal fun sceneModeLabel(
    mode: String,
    splitLabel: String,
    proxyLabel: String,
    directLabel: String,
): String = when (mode) {
    "Rule", "RuleAllowAds" -> splitLabel
    "Proxy" -> proxyLabel
    "Direct" -> directLabel
    else -> mode
}

/** 同名场景保存时替换原位，否则追加到末尾。 */
internal fun List<SceneSnapshot>.withScene(scene: SceneSnapshot): List<SceneSnapshot> {
    val index = indexOfFirst { it.name == scene.name }
    if (index < 0) return this + scene
    return toMutableList().also { it[index] = scene }
}

/** 从目录快照选择中解析场景的节点选择：自动选择时节点 tag 为空。 */
internal fun selectionNodeTag(selectorMode: String, activeGroupId: String, selected: String): String {
    if (selectorMode == "urltest") return ""
    val prefix = "$activeGroupId/"
    if (!selected.startsWith(prefix)) return ""
    return selected.removePrefix(prefix)
}
