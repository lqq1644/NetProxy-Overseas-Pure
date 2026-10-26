package com.fanjv.netproxy.feature.scenes.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.fanjv.netproxy.feature.scenes.model.SceneSnapshot
import com.fanjv.netproxy.feature.scenes.model.withScene
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val scenesJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/** 自定义场景的本地存取：只存管理器本地偏好，不写入模块配置。 */
internal class ScenePreferenceStore(
    private val prefs: SharedPreferences
) {
    fun scenes(): List<SceneSnapshot> = decodeScenes(prefs.getString(KEY, null))

    fun save(scene: SceneSnapshot) {
        persist(scenes().withScene(scene))
    }

    fun delete(name: String) {
        persist(scenes().filterNot { it.name == name })
    }

    private fun persist(scenes: List<SceneSnapshot>) {
        prefs.edit { putString(KEY, encodeScenes(scenes)) }
    }

    private companion object {
        const val KEY = "custom_scenes"
    }
}

/** 场景列表与偏好字符串之间的纯转换，供存取与单测共用。 */
internal fun encodeScenes(scenes: List<SceneSnapshot>): String =
    scenesJson.encodeToString(scenes)

/** 解码失败（旧格式、损坏内容）按空列表处理，不阻断界面。 */
internal fun decodeScenes(raw: String?): List<SceneSnapshot> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { scenesJson.decodeFromString<List<SceneSnapshot>>(raw) }.getOrDefault(emptyList())
}
